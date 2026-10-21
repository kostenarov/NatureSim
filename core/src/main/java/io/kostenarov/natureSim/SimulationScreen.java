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
import com.kotcrab.vis.ui.VisUI;
import io.kostenarov.natureSim.Components.AgeComponent;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.GenderComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Systems.PopulationHistory;
import space.earlygrey.shapedrawer.ShapeDrawer;

/**
 * Runs one simulation built from a {@link SimulationConfig}: steps the engine at a fixed rate,
 * handles input and draws the on-screen panels. The world itself is drawn by {@link WorldRenderer}.
 * Esc returns to the start screen.
 */
public class SimulationScreen extends ScreenAdapter {
    // Simulation speed: fixed steps, run more of them per frame to speed up
    private static final float SIM_STEP = 1f / 60f;
    private static final float MAX_FRAME_TIME = 0.25f;    // Ignore huge frame gaps (e.g. window dragged)
    private static final int MAX_STEPS_PER_FRAME = 40;
    private static final float[] SPEEDS = {1f, 2f, 5f, 10f, 20f}; // Keys 1-5

    private static final float MARGIN = 20f;
    private static final String CONTROLS =
        "Esc: settings   P: pause   1-5: speed   G: graphs   T: tint by gene   WASD: move   Q/E: zoom   Click: inspect & follow   Space: follow on/off";

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
    private final PopulationHistory history;
    private final ImmutableArray<Entity> creatures;

    private final Color panelBg = new Color(0.08f, 0.08f, 0.08f, 0.75f);
    private final Color panelBorder = new Color(0.6f, 0.6f, 0.6f, 0.8f);
    private final Vector3 touchPoint = new Vector3();

    private int speedIndex = 0;
    private boolean paused = false;
    private float accumulator = 0f;
    private boolean showGraphs = false;
    private boolean showPredatorGenes = false;
    private int tintGene = -1;
    private Entity selectedEntity;

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
        Simulation.spawnStartingCreatures(engine, config);

        worldRenderer = new WorldRenderer(batch, shapeDrawer, engine, config);
        graphRenderer = new GraphRenderer(batch, shapeDrawer, font);
    }

    private static OrthographicCamera createCenteredCamera() {
        OrthographicCamera camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f, 0);
        camera.update();
        return camera;
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

        if (Gdx.input.justTouched()) {
            selectCreatureAt(Gdx.input.getX(), Gdx.input.getY());
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
            showPredatorGenes = !showPredatorGenes;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.T)) {
            // Off, then each gene in turn, then off again
            tintGene = tintGene + 1 >= PopulationHistory.GENE_COUNT ? -1 : tintGene + 1;
            worldRenderer.setTintGene(tintGene);
        }
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

    // --- On-screen panels -------------------------------------------------------------------------------

    private void renderHud() {
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        drawStatusLine();
        drawStatsPanel();
        if (showGraphs) {
            float width = Math.min(1100f, uiCamera.viewportWidth * 0.45f);
            float height = Math.min(760f, uiCamera.viewportHeight * 0.6f);
            graphRenderer.render(history, showPredatorGenes, uiCamera.viewportWidth - width - MARGIN, MARGIN * 3f, width, height);
        }
        batch.end();
    }

    private void drawStatusLine() {
        String speed = paused ? "PAUSED" : (int) SPEEDS[speedIndex] + "x speed";
        String tint = tintGene >= 0 ? "    Tint: " + PopulationHistory.GENE_NAMES[tintGene] + " (blue low, red high)" : "";
        String following = cameraController.isFollowing() ? "    Following" : "";

        font.setColor(Color.WHITE);
        float top = uiCamera.viewportHeight - MARGIN;
        font.draw(batch, "Population: " + creatures.size() + "    " + speed + "    Seed: " + seed + following + tint, MARGIN, top);
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

        String[] lines = {
            (Creatures.isPredator(selectedEntity) ? "PREDATOR" : "PREY") + "  " + gender.gender + "  gen " + age.generation,
            "Age: " + (int) (age.age / 60f) + ":" + String.format("%02d", (int) age.age % 60)
                + " of ~" + (int) (age.lifespan / 60f) + " min",
            "Health " + (int) stats.health + "   Energy " + (int) stats.energy,
            "Hunger " + (int) stats.hunger + "   Thirst " + (int) stats.thirst,
            "Doing: " + behaviour.behaviour,
            "",
            "Speed " + (int) Traits.speed(dna) + "   Vision " + (int) Traits.visionRange(dna),
            String.format("Size %.2f   Stealth %.2f", dna.genes[GenomeComponent.SIZE], dna.genes[GenomeComponent.STEALTH]),
            String.format("Metabolism %.2f   Recovery %.2f", dna.genes[GenomeComponent.METABOLISM], dna.genes[GenomeComponent.RECOVERY_RATE]),
        };

        float lineHeight = font.getLineHeight();
        float panelWidth = 560f;
        float panelHeight = lines.length * lineHeight + MARGIN * 1.5f;
        float x = MARGIN;
        float y = uiCamera.viewportHeight - MARGIN * 2.5f - lineHeight - panelHeight;

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
