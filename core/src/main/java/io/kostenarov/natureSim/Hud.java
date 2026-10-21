package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.kotcrab.vis.ui.VisUI;
import io.kostenarov.natureSim.Components.AgeComponent;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.GenderComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.LineageComponent;
import io.kostenarov.natureSim.Components.SpeciesComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Enums.Role;
import io.kostenarov.natureSim.Systems.ClockSystem;
import io.kostenarov.natureSim.Systems.LineageSystem;
import io.kostenarov.natureSim.Systems.MigrationSystem;
import io.kostenarov.natureSim.Systems.PopulationHistory;
import io.kostenarov.natureSim.Systems.SpeciesSystem;
import space.earlygrey.shapedrawer.ShapeDrawer;

/**
 * The panels drawn over the world in screen space: the status line, messages, the list of controls, the
 * selected creature's details, the minimap and the history graphs. {@link SimulationScreen} tells it what the
 * screen is doing each frame through {@link Status}.
 */
class Hud {
    private static final float MARGIN = 20f;
    private static final float MINIMAP_SIZE = 320f;
    private static final float MESSAGE_SECONDS = 6f;
    private static final float FAMILY_REFRESH_SECONDS = 0.5f;
    private static final String[] CONTROLS = {
        "Esc menu   P pause   1-5 speed   O settings   F5 save   F9 load   X export CSV",
        "G graphs   H graph species   T tint   M minimap   WASD/drag move   Q/E/wheel zoom   Click inspect & follow   Space follow on/off",
    };

    /** What the screen is doing, filled in by SimulationScreen each frame. */
    static final class Status {
        boolean paused;
        float speed;
        long seed;
        boolean following;
        String tint;       // What creatures are tinted by, or null
        float stepMillis;  // Average time per simulation step
        float drawMillis;  // Time to draw the world last frame
    }

    private final SpriteBatch batch;
    private final ShapeDrawer shapes;
    private final BitmapFont font = VisUI.getSkin().getFont("default-font"); // Owned by VisUI, not disposed here
    private final Minimap minimap;
    private final GraphRenderer graphRenderer;
    private final PopulationHistory history;
    private final ClockSystem clock;
    private final LineageSystem lineage;
    private final MigrationSystem migration; // Null when migration is switched off
    private final SpeciesSystem species;
    private final ImmutableArray<Entity> prey;
    private final ImmutableArray<Entity> predators;
    private final ImmutableArray<Entity> scavengers;

    private final Color panelBg = new Color(0.08f, 0.08f, 0.08f, 0.75f);
    private final Color panelBorder = new Color(0.6f, 0.6f, 0.6f, 0.8f);
    private final StringBuilder line = new StringBuilder();

    private boolean showGraphs = false;
    private Role graphSpecies = Role.PREY;
    private boolean showMinimap = true;
    private String message = null;
    private float messageTimer = 0f;
    private Entity familyOf = null;
    private int livingDescendants = 0;
    private float familyRefreshTimer = 0f;

    Hud(SpriteBatch batch, ShapeDrawer shapes, Engine engine) {
        this.batch = batch;
        this.shapes = shapes;
        minimap = new Minimap(shapes, engine);
        graphRenderer = new GraphRenderer(batch, shapes, font);
        history = engine.getSystem(PopulationHistory.class);
        clock = engine.getSystem(ClockSystem.class);
        lineage = engine.getSystem(LineageSystem.class);
        migration = engine.getSystem(MigrationSystem.class);
        species = engine.getSystem(SpeciesSystem.class);
        prey = engine.getEntitiesFor(Creatures.PREY);
        predators = engine.getEntitiesFor(Creatures.PREDATORS);
        scavengers = engine.getEntitiesFor(Creatures.SCAVENGERS);
    }

    // --- Toggles and messages ---------------------------------------------------------------------------

    void toggleGraphs() {
        showGraphs = !showGraphs;
    }

    /** Which species' genes the graphs show: prey, then predators, then scavengers. */
    void cycleGraphSpecies() {
        graphSpecies = Role.values()[(graphSpecies.ordinal() + 1) % Role.values().length];
    }

    void toggleMinimap() {
        showMinimap = !showMinimap;
    }

    void showMessage(String text) {
        message = text;
        messageTimer = MESSAGE_SECONDS;
    }

    /** True if a screen-space point (y up) is on the minimap; if so, {@code worldOut} is the map point under it. */
    boolean minimapToWorld(float screenX, float screenY, Vector2 worldOut) {
        if (!showMinimap || !minimap.contains(screenX, screenY)) {
            return false;
        }
        minimap.toWorld(screenX, screenY, worldOut);
        return true;
    }

    // --- Drawing ----------------------------------------------------------------------------------------

    void render(OrthographicCamera uiCamera, OrthographicCamera worldCamera, Status status, Entity selected, float realDelta) {
        messageTimer -= realDelta;
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        drawStatusLine(uiCamera, status);
        drawStatsPanel(uiCamera, selected, realDelta);
        if (showMinimap) {
            minimap.render(MARGIN, MARGIN * 2.5f + font.getLineHeight() * 2f, MINIMAP_SIZE, worldCamera);
        }
        if (showGraphs) {
            float width = Math.min(1400f, uiCamera.viewportWidth * 0.55f);
            float height = Math.min(800f, uiCamera.viewportHeight * 0.62f);
            graphRenderer.render(history, graphSpecies, uiCamera.viewportWidth - width - MARGIN,
                MARGIN * 3f + font.getLineHeight() * 2f, width, height);
        }
        batch.end();
    }

