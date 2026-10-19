package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.kostenarov.natureSim.World;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Components.WaterSourceComponent;
import io.kostenarov.natureSim.SimulationConfig;

/**
 * Places ponds on the map at startup and holds the shared drinking rules used by the creature systems.
 */
public class WaterSourceSystem extends EntitySystem {
    private static final float MIN_POND_RADIUS = 90f;
    private static final float MAX_POND_RADIUS = 180f;
    private static final float MIN_POND_GAP = 150f; // Keep ponds from merging into one blob
    private static final int MAX_PLACEMENT_ATTEMPTS = 50;

    public static final Family WATER_FAMILY = Family.all(WaterSourceComponent.class, PositionComponent.class).get();
    // When to start drinking is genetic (Traits.waterSeekThreshold)
    public static final float WATER_FULL_THRESHOLD = 98f;  // Stop drinking once thirst is back above this
    public static final float DRINK_RATE_PER_SEC = 25f;    // Thirst restored per second while drinking
    private static final float DRINK_MARGIN = 24f;         // How far outside the shore a creature can still drink
    public static final float SHALLOW_WIDTH = 40f;         // Wadeable band inside the shore; deeper water is impassable
    private static final float CREATURE_BODY_RADIUS = 13f; // Same body size collisions use

    private final int pondCount;
    private ImmutableArray<Entity> ponds;

    public WaterSourceSystem() {
        this(new SimulationConfig());
    }

    public WaterSourceSystem(SimulationConfig config) {
        this.pondCount = config.pondCount;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        ponds = engine.getEntitiesFor(WATER_FAMILY);

        for (int i = 0; i < pondCount; i++) {
            spawnPond(engine);
        }
    }

    private void spawnPond(Engine engine) {
        float mapMaxX = World.WIDTH;
        float mapMaxY = World.HEIGHT;
        float radius = MathUtils.random(MIN_POND_RADIUS, MAX_POND_RADIUS);

        for (int attempt = 0; attempt < MAX_PLACEMENT_ATTEMPTS; attempt++) {
            float x = MathUtils.random(radius, mapMaxX - radius);
            float y = MathUtils.random(radius, mapMaxY - radius);
            if (!overlapsExistingPond(x, y, radius)) {
                Entity pond = engine.createEntity();
                PositionComponent pos = new PositionComponent();
                pos.position.set(x, y);
                pond.add(pos);
                pond.add(new WaterSourceComponent(radius));
                engine.addEntity(pond);
                return;
            }
        }
    }

    private boolean overlapsExistingPond(float x, float y, float radius) {
        for (Entity other : ponds) {
            PositionComponent otherPos = other.getComponent(PositionComponent.class);
            float otherRadius = other.getComponent(WaterSourceComponent.class).radius;
            if (otherPos.position.dst(x, y) < radius + otherRadius + MIN_POND_GAP) {
                return true;
            }
        }
        return false;
    }

    /** Nearest pond by distance to its shore, or null if the map has no water. */
    public static Entity findNearestWater(ImmutableArray<Entity> ponds, PositionComponent creaturePos) {
        float centerX = creaturePos.position.x + World.CREATURE_HALF_SIZE;
        float centerY = creaturePos.position.y + World.CREATURE_HALF_SIZE;

        Entity nearest = null;
        float nearestShoreDistance = Float.MAX_VALUE;
        for (Entity pond : ponds) {
            PositionComponent pondPos = pond.getComponent(PositionComponent.class);
            float shoreDistance = pondPos.position.dst(centerX, centerY) - pond.getComponent(WaterSourceComponent.class).radius;
            if (shoreDistance < nearestShoreDistance) {
                nearestShoreDistance = shoreDistance;
                nearest = pond;
            }
        }
        return nearest;
    }

    /** True when the creature is close enough to the pond to drink. */
    public static boolean canDrinkFrom(Entity pond, PositionComponent creaturePos) {
        return distanceFromShore(pond, creaturePos) <= DRINK_MARGIN;
    }

    /** How far the creature's center is from the pond's shore (negative when standing in the water). */
    public static float distanceFromShore(Entity pond, PositionComponent creaturePos) {
        PositionComponent pondPos = pond.getComponent(PositionComponent.class);
        float radius = pond.getComponent(WaterSourceComponent.class).radius;
        return pondPos.position.dst(creaturePos.position.x + World.CREATURE_HALF_SIZE, creaturePos.position.y + World.CREATURE_HALF_SIZE) - radius;
    }

