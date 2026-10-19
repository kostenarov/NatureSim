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
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Components.WaterSourceComponent;
import io.kostenarov.natureSim.Systems.WaterSourceSystem;
import space.earlygrey.shapedrawer.ShapeDrawer;

/**
 * Draws the world: ground, ponds, vision cones, scent ranges, food and creatures.
 * Anything outside the camera's view is skipped.
 */
public class WorldRenderer implements Disposable {
    private static final float TILE_SIZE = 40f;
    private static final int CIRCLE_SEGMENTS = 48;
    private static final float CONE_SEGMENT_DEGREES = 5f;

    private final SpriteBatch batch;
    private final ShapeDrawer shapeDrawer;
    private final SimulationConfig config;

    private final Texture preyTexture;
    private final Texture predatorTexture;
    private final Texture foodTexture;
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

    private final ImmutableArray<Entity> ponds;
    private final ImmutableArray<Entity> seeingCreatures;
    private final ImmutableArray<Entity> predators;
    private final ImmutableArray<Entity> food;
    private final ImmutableArray<Entity> creatures;

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

        preyTexture = new Texture("agent.png");
        predatorTexture = new Texture("agentPredator.png");
        foodTexture = createFoodTexture();
        fieldTexture = new Texture(Gdx.files.internal("field.png"), true);
        fieldTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        tileRegion = new TextureRegion(fieldTexture, 0, 0,
            Math.min(fieldTexture.getWidth(), (int) TILE_SIZE), Math.min(fieldTexture.getHeight(), (int) TILE_SIZE));

        ponds = engine.getEntitiesFor(WaterSourceSystem.WATER_FAMILY);
        seeingCreatures = engine.getEntitiesFor(Family.all(PositionComponent.class, VisionComponent.class).get());
        predators = engine.getEntitiesFor(Family.all(PositionComponent.class, PredatorComponent.class).get());
        food = engine.getEntitiesFor(Family.all(FoodSourceComponent.class, PositionComponent.class).get());
        creatures = engine.getEntitiesFor(Family.all(PositionComponent.class, StatsComponent.class).get());
    }

    /** A simple green circle. */
    private static Texture createFoodTexture() {
        Pixmap pixmap = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pixmap.setColor(0.2f, 0.8f, 0.2f, 1f);
        pixmap.fillCircle(8, 8, 7);
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
        drawCreatures();

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

        for (int column = firstColumn; column <= lastColumn; column++) {
            for (int row = firstRow; row <= lastRow; row++) {
                batch.draw(tileRegion, column * TILE_SIZE, row * TILE_SIZE, TILE_SIZE, TILE_SIZE);
            }
        }
    }

    /** Ponds sit underneath everything else; their position is the pond center. */
    private void drawPonds() {
        for (Entity pond : ponds) {
            PositionComponent pos = pond.getComponent(PositionComponent.class);
            float radius = pond.getComponent(WaterSourceComponent.class).radius;
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
            PositionComponent pos = creature.getComponent(PositionComponent.class);
            VisionComponent vision = creature.getComponent(VisionComponent.class);
            float centerX = pos.position.x + World.CREATURE_HALF_SIZE;
            float centerY = pos.position.y + World.CREATURE_HALF_SIZE;
            if (isVisible(centerX, centerY, vision.visionRange)) {
                drawVisionCone(centerX, centerY, vision);
            }
        }
    }

    private void drawScentRanges() {
        for (Entity predator : predators) {
            PositionComponent pos = predator.getComponent(PositionComponent.class);
            PredatorComponent predatorData = predator.getComponent(PredatorComponent.class);
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
            PositionComponent pos = item.getComponent(PositionComponent.class);
            if (isVisible(pos.position.x, pos.position.y, 16f)) {
                batch.draw(foodTexture, pos.position.x, pos.position.y);
            }
        }
    }

    private void drawCreatures() {
        for (Entity creature : creatures) {
            PositionComponent pos = creature.getComponent(PositionComponent.class);
            if (isVisible(pos.position.x, pos.position.y, World.CREATURE_SIZE)) {
                if (tintGene >= 0) {
                    batch.setColor(geneColor(creature.getComponent(GenomeComponent.class).genes[tintGene]));
                }
                Texture texture = Creatures.isPredator(creature) ? predatorTexture : preyTexture;
                batch.draw(texture, pos.position.x, pos.position.y);
            }
        }
        batch.setColor(Color.WHITE);
    }

    /** Blue for a gene of 0, through green, to red for 2. */
    private Color geneColor(float gene) {
        float hue = 240f * (1f - MathUtils.clamp(gene / 2f, 0f, 1f));
        return tint.fromHsv(hue, 0.85f, 1f);
    }

    /** Colors creatures by this gene (a GenomeComponent index), or -1 for their normal look. */
    public void setTintGene(int gene) {
        tintGene = gene;
    }

    // --- Shapes -----------------------------------------------------------------------------------------

    /** A filled, outlined wedge showing what the creature can see. */
    private void drawVisionCone(float centerX, float centerY, VisionComponent vision) {
        float startAngle = vision.directionAngle - vision.visionAngle / 2f;
        float endAngle = vision.directionAngle + vision.visionAngle / 2f;
        float range = vision.visionRange;

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
        foodTexture.dispose();
        fieldTexture.dispose();
    }
}
