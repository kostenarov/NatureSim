package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
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
import io.kostenarov.natureSim.Systems.CameraSystem;
import io.kostenarov.natureSim.Systems.MovementSystem;
import io.kostenarov.natureSim.Systems.VisionRenderingSystem;
import io.kostenarov.natureSim.Systems.FoodSourceSystem;
import io.kostenarov.natureSim.Systems.MatingSystem;
import space.earlygrey.shapedrawer.ShapeDrawer;

public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private Texture agentTexture;
    private Texture foodTexture;
    private Engine engine;
    private OrthographicCamera camera;
    private OrthographicCamera uiCamera;
    private CameraSystem cameraSystem;
    private ShapeDrawer shapeDrawer;
    private Texture whitePixel;
    private BitmapFont font;
    private Entity selectedEntity;
    private Texture fieldTexture;
    private com.badlogic.gdx.graphics.g2d.TextureRegion tileRegion;
    private static final float TILE_SIZE = 40f;
    private final float screenWidth = 2560f;
    private final float screenHeight = 1440f;
    private float currentScreenWidth;
    private float currentScreenHeight;

    @Override
    public void create() {
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
        engine.addSystem(new FoodSourceSystem());
        engine.addSystem(new MatingSystem());
        engine.addSystem(new MovementSystem());
        cameraSystem = new CameraSystem(camera, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        engine.addSystem(cameraSystem);
        engine.addSystem(new VisionRenderingSystem(shapeDrawer, camera));
    }

    private void createInitialAgents() {
        createAgent(300, 200, 200, 45f, 100f, 100f);
        createAgent(500, 400, 100, 90f, 150f, 180f);
        createAgent(600, 400, 100, 90f, 300f, 170f);
        createAgent(700, 400, 100, 90f, 220f, 100f);
        createAgent(800, 400, 100, 90f, 150f, 90f);
        createAgent(900, 400, 100, 90f, 150f, 90f);
        createAgent(1300, 400, 150, 95f, 130f, 90f);
        createAgent(1400, 400, 120, 93f, 111f, 100f);
        createAgent(1500, 400, 120, 70f, 120f, 70f);
        createAgent(1100, 200, 110, 20f, 150f, 83f);
        createAgent(1000, 500, 140, 1200f, 112f, 90f);
    }

    private void createAgent(float x, float y, float speed, float angle, float visionRange, float visionAngle) {
        Entity agent = engine.createEntity();
        PositionComponent pos = new PositionComponent();
        pos.position.set(x, y);
        agent.add(pos);

        VelocityComponent vel = new VelocityComponent(speed);
        vel.velocity.set(angle, angle);
        agent.add(vel);

        GenomeComponent dna = new GenomeComponent();
        dna.genes[GenomeComponent.SPEED] = speed / 250f;
        agent.add(dna);

        GenderComponent gender = new GenderComponent();
        agent.add(gender);

        StatsComponent stats = new StatsComponent();
        agent.add(stats);

        VisionComponent vision = new VisionComponent(visionRange, visionAngle);
        agent.add(vision);

        // Add BehaviourComponent for decision-making system
        BehaviourComponent behaviour = new BehaviourComponent();
        agent.add(behaviour);

        // Add ReproductionComponent for mating system
        ReproductionComponent reproduction = new ReproductionComponent();
        agent.add(reproduction);

        engine.addEntity(agent);
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.30f, 0.40f, 0.30f, 1f);

        updateEngineAndInput();

        renderBackgroundTiles();
        renderEntities();
        renderUI();
    }

    private void updateEngineAndInput() {
        float deltaTime = Gdx.graphics.getDeltaTime();
        engine.update(deltaTime);

        if (Gdx.input.justTouched()) {
            float screenX = Gdx.input.getX();
            float screenY = Gdx.input.getY();
            selectEntityAtScreen(screenX, screenY);
        }
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

        for (Entity entity : engine.getEntitiesFor(Family.all(PositionComponent.class, VisionComponent.class).get())) {
            PositionComponent pos = entity.getComponent(PositionComponent.class);
            VisionComponent vision = entity.getComponent(VisionComponent.class);
            drawVisionCone(pos.position.x + 16, pos.position.y + 16, vision);
        }

        batch.flush();

        // Draw food sources
        for (Entity entity : engine.getEntitiesFor(Family.all(FoodSourceComponent.class, PositionComponent.class).get())) {
            PositionComponent pos = entity.getComponent(PositionComponent.class);
            batch.draw(foodTexture, pos.position.x, pos.position.y);
        }

        // Draw the agents
        for (Entity entity : engine.getEntitiesFor(Family.all(PositionComponent.class).exclude(FoodSourceComponent.class).get())) {
            PositionComponent pos = entity.getComponent(PositionComponent.class);
            batch.draw(agentTexture, pos.position.x, pos.position.y);
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
        // Count agents (entities with StatsComponent)
        int populationCount = 0;
        for (Entity entity : engine.getEntitiesFor(Family.all(StatsComponent.class, GenomeComponent.class).get())) {
            populationCount++;
        }

        font.setColor(Color.WHITE);
        font.getData().setScale(2f);
        font.draw(batch, "Population: " + populationCount, 20f, uiCamera.position.y + (uiCamera.viewportHeight / 2f) - 20f);
    }

    private void selectEntityAtScreen(float screenX, float screenY) {
        Vector3 worldCoords = camera.unproject(new Vector3(screenX, screenY, 0));

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
        float x = padding;
        float topY = uiCamera.position.y + (uiCamera.viewportHeight / 2f);
        float y = topY - panelHeight - padding;

        Color panelBg = new Color(0.08f, 0.08f, 0.08f, 0.7f);
        Color panelBorder = new Color(0.6f, 0.6f, 0.6f, 0.8f);

        shapeDrawer.filledRectangle(x, y, panelWidth, panelHeight, panelBg);
        shapeDrawer.rectangle(x, y, panelWidth, panelHeight, panelBorder, 1f);

        font.setColor(Color.WHITE);
        font.getData().setScale(2.5f * widthScale * heightScale); // Scale font based on current screen width
        float textX = x + 10f;
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
        font.draw(batch, "Speed: " + String.format("%.2f", selectedEntity.getComponent(GenomeComponent.class).genes[GenomeComponent.SPEED] * 250f), textX, textY);
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
        Color visionColor = new Color(0.72f, 0.62f, 0.42f, 0.22f); // Sandy/dirty tone with 22% opacity

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
        Color fillColor = new Color(color.r, color.g, color.b, 0.12f);

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
            drawFilledTriangle(centerX, centerY, x1, y1, x2, y2, fillColor);

            angle += angleStep;
        }
    }

    private void drawFilledTriangle(float x1, float y1, float x2, float y2, float x3, float y3, Color color) {
        shapeDrawer.filledTriangle(x1, y1, x2, y2, x3, y3, color);
    }

    @Override
    public void dispose() {
        batch.dispose();
        agentTexture.dispose();
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
