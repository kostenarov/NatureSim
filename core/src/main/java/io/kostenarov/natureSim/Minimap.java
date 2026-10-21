package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Systems.WaterSourceSystem;
import space.earlygrey.shapedrawer.ShapeDrawer;

/**
 * A small overview of the whole map in a corner of the screen: ponds, every creature as a dot colored by
 * role, and a box showing what the camera currently sees. Clicking it moves the camera there.
 * Drawn in screen space, between batch.begin() and batch.end().
 */
public class Minimap {
    private static final Color BACKGROUND = new Color(0.15f, 0.25f, 0.15f, 0.85f);
    private static final Color BORDER = new Color(0.8f, 0.8f, 0.8f, 0.9f);
    private static final Color WATER = new Color(0.25f, 0.5f, 0.9f, 1f);
    private static final Color PREY = new Color(0.55f, 0.95f, 0.55f, 1f);
    private static final Color PREDATOR = new Color(1f, 0.3f, 0.3f, 1f);
    private static final Color SCAVENGER = new Color(0.8f, 0.55f, 1f, 1f);
    private static final Color VIEW = new Color(1f, 1f, 1f, 0.9f);

    private final ShapeDrawer shapes;
    private final ImmutableArray<Entity> ponds;
    private final ImmutableArray<Entity> creatures;

    // Where the minimap was last drawn, in screen space
    private float left;
    private float bottom;
    private float size;

    public Minimap(ShapeDrawer shapes, Engine engine) {
        this.shapes = shapes;
        this.ponds = engine.getEntitiesFor(WaterSourceSystem.WATER_FAMILY);
        this.creatures = engine.getEntitiesFor(Family.all(PositionComponent.class, StatsComponent.class).get());
    }

    public void render(float left, float bottom, float size, OrthographicCamera worldCamera) {
        this.left = left;
        this.bottom = bottom;
        this.size = size;
        float scale = size / World.WIDTH;

        shapes.filledRectangle(left, bottom, size, size, BACKGROUND);
        for (Entity pond : ponds) {
            Vector2 center = Mappers.position.get(pond).position;
            shapes.filledCircle(left + center.x * scale, bottom + center.y * scale,
                Math.max(1.5f, Mappers.waterSource.get(pond).radius * scale), WATER);
        }
        for (Entity creature : creatures) {
            Vector2 pos = Mappers.position.get(creature).position;
            Color color;
            float dot;
            switch (Creatures.role(creature)) {
                case PREDATOR:
                    color = PREDATOR;
                    dot = 3f;
                    break;
                case SCAVENGER:
                    color = SCAVENGER;
                    dot = 3f;
                    break;
                default:
                    color = PREY;
                    dot = 1.5f;
                    break;
            }
            float x = left + (pos.x + World.CREATURE_HALF_SIZE) * scale;
            float y = bottom + (pos.y + World.CREATURE_HALF_SIZE) * scale;
            shapes.filledRectangle(x - dot / 2f, y - dot / 2f, dot, dot, color);
        }

        // The camera's current view
        float viewWidth = worldCamera.viewportWidth * worldCamera.zoom * scale;
        float viewHeight = worldCamera.viewportHeight * worldCamera.zoom * scale;
        shapes.rectangle(left + worldCamera.position.x * scale - viewWidth / 2f, bottom + worldCamera.position.y * scale - viewHeight / 2f,
            viewWidth, viewHeight, VIEW, 1.5f);
        shapes.rectangle(left, bottom, size, size, BORDER, 1f);
    }

    /** True if a point in screen space (y up) is on the minimap as last drawn. */
    public boolean contains(float screenX, float screenY) {
        return size > 0f && screenX >= left && screenX <= left + size && screenY >= bottom && screenY <= bottom + size;
    }

    /** The world point under a screen-space point on the minimap. */
    public Vector2 toWorld(float screenX, float screenY, Vector2 out) {
        float scale = World.WIDTH / size;
        return out.set((screenX - left) * scale, (screenY - bottom) * scale);
    }
}
