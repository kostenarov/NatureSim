package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.ReproductionComponent;
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
    // With nothing else to do, a resting creature stays put until this fresh
    protected static final float IDLE_RESUME_THRESHOLD = 80f;
    // How far from a pond's shore still counts as "at the waterhole" (matches the band where food grows)
    private static final float WATERHOLE_RANGE = 300f;

    protected final Vector2 tmp = new Vector2(); // Scratch vector, avoids allocating every frame
    private final Vector2 heading = new Vector2();
    protected final Array<Entity> nearby = new Array<>(false, 64); // Scratch list for grid queries
    private final Vector2 waterTarget = new Vector2();
    private ImmutableArray<Entity> waterEntities;
    private ImmutableArray<Entity> allCreatures;

    protected CreatureSystem(Family family) {
        super(family);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        waterEntities = engine.getEntitiesFor(WaterSourceSystem.WATER_FAMILY);
        allCreatures = engine.getEntitiesFor(Family.all(PositionComponent.class, StatsComponent.class).get());
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

    /**
     * With no needs to meet, rest until fairly fresh instead of wandering: wandering burns the energy that
     * breeding needs. A creature that is already up keeps wandering until it is properly tired.
     */
    protected EntityBehaviour restOrWander(StatsComponent stats, BehaviourComponent behaviour) {
        boolean resting = behaviour.behaviour == EntityBehaviour.IDLE;
        if (resting ? stats.energy < IDLE_RESUME_THRESHOLD : stats.energy <= ENERGY_STOP_THRESHOLD) {
            return EntityBehaviour.IDLE;
        }
        return EntityBehaviour.EXPLORING;
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
        WaterSourceSystem.pushOutOfDeepWater(waterEntities, behaviour.targetPoint); // Never aim into deep water
        behaviour.hasTarget = true;
    }

    /**
     * Head for the fertile ground around the nearest pond and wander there. Food grows mostly near water and
     * everyone comes to drink, so this is where to look when there's nothing in sight.
     */
    protected void roamNearWater(Entity entity, float deltaTime) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        Entity pond = WaterSourceSystem.findNearestWater(waterEntities, pos);
        if (pond == null || WaterSourceSystem.distanceFromShore(pond, pos) <= WATERHOLE_RANGE) {
            explore(entity, deltaTime);
            return;
        }
        float speed = Traits.speed(entity.getComponent(GenomeComponent.class));
        moveToward(entity, WaterSourceSystem.approachPoint(pond, waterTarget), speed, deltaTime);
        entity.getComponent(BehaviourComponent.class).hasTarget = false; // Pick a fresh wander target on arrival
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

    // --- Courtship -------------------------------------------------------------------------------------

    /**
     * The nearest partner this creature can detect that is ready to mate, or null if it isn't ready itself
     * or no one suitable is around. The actual mating happens in MatingSystem once they are close.
     */
    protected Entity findVisiblePartner(Entity entity) {
        if (!MatingSystem.isReadyToMate(entity)) {
            return null;
        }
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        float range = partnerSearchRange(entity);

        Iterable<Entity> candidates = allCreatures;
        SpatialIndexSystem index = getEngine().getSystem(SpatialIndexSystem.class);
        if (index != null) {
            index.creatures().query(pos.position.x, pos.position.y, range, nearby);
            candidates = nearby;
        }

        Entity nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        for (Entity candidate : candidates) {
            if (!MatingSystem.arePartners(entity, candidate) || !MatingSystem.isReadyToMate(candidate)
                || !canDetectPartner(entity, candidate)) {
                continue;
            }
            float distance = pos.position.dst(candidate.getComponent(PositionComponent.class).position);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /** How far away a partner can be noticed. Prey rely on sight; predators override this to use smell. */
    protected float partnerSearchRange(Entity entity) {
        return entity.getComponent(VisionComponent.class).visionRange;
    }

    protected boolean canDetectPartner(Entity entity, Entity candidate) {
        return canSee(entity.getComponent(PositionComponent.class), entity.getComponent(VisionComponent.class), candidate);
    }

    /**
     * Walk up to the partner; MatingSystem pairs them once they are within mating distance.
     * With no partner in sight, head for a waterhole, where others gather.
     */
    protected void approachPartner(Entity entity, Entity partner, float deltaTime) {
        if (partner == null) {
            roamNearWater(entity, deltaTime);
            return;
        }
        Vector2 partnerPos = partner.getComponent(PositionComponent.class).position;
        if (entity.getComponent(PositionComponent.class).position.dst(partnerPos) <= ReproductionComponent.MATING_DISTANCE * 0.8f) {
            stop(entity);
            return;
        }
        moveToward(entity, partnerPos, Traits.speed(entity.getComponent(GenomeComponent.class)), deltaTime);
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

        // Deep water can't be crossed: slide along its edge, i.e. walk around the pond
        heading.set(directionX, directionY);
        WaterSourceSystem.slideAlongDeepWater(waterEntities, pos.position, heading, speed * deltaTime);
        vel.velocity.set(heading.x * speed, heading.y * speed);
        pos.position.mulAdd(vel.velocity, deltaTime);
        WaterSourceSystem.pushOutOfDeepWater(waterEntities, pos.position);

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

    /**
     * True if the observer can see the target creature: inside its vision cone and within its vision range,
     * scaled by how easy the target is to spot (stealthy creatures must be closer to be seen).
     */
    protected static boolean canSee(PositionComponent observerPos, VisionComponent vision, Entity target) {
        GenomeComponent targetDna = target.getComponent(GenomeComponent.class);
        float range = vision.visionRange * (targetDna != null ? Traits.visibility(targetDna) : 1f);
        return isInVisionCone(observerPos, vision, target.getComponent(PositionComponent.class), range);
    }

    /** True if the point is inside the observer's vision cone and within {@code range}. */
    protected static boolean isInVisionCone(PositionComponent observerPos, VisionComponent vision, PositionComponent targetPos, float range) {
        // Positions are bottom-left corners and all creatures are the same size, so the offset cancels out.
        float dx = targetPos.position.x - observerPos.position.x;
        float dy = targetPos.position.y - observerPos.position.y;
        if (dx * dx + dy * dy > range * range) {
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