    private void drawStatusLine(OrthographicCamera uiCamera, Status status) {
        line.setLength(0);
        appendCount("Prey", prey.size(), Role.PREY);
        appendCount("   Predators", predators.size(), Role.PREDATOR);
        appendCount("   Scavengers", scavengers.size(), Role.SCAVENGER);
        if (clock != null) {
            line.append("      ").append(clock.describeTime());
            if (clock.isSeasonsEnabled()) {
                line.append("  ").append(ClockSystem.SEASONS[clock.season()]);
            }
        }
        line.append("      ").append(status.paused ? "PAUSED" : (int) status.speed + "x speed");
        line.append("      Seed ").append(status.seed);
        if (migration != null && migration.getMigrants() > 0) {
            line.append("      Migrants ").append(migration.getMigrants());
        }
        if (status.following) {
            line.append("      Following");
        }
        if (status.tint != null) {
            line.append("      Tint: ").append(status.tint);
        }

        font.setColor(Color.WHITE);
        float top = uiCamera.viewportHeight - MARGIN;
        font.draw(batch, line, MARGIN, top);
        if (message != null && messageTimer > 0f) {
            font.setColor(Color.GOLD);
            font.draw(batch, message, MARGIN, top - font.getLineHeight());
        }

        // Controls along the bottom, performance above them on the right
        font.setColor(Color.LIGHT_GRAY);
        float bottom = MARGIN + font.getCapHeight();
        font.draw(batch, CONTROLS[0], MARGIN, bottom + font.getLineHeight());
        font.draw(batch, CONTROLS[1], MARGIN, bottom);
        line.setLength(0);
        line.append(Gdx.graphics.getFramesPerSecond()).append(" fps   step ")
            .append(String.format("%.2f", status.stepMillis)).append(" ms   draw ")
            .append(String.format("%.1f", status.drawMillis)).append(" ms");
        font.draw(batch, line, uiCamera.viewportWidth - MARGIN - 560f, bottom + font.getLineHeight() * 2f);
    }

    private void appendCount(String label, int count, Role role) {
        line.append(label).append(' ').append(count);
        int speciesCount = species != null ? species.speciesCount(role) : 0;
        if (speciesCount > 1) {
            line.append(" (").append(speciesCount).append(" species)");
        }
    }

    private void drawStatsPanel(OrthographicCamera uiCamera, Entity selected, float realDelta) {
        if (selected == null) {
            familyOf = null;
            return;
        }
        // Walking the family tree is too slow to redo every frame
        familyRefreshTimer -= realDelta;
        if (lineage != null && (selected != familyOf || familyRefreshTimer <= 0f)) {
            livingDescendants = lineage.livingDescendants(Mappers.lineage.get(selected).id);
            familyOf = selected;
            familyRefreshTimer = FAMILY_REFRESH_SECONDS;
        }

        StatsComponent stats = Mappers.stats.get(selected);
        GenderComponent gender = Mappers.gender.get(selected);
        BehaviourComponent behaviour = Mappers.behaviour.get(selected);
        GenomeComponent dna = Mappers.genome.get(selected);
        AgeComponent age = Mappers.age.get(selected);
        LineageComponent family = Mappers.lineage.get(selected);
        SpeciesComponent kind = Mappers.species.get(selected);

        String parents = family.motherId < 0 ? "first generation / migrant" : "parents #" + family.motherId + " & #" + family.fatherId;
        String[] lines = {
            Creatures.role(selected) + "  " + gender.gender + "  #" + family.id + "  gen " + age.generation
                + (kind != null && kind.id > 0 ? "  species " + kind.id : ""),
            "Age: " + (int) (age.age / 60f) + ":" + String.format("%02d", (int) age.age % 60)
                + " of ~" + (int) (age.lifespan / 60f) + " min",
            "Health " + (int) stats.health + "   Energy " + (int) stats.energy,
            "Hunger " + (int) stats.hunger + "   Thirst " + (int) stats.thirst,
            "Doing: " + behaviour.behaviour + (behaviour.exhausted ? " (exhausted)" : ""),
            "",
            parents,
            "Children " + family.children + "   Living descendants " + livingDescendants,
            "",
            "Speed " + (int) Traits.speed(dna) + "   Vision " + (int) Traits.visionRange(dna),
            String.format("Size %.2f   Stealth %.2f", dna.genes[GenomeComponent.SIZE], dna.genes[GenomeComponent.STEALTH]),
            String.format("Metabolism %.2f   Recovery %.2f", dna.genes[GenomeComponent.METABOLISM], dna.genes[GenomeComponent.RECOVERY_RATE]),
            String.format("Scent %.2f   Ornament %.2f", dna.genes[GenomeComponent.SCENT_RANGE], dna.genes[GenomeComponent.ORNAMENT]),
        };

        float lineHeight = font.getLineHeight();
        float panelWidth = 620f;
        float panelHeight = lines.length * lineHeight + MARGIN * 1.5f;
        float x = MARGIN;
        float y = uiCamera.viewportHeight - MARGIN * 3.5f - lineHeight * 2f - panelHeight;

        shapes.filledRectangle(x, y, panelWidth, panelHeight, panelBg);
        shapes.rectangle(x, y, panelWidth, panelHeight, panelBorder, 1f);

        font.setColor(Color.WHITE);
        float textY = y + panelHeight - MARGIN * 0.75f;
        for (String text : lines) {
            font.draw(batch, text, x + MARGIN, textY);
            textY -= lineHeight;
        }
    }

    /** The font is shared with the start screen's widgets, so leave it white. */
    void dispose() {
        font.setColor(Color.WHITE);
    }
}
