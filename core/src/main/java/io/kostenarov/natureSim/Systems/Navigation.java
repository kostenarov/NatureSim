package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import io.kostenarov.natureSim.Balance;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.VelocityComponent;
import io.kostenarov.natureSim.Mappers;
import io.kostenarov.natureSim.World;

/**
 * Getting a creature from A to B: swerving around creatures that stand in the way, walking around deep water,
 * staying on the map, and noticing when it's stuck. Used by {@link CreatureSystem}.
 */
final class Navigation {
    private final Vector2 heading = new Vector2();
    private final Array<Entity> obstacles = new Array<>(false, 16);
    private final ImmutableArray<Entity> ponds;
    private SpatialIndexSystem index; // Null if the engine has no spatial index (some tests)

    Navigation(ImmutableArray<Entity> ponds) {
        this.ponds = ponds;
    }

    /** Called once per step with the engine's spatial index, which may be null. */
    void setIndex(SpatialIndexSystem index) {
        this.index = index;
    }

    /** Moves along a unit direction at the given speed. Returns true if the map edge stopped the creature. */
    boolean move(Entity entity, float directionX, float directionY, float speed, float deltaTime) {
        PositionComponent pos = Mappers.position.get(entity);
        VelocityComponent vel = Mappers.velocity.get(entity);

        heading.set(directionX, directionY);
        // Swerve around creatures standing in the way (resting, eating, drinking) instead of walking into them
        avoidStandingCreatures(entity, pos.position, heading);
        // Deep water can't be crossed: slide along its edge, i.e. walk around the pond
        WaterSourceSystem.slideAlongDeepWater(ponds, pos.position, heading, speed * deltaTime);
        vel.velocity.set(heading.x * speed, heading.y * speed);
        pos.position.mulAdd(vel.velocity, deltaTime);
        WaterSourceSystem.pushOutOfDeepWater(ponds, pos.position);

        boolean hitBoundary = clampToMap(pos);
        if (!hitBoundary && !vel.velocity.isZero()) {
            // Look where we're going.
            Mappers.vision.get(entity).directionAngle =
                (float) Math.toDegrees(Math.atan2(vel.velocity.y, vel.velocity.x));
        }
        return hitBoundary;
    }

    /**
     * True once a creature trying to reach a target has barely moved for {@link Balance#STUCK_SECONDS}.
     */
    static boolean isMakingNoProgress(BehaviourComponent behaviour, Vector2 position, float deltaTime) {
        if (position.dst2(behaviour.progressAnchor) > Balance.STUCK_DISTANCE * Balance.STUCK_DISTANCE) {
            behaviour.progressAnchor.set(position);
            behaviour.progressTimer = 0f;
            return false;
        }
        behaviour.progressTimer += deltaTime;
        if (behaviour.progressTimer >= Balance.STUCK_SECONDS) {
            behaviour.progressTimer = 0f;
            return true;
        }
        return false;
    }

    /**
     * Bends a unit heading away from creatures that are standing still just ahead. Collisions won't push a
     * standing creature aside (so it doesn't twitch), which means a walker has to go around it instead.
     * Moving creatures aren't avoided: collisions sort those out, and a predator mustn't swerve around its prey.
     */
    private void avoidStandingCreatures(Entity self, Vector2 position, Vector2 heading) {
        if (index == null) {
            return;
        }
        index.standingCreatures().query(position.x, position.y, Balance.AVOID_RANGE, obstacles);
        float pushX = 0f;
        float pushY = 0f;
        for (Entity other : obstacles) {
            if (other == self) {
                continue;
            }
            Vector2 otherPos = Mappers.position.get(other).position;
            float dx = otherPos.x - position.x;
            float dy = otherPos.y - position.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance > Balance.AVOID_RANGE || distance < 0.01f) {
                continue;
            }
            float ahead = (dx * heading.x + dy * heading.y) / distance; // 1 = dead ahead, 0 = beside
            if (ahead <= 0.3f || !Mappers.velocity.get(other).velocity.isZero()) {
                continue;
            }
            // Steer toward whichever side it isn't on; closer and more directly ahead counts more
            float side = heading.x * dy - heading.y * dx >= 0f ? -1f : 1f;
            float weight = (1f - distance / Balance.AVOID_RANGE) * ahead;
            pushX += -heading.y * side * weight;
            pushY += heading.x * side * weight;
        }
        if (pushX != 0f || pushY != 0f) {
            heading.add(pushX * Balance.AVOID_STRENGTH, pushY * Balance.AVOID_STRENGTH).nor();
        }
    }

    private static boolean clampToMap(PositionComponent pos) {
        float maxX = World.WIDTH - World.CREATURE_SIZE;
        float maxY = World.HEIGHT - World.CREATURE_SIZE;
        boolean hitBoundary = pos.position.x < 0f || pos.position.x > maxX || pos.position.y < 0f || pos.position.y > maxY;
        pos.position.x = MathUtils.clamp(pos.position.x, 0f, maxX);
        pos.position.y = MathUtils.clamp(pos.position.y, 0f, maxY);
        return hitBoundary;
    }
}
