package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Systems.PopulationHistory;
import space.earlygrey.shapedrawer.ShapeDrawer;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Runs one world: steps the engine at a fixed rate and handles keyboard and mouse input. The world is drawn
 * by {@link WorldRenderer}, the panels over it by {@link Hud}, and the in-game settings are a
 * {@link LiveSettingsWindow}. Esc returns to the start screen.
 */
public class SimulationScreen extends ScreenAdapter {
    // Simulation speed: fixed steps, run more of them per frame to speed up
    private static final float SIM_STEP = 1f / 60f;
    private static final float MAX_FRAME_TIME = 0.25f;    // Ignore huge frame gaps (e.g. window dragged)
    private static final int MAX_STEPS_PER_FRAME = 40;
    private static final float[] SPEEDS = {1f, 2f, 5f, 10f, 20f}; // Keys 1-5
    private static final float DRAG_THRESHOLD = 6f;       // Pixels the mouse must move before a press counts as a drag
    private static final float TIMING_SMOOTHING = 0.05f;  // How quickly the step and draw times shown react

    private final Main game;
    private final SimulationConfig config; // This world's own copy: the settings window changes it live
    private final long seed;

    private final SpriteBatch batch = new SpriteBatch();
    private final Texture whitePixel;
    private final OrthographicCamera camera;
    private final OrthographicCamera uiCamera;
    private final Engine engine;
    private final CameraController cameraController;
    private final WorldRenderer worldRenderer;
    private final Hud hud;
    private final PopulationHistory history;
    private final ImmutableArray<Entity> creatures;
    private final Stage stage = new Stage(new ScreenViewport());
    private final LiveSettingsWindow settingsWindow;

    private final Hud.Status status = new Hud.Status();
    private final Vector3 touchPoint = new Vector3();
    private final Vector2 minimapPoint = new Vector2();

    private int speedIndex = 0;
    private boolean paused = false;
    private float accumulator = 0f;
    private int tintGene = -1;
    private Entity selectedEntity;

    /** A new world built from the settings. */
    public SimulationScreen(Main game, SimulationConfig settings) {
        // Same seed + same settings = same run. 0 means "pick one", and the HUD shows which was used.
        this(game, settings.copy(), null, settings.seed != 0 ? settings.seed : MathUtils.random(1, 999_999));
        Simulation.spawnStartingCreatures(engine, config);
    }

    /** A world loaded from a save (see {@link WorldSave}). */
    public SimulationScreen(Main game, WorldSave.Loaded saved) {
        this(game, saved.config, saved.engine, saved.seed);
    }

