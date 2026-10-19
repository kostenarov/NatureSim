package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Components.VelocityComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Traits;
import io.kostenarov.natureSim.World;

/**
 * Shared base for the creature behaviour systems: each step a creature decides what to do
 * ({@link #decideBehaviour}) and then does it ({@link #executeBehaviour}). Movement, wandering,
 * drinking and sight checks are the same for every kind of creature and live here.
 * Hunger, thirst and energy upkeep is handled separately by {@link MetabolismSystem}.
 */
public abstract class CreatureSystem extends IteratingSystem {
    // Hysteresis: creatures hard-stop at low energy and only move again after recharging.
    protected static final float ENERGY_STOP_THRESHOLD = 3f;
    protected static final float ENERGY_RESUME_THRESHOLD = 30f;

    protected final Vector2 tmp = new Vector2(); // Scratch vector, avoids allocating every frame
    private final Vector2 waterTarget = new Vector2();
    private ImmutableArray<Entity> waterEntities;

    protected CreatureSystem(Family family) {
        super(family);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        waterEntities = engine.getEntitiesFor(WaterSourceSystem.WATER_FAMILY);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);
        behaviour.behaviour = decideBehaviour(entity);
        executeBehaviour(entity, behaviour.behaviour, deltaTime);
    }

    protected abstract EntityBehaviour decideBehaviour(Entity entity);

    protected abstract void executeBehaviour(Entity entity, EntityBehaviour behaviour, float deltaTime);

    // --- Decisions shared by all creatures --------------------------------------------------------------

    /** Drink when thirst is the more urgent need; once drinking, keep drinking until quenched. */
    protected boolean wantsWater(StatsComponent stats, BehaviourComponent behaviour, GenomeComponent dna, boolean hungry) {
        boolean keepDrinking = behaviour.behaviour == EntityBehaviour.SEEKING_WATER && stats.thirst < WaterSourceSystem.WATER_FULL_THRESHOLD;
        boolean thirsty = stats.thirst < Traits.waterSeekThreshold(dna);
        return keepDrinking || (thirsty && (!hungry || stats.thirst < stats.hunger));
    }

    // --- Actions ----------------------------------------------------------------------------------------

    protected void stop(Entity entity) {
        entity.getComponent(VelocityComponent.class).velocity.setZero();
    }

    /** Wander between random points inside the vision cone, pausing to look around at each one. */
    protected void explore(Entity entity, float deltaTime) {
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VisionComponent vision = entity.getComponent(VisionComponent.class);

        // In the "look around" pause, stay put until the timer expires, then face a new direction.
        if (behaviour.waitingAtTarget) {
            vel.velocity.setZero();
            behaviour.waitAtTargetTimer -= deltaTime;
            if (behaviour.waitAtTargetTimer <= 0f) {
                behaviour.waitingAtTarget = false;
                faceRandomDirection(behaviour, vision);
            }
            return;
        }

        // Reached the target: start the pause.
        if (behaviour.hasTarget && pos.position.dst(behaviour.targetPoint) < behaviour.targetReachedThreshold) {
            startLookingAround(behaviour, vel);
            return;
        }

        if (!behaviour.hasTarget) {
            pickTargetPointInVisibleArea(entity);
        }

        if (Traits.speed(dna) * deltaTime >= pos.position.dst(behaviour.targetPoint)) {
            // Would overshoot this step: land exactly on the target.
            pos.position.set(behaviour.targetPoint);
            startLookingAround(behaviour, vel);
            return;
        }

        boolean hitBoundary = moveToward(entity, behaviour.targetPoint, Traits.speed(dna), deltaTime);
        if (hitBoundary) {
            vel.velocity.setZero();
            faceRandomDirection(behaviour, vision);
            pickTargetPointInVisibleArea(entity);
        }
    }

    private void startLookingAround(BehaviourComponent behaviour, VelocityComponent vel) {
        vel.velocity.setZero();
        behaviour.hasTarget = false;
        behaviour.waitingAtTarget = true;
        behaviour.waitAtTargetTimer = BehaviourComponent.WAIT_AT_TARGET_DURATION;
    }

    private void faceRandomDirection(BehaviourComponent behaviour, VisionComponent vision) {
        behaviour.directionAngle = MathUtils.random(0f, 360f);
        vision.directionAngle = behaviour.directionAngle;
        behaviour.hasTarget = false;
    }

    private void pickTargetPointInVisibleArea(Entity entity) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VisionComponent vision = entity.getComponent(VisionComponent.class);
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);

        float distance = MathUtils.random(vision.visionRange * 0.5f, vision.visionRange);
        float halfVisionAngle = vision.visionAngle / 2f;
        float targetAngle = behaviour.directionAngle + MathUtils.random(-halfVisionAngle, halfVisionAngle);

        float targetX = pos.position.x + MathUtils.cosDeg(targetAngle) * distance;
        float targetY = pos.position.y + MathUtils.sinDeg(targetAngle) * distance;
        behaviour.targetPoint.set(
            MathUtils.clamp(targetX, 0f, World.WIDTH - World.CREATURE_SIZE),
            MathUtils.clamp(targetY, 0f, World.HEIGHT - World.CREATURE_SIZE));
        behaviour.hasTarget = true;
    }

    /** Walk to the nearest pond and drink there. */
    protected void seekWater(Entity entity, float deltaTime) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);

        Entity pond = WaterSourceSystem.findNearestWater(waterEntities, pos);
        if (pond == null) {
            stop(entity);
            return;
        }

        if (WaterSourceSystem.canDrinkFrom(pond, pos)) {
            stop(entity);
            WaterSourceSystem.drink(entity.getComponent(StatsComponent.class), deltaTime);
            return;
        }

        float speed = Traits.speed(entity.getComponent(GenomeComponent.class));
        moveToward(entity, WaterSourceSystem.approachPoint(pond, waterTarget), speed, deltaTime);
    }

    // --- Movement ---------------------------------------------------------------------------------------

    /** Moves toward a point at the given speed. Returns true if the map edge stopped the creature. */
    protected boolean moveToward(Entity entity, Vector2 target, float speed, float deltaTime) {
        Vector2 direction = tmp.set(target).sub(entity.getComponent(PositionComponent.class).position).nor();
        return moveInDirection(entity, direction.x, direction.y, speed, deltaTime);
    }

    /** Moves along a unit direction at the given speed. Returns true if the map edge stopped the creature. */
    protected boolean moveInDirection(Entity entity, float directionX, float directionY, float speed, float deltaTime) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);

        vel.velocity.set(directionX * speed, directionY * speed);
        pos.position.mulAdd(vel.velocity, deltaTime);

        boolean hitBoundary = clampToMap(pos);
        if (!hitBoundary && !vel.velocity.isZero()) {
            // Look where we're going.
            entity.getComponent(VisionComponent.class).directionAngle =
                (float) Math.toDegrees(Math.atan2(vel.velocity.y, vel.velocity.x));
        }
        return hitBoundary;
    }

    private boolean clampToMap(PositionComponent pos) {
        float maxX = World.WIDTH - World.CREATURE_SIZE;
        float maxY = World.HEIGHT - World.CREATURE_SIZE;
        boolean hitBoundary = pos.position.x < 0f || pos.position.x > maxX || pos.position.y < 0f || pos.position.y > maxY;
        pos.position.x = MathUtils.clamp(pos.position.x, 0f, maxX);
        pos.position.y = MathUtils.clamp(pos.position.y, 0f, maxY);
        return hitBoundary;
    }

    // --- Perception -------------------------------------------------------------------------------------

    /** True if the target is within the observer's vision range and inside its vision cone. */
    protected static boolean canSee(PositionComponent observerPos, VisionComponent vision, PositionComponent targetPos) {
        // Positions are bottom-left corners and all creatures are the same size, so the offset cancels out.
        float dx = targetPos.position.x - observerPos.position.x;
        float dy = targetPos.position.y - observerPos.position.y;
        if (dx * dx + dy * dy > vision.visionRange * vision.visionRange) {
            return false;
        }

        float angleToTarget = (float) Math.toDegrees(Math.atan2(dy, dx));
        float angleDifference = Math.abs(normalizeAngle(angleToTarget - vision.directionAngle));
        return angleDifference <= vision.visionAngle / 2f;
    }

    /** Wraps an angle into the range -180..180 degrees. */
    private static float normalizeAngle(float angle) {
        float normalized = angle % 360f;
        if (normalized < -180f) {
            normalized += 360f;
        } else if (normalized > 180f) {
            normalized -= 360f;
        }
        return normalized;
    }
}
