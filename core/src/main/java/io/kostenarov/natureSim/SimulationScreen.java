package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.kotcrab.vis.ui.VisUI;
import io.kostenarov.natureSim.Components.AgeComponent;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.GenderComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.LineageComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Enums.Role;
import io.kostenarov.natureSim.Systems.ClockSystem;
import io.kostenarov.natureSim.Systems.LineageSystem;
import io.kostenarov.natureSim.Systems.MigrationSystem;
import io.kostenarov.natureSim.Systems.PopulationHistory;
import space.earlygrey.shapedrawer.ShapeDrawer;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Runs one simulation built from a {@link SimulationConfig}: steps the engine at a fixed rate,
 * handles keyboard and mouse input and draws the on-screen panels. The world itself is drawn by
 * {@link WorldRenderer}. Esc returns to the start screen.
 */
public class SimulationScreen extends ScreenAdapter {
    // Simulation speed: fixed steps, run more of them per frame to speed up
    private static final float SIM_STEP = 1f / 60f;
    private static final float MAX_FRAME_TIME = 0.25f;    // Ignore huge frame gaps (e.g. window dragged)
    private static final int MAX_STEPS_PER_FRAME = 40;
    private static final float[] SPEEDS = {1f, 2f, 5f, 10f, 20f}; // Keys 1-5

    private static final float MARGIN = 20f;
    private static final float MINIMAP_SIZE = 320f;
    private static final float DRAG_THRESHOLD = 6f;       // Pixels the mouse must move before a press counts as a drag
    private static final float MESSAGE_SECONDS = 6f;
    private static final float FAMILY_REFRESH_SECONDS = 0.5f;
    private static final String CONTROLS = "Esc settings   P pause   1-5 speed   G graphs   H graph species   T tint by gene   "
        + "M minimap   X export CSV   WASD/drag move   Q/E/wheel zoom   Click inspect & follow   Space follow on/off";

    private final Main game;
    private final long seed;

    private final SpriteBatch batch = new SpriteBatch();
    private final Texture whitePixel;
    private final ShapeDrawer shapeDrawer;
    private final BitmapFont font = VisUI.getSkin().getFont("default-font"); // Owned by VisUI, not disposed here
    private final OrthographicCamera camera;
    private final OrthographicCamera uiCamera;
    private final Engine engine;
    private final CameraController cameraController;
    private final WorldRenderer worldRenderer;
    private final GraphRenderer graphRenderer;
    private final Minimap minimap;
    private final PopulationHistory history;
    private final ClockSystem clock;
    private final LineageSystem lineage;
    private final MigrationSystem migration; // Null when migration is switched off
    private final ImmutableArray<Entity> creatures;
    private final ImmutableArray<Entity> prey;
    private final ImmutableArray<Entity> predators;
    private final ImmutableArray<Entity> scavengers;

    private final Color panelBg = new Color(0.08f, 0.08f, 0.08f, 0.75f);
    private final Color panelBorder = new Color(0.6f, 0.6f, 0.6f, 0.8f);
    private final Vector3 touchPoint = new Vector3();
    private final Vector2 minimapPoint = new Vector2();

    private int speedIndex = 0;
    private boolean paused = false;
    private float accumulator = 0f;
    private boolean showGraphs = false;
    private Role graphSpecies = Role.PREY;
    private boolean showMinimap = true;
    private int tintGene = -1;
    private Entity selectedEntity;
    private int selectedDescendants = 0;
    private float familyRefreshTimer = 0f;
    private String message = null;
    private float messageTimer = 0f;

    public SimulationScreen(Main game, SimulationConfig config) {
        this.game = game;

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
        cameraController = new CameraController(camera);

        engine = Simulation.createEngine(config);
        history = engine.getSystem(PopulationHistory.class);
        clock = engine.getSystem(ClockSystem.class);
        lineage = engine.getSystem(LineageSystem.class);
        migration = engine.getSystem(MigrationSystem.class);
        creatures = engine.getEntitiesFor(Family.all(PositionComponent.class, StatsComponent.class).get());
        prey = engine.getEntitiesFor(Creatures.PREY);
        predators = engine.getEntitiesFor(Creatures.PREDATORS);
        scavengers = engine.getEntitiesFor(Creatures.SCAVENGERS);
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
        Simulation.spawnStartingCreatures(engine, config);

        worldRenderer = new WorldRenderer(batch, shapeDrawer, engine, config);
        graphRenderer = new GraphRenderer(batch, shapeDrawer, font);
        minimap = new Minimap(shapeDrawer, engine);
    }

