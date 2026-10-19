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
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.GenderComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Systems.CameraSystem;
import io.kostenarov.natureSim.Systems.CollisionSystem;
import io.kostenarov.natureSim.Systems.DeathSystem;
import io.kostenarov.natureSim.Systems.FoodSourceSystem;
import io.kostenarov.natureSim.Systems.MatingSystem;
import io.kostenarov.natureSim.Systems.MetabolismSystem;
import io.kostenarov.natureSim.Systems.PredatorSystem;
import io.kostenarov.natureSim.Systems.PreySystem;
import io.kostenarov.natureSim.Systems.SpatialIndexSystem;
import io.kostenarov.natureSim.Systems.WaterSourceSystem;
import space.earlygrey.shapedrawer.ShapeDrawer;

/**
 * Runs one simulation built from a {@link SimulationConfig}: steps the engine at a fixed rate,
 * handles input and draws the on-screen panel. The world itself is drawn by {@link WorldRenderer}.
 * Esc returns to the start screen.
 */
public class SimulationScreen extends ScreenAdapter {
    // Simulation speed: fixed steps, run more of them per frame to speed up
    private static final float SIM_STEP = 1f / 60f;
    private static final float MAX_FRAME_TIME = 0.25f;    // Ignore huge frame gaps (e.g. window dragged)
    private static final int MAX_STEPS_PER_FRAME = 40;
    private static final float[] SPEEDS = {1f, 2f, 5f, 10f, 20f}; // Keys 1-5

    // The stats panel was laid out for this window size and scales from it
    private static final float DESIGN_WIDTH = 2560f;
    private static final float DESIGN_HEIGHT = 1440f;

    private final Main game;
    private final SimulationConfig config;
    private final long seed;

    private final SpriteBatch batch = new SpriteBatch();
    private final Texture whitePixel;
    private final ShapeDrawer shapeDrawer;
    private final BitmapFont font = new BitmapFont();
    private final OrthographicCamera camera;
    private final OrthographicCamera uiCamera;
    private final Engine engine = new Engine();
    private final CameraSystem cameraSystem;
    private final WorldRenderer worldRenderer;
    private final ImmutableArray<Entity> creatures;

    private final Color panelBg = new Color(0.08f, 0.08f, 0.08f, 0.7f);
    private final Color panelBorder = new Color(0.6f, 0.6f, 0.6f, 0.8f);
    private final Vector3 touchPoint = new Vector3();

    private int speedIndex = 0;
    private boolean paused = false;
    private float accumulator = 0f;
    private Entity selectedEntity;
    private float currentScreenWidth = DESIGN_WIDTH;
    private float currentScreenHeight = DESIGN_HEIGHT;

    public SimulationScreen(Main game, SimulationConfig config) {
        this.game = game;
        this.config = config;

        // Same seed + same settings = same run. 0 means "pick one", and the HUD shows which was used.
        seed = config.seed != 0 ? config.seed : MathUtils.random(1, 999_999);
        MathUtils.random.setSeed(seed);

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        whitePixel = new Texture(pixmap);
        pixmap.dispose();
        shapeDrawer = new ShapeDrawer(batch, new TextureRegion(whitePixel));

        camera = createCenteredCamera();
        uiCamera = createCenteredCamera();

        buildEngine();
        // Not added to the engine: the camera runs on real time, outside the fixed simulation steps.
        cameraSystem = new CameraSystem(camera, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        creatures = engine.getEntitiesFor(Family.all(PositionComponent.class, StatsComponent.class).get());
        spawnStartingCreatures();

        worldRenderer = new WorldRenderer(batch, shapeDrawer, engine, config);
    }

    private static OrthographicCamera createCenteredCamera() {
        OrthographicCamera camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f, 0);
        camera.update();
        return camera;
    }

