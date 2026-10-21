package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import io.kostenarov.natureSim.Components.FoodSourceComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.PredatorComponent;
import io.kostenarov.natureSim.Components.SpeciesComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Components.WaterSourceComponent;
import io.kostenarov.natureSim.Systems.CarcassSystem;
import io.kostenarov.natureSim.Systems.ClockSystem;
import io.kostenarov.natureSim.Systems.WaterSourceSystem;
import space.earlygrey.shapedrawer.ShapeDrawer;

/**
 * Draws the world: ground (tinted by season), ponds, vision cones, scent ranges, food, carcasses, creatures,
 * and the dark of night on top. Anything outside the camera's view is skipped.
 */
public class WorldRenderer implements Disposable {
    private static final float TILE_SIZE = 40f;
    private static final float FOOD_DRAW_SIZE = 16f; // The size food has in the simulation (FoodSourceSystem)
    private static final int CIRCLE_SEGMENTS = 48;
    private static final float CONE_SEGMENT_DEGREES = 5f;

    private final SpriteBatch batch;
    private final ShapeDrawer shapeDrawer;
    private final SimulationConfig config;

    private final Texture preyTexture;
    private final Texture predatorTexture;
    private final Texture scavengerTexture;
    private final Texture foodTexture;
    private final Texture carcassTexture;
    private final Texture fieldTexture;
    private final TextureRegion tileRegion;

    // Colors are created once; allocating them per creature per frame made a lot of garbage
    private final Color shallowWaterColor = new Color(0.35f, 0.65f, 0.90f, 0.75f);
    private final Color deepWaterColor = new Color(0.10f, 0.28f, 0.70f, 0.95f);
    private final Color shoreColor = new Color(0.75f, 0.68f, 0.45f, 1f);
    private final Color visionColor = new Color(0.72f, 0.62f, 0.42f, 0.22f); // Sandy tone, 22% opacity
    private final Color visionFillColor = new Color(0.72f, 0.62f, 0.42f, 0.12f);
    private final Color scentFillColor = new Color(1f, 0.1f, 0.1f, 0.10f);
    private final Color scentOutlineColor = new Color(1f, 0.1f, 0.1f, 0.25f);
    private final Color scentTargetColor = new Color(1f, 0.2f, 0.2f, 0.6f);
    private final Color nightColor = new Color(0.02f, 0.03f, 0.12f, 0f);
    private final Color groundTint = new Color(1f, 1f, 1f, 1f);
    private final Color carcassTint = new Color(1f, 1f, 1f, 1f);
    // Ground color per season: spring as drawn, summer warmer, autumn browner, winter frosted
    private static final Color[] SEASON_GROUND = {
        new Color(1f, 1f, 1f, 1f), new Color(1.0f, 0.97f, 0.82f, 1f), new Color(1.0f, 0.86f, 0.68f, 1f), new Color(0.86f, 0.92f, 1.0f, 1f)
    };
    private static final float MAX_NIGHT_DARKNESS = 0.55f;

    private final ImmutableArray<Entity> ponds;
    private final ImmutableArray<Entity> seeingCreatures;
    private final ImmutableArray<Entity> predators;
    private final ImmutableArray<Entity> food;
    private final ImmutableArray<Entity> creatures;
    private final ImmutableArray<Entity> carcasses;
    private final ClockSystem clock; // May be null (no day/night or seasons)

    public static final int TINT_SPECIES = GenomeComponent.COUNT; // Tint "gene" meaning: color by species
    private int tintGene = -1;
    private final Color tint = new Color(1f, 1f, 1f, 1f);

    // The part of the world the camera shows this frame
    private float visibleLeft;
    private float visibleRight;
    private float visibleBottom;
    private float visibleTop;