    private static OrthographicCamera createCenteredCamera() {
        OrthographicCamera camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f, 0);
        camera.update();
        return camera;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(mouse);
    }

    @Override
    public void hide() {
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
        handleKeys();

        // The camera follows real time so it stays responsive while the simulation is paused or sped up.
        cameraController.update(realDelta);

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

        messageTimer -= realDelta;
        familyRefreshTimer -= realDelta;
        if (selectedEntity != null && familyRefreshTimer <= 0f && lineage != null) {
            // Walking the family tree is too slow to redo every frame
            selectedDescendants = lineage.livingDescendants(Mappers.lineage.get(selectedEntity).id);
            familyRefreshTimer = FAMILY_REFRESH_SECONDS;
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
            showGraphs = !showGraphs;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            graphSpecies = Role.values()[(graphSpecies.ordinal() + 1) % Role.values().length];
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            showMinimap = !showMinimap;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.X)) {
            exportHistory();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.T)) {
            // Off, then each gene in turn, then off again
            tintGene = tintGene + 1 >= PopulationHistory.GENE_COUNT ? -1 : tintGene + 1;
            worldRenderer.setTintGene(tintGene);
        }
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
        if (showMinimap && minimap.contains(screenX, uiY)) {
            minimap.toWorld(screenX, uiY, minimapPoint);
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
        familyRefreshTimer = 0f;
        cameraController.follow(selectedEntity);
    }

    /** Saves the population history as CSV in the user's home folder, under NatureSim/exports. */
    private void exportHistory() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        FileHandle file = Gdx.files.external("NatureSim/exports/naturesim-seed" + seed + "-" + stamp + ".csv");
        try {
            file.writeString(HistoryExporter.toCsv(history), false, "UTF-8");
            showMessage("Exported " + history.sampleCount() + " samples to " + file.file().getAbsolutePath());
        } catch (RuntimeException e) {
            showMessage("Export failed: " + e.getMessage());
        }
    }

    private void showMessage(String text) {
        message = text;
        messageTimer = MESSAGE_SECONDS;
    }

    // --- On-screen panels -------------------------------------------------------------------------------

    private void renderHud() {
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        drawStatusLine();
        drawStatsPanel();
        if (showMinimap) {
            minimap.render(MARGIN, MARGIN * 2.5f + font.getLineHeight(), MINIMAP_SIZE, camera);
        }
        if (showGraphs) {
            float width = Math.min(1100f, uiCamera.viewportWidth * 0.45f);
            float height = Math.min(800f, uiCamera.viewportHeight * 0.62f);
            graphRenderer.render(history, graphSpecies, uiCamera.viewportWidth - width - MARGIN, MARGIN * 3f, width, height);
        }
        batch.end();
    }

    private void drawStatusLine() {
        StringBuilder status = new StringBuilder()
            .append("Prey ").append(prey.size())
            .append("   Predators ").append(predators.size())
            .append("   Scavengers ").append(scavengers.size());
        if (clock != null) {
            status.append("      ").append(clock.describeTime());
            if (clock.isSeasonsEnabled()) {
                status.append("  ").append(ClockSystem.SEASONS[clock.season()]);
            }
        }
        status.append("      ").append(paused ? "PAUSED" : (int) SPEEDS[speedIndex] + "x speed");
        status.append("      Seed ").append(seed);
        if (migration != null && migration.getMigrants() > 0) {
            status.append("      Migrants ").append(migration.getMigrants());
        }
        if (cameraController.isFollowing()) {
            status.append("      Following");
        }
        if (tintGene >= 0) {
            status.append("      Tint: ").append(PopulationHistory.GENE_NAMES[tintGene]).append(" (blue low, red high)");
        }

        font.setColor(Color.WHITE);
        float top = uiCamera.viewportHeight - MARGIN;
        font.draw(batch, status, MARGIN, top);
        if (message != null && messageTimer > 0f) {
            font.setColor(Color.GOLD);
            font.draw(batch, message, MARGIN, top - font.getLineHeight());
        }
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, CONTROLS, MARGIN, MARGIN + font.getCapHeight());
    }

    private void drawStatsPanel() {
        if (selectedEntity == null) {
            return;
        }

        StatsComponent stats = Mappers.stats.get(selectedEntity);
        GenderComponent gender = Mappers.gender.get(selectedEntity);
        BehaviourComponent behaviour = Mappers.behaviour.get(selectedEntity);
        GenomeComponent dna = Mappers.genome.get(selectedEntity);
        AgeComponent age = Mappers.age.get(selectedEntity);
        LineageComponent family = Mappers.lineage.get(selectedEntity);

        String parents = family.motherId < 0 ? "first generation / migrant" : "parents #" + family.motherId + " & #" + family.fatherId;
        String[] lines = {
            Creatures.role(selectedEntity) + "  " + gender.gender + "  #" + family.id + "  gen " + age.generation,
            "Age: " + (int) (age.age / 60f) + ":" + String.format("%02d", (int) age.age % 60)
                + " of ~" + (int) (age.lifespan / 60f) + " min",
            "Health " + (int) stats.health + "   Energy " + (int) stats.energy,
            "Hunger " + (int) stats.hunger + "   Thirst " + (int) stats.thirst,
            "Doing: " + behaviour.behaviour + (behaviour.exhausted ? " (exhausted)" : ""),
            "",
            parents,
            "Children " + family.children + "   Living descendants " + selectedDescendants,
            "",
            "Speed " + (int) Traits.speed(dna) + "   Vision " + (int) Traits.visionRange(dna),
            String.format("Size %.2f   Stealth %.2f", dna.genes[GenomeComponent.SIZE], dna.genes[GenomeComponent.STEALTH]),
            String.format("Metabolism %.2f   Recovery %.2f", dna.genes[GenomeComponent.METABOLISM], dna.genes[GenomeComponent.RECOVERY_RATE]),
        };

        float lineHeight = font.getLineHeight();
        float panelWidth = 620f;
        float panelHeight = lines.length * lineHeight + MARGIN * 1.5f;
        float x = MARGIN;
        float y = uiCamera.viewportHeight - MARGIN * 3.5f - lineHeight * 2f - panelHeight;

        shapeDrawer.filledRectangle(x, y, panelWidth, panelHeight, panelBg);
        shapeDrawer.rectangle(x, y, panelWidth, panelHeight, panelBorder, 1f);

        font.setColor(Color.WHITE);
        float textY = y + panelHeight - MARGIN * 0.75f;
        for (String line : lines) {
            font.draw(batch, line, x + MARGIN, textY);
            textY -= lineHeight;
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
    }

    @Override
    public void dispose() {
        worldRenderer.dispose();
        batch.dispose();
        whitePixel.dispose();
        font.setColor(Color.WHITE); // Shared with the start screen's widgets
    }
}
