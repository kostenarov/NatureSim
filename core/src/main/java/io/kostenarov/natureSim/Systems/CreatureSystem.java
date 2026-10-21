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
import io.kostenarov.natureSim.Mappers;
import io.kostenarov.natureSim.Balance;

/**
 * Shared base for the creature behaviour systems: each step a creature decides what to do
 * ({@link #decideBehaviour}) and then does it ({@link #executeBehaviour}). Movement, wandering,
 * drinking and sight checks are the same for every kind of creature and live here.
 * Hunger, thirst and energy upkeep is handled separately by {@link MetabolismSystem}.
 */
public abstract class CreatureSystem extends IteratingSystem {
    // A creature that runs out of energy collapses and rests, whatever else it needs, until it has

    protected final Vector2 tmp = new Vector2(); // Scratch vector, avoids allocating every frame
    private final Vector2 heading = new Vector2();
    protected final Array<Entity> nearby = new Array<>(false, 64); // Scratch list for grid queries
    private final Vector2 waterTarget = new Vector2();
    private ImmutableArray<Entity> waterEntities;
    private ImmutableArray<Entity> allCreatures;
    private float lightLevel = 1f; // Share of normal sight range available this step

    protected CreatureSystem(Family family) {
        super(family);
    }

    @Override
    public void update(float deltaTime) {
        // Sight depends on daylight; smell doesn't (see ClockSystem)
        ClockSystem clock = getEngine().getSystem(ClockSystem.class);
        lightLevel = clock != null ? clock.visionScale() : 1f;
        super.update(deltaTime);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        waterEntities = engine.getEntitiesFor(WaterSourceSystem.WATER_FAMILY);
        allCreatures = engine.getEntitiesFor(Family.all(PositionComponent.class, StatsComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        BehaviourComponent behaviour = Mappers.behaviour.get(entity);
        if (isExhausted(entity, behaviour)) {
            behaviour.behaviour = EntityBehaviour.IDLE;
            stop(entity);
            return;
        }
        behaviour.behaviour = decideBehaviour(entity);
        executeBehaviour(entity, behaviour.behaviour, deltaTime);
    }

    /**
     * Out of energy: the creature rests no matter how hungry, thirsty or threatened it is, and stays down
     * until its energy is back up to {@link #Balance.EXHAUSTION_RECOVERY_THRESHOLD}.
     */
    private static boolean isExhausted(Entity entity, BehaviourComponent behaviour) {
        float energy = Mappers.stats.get(entity).energy;
        if (energy <= 0f) {
            behaviour.exhausted = true;
        } else if (behaviour.exhausted && energy >= Balance.EXHAUSTION_RECOVERY_THRESHOLD) {
            behaviour.exhausted = false;
        }
        return behaviour.exhausted;
    }

    protected abstract EntityBehaviour decideBehaviour(Entity entity);

    protected abstract void executeBehaviour(Entity entity, EntityBehaviour behaviour, float deltaTime);

    // --- Decisions shared by all creatures --------------------------------------------------------------

    /** Drink when thirst is the more urgent need; once drinking, keep drinking until quenched. */
    protected boolean wantsWater(StatsComponent stats, BehaviourComponent behaviour, GenomeComponent dna, boolean hungry) {
        boolean keepDrinking = behaviour.behaviour == EntityBehaviour.SEEKING_WATER && stats.thirst < Balance.WATER_FULL_THRESHOLD;
        boolean thirsty = stats.thirst < Traits.waterSeekThreshold(dna);
        return keepDrinking || (thirsty && (!hungry || stats.thirst < stats.hunger));
    }

    /**
     * With no needs to meet, rest until fairly fresh instead of wandering: wandering burns the energy that
     * breeding needs. A creature that is already up keeps wandering (until exhaustion stops it).
     */
    protected EntityBehaviour restOrWander(StatsComponent stats, BehaviourComponent behaviour) {
        boolean resting = behaviour.behaviour == EntityBehaviour.IDLE;
        return resting && stats.energy < Balance.IDLE_RESUME_THRESHOLD ? EntityBehaviour.IDLE : EntityBehaviour.EXPLORING;
    }

    // --- Actions ----------------------------------------------------------------------------------------

    protected void stop(Entity entity) {
        Mappers.velocity.get(entity).velocity.setZero();
    }

    /** Wander between random points inside the vision cone, pausing to look around at each one. */
    protected void explore(Entity entity, float deltaTime) {
        BehaviourComponent behaviour = Mappers.behaviour.get(entity);
        VelocityComponent vel = Mappers.velocity.get(entity);
        GenomeComponent dna = Mappers.genome.get(entity);
        PositionComponent pos = Mappers.position.get(entity);
        VisionComponent vision = Mappers.vision.get(entity);

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
        behaviour.waitAtTargetTimer = Balance.LOOK_AROUND_DURATION;
    }

    private void faceRandomDirection(BehaviourComponent behaviour, VisionComponent vision) {
        behaviour.directionAngle = MathUtils.random(0f, 360f);
        vision.directionAngle = behaviour.directionAngle;
        behaviour.hasTarget = false;
    }

    private void pickTargetPointInVisibleArea(Entity entity) {
        PositionComponent pos = Mappers.position.get(entity);
        VisionComponent vision = Mappers.vision.get(entity);
        BehaviourComponent behaviour = Mappers.behaviour.get(entity);

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
        PositionComponent pos = Mappers.position.get(entity);
        Entity pond = WaterSourceSystem.findNearestWater(waterEntities, pos);
        if (pond == null || WaterSourceSystem.distanceFromShore(pond, pos) <= Balance.WATERHOLE_RANGE) {
            explore(entity, deltaTime);
            return;
        }
        float speed = Traits.speed(Mappers.genome.get(entity));
        moveToward(entity, WaterSourceSystem.approachPoint(pond, waterTarget), speed, deltaTime);
        Mappers.behaviour.get(entity).hasTarget = false; // Pick a fresh wander target on arrival
    }

    /** Walk to the nearest pond and drink there. */
    protected void seekWater(Entity entity, float deltaTime) {
        PositionComponent pos = Mappers.position.get(entity);

        Entity pond = WaterSourceSystem.findNearestWater(waterEntities, pos);
        if (pond == null) {
            stop(entity);
            return;
        }

        if (WaterSourceSystem.canDrinkFrom(pond, pos)) {
            stop(entity);
            WaterSourceSystem.drink(Mappers.stats.get(entity), deltaTime);
            return;
        }

        float speed = Traits.speed(Mappers.genome.get(entity));
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
        PositionComponent pos = Mappers.position.get(entity);
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
            float distance = pos.position.dst(Mappers.position.get(candidate).position);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /** How far away a partner can be noticed. Prey rely on sight; predators override this to use smell. */
    protected float partnerSearchRange(Entity entity) {
        float smell = mateScentRange(entity);
        return smell > 0f ? smell : Mappers.vision.get(entity).visionRange;
    }

    /** Range at which this creature can smell a mate, or 0 if it finds mates by sight (prey). */
    protected float mateScentRange(Entity entity) {
        return 0f;
    }

    protected boolean canDetectPartner(Entity entity, Entity candidate) {
        float smell = mateScentRange(entity);
        if (smell > 0f) {
            return Mappers.position.get(entity).position.dst2(Mappers.position.get(candidate).position) <= smell * smell;
        }
        return canSee(Mappers.position.get(entity), Mappers.vision.get(entity), candidate);
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
        Vector2 partnerPos = Mappers.position.get(partner).position;
        if (Mappers.position.get(entity).position.dst(partnerPos) <= Balance.MATING_DISTANCE * 0.8f) {
            stop(entity);
            return;
        }
        moveToward(entity, partnerPos, Traits.speed(Mappers.genome.get(entity)), deltaTime);
    }

    // --- Movement ---------------------------------------------------------------------------------------

    /** Moves toward a point at the given speed. Returns true if the map edge stopped the creature. */
    protected boolean moveToward(Entity entity, Vector2 target, float speed, float deltaTime) {
        Vector2 direction = tmp.set(target).sub(Mappers.position.get(entity).position).nor();
        return moveInDirection(entity, direction.x, direction.y, speed, deltaTime);
    }

    /** Moves along a unit direction at the given speed. Returns true if the map edge stopped the creature. */
    protected boolean moveInDirection(Entity entity, float directionX, float directionY, float speed, float deltaTime) {
        PositionComponent pos = Mappers.position.get(entity);
        VelocityComponent vel = Mappers.velocity.get(entity);

        // Deep water can't be crossed: slide along its edge, i.e. walk around the pond
        heading.set(directionX, directionY);
        WaterSourceSystem.slideAlongDeepWater(waterEntities, pos.position, heading, speed * deltaTime);
        vel.velocity.set(heading.x * speed, heading.y * speed);
        pos.position.mulAdd(vel.velocity, deltaTime);
        WaterSourceSystem.pushOutOfDeepWater(waterEntities, pos.position);

        boolean hitBoundary = clampToMap(pos);
        if (!hitBoundary && !vel.velocity.isZero()) {
            // Look where we're going.
            Mappers.vision.get(entity).directionAngle =
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
    protected boolean canSee(PositionComponent observerPos, VisionComponent vision, Entity target) {
        GenomeComponent targetDna = Mappers.genome.get(target);
        float range = sightRange(vision) * (targetDna != null ? Traits.visibility(targetDna) : 1f);
        return isInVisionCone(observerPos, vision, Mappers.position.get(target), range);
    }

    /** How far a creature can see right now: its vision range, shortened at night. */
    protected float sightRange(VisionComponent vision) {
        return vision.visionRange * lightLevel;
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