    public WorldRenderer(SpriteBatch batch, ShapeDrawer shapeDrawer, Engine engine, SimulationConfig config) {
        this.batch = batch;
        this.shapeDrawer = shapeDrawer;
        this.config = config;

        preyTexture = new Texture("agentPrey.png");
        predatorTexture = new Texture("agentPredator.png");
        scavengerTexture = new Texture("agentScavenger.png");
        foodTexture = new Texture("food.png");
        foodTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear); // Drawn scaled down
        carcassTexture = createCarcassTexture();
        fieldTexture = new Texture(Gdx.files.internal("field.png"), true);
        fieldTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        tileRegion = new TextureRegion(fieldTexture, 0, 0,
            Math.min(fieldTexture.getWidth(), (int) TILE_SIZE), Math.min(fieldTexture.getHeight(), (int) TILE_SIZE));

        ponds = engine.getEntitiesFor(WaterSourceSystem.WATER_FAMILY);
        seeingCreatures = engine.getEntitiesFor(Family.all(PositionComponent.class, VisionComponent.class).get());
        predators = engine.getEntitiesFor(Family.all(PositionComponent.class, PredatorComponent.class).get());
        food = engine.getEntitiesFor(Family.all(FoodSourceComponent.class, PositionComponent.class).get());
        creatures = engine.getEntitiesFor(Family.all(PositionComponent.class, StatsComponent.class).get());
        carcasses = engine.getEntitiesFor(CarcassSystem.CARCASSES);
        clock = engine.getSystem(ClockSystem.class);
    }

    /** A dark red-brown blot. */
    private static Texture createCarcassTexture() {
        Pixmap pixmap = new Pixmap(20, 20, Pixmap.Format.RGBA8888);
        pixmap.setColor(0.45f, 0.12f, 0.10f, 0.9f);
        pixmap.fillCircle(10, 10, 9);
        pixmap.setColor(0.85f, 0.80f, 0.70f, 1f); // A glimpse of bone
        pixmap.fillRectangle(6, 9, 8, 2);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    public void render(OrthographicCamera camera) {
        updateVisibleArea(camera);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.setColor(Color.WHITE);

        drawGround();
        drawPonds();
        if (config.showVisionCones) {
            drawVisionCones();
        }
        if (config.showScentRanges) {
            drawScentRanges();
        }
        drawFood();
        drawCarcasses();
        drawCreatures();
        drawNight();

        batch.end();
    }

    private void updateVisibleArea(OrthographicCamera camera) {
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

    // --- Layers -----------------------------------------------------------------------------------------

    private void drawGround() {
        int firstColumn = (int) Math.floor(Math.max(0f, visibleLeft) / TILE_SIZE);
        int lastColumn = (int) Math.floor(Math.min(World.WIDTH - 1f, visibleRight) / TILE_SIZE);
        int firstRow = (int) Math.floor(Math.max(0f, visibleBottom) / TILE_SIZE);
        int lastRow = (int) Math.floor(Math.min(World.HEIGHT - 1f, visibleTop) / TILE_SIZE);

        batch.setColor(seasonGroundColor());
        for (int column = firstColumn; column <= lastColumn; column++) {
            for (int row = firstRow; row <= lastRow; row++) {
                batch.draw(tileRegion, column * TILE_SIZE, row * TILE_SIZE, TILE_SIZE, TILE_SIZE);
            }
        }
        batch.setColor(Color.WHITE);
    }

    /** Blends smoothly between the season colors over the year. */
    private Color seasonGroundColor() {
        if (clock == null || !clock.isSeasonsEnabled()) {
            return Color.WHITE;
        }
        float position = clock.yearPhase() * 4f + 0.5f; // Each season's color peaks at its middle
        int from = (int) Math.floor(position - 0.5f) & 3;
        float blend = (position - 0.5f) - (float) Math.floor(position - 0.5f);
        return groundTint.set(SEASON_GROUND[from]).lerp(SEASON_GROUND[(from + 1) & 3], blend);
    }

    private void drawCarcasses() {
        for (Entity carcass : carcasses) {
            PositionComponent pos = Mappers.position.get(carcass);
            if (isVisible(pos.position.x, pos.position.y, 20f)) {
                // Fades as it gets eaten or rots
                carcassTint.a = MathUtils.clamp(Mappers.carcass.get(carcass).food / Balance.CARCASS_FOOD, 0.25f, 1f);
                batch.setColor(carcassTint);
                batch.draw(carcassTexture, pos.position.x + 6f, pos.position.y + 6f);
            }
        }
        batch.setColor(Color.WHITE);
    }

    /** Darkens the view at night (nothing with day/night switched off). */
    private void drawNight() {
        if (clock == null) {
            return;
        }
        nightColor.a = (1f - clock.daylight()) * MAX_NIGHT_DARKNESS;
        if (nightColor.a > 0.01f) {
            shapeDrawer.filledRectangle(visibleLeft, visibleBottom, visibleRight - visibleLeft, visibleTop - visibleBottom, nightColor);
        }
    }

    /** Ponds sit underneath everything else; their position is the pond center. */
    private void drawPonds() {
        for (Entity pond : ponds) {
            PositionComponent pos = Mappers.position.get(pond);
            float radius = Mappers.waterSource.get(pond).radius;
            if (isVisible(pos.position.x, pos.position.y, radius)) {
                // Light shallows creatures can wade into, around darker deep water they can't enter
                drawFilledCircle(pos.position.x, pos.position.y, radius, shallowWaterColor);
                drawFilledCircle(pos.position.x, pos.position.y, WaterSourceSystem.deepRadius(pond), deepWaterColor);
                drawCircleOutline(pos.position.x, pos.position.y, radius, shoreColor);
            }
        }
    }

    private void drawVisionCones() {
        for (Entity creature : seeingCreatures) {
            PositionComponent pos = Mappers.position.get(creature);
            VisionComponent vision = Mappers.vision.get(creature);
            float centerX = pos.position.x + World.CREATURE_HALF_SIZE;
            float centerY = pos.position.y + World.CREATURE_HALF_SIZE;
            if (isVisible(centerX, centerY, vision.visionRange)) {
                drawVisionCone(centerX, centerY, vision);
            }
        }
    }

    private void drawScentRanges() {
        for (Entity predator : predators) {
            PositionComponent pos = Mappers.position.get(predator);
            PredatorComponent predatorData = Mappers.predator.get(predator);
            float centerX = pos.position.x + World.CREATURE_HALF_SIZE;
            float centerY = pos.position.y + World.CREATURE_HALF_SIZE;
            if (isVisible(centerX, centerY, predatorData.scentRange)) {
                drawFilledCircle(centerX, centerY, predatorData.scentRange, scentFillColor);
                drawCircleOutline(centerX, centerY, predatorData.scentRange, scentOutlineColor);
                if (predatorData.hasScentTarget) {
                    // A cross where the prey was last smelled
                    float x = predatorData.scentTarget.x;
                    float y = predatorData.scentTarget.y;
                    shapeDrawer.line(x - 10f, y - 10f, x + 10f, y + 10f, scentTargetColor, 2f);
                    shapeDrawer.line(x - 10f, y + 10f, x + 10f, y - 10f, scentTargetColor, 2f);
                }
            }
        }
    }

    private void drawFood() {
        for (Entity item : food) {
            PositionComponent pos = Mappers.position.get(item);
            if (isVisible(pos.position.x, pos.position.y, FOOD_DRAW_SIZE)) {
                batch.draw(foodTexture, pos.position.x, pos.position.y, FOOD_DRAW_SIZE, FOOD_DRAW_SIZE);
            }
        }
    }

    private void drawCreatures() {
        for (Entity creature : creatures) {
            PositionComponent pos = Mappers.position.get(creature);
            if (isVisible(pos.position.x, pos.position.y, World.CREATURE_SIZE)) {
                batch.setColor(tintFor(creature));
                batch.draw(textureFor(creature), pos.position.x, pos.position.y);
            }
        }
        batch.setColor(Color.WHITE);
    }

    private Texture textureFor(Entity creature) {
        switch (Creatures.role(creature)) {
            case PREDATOR:
                return predatorTexture;
            case SCAVENGER:
                return scavengerTexture;
            default:
                return preyTexture;
        }
    }

    private Color tintFor(Entity creature) {
        if (tintGene == TINT_SPECIES) {
            SpeciesComponent kind = Mappers.species.get(creature);
            return kind != null && kind.id > 0 ? speciesColor(kind.id) : Color.WHITE;
        }
        return tintGene >= 0 ? geneColor(Mappers.genome.get(creature).genes[tintGene]) : Color.WHITE;
    }

    /** A distinct color per species id: the hue steps round by the golden angle, so neighbouring ids differ. */
    private Color speciesColor(int id) {
        return tint.fromHsv((id * 137.5f) % 360f, 0.75f, 1f);
    }

    /** Blue for a gene of 0, through green, to red for 2. */
    private Color geneColor(float gene) {
        float hue = 240f * (1f - MathUtils.clamp(gene / 2f, 0f, 1f));
        return tint.fromHsv(hue, 0.85f, 1f);
    }

    /**
     * Colors creatures by this gene (a GenomeComponent index), by species ({@link #TINT_SPECIES}),
     * or -1 for their normal look.
     */
    public void setTintGene(int gene) {
        tintGene = gene;
    }

    // --- Shapes -----------------------------------------------------------------------------------------

    /** A filled, outlined wedge showing what the creature can see. */
    private void drawVisionCone(float centerX, float centerY, VisionComponent vision) {
        float startAngle = vision.directionAngle - vision.visionAngle / 2f;
        float endAngle = vision.directionAngle + vision.visionAngle / 2f;
        float range = vision.visionRange * (clock != null ? clock.visionScale() : 1f); // Shorter sight at night

        // Edges of the cone
        shapeDrawer.line(centerX, centerY, centerX + range * MathUtils.cosDeg(startAngle), centerY + range * MathUtils.sinDeg(startAngle), visionColor, 2f);
        shapeDrawer.line(centerX, centerY, centerX + range * MathUtils.cosDeg(endAngle), centerY + range * MathUtils.sinDeg(endAngle), visionColor, 2f);

        // Outer arc and fill, in 5 degree segments
        int segments = Math.max(2, (int) ((endAngle - startAngle) / CONE_SEGMENT_DEGREES) + 1);
        float angleStep = (endAngle - startAngle) / segments;
        for (int i = 0; i < segments; i++) {
            float angle = startAngle + i * angleStep;
            float x1 = centerX + range * MathUtils.cosDeg(angle);
            float y1 = centerY + range * MathUtils.sinDeg(angle);
            float x2 = centerX + range * MathUtils.cosDeg(angle + angleStep);
            float y2 = centerY + range * MathUtils.sinDeg(angle + angleStep);
            shapeDrawer.line(x1, y1, x2, y2, visionColor, 2f);
            shapeDrawer.filledTriangle(centerX, centerY, x1, y1, x2, y2, visionFillColor);
        }
    }

    private void drawFilledCircle(float centerX, float centerY, float radius, Color color) {
        float angleStep = 360f / CIRCLE_SEGMENTS;
        float prevX = centerX + radius;
        float prevY = centerY;
        for (int i = 1; i <= CIRCLE_SEGMENTS; i++) {
            float x = centerX + radius * MathUtils.cosDeg(angleStep * i);
            float y = centerY + radius * MathUtils.sinDeg(angleStep * i);
            shapeDrawer.filledTriangle(centerX, centerY, prevX, prevY, x, y, color);
            prevX = x;
            prevY = y;
        }
    }

    private void drawCircleOutline(float centerX, float centerY, float radius, Color color) {
        float angleStep = 360f / CIRCLE_SEGMENTS;
        float prevX = centerX + radius;
        float prevY = centerY;
        for (int i = 1; i <= CIRCLE_SEGMENTS; i++) {
            float x = centerX + radius * MathUtils.cosDeg(angleStep * i);
            float y = centerY + radius * MathUtils.sinDeg(angleStep * i);
            shapeDrawer.line(prevX, prevY, x, y, color, 1.5f);
            prevX = x;
            prevY = y;
        }
    }

    @Override
    public void dispose() {
        preyTexture.dispose();
        predatorTexture.dispose();
        scavengerTexture.dispose();
        foodTexture.dispose();
        carcassTexture.dispose();
        fieldTexture.dispose();
    }
}