    private void buildEngine() {
        engine.addSystem(new SpatialIndexSystem()); // Always runs first each step
        engine.addSystem(new WaterSourceSystem(config)); // Ponds before food: food grows around them
        engine.addSystem(new FoodSourceSystem(config));
        engine.addSystem(new MatingSystem(config));
        engine.addSystem(new PredatorSystem());
        engine.addSystem(new PreySystem());
        engine.addSystem(new MetabolismSystem()); // After behaviour: upkeep depends on what creatures did
        if (config.collisionEnabled) {
            engine.addSystem(new CollisionSystem());
        }
        engine.addSystem(new DeathSystem());

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

    private void spawnStartingCreatures() {
        float spawnMin = 200f;
        float spawnMax = World.WIDTH - spawnMin;

        // Starting gene ranges keep the first generation close to the old hand-tuned creatures;
        // all other genes are random (0 to 2) and evolution takes it from there.
        for (int i = 0; i < config.preyCount; i++) {
            GenomeComponent dna = new GenomeComponent();
            dna.genes[GenomeComponent.SPEED] = MathUtils.random(0.35f, 0.85f);   // ~120-220 px/s
            dna.genes[GenomeComponent.VISION] = MathUtils.random(0.1f, 1.4f);    // ~90-235 px
            engine.addEntity(Creatures.create(engine, MathUtils.random(spawnMin, spawnMax), MathUtils.random(spawnMin, spawnMax), dna));
        }

        for (int i = 0; i < config.predatorCount; i++) {
            GenomeComponent dna = new GenomeComponent();
            dna.genes[GenomeComponent.SPEED] = MathUtils.random(0.7f, 0.85f);    // ~190-220 px/s
            dna.genes[GenomeComponent.VISION] = MathUtils.random(1.3f, 2f);      // ~225-300 px
            Entity predator = Creatures.create(engine, MathUtils.random(spawnMin, spawnMax), MathUtils.random(spawnMin, spawnMax), dna);
            Creatures.makePredator(predator);
            engine.addEntity(predator);
        }
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

        update();

        ScreenUtils.clear(0.30f, 0.40f, 0.30f, 1f);
        worldRenderer.render(camera);
        renderHud();
    }

    // --- Simulation and input ---------------------------------------------------------------------------

    private void update() {
        float realDelta = Math.min(Gdx.graphics.getDeltaTime(), MAX_FRAME_TIME);
        handleSpeedKeys();

        // The camera follows real time so it stays responsive while the simulation is paused or sped up.
        cameraSystem.update(realDelta);

        // Fixed-size simulation steps: results don't depend on frame rate, and speed-up just runs more steps.
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
            selectCreatureAt(Gdx.input.getX(), Gdx.input.getY());
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

    private void selectCreatureAt(float screenX, float screenY) {
        Vector3 world = camera.unproject(touchPoint.set(screenX, screenY, 0));
        selectedEntity = null;
        for (Entity creature : creatures) {
            PositionComponent pos = creature.getComponent(PositionComponent.class);
            if (world.x >= pos.position.x && world.x <= pos.position.x + World.CREATURE_SIZE
                && world.y >= pos.position.y && world.y <= pos.position.y + World.CREATURE_SIZE) {
                selectedEntity = creature;
                return;
            }
        }
    }

    // --- On-screen panel --------------------------------------------------------------------------------

    private void renderHud() {
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        drawStatusLine();
        drawStatsPanel();
        batch.end();
    }

    private void drawStatusLine() {
        String speed = paused ? "PAUSED" : (int) SPEEDS[speedIndex] + "x speed";

        font.setColor(Color.WHITE);
        font.getData().setScale(2f);
        font.draw(batch, "Population: " + creatures.size() + "    " + speed + "    Seed: " + seed,
            20f, uiCamera.position.y + (uiCamera.viewportHeight / 2f) - 20f);

        font.getData().setScale(1.5f);
        font.draw(batch, "Esc: settings   P: pause   1-5: speed   WASD: move   Q/E: zoom   Space: follow   Click: inspect", 20f, 30f);
    }

    private void drawStatsPanel() {
        if (selectedEntity == null) {
            return;
        }

        StatsComponent stats = selectedEntity.getComponent(StatsComponent.class);
        GenderComponent gender = selectedEntity.getComponent(GenderComponent.class);
        BehaviourComponent behaviour = selectedEntity.getComponent(BehaviourComponent.class);

        float widthScale = currentScreenWidth / DESIGN_WIDTH;
        float heightScale = currentScreenHeight / DESIGN_HEIGHT;

        float panelWidth = 440f * widthScale;
        float panelHeight = 280f * heightScale;
        float padding = 20f * heightScale;
        float topY = uiCamera.position.y + (uiCamera.viewportHeight / 2f);
        float y = topY - panelHeight - padding;

        shapeDrawer.filledRectangle(padding, y, panelWidth, panelHeight, panelBg);
        shapeDrawer.rectangle(padding, y, panelWidth, panelHeight, panelBorder, 1f);

        font.setColor(Color.WHITE);
        font.getData().setScale(2.5f * widthScale * heightScale);
        float textX = padding + 10f;
        float textY = y + panelHeight - 10f;
        float lineHeight = 40f * heightScale;

        font.draw(batch, "STATS", textX, textY);
        textY -= 50f * heightScale;
        String[] lines = {
            "Hunger: " + (int) stats.hunger,
            "Thirst: " + (int) stats.thirst,
            "Energy: " + (int) stats.energy,
            "Health: " + (int) stats.health,
            "Gender: " + gender.gender,
            "Role: " + (Creatures.isPredator(selectedEntity) ? "Predator" : "Prey"),
            "Speed: " + (int) Traits.speed(selectedEntity.getComponent(GenomeComponent.class)),
            "Behaviour: " + behaviour.behaviour,
        };
        for (String line : lines) {
            font.draw(batch, line, textX, textY);
            textY -= lineHeight;
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

    @Override
    public void dispose() {
        worldRenderer.dispose();
        batch.dispose();
        whitePixel.dispose();
        font.dispose();
    }
}
