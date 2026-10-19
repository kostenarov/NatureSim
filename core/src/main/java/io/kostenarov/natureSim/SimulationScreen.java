package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.GenderComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.VelocityComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.FoodSourceComponent;
import io.kostenarov.natureSim.Components.ReproductionComponent;
import io.kostenarov.natureSim.Components.PredatorComponent;
import io.kostenarov.natureSim.Components.WaterSourceComponent;
import io.kostenarov.natureSim.Systems.CameraSystem;
import io.kostenarov.natureSim.Systems.CollisionSystem;
import io.kostenarov.natureSim.Systems.DeathSystem;
import io.kostenarov.natureSim.Systems.SpatialIndexSystem;
import io.kostenarov.natureSim.Systems.WaterSourceSystem;
import io.kostenarov.natureSim.Systems.PredatorSystem;
import io.kostenarov.natureSim.Systems.MovementSystem;
import io.kostenarov.natureSim.Systems.VisionRenderingSystem;
import io.kostenarov.natureSim.Systems.FoodSourceSystem;
import io.kostenarov.natureSim.Systems.MatingSystem;
import space.earlygrey.shapedrawer.ShapeDrawer;

/**
 * Runs and renders one simulation built from a {@link SimulationConfig}. Esc returns to the start screen.
 */
public class SimulationScreen extends ScreenAdapter {
    private final Main game;
    private final SimulationConfig config;
    private SpriteBatch batch;
    private Texture agentTexture;
    private Texture predatorTexture;
    private Texture foodTexture;
    private Engine engine;
    private OrthographicCamera camera;
    private OrthographicCamera uiCamera;
    private ShapeDrawer shapeDrawer;
    private Texture whitePixel;
    private BitmapFont font;
    private Entity selectedEntity;
    private Texture fieldTexture;
    private com.badlogic.gdx.graphics.g2d.TextureRegion tileRegion;
    private static final float TILE_SIZE = 40f;
    // Colors are created once; allocating them per creature per frame made a lot of garbage
    private final Color waterColor = new Color(0.20f, 0.45f, 0.85f, 0.85f);
    private final Color shoreColor = new Color(0.75f, 0.68f, 0.45f, 1f);
    private final Color visionColor = new Color(0.72f, 0.62f, 0.42f, 0.22f); // Sandy tone, 22% opacity
    private final Color visionFillColor = new Color(0.72f, 0.62f, 0.42f, 0.12f);
    private final Color scentFillColor = new Color(1f, 0.1f, 0.1f, 0.10f);
    private final Color scentOutlineColor = new Color(1f, 0.1f, 0.1f, 0.25f);
    private final Color targetColor = new Color(1f, 0.2f, 0.2f, 0.6f);
    private final Color panelBg = new Color(0.08f, 0.08f, 0.08f, 0.7f);
    private final Color panelBorder = new Color(0.6f, 0.6f, 0.6f, 0.8f);
    private final Vector3 touchPoint = new Vector3();

    // Simulation speed: fixed steps, run more of them per frame to speed up
    private static final float SIM_STEP = 1f / 60f;
    private static final float MAX_FRAME_TIME = 0.25f;    // Ignore huge frame gaps (e.g. window dragged)
    private static final int MAX_STEPS_PER_FRAME = 40;
    private static final float[] SPEEDS = {1f, 2f, 5f, 10f, 20f}; // Keys 1-5
    private int speedIndex = 0;
    private boolean paused = false;
    private float accumulator = 0f;
    private CameraSystem cameraSystem;

    // Cached entity lists for rendering, and the visible world area this frame
    private ImmutableArray<Entity> pondEntities;
    private ImmutableArray<Entity> visionEntities;
    private ImmutableArray<Entity> predatorEntities;
    private ImmutableArray<Entity> foodEntities;
    private ImmutableArray<Entity> creatureEntities;
    private float visibleLeft;
    private float visibleRight;
    private float visibleBottom;
    private float visibleTop;
    private final float screenWidth = 2560f;
    private final float screenHeight = 1440f;
    private float currentScreenWidth;
    private float currentScreenHeight;