    /** Where a creature's PositionComponent should head to reach the pond (aims its center at the pond center). */
    public static Vector2 approachPoint(Entity pond, Vector2 out) {
        PositionComponent pondPos = pond.getComponent(PositionComponent.class);
        return out.set(pondPos.position.x - World.CREATURE_HALF_SIZE, pondPos.position.y - World.CREATURE_HALF_SIZE);
    }

    public static void drink(StatsComponent stats, float deltaTime) {
        stats.thirst = Math.min(100f, stats.thirst + DRINK_RATE_PER_SEC * deltaTime);
    }

    // --- Deep water -------------------------------------------------------------------------------------

    /** Radius of the impassable deep part of a pond; the band outside it (SHALLOW_WIDTH wide) can be waded into. */
    public static float deepRadius(Entity pond) {
        return Math.max(0f, pond.getComponent(WaterSourceComponent.class).radius - SHALLOW_WIDTH);
    }

    /**
     * True if a body of the given radius centered at (x, y) would overlap deep water.
     */
    public static boolean isInDeepWater(ImmutableArray<Entity> ponds, float x, float y, float bodyRadius) {
        for (Entity pond : ponds) {
            float limit = deepRadius(pond) + bodyRadius;
            if (pond.getComponent(PositionComponent.class).position.dst2(x, y) < limit * limit) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves a creature (by its bottom-left {@code position}) out of any deep water it overlaps, to the nearest
     * point at the edge. Returns true if it had to be moved.
     */
    public static boolean pushOutOfDeepWater(ImmutableArray<Entity> ponds, Vector2 position) {
        boolean moved = false;
        for (Entity pond : ponds) {
            Vector2 center = pond.getComponent(PositionComponent.class).position;
            float limit = deepRadius(pond) + CREATURE_BODY_RADIUS;
            float creatureX = position.x + World.CREATURE_HALF_SIZE;
            float creatureY = position.y + World.CREATURE_HALF_SIZE;
            float dx = creatureX - center.x;
            float dy = creatureY - center.y;
            float distanceSquared = dx * dx + dy * dy;
            if (distanceSquared >= limit * limit) {
                continue;
            }
            float distance = (float) Math.sqrt(distanceSquared);
            if (distance < 0.001f) {
                dx = 1f; // Dead center: pick a way out
                dy = 0f;
                distance = 1f;
            }
            position.set(center.x + dx / distance * limit - World.CREATURE_HALF_SIZE,
                center.y + dy / distance * limit - World.CREATURE_HALF_SIZE);
            moved = true;
        }
        return moved;
    }

    /**
     * Adjusts a unit movement direction so a step of {@code stepLength} won't walk into deep water:
     * the part of the direction pointing into the pond is removed, so the creature slides along the edge.
     * Writes the result to {@code direction}, always a unit vector.
     */
    public static void slideAlongDeepWater(ImmutableArray<Entity> ponds, Vector2 position, Vector2 direction, float stepLength) {
        float nextX = position.x + World.CREATURE_HALF_SIZE + direction.x * stepLength;
        float nextY = position.y + World.CREATURE_HALF_SIZE + direction.y * stepLength;
        for (Entity pond : ponds) {
            Vector2 center = pond.getComponent(PositionComponent.class).position;
            float limit = deepRadius(pond) + CREATURE_BODY_RADIUS;
            float dx = nextX - center.x;
            float dy = nextY - center.y;
            float distanceSquared = dx * dx + dy * dy;
            if (distanceSquared >= limit * limit || distanceSquared < 0.0001f) {
                continue;
            }
            float distance = (float) Math.sqrt(distanceSquared);
            float normalX = dx / distance; // Points out of the pond
            float normalY = dy / distance;
            float inward = direction.x * normalX + direction.y * normalY;
            if (inward < 0f) {
                direction.sub(normalX * inward, normalY * inward);
                if (direction.isZero(0.0001f)) {
                    direction.set(-normalY, normalX); // Headed straight in: go around instead
                } else {
                    direction.nor();
                }
            }
        }
    }
}