    private SimulationScreen(Main game, SimulationConfig config, Engine loaded, long seed) {
        this.game = game;
        this.config = config;
        this.seed = seed;
        if (loaded == null) {
            MathUtils.random.setSeed(seed); // Before building the world: ponds and food are placed then
        }
        this.engine = loaded != null ? loaded : Simulation.createEngine(config);

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        whitePixel = new Texture(pixmap);
        pixmap.dispose();
        ShapeDrawer shapeDrawer = new ShapeDrawer(batch, new TextureRegion(whitePixel));

        camera = createCenteredCamera();
        uiCamera = createCenteredCamera();
        cameraController = new CameraController(camera);

        history = engine.getSystem(PopulationHistory.class);
        creatures = engine.getEntitiesFor(Family.all(PositionComponent.class, StatsComponent.class).get());
        engine.addEntityListener(new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
            }

            @Override
            public void entityRemoved(Entity entity) {
                // Drop the stats panel and stop following when the selected creature dies.
                if (entity == selectedEntity) {
                    selectedEntity = null;
                }
                if (entity == cameraController.getTarget()) {
                    cameraController.follow(null);
                }
            }
        });

        worldRenderer = new WorldRenderer(batch, shapeDrawer, engine, config);
        hud = new Hud(batch, shapeDrawer, engine);
        status.seed = seed;

        settingsWindow = new LiveSettingsWindow(config);
        settingsWindow.setVisible(false);
        stage.addActor(settingsWindow);
    }

    private static OrthographicCamera createCenteredCamera() {
        OrthographicCamera camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f, 0);
        camera.update();
        return camera;
    }

    /** Shows a message on screen, e.g. why loading a save failed. */
    public void showMessage(String text) {
        hud.showMessage(text);
    }

    @Override
    public void show() {
        // The settings window gets first go at input; whatever it doesn't use goes to the world
        Gdx.input.setInputProcessor(new InputMultiplexer(stage, mouse));
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void render(float delta) {
        float realDelta = Math.min(Gdx.graphics.getDeltaTime(), MAX_FRAME_TIME);
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (settingsWindow.isVisible()) {
                settingsWindow.setVisible(false);
            } else {
                // Switching screens disposes this one, so stop rendering right away.
                game.showStartScreen();
                return;
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F9) && game.loadSavedWorld(this)) {
            return; // This screen has been replaced (and disposed)
        }

        handleKeys();
        cameraController.update(realDelta); // Real time, so the camera stays responsive while paused or sped up
        stepSimulation(realDelta);

        ScreenUtils.clear(0.30f, 0.40f, 0.30f, 1f);
        long drawStart = System.nanoTime();
        worldRenderer.render(camera);
        status.drawMillis += ((System.nanoTime() - drawStart) / 1e6f - status.drawMillis) * TIMING_SMOOTHING;

        status.paused = paused;
        status.speed = SPEEDS[speedIndex];
        status.following = cameraController.isFollowing();
        status.tint = tintGene == WorldRenderer.TINT_SPECIES ? "species (one color each)"
            : tintGene >= 0 ? PopulationHistory.GENE_NAMES[tintGene] + " (blue low, red high)" : null;
        hud.render(uiCamera, camera, status, selectedEntity, realDelta);

        stage.act(realDelta);
        stage.draw();
    }

    // --- Simulation and input ---------------------------------------------------------------------------

    /** Fixed-size simulation steps: results don't depend on frame rate, and speed-up just runs more steps. */
    private void stepSimulation(float realDelta) {
        if (paused) {
            return;
        }
        accumulator += realDelta * SPEEDS[speedIndex];
        int steps = 0;
        long start = System.nanoTime();
        while (accumulator >= SIM_STEP && steps < MAX_STEPS_PER_FRAME) {
            engine.update(SIM_STEP);
            accumulator -= SIM_STEP;
            steps++;
        }
        if (steps == MAX_STEPS_PER_FRAME) {
            accumulator = 0f; // Can't keep up at this speed; drop the backlog instead of spiralling
        }
        if (steps > 0) {
            float millisPerStep = (System.nanoTime() - start) / 1e6f / steps;
            status.stepMillis += (millisPerStep - status.stepMillis) * TIMING_SMOOTHING;
        }
    }

    private void handleKeys() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.P)) {
            paused = !paused;
        }
        for (int i = 0; i < SPEEDS.length; i++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) {
                speedIndex = i;
                paused = false;
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.G)) {
            hud.toggleGraphs();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            hud.cycleGraphSpecies();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            hud.toggleMinimap();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.X)) {
            exportHistory();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            saveWorld();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.O)) {
            toggleSettings();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.T)) {
            // Off, then each gene in turn, then species, then off again
            tintGene = tintGene + 1 > WorldRenderer.TINT_SPECIES ? -1 : tintGene + 1;
            worldRenderer.setTintGene(tintGene);
        }
    }

    private void toggleSettings() {
        if (settingsWindow.isVisible()) {
            settingsWindow.setVisible(false);
            return;
        }
        settingsWindow.setVisible(true);
        settingsWindow.setPosition((stage.getWidth() - settingsWindow.getWidth()) / 2f,
            (stage.getHeight() - settingsWindow.getHeight()) / 2f);
        settingsWindow.toFront();
    }

    /** Mouse: wheel zooms toward the cursor, dragging pans, a click selects a creature (or jumps via the minimap). */
    private final InputAdapter mouse = new InputAdapter() {
        private boolean pressed;
        private boolean dragging;
        private int downX;
        private int downY;
        private int lastX;
        private int lastY;

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT) {
                return false;
            }
            pressed = true;
            dragging = false;
            downX = lastX = screenX;
            downY = lastY = screenY;
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (!pressed) {
                return false;
            }
            if (!dragging && Vector2.dst(downX, downY, screenX, screenY) > DRAG_THRESHOLD) {
                dragging = true;
            }
            if (dragging) {
                cameraController.panByScreenPixels(screenX - lastX, screenY - lastY);
            }
            lastX = screenX;
            lastY = screenY;
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (!pressed || button != Input.Buttons.LEFT) {
                return false;
            }
            pressed = false;
            if (!dragging) {
                click(screenX, screenY);
            }
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            cameraController.zoomAt(Gdx.input.getX(), Gdx.input.getY(), amountY);
            return true;
        }
    };

    private void click(int screenX, int screenY) {
        float uiY = Gdx.graphics.getHeight() - screenY; // Screen coordinates have y pointing down
        if (hud.minimapToWorld(screenX, uiY, minimapPoint)) {
            cameraController.centerOn(minimapPoint.x, minimapPoint.y);
            return;
        }
        selectCreatureAt(screenX, screenY);
    }

    /** Clicking a creature inspects and follows it; clicking empty ground clears the selection. */
    private void selectCreatureAt(float screenX, float screenY) {
        Vector3 world = camera.unproject(touchPoint.set(screenX, screenY, 0));
        selectedEntity = null;
        for (Entity creature : creatures) {
            PositionComponent pos = Mappers.position.get(creature);
            if (world.x >= pos.position.x && world.x <= pos.position.x + World.CREATURE_SIZE
                && world.y >= pos.position.y && world.y <= pos.position.y + World.CREATURE_SIZE) {
                selectedEntity = creature;
                break;
            }
        }
        cameraController.follow(selectedEntity);
    }

    // --- Files ------------------------------------------------------------------------------------------

    /** Saves the population history as CSV in the user's home folder, under NatureSim/exports. */
    private void exportHistory() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        FileHandle file = Gdx.files.external("NatureSim/exports/naturesim-seed" + seed + "-" + stamp + ".csv");
        try {
            file.writeString(HistoryExporter.toCsv(history), false, "UTF-8");
            hud.showMessage("Exported " + history.sampleCount() + " samples to " + file.file().getAbsolutePath());
        } catch (RuntimeException e) {
            hud.showMessage("Export failed: " + e.getMessage());
        }
    }

    /** Saves the whole world over the previous save (F9 loads it back). */
    private void saveWorld() {
        FileHandle file = Main.savedWorldFile();
        try {
            file.writeString(WorldSave.save(engine, config, seed), false, "UTF-8");
            hud.showMessage("Saved the world to " + file.file().getAbsolutePath() + "  (F9 loads it)");
        } catch (RuntimeException e) {
            hud.showMessage("Save failed: " + e.getMessage());
        }
    }

    @Override
    public void resize(int width, int height) {
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
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        worldRenderer.dispose();
        stage.dispose();
        batch.dispose();
        whitePixel.dispose();
        hud.dispose();
    }
}