    public SimulationScreen(Main game, SimulationConfig config) {
        this.game = game;
        this.config = config;

        batch = new SpriteBatch();

        initTextures();

        initShapeDrawer();

        initCameras();

        font = new BitmapFont();

        initEngineAndSystems();

        createInitialAgents();

        currentScreenWidth = screenWidth;
        currentScreenHeight = screenHeight;
    }

    private void initTextures() {
        agentTexture = new Texture("agent.png");
        predatorTexture = new Texture("agentPredator.png");

        // Create a simple green circle texture for food
        Pixmap foodPixmap = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        foodPixmap.setColor(0.2f, 0.8f, 0.2f, 1f);
        foodPixmap.fillCircle(8, 8, 7);
        foodTexture = new Texture(foodPixmap);
        foodPixmap.dispose();

        fieldTexture = new Texture(Gdx.files.internal("field.png"), true);
        fieldTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        int regionW = Math.min(fieldTexture.getWidth(), (int) TILE_SIZE);
        int regionH = Math.min(fieldTexture.getHeight(), (int) TILE_SIZE);
        tileRegion = new TextureRegion(fieldTexture, 0, 0, regionW, regionH);
    }

    private void initShapeDrawer() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(1, 1, 1, 1); // White with full opacity
        pixmap.fill();
        whitePixel = new Texture(pixmap);
        pixmap.dispose();
        shapeDrawer = new ShapeDrawer(batch, new TextureRegion(whitePixel));
    }

    private void initCameras() {
        camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f, 0);
        camera.update();

        uiCamera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiCamera.position.set(Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f, 0);
        uiCamera.update();
    }

    private void initEngineAndSystems() {
        engine = new Engine();
        engine.addSystem(new SpatialIndexSystem()); // Always runs first each step
        // Ponds first: food grows around them
        engine.addSystem(new WaterSourceSystem(config));
        engine.addSystem(new FoodSourceSystem(config));
        engine.addSystem(new MatingSystem(config));
        engine.addSystem(new PredatorSystem());
        engine.addSystem(new MovementSystem());
        if (config.collisionEnabled) {
            engine.addSystem(new CollisionSystem());
        }
        engine.addSystem(new DeathSystem());
        // Not added to the engine: the camera is updated with real time, outside the fixed simulation steps.
        cameraSystem = new CameraSystem(camera, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        pondEntities = engine.getEntitiesFor(WaterSourceSystem.WATER_FAMILY);
        visionEntities = engine.getEntitiesFor(Family.all(PositionComponent.class, VisionComponent.class).get());
        predatorEntities = engine.getEntitiesFor(Family.all(PositionComponent.class, PredatorComponent.class).get());
        foodEntities = engine.getEntitiesFor(Family.all(FoodSourceComponent.class, PositionComponent.class).get());
        creatureEntities = engine.getEntitiesFor(Family.all(PositionComponent.class, StatsComponent.class).get());
        engine.addSystem(new VisionRenderingSystem(shapeDrawer, camera));

        // Drop the stats panel when the selected creature dies.
        engine.addEntityListener(new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
            }

            @Override
            public void entityRemoved(Entity entity) {
                if (entity == selectedEntity) {
                    selectedEntity = null;
                }
            }
        });
    }

    private void createInitialAgents() {
        float spawnMin = 200f;
        float spawnRange = MovementSystem.getMapMaxX() - 2 * spawnMin;

        // Starting gene ranges keep the first generation close to the old hand-tuned creatures;
        // all other genes are random (0 to 2) and evolution takes it from there.
        for (int i = 0; i < config.preyCount; i++) {
            GenomeComponent dna = new GenomeComponent();
            dna.genes[GenomeComponent.SPEED] = MathUtils.random(0.35f, 0.85f);   // ~120-220 px/s
            dna.genes[GenomeComponent.VISION] = MathUtils.random(0.1f, 1.4f);    // ~90-235 px
            createCreature(MathUtils.random(spawnMin, spawnMin + spawnRange), MathUtils.random(spawnMin, spawnMin + spawnRange), dna);
        }

        for (int i = 0; i < config.predatorCount; i++) {
            GenomeComponent dna = new GenomeComponent();
            dna.genes[GenomeComponent.SPEED] = MathUtils.random(0.7f, 0.85f);    // ~190-220 px/s
            dna.genes[GenomeComponent.VISION] = MathUtils.random(1.3f, 2f);      // ~225-300 px
            Entity predator = createCreature(MathUtils.random(spawnMin, spawnMin + spawnRange), MathUtils.random(spawnMin, spawnMin + spawnRange), dna);
            makePredator(predator, dna);
        }
    }

    /** Builds a prey creature whose speed and vision come from its genes, and adds it to the engine. */
    private Entity createCreature(float x, float y, GenomeComponent dna) {
        Entity creature = engine.createEntity();

        PositionComponent pos = new PositionComponent();
        pos.position.set(x, y);
        creature.add(pos);

        creature.add(new VelocityComponent(Traits.speed(dna)));
        creature.add(dna);
        creature.add(new GenderComponent());
        creature.add(new StatsComponent());

        VisionComponent vision = new VisionComponent(Traits.visionRange(dna), Traits.visionAngle(dna));
        BehaviourComponent behaviour = new BehaviourComponent();
        behaviour.directionAngle = MathUtils.random(0f, 360f);
        vision.directionAngle = behaviour.directionAngle;
        creature.add(vision);
        creature.add(behaviour);

        creature.add(new ReproductionComponent());

        engine.addEntity(creature);
        return creature;
    }

    private void makePredator(Entity predator, GenomeComponent dna) {
        PredatorComponent predatorComponent = new PredatorComponent();
        predatorComponent.attackRange = 30f;
        predatorComponent.attackDamage = 40f;
        predatorComponent.killEnergyGain = 45f;
        predatorComponent.huntSpeedMultiplier = 1.35f;
        predatorComponent.huntEnergyBufferMultiplier = 1.15f;
        predatorComponent.scentRange = Traits.scentRange(dna);
        predatorComponent.scentReachThreshold = 24f;
        predatorComponent.scentSpeedMultiplier = 1.0f;
        predator.add(predatorComponent);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            // Switching screens disposes this one, so stop rendering right away.
            game.showStartScreen();
            return;
        }

        ScreenUtils.clear(0.30f, 0.40f, 0.30f, 1f);

        updateEngineAndInput();

        renderBackgroundTiles();
        renderEntities();
        renderUI();
    }

    private void updateEngineAndInput() {
        float realDelta = Math.min(Gdx.graphics.getDeltaTime(), MAX_FRAME_TIME);
        handleSpeedKeys();

        // The camera follows real time so it stays responsive while the simulation is paused or sped up.
        cameraSystem.update(realDelta);

        // Fixed-size simulation steps: results no longer depend on frame rate, and speed-up just runs more steps.
        if (!paused) {
            accumulator += realDelta * SPEEDS[speedIndex];
            int steps = 0;
            while (accumulator >= SIM_STEP && steps < MAX_STEPS_PER_FRAME) {
                engine.update(SIM_STEP);
                accumulator -= SIM_STEP;
                steps++;
            }
            if (steps == MAX_STEPS_PER_FRAME) {
                accumulator = 0f; // Can't keep up at this speed; drop the backlog instead of spiralling
            }
        }

        if (Gdx.input.justTouched()) {
            float screenX = Gdx.input.getX();
            float screenY = Gdx.input.getY();
            selectEntityAtScreen(screenX, screenY);
        }
    }

    private void handleSpeedKeys() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.P)) {
            paused = !paused;
        }
        for (int i = 0; i < SPEEDS.length; i++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) {
                speedIndex = i;
                paused = false;
            }
        }
    }

    /** Works out which part of the world the camera currently shows, for culling. */
    private void updateVisibleArea() {
        float halfWidth = camera.viewportWidth * camera.zoom / 2f;
        float halfHeight = camera.viewportHeight * camera.zoom / 2f;
        visibleLeft = camera.position.x - halfWidth;
        visibleRight = camera.position.x + halfWidth;
        visibleBottom = camera.position.y - halfHeight;
        visibleTop = camera.position.y + halfHeight;
    }

    /** True if something within {@code radius} of (x, y) could be on screen. */
    private boolean isVisible(float x, float y, float radius) {
        return x + radius >= visibleLeft && x - radius <= visibleRight
            && y + radius >= visibleBottom && y - radius <= visibleTop;
    }

    private void renderBackgroundTiles() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        float mapWidth = MovementSystem.getMapMaxX();
        float mapHeight = MovementSystem.getMapMaxY();

        float halfViewportW = (camera.viewportWidth * camera.zoom) / 2f;
        float halfViewportH = (camera.viewportHeight * camera.zoom) / 2f;
        float visibleLeft = camera.position.x - halfViewportW;
        float visibleRight = camera.position.x + halfViewportW;
        float visibleBottom = camera.position.y - halfViewportH;
        float visibleTop = camera.position.y + halfViewportH;

        int startIx = (int) Math.floor(Math.max(0f, visibleLeft) / TILE_SIZE);
        int endIx = (int) Math.floor(Math.min(mapWidth - 1f, visibleRight) / TILE_SIZE);
        int startIy = (int) Math.floor(Math.max(0f, visibleBottom) / TILE_SIZE);
        int endIy = (int) Math.floor(Math.min(mapHeight - 1f, visibleTop) / TILE_SIZE);

        for (int ix = startIx; ix <= endIx; ix++) {
            float x = ix * TILE_SIZE;
            float tileWidth = Math.min(TILE_SIZE, mapWidth - x);
            for (int iy = startIy; iy <= endIy; iy++) {
                float y = iy * TILE_SIZE;
                float tileHeight = Math.min(TILE_SIZE, mapHeight - y);

                int srcW = Math.max(1, Math.min((int) tileWidth, tileRegion.getRegionWidth()));
                int srcH = Math.max(1, Math.min((int) tileHeight, tileRegion.getRegionHeight()));
                TextureRegion srcRegion = (srcW == tileRegion.getRegionWidth() && srcH == tileRegion.getRegionHeight())
                        ? tileRegion
                        : new TextureRegion(fieldTexture, 0, 0, srcW, srcH);

                batch.draw(srcRegion, x, y, tileWidth, tileHeight);
            }
        }
        batch.end();
    }

    private void renderEntities() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.flush();
        updateVisibleArea();

        // Everything below skips what is off screen; drawing all of it was the main rendering cost.
        // Draw ponds underneath everything else (their position is the pond center)
        for (Entity entity : pondEntities) {
            PositionComponent pos = entity.getComponent(PositionComponent.class);
            WaterSourceComponent water = entity.getComponent(WaterSourceComponent.class);
            if (isVisible(pos.position.x, pos.position.y, water.radius)) {
                drawFilledCircle(pos.position.x, pos.position.y, water.radius, waterColor);
                drawCircleOutline(pos.position.x, pos.position.y, water.radius, shoreColor);
            }
        }

        batch.flush();

        if (config.showVisionCones) {
            for (Entity entity : visionEntities) {
                PositionComponent pos = entity.getComponent(PositionComponent.class);
                VisionComponent vision = entity.getComponent(VisionComponent.class);
                if (isVisible(pos.position.x + 16f, pos.position.y + 16f, vision.visionRange)) {
                    drawVisionCone(pos.position.x + 16, pos.position.y + 16, vision);
                }
            }
        }

        batch.flush();

        // Draw predator scent ranges / smell targets
        if (config.showScentRanges) {
            for (Entity entity : predatorEntities) {
                PositionComponent pos = entity.getComponent(PositionComponent.class);
                PredatorComponent predator = entity.getComponent(PredatorComponent.class);
                if (isVisible(pos.position.x + 16f, pos.position.y + 16f, predator.scentRange)) {
                    drawScentRange(pos.position.x + 16f, pos.position.y + 16f, predator);
                }
            }
        }

        batch.flush();

        // Draw food sources
        for (Entity entity : foodEntities) {
            PositionComponent pos = entity.getComponent(PositionComponent.class);
            if (isVisible(pos.position.x, pos.position.y, 16f)) {
                batch.draw(foodTexture, pos.position.x, pos.position.y);
            }
        }

        // Draw the creatures
        batch.setColor(Color.WHITE);
        for (Entity entity : creatureEntities) {
            PositionComponent pos = entity.getComponent(PositionComponent.class);
            if (isVisible(pos.position.x, pos.position.y, 32f)) {
                Texture texture = entity.getComponent(PredatorComponent.class) != null ? predatorTexture : agentTexture;
                batch.draw(texture, pos.position.x, pos.position.y);
            }
        }
        batch.end();
    }

    private void renderUI() {
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        drawPopulationCounter();
        drawStatsPanel();
        batch.end();
    }

    private void drawPopulationCounter() {
        String speed = paused ? "PAUSED" : (int) SPEEDS[speedIndex] + "x speed";

        font.setColor(Color.WHITE);
        font.getData().setScale(2f);
        font.draw(batch, "Population: " + creatureEntities.size() + "    " + speed, 20f, uiCamera.position.y + (uiCamera.viewportHeight / 2f) - 20f);

        font.getData().setScale(1.5f);
        font.draw(batch, "Esc: settings   P: pause   1-5: speed   WASD: move   Q/E: zoom   Space: follow   Click: inspect", 20f, 30f);
    }

    private void selectEntityAtScreen(float screenX, float screenY) {
        Vector3 worldCoords = camera.unproject(touchPoint.set(screenX, screenY, 0));

        Entity picked = null;
        for (Entity entity : engine.getEntitiesFor(Family.all(PositionComponent.class, StatsComponent.class).get())) {
            PositionComponent pos = entity.getComponent(PositionComponent.class);
            float x = pos.position.x;
            float y = pos.position.y;
            float size = 32f;

            if (worldCoords.x >= x && worldCoords.x <= x + size && worldCoords.y >= y && worldCoords.y <= y + size) {
                picked = entity;
                break;
            }
        }

        selectedEntity = picked;
    }

    private void drawStatsPanel() {
        if (selectedEntity == null) {
            return;
        }

        StatsComponent stats = selectedEntity.getComponent(StatsComponent.class);
        GenderComponent gender = selectedEntity.getComponent(GenderComponent.class);
        BehaviourComponent behaviour = selectedEntity.getComponent(BehaviourComponent.class);

        if (stats == null) {
            return;
        }

        if(gender == null) {
            return;
        }

        float widthScale = currentScreenWidth / screenWidth;
        float heightScale = currentScreenHeight / screenHeight;

        float panelWidth = 440f * widthScale;
        float panelHeight = 280f * heightScale;
        float padding = 20f * heightScale;
        float topY = uiCamera.position.y + (uiCamera.viewportHeight / 2f);
        float y = topY - panelHeight - padding;


        shapeDrawer.filledRectangle(padding, y, panelWidth, panelHeight, panelBg);
        shapeDrawer.rectangle(padding, y, panelWidth, panelHeight, panelBorder, 1f);

        font.setColor(Color.WHITE);
        font.getData().setScale(2.5f * widthScale * heightScale); // Scale font based on current screen width
        float textX = padding + 10f;
        float textY = y + panelHeight - 10f;

        font.draw(batch, "STATS", textX, textY);
        textY -= 50f * heightScale;
        font.draw(batch, "Hunger: " + (int) stats.hunger, textX, textY);
        textY -= 40f * heightScale;
        font.draw(batch, "Thirst: " + (int) stats.thirst, textX, textY);
        textY -= 40f * heightScale;
        font.draw(batch, "Energy: " + (int) stats.energy, textX, textY);
        textY -= 40f * heightScale;
        font.draw(batch, "Health: " + (int) stats.health, textX, textY);
        textY -= 40f * heightScale;
        font.draw(batch, "Gender: " + gender.gender, textX, textY);
        textY -= 40f * heightScale;
        font.draw(batch, "Role: " + (selectedEntity.getComponent(PredatorComponent.class) != null ? "Predator" : "Prey"), textX, textY);
        textY -= 40f * heightScale;
        font.draw(batch, "Speed: " + (int) Traits.speed(selectedEntity.getComponent(GenomeComponent.class)), textX, textY);
        textY -= 40f * heightScale;
        font.draw(batch, "Behaviour: " + behaviour.behaviour, textX, textY);
    }

    /**
     * Draw a cone representing the agent's vision field
     */
    private void drawVisionCone(float centerX, float centerY, VisionComponent vision) {
        float coneAngle = vision.visionAngle;
        float visionRange = vision.visionRange;
        float directionAngle = vision.directionAngle;

        // Calculate the start and end angles of the cone
        float startAngle = directionAngle - (coneAngle / 2);
        float endAngle = directionAngle + (coneAngle / 2);

        // Set color to semi-transparent sandy/dirty tone

        // Draw lines from center to the edges of the cone
        float startRad = (float) Math.toRadians(startAngle);
        float endRad = (float) Math.toRadians(endAngle);

        float startX = centerX + visionRange * (float) Math.cos(startRad);
        float startY = centerY + visionRange * (float) Math.sin(startRad);
        float endX = centerX + visionRange * (float) Math.cos(endRad);
        float endY = centerY + visionRange * (float) Math.sin(endRad);

        // Draw the two edge lines of the cone
        shapeDrawer.line(centerX, centerY, startX, startY, visionColor, 2f);
        shapeDrawer.line(centerX, centerY, endX, endY, visionColor, 2f);

        // Draw an arc for the outer edge of the cone
        drawArc(centerX, centerY, visionRange, startAngle, endAngle, visionColor);

        // Fill the cone with a semi-transparent polygon
        drawFilledCone(centerX, centerY, visionRange, startAngle, endAngle, visionColor);
    }

    private void drawArc(float centerX, float centerY, float radius, float startAngle, float endAngle, Color color) {
        int segments = (int) ((endAngle - startAngle) / 5f) + 1; // 5 degree segments
        segments = Math.max(segments, 2);

        float angle = startAngle;
        float angleStep = (endAngle - startAngle) / segments;

        for (int i = 0; i < segments; i++) {
            float currentAngle = (float) Math.toRadians(angle);
            float nextAngle = (float) Math.toRadians(angle + angleStep);

            float x1 = centerX + radius * (float) Math.cos(currentAngle);
            float y1 = centerY + radius * (float) Math.sin(currentAngle);
            float x2 = centerX + radius * (float) Math.cos(nextAngle);
            float y2 = centerY + radius * (float) Math.sin(nextAngle);

            shapeDrawer.line(x1, y1, x2, y2, color, 2f);

            angle += angleStep;
        }
    }

    private void drawFilledCone(float centerX, float centerY, float radius, float startAngle, float endAngle, Color color) {
        // Semi-transparent version for fill

        int segments = (int) ((endAngle - startAngle) / 5f) + 1; // 5 degree segments
        segments = Math.max(segments, 2);

        float angle = startAngle;
        float angleStep = (endAngle - startAngle) / segments;

        for (int i = 0; i < segments; i++) {
            float currentAngle = (float) Math.toRadians(angle);
            float nextAngle = (float) Math.toRadians(angle + angleStep);

            float x1 = centerX + radius * (float) Math.cos(currentAngle);
            float y1 = centerY + radius * (float) Math.sin(currentAngle);
            float x2 = centerX + radius * (float) Math.cos(nextAngle);
            float y2 = centerY + radius * (float) Math.sin(nextAngle);

            // Draw a triangle from center to the arc segment
            drawFilledTriangle(centerX, centerY, x1, y1, x2, y2, visionFillColor);

            angle += angleStep;
        }
    }

    private void drawFilledTriangle(float x1, float y1, float x2, float y2, float x3, float y3, Color color) {
        shapeDrawer.filledTriangle(x1, y1, x2, y2, x3, y3, color);
    }

    private void drawScentRange(float centerX, float centerY, PredatorComponent predator) {
        if (predator == null) {
            return;
        }


        drawFilledCircle(centerX, centerY, predator.scentRange, scentFillColor);
        drawCircleOutline(centerX, centerY, predator.scentRange, scentOutlineColor);

        if (predator.hasScentTarget) {
            shapeDrawer.line(predator.scentTarget.x - 10f, predator.scentTarget.y - 10f, predator.scentTarget.x + 10f, predator.scentTarget.y + 10f, targetColor, 2f);
            shapeDrawer.line(predator.scentTarget.x - 10f, predator.scentTarget.y + 10f, predator.scentTarget.x + 10f, predator.scentTarget.y - 10f, targetColor, 2f);
        }
    }

    private void drawFilledCircle(float centerX, float centerY, float radius, Color color) {
        int segments = 48;
        float angleStep = 360f / segments;
        float prevX = centerX + radius;
        float prevY = centerY;

        for (int i = 1; i <= segments; i++) {
            float angle = angleStep * i;
            float currentX = centerX + radius * (float) Math.cos(Math.toRadians(angle));
            float currentY = centerY + radius * (float) Math.sin(Math.toRadians(angle));
            drawFilledTriangle(centerX, centerY, prevX, prevY, currentX, currentY, color);
            prevX = currentX;
            prevY = currentY;
        }
    }

    private void drawCircleOutline(float centerX, float centerY, float radius, Color color) {
        int segments = 48;
        float thickness = 1.5f;
        float angleStep = 360f / segments;
        float prevX = centerX + radius;
        float prevY = centerY;

        for (int i = 1; i <= segments; i++) {
            float angle = angleStep * i;
            float currentX = centerX + radius * (float) Math.cos(Math.toRadians(angle));
            float currentY = centerY + radius * (float) Math.sin(Math.toRadians(angle));
            shapeDrawer.line(prevX, prevY, currentX, currentY, color, thickness);
            prevX = currentX;
            prevY = currentY;
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        agentTexture.dispose();
        if (predatorTexture != null) {
            predatorTexture.dispose();
        }
        if (foodTexture != null) {
            foodTexture.dispose();
        }
        if (whitePixel != null) {
            whitePixel.dispose();
        }
        if (font != null) {
            font.dispose();
        }
        if (fieldTexture != null) {
            fieldTexture.dispose();
        }
    }

    @Override
    public void resize(int width, int height) {
        currentScreenWidth = width;
        currentScreenHeight = height;

        // Update world camera and recenter it
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.position.set(width / 2f, height / 2f, 0);
        camera.update();

        // Update UI camera and recenter so screen-space coordinates align with window
        uiCamera.viewportWidth = width;
        uiCamera.viewportHeight = height;
        uiCamera.position.set(width / 2f, height / 2f, 0);
        uiCamera.update();
    }
}
