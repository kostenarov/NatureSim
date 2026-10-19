package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.GenderComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.PredatorComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Components.VelocityComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Traits;

public class PredatorSystem extends IteratingSystem {
    private static final float MAP_MIN_X = 0f;
    private static final float MAP_MIN_Y = 0f;
    private static final float MAP_MAX_X = MovementSystem.getMapMaxX();
    private static final float MAP_MAX_Y = MovementSystem.getMapMaxY();
    private static final float ENTITY_SIZE = 32f;

    private static final float ENERGY_STOP_THRESHOLD = 3f;
    private static final float ENERGY_RESUME_THRESHOLD = 30f;

    private static final float LOOK_AROUND_DURATION = BehaviourComponent.WAIT_AT_TARGET_DURATION;

    private ImmutableArray<Entity> preyEntities;
    private ImmutableArray<Entity> waterEntities;
    private final Vector2 waterTarget = new Vector2();
    private final Vector2 tmp = new Vector2(); // Scratch vector, avoids allocating every frame

    public PredatorSystem() {
        super(Family.all(
                PositionComponent.class,
                VelocityComponent.class,
                GenomeComponent.class,
                VisionComponent.class,
                GenderComponent.class,
                StatsComponent.class,
                BehaviourComponent.class,
                PredatorComponent.class
        ).get());
    }

    @Override
    public void addedToEngine(com.badlogic.ashley.core.Engine engine) {
        super.addedToEngine(engine);
        preyEntities = engine.getEntitiesFor(Family.all(
                PositionComponent.class,
                VelocityComponent.class,
                GenomeComponent.class,
                VisionComponent.class,
                GenderComponent.class,
                StatsComponent.class,
                BehaviourComponent.class
        ).exclude(PredatorComponent.class).get());
        waterEntities = engine.getEntitiesFor(WaterSourceSystem.WATER_FAMILY);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        BehaviourComponent behaviourComponent = entity.getComponent(BehaviourComponent.class);
        if (behaviourComponent == null) {
            return;
        }

        PredatorComponent predator = entity.getComponent(PredatorComponent.class);
        predator.attackTimer = Math.max(0f, predator.attackTimer - deltaTime);

        EntityBehaviour behaviour = decideBehaviour(entity);
        behaviourComponent.behaviour = behaviour;

        executeBehaviour(entity, behaviour, deltaTime);
    }

    private EntityBehaviour decideBehaviour(Entity entity) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);
        PredatorComponent predator = entity.getComponent(PredatorComponent.class);

        if (stats == null || behaviour == null || predator == null) {
            return EntityBehaviour.IDLE;
        }

        // Same hysteresis as prey: once resting, keep resting until energy has recovered.
        boolean resting = behaviour.behaviour == EntityBehaviour.IDLE && stats.energy < ENERGY_RESUME_THRESHOLD;
        if (resting || stats.energy <= ENERGY_STOP_THRESHOLD) {
            clearScentTarget(predator);
            return EntityBehaviour.IDLE;
        }

        // Drink when thirst is the more urgent need; once drinking, keep drinking until fully quenched.
        boolean hungry = isHungry(stats, predator);
        boolean keepDrinking = behaviour.behaviour == EntityBehaviour.SEEKING_WATER && stats.thirst < WaterSourceSystem.WATER_FULL_THRESHOLD;
        boolean thirsty = stats.thirst < Traits.waterSeekThreshold(entity.getComponent(GenomeComponent.class));
        if (keepDrinking || (thirsty && (!hungry || stats.thirst < stats.hunger))) {
            clearScentTarget(predator);
            return EntityBehaviour.SEEKING_WATER;
        }

        // Only hunt once there is room for a full meal; a sated predator just wanders.
        if (!hungry) {
            clearScentTarget(predator);
            return EntityBehaviour.EXPLORING;
        }

        Entity prey = findNearestVisiblePrey(entity);
        if (prey == null) {
            if (!predator.hasScentTarget) {
                Entity smelledPrey = findNearestSmelledPrey(entity);
                if (smelledPrey != null) {
                    rememberScentTarget(predator, smelledPrey.getComponent(PositionComponent.class));
                }
            }
            return EntityBehaviour.EXPLORING;
        }

        PositionComponent predatorPos = entity.getComponent(PositionComponent.class);
        PositionComponent preyPos = prey.getComponent(PositionComponent.class);
        float distance = predatorPos.position.dst(preyPos.position);

        return distance <= predator.attackRange ? EntityBehaviour.ATTACKING : EntityBehaviour.HUNTING;
    }

    private boolean isHungry(StatsComponent stats, PredatorComponent predator) {
        float missingHunger = 100f - stats.hunger;
        return missingHunger >= predator.killEnergyGain * predator.huntEnergyBufferMultiplier;
    }

    private void executeBehaviour(Entity entity, EntityBehaviour behaviour, float deltaTime) {
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);
        if (behaviour == EntityBehaviour.HUNTING || behaviour == EntityBehaviour.ATTACKING) {
            huntPrey(entity, deltaTime);
            decreaseStats(entity, deltaTime);
        } else if (behaviour == EntityBehaviour.IDLE) {
            vel.velocity.set(0f, 0f);
            recoverEnergy(entity, deltaTime);
        } else if (behaviour == EntityBehaviour.SEEKING_WATER) {
            seekWater(entity, deltaTime);
            decreaseStats(entity, deltaTime);
        } else if (behaviour == EntityBehaviour.EXPLORING) {
            // decideBehaviour only sets a scent target while the predator is hungry.
            if (entity.getComponent(PredatorComponent.class).hasScentTarget) {
                sniffForPrey(entity, deltaTime);
            } else {
                explore(entity, deltaTime);
            }
            decreaseStats(entity, deltaTime);
        } else {
            vel.velocity.set(0f, 0f);
        }
    }

    private void explore(Entity entity, float deltaTime) {
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VisionComponent vision = entity.getComponent(VisionComponent.class);

        if (behaviour.behaviour == EntityBehaviour.IDLE) {
            return;
        }

        if (behaviour.waitingAtTarget) {
            vel.velocity.set(0f, 0f);
            behaviour.waitAtTargetTimer -= deltaTime;
            if (behaviour.waitAtTargetTimer <= 0f) {
                behaviour.waitingAtTarget = false;
                behaviour.directionAngle = MathUtils.random(0f, 360f);
                vision.directionAngle = behaviour.directionAngle;
                behaviour.targetPoint.setZero();
                behaviour.hasTarget = false;
            }
            return;
        }

        if (behaviour.hasTarget && isTargetReached(pos, behaviour)) {
            vel.velocity.set(0f, 0f);
            behaviour.waitingAtTarget = true;
            behaviour.waitAtTargetTimer = LOOK_AROUND_DURATION;
            return;
        }

        if (!behaviour.hasTarget) {
            pickTargetPointInVisibleArea(entity);
        }

        if (behaviour.hasTarget) {
            float distanceToTarget = pos.position.dst(behaviour.targetPoint);
            float speedMultiplier = Traits.speed(dna);
            float step = speedMultiplier * deltaTime;

            if (step >= distanceToTarget) {
                pos.position.set(behaviour.targetPoint);
                vel.velocity.set(0f, 0f);
                behaviour.hasTarget = false;
                behaviour.waitingAtTarget = true;
                behaviour.waitAtTargetTimer = LOOK_AROUND_DURATION;
            } else {
                moveTowardTarget(entity, behaviour.targetPoint, dna);
                updatePosition(pos, vel, deltaTime);
            }
        }

        boolean hitBoundary = constrainPositionToBounds(pos);
        if (hitBoundary) {
            vel.velocity.set(0f, 0f);
            behaviour.directionAngle = MathUtils.random(0f, 360f);
            vision.directionAngle = behaviour.directionAngle;
            behaviour.targetPoint.setZero();
            behaviour.hasTarget = false;
            pickTargetPointInVisibleArea(entity);
        } else {
            updateVisionDirection(entity);
        }
    }

    private void huntPrey(Entity predator, float deltaTime) {
        PositionComponent predatorPos = predator.getComponent(PositionComponent.class);
        VelocityComponent vel = predator.getComponent(VelocityComponent.class);
        GenomeComponent dna = predator.getComponent(GenomeComponent.class);
        PredatorComponent predatorData = predator.getComponent(PredatorComponent.class);

        Entity prey = findNearestVisiblePrey(predator);
        if (prey == null) {
            vel.velocity.set(0f, 0f);
            return;
        }

        PositionComponent preyPos = prey.getComponent(PositionComponent.class);
        float distance = predatorPos.position.dst(preyPos.position);

        if (distance <= predatorData.attackRange) {
            if (predatorData.attackTimer <= 0f) {
                attackPrey(predator, prey);
                predatorData.attackTimer = predatorData.attackCooldown;
            }
            vel.velocity.set(0f, 0f);
            return;
        }

        Vector2 direction = tmp.set(preyPos.position).sub(predatorPos.position).nor();
        float speedMultiplier = Traits.speed(dna) * predatorData.huntSpeedMultiplier;
        vel.velocity.set(direction.x * speedMultiplier, direction.y * speedMultiplier);

        updatePosition(predatorPos, vel, deltaTime);
        constrainPositionToBounds(predatorPos);
        updateVisionDirection(predator);
    }

    private void seekWater(Entity predator, float deltaTime) {
        PositionComponent pos = predator.getComponent(PositionComponent.class);
        VelocityComponent vel = predator.getComponent(VelocityComponent.class);
        GenomeComponent dna = predator.getComponent(GenomeComponent.class);
        StatsComponent stats = predator.getComponent(StatsComponent.class);

        Entity pond = WaterSourceSystem.findNearestWater(waterEntities, pos);
        if (pond == null) {
            vel.velocity.set(0f, 0f);
            return;
        }

        if (WaterSourceSystem.canDrinkFrom(pond, pos)) {
            vel.velocity.set(0f, 0f);
            WaterSourceSystem.drink(stats, deltaTime);
            return;
        }

        moveTowardTarget(predator, WaterSourceSystem.approachPoint(pond, waterTarget), dna);
        updatePosition(pos, vel, deltaTime);
        constrainPositionToBounds(pos);
        updateVisionDirection(predator);
    }

    private void sniffForPrey(Entity predator, float deltaTime) {
        PositionComponent predatorPos = predator.getComponent(PositionComponent.class);
        VelocityComponent vel = predator.getComponent(VelocityComponent.class);
        GenomeComponent dna = predator.getComponent(GenomeComponent.class);
        PredatorComponent predatorData = predator.getComponent(PredatorComponent.class);

        if (predatorData == null || predatorPos == null || vel == null || dna == null) {
            return;
        }

        if (!predatorData.hasScentTarget) {
            Entity smelledPrey = findNearestSmelledPrey(predator);
            if (smelledPrey == null) {
                vel.velocity.set(0f, 0f);
                return;
            }

            rememberScentTarget(predatorData, smelledPrey.getComponent(PositionComponent.class));
        }

        float distanceToScent = predatorPos.position.dst(predatorData.scentTarget);
        if (distanceToScent <= predatorData.scentReachThreshold) {
            vel.velocity.set(0f, 0f);

            Entity visiblePrey = findNearestVisiblePrey(predator);
            if (visiblePrey != null) {
                return;
            }

            Entity nextSmell = findNearestSmelledPrey(predator);
            if (nextSmell != null) {
                rememberScentTarget(predatorData, nextSmell.getComponent(PositionComponent.class));
            } else {
                clearScentTarget(predatorData);
            }
            return;
        }

        moveTowardTarget(predator, predatorData.scentTarget, dna, predatorData.scentSpeedMultiplier);
        updatePosition(predatorPos, vel, deltaTime);
        boolean hitBoundary = constrainPositionToBounds(predatorPos);
        if (hitBoundary) {
            vel.velocity.set(0f, 0f);
            clearScentTarget(predatorData);
        } else {
            updateVisionDirection(predator);
        }
    }

    private void attackPrey(Entity predator, Entity prey) {
        StatsComponent predatorStats = predator.getComponent(StatsComponent.class);
        PredatorComponent predatorData = predator.getComponent(PredatorComponent.class);
        StatsComponent preyStats = prey.getComponent(StatsComponent.class);

        if (preyStats != null) {
            preyStats.health = Math.max(0f, preyStats.health - predatorData.attackDamage);
            if (preyStats.health <= 0f) {
                if (predatorStats != null) {
                    predatorStats.energy = Math.min(100f, predatorStats.energy + predatorData.killEnergyGain);
                    predatorStats.hunger = Math.min(100f, predatorStats.hunger + predatorData.killEnergyGain);
                }
                clearScentTarget(predatorData);
                getEngine().removeEntity(prey);
            }
        }
    }

    private Entity findNearestVisiblePrey(Entity predator) {
        PositionComponent predatorPos = predator.getComponent(PositionComponent.class);
        VisionComponent vision = predator.getComponent(VisionComponent.class);
        if (predatorPos == null || vision == null || preyEntities == null) {
            return null;
        }

        Entity nearestPrey = null;
        float nearestDistance = Float.MAX_VALUE;

        for (Entity candidate : preyEntities) {
            PositionComponent candidatePos = candidate.getComponent(PositionComponent.class);
            if (candidatePos != null && canSeeTarget(predatorPos, vision, candidatePos)) {
                float distance = predatorPos.position.dst(candidatePos.position);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPrey = candidate;
                }
            }
        }

        return nearestPrey;
    }

    private Entity findNearestSmelledPrey(Entity predator) {
        PositionComponent predatorPos = predator.getComponent(PositionComponent.class);
        PredatorComponent predatorData = predator.getComponent(PredatorComponent.class);
        if (predatorPos == null || predatorData == null || preyEntities == null) {
            return null;
        }

        Entity nearestPrey = null;
        float nearestDistance = Float.MAX_VALUE;

        for (Entity candidate : preyEntities) {
            PositionComponent candidatePos = candidate.getComponent(PositionComponent.class);
            if (candidatePos == null) {
                continue;
            }

            float distance = predatorPos.position.dst(candidatePos.position);
            if (distance <= predatorData.scentRange && distance < nearestDistance) {
                nearestDistance = distance;
                nearestPrey = candidate;
            }
        }

        return nearestPrey;
    }

    private void rememberScentTarget(PredatorComponent predatorData, PositionComponent preyPos) {
        if (predatorData == null || preyPos == null) {
            return;
        }

        predatorData.scentTarget.set(preyPos.position);
        predatorData.hasScentTarget = true;
    }

    private void clearScentTarget(PredatorComponent predatorData) {
        if (predatorData == null) {
            return;
        }

        predatorData.hasScentTarget = false;
        predatorData.scentTarget.setZero();
    }

    private void recoverEnergy(Entity entity, float deltaTime) {
        Traits.rest(entity.getComponent(StatsComponent.class), entity.getComponent(GenomeComponent.class), true, deltaTime);
    }

    private void decreaseStats(Entity entity, float deltaTime) {
        Traits.burnActive(entity.getComponent(StatsComponent.class), entity.getComponent(GenomeComponent.class), true, deltaTime);
    }

    private void pickTargetPointInVisibleArea(Entity entity) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VisionComponent vision = entity.getComponent(VisionComponent.class);
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);

        if (vision == null || pos == null || behaviour == null) {
            return;
        }

        float distance = MathUtils.random(vision.visionRange * 0.5f, vision.visionRange);
        float halfVisionAngle = vision.visionAngle / 2f;
        float angleOffset = MathUtils.random(-halfVisionAngle, halfVisionAngle);
        float targetAngle = behaviour.directionAngle + angleOffset;

        float targetX = pos.position.x + MathUtils.cosDeg(targetAngle) * distance;
        float targetY = pos.position.y + MathUtils.sinDeg(targetAngle) * distance;

        targetX = MathUtils.clamp(targetX, MAP_MIN_X, MAP_MAX_X - ENTITY_SIZE);
        targetY = MathUtils.clamp(targetY, MAP_MIN_Y, MAP_MAX_Y - ENTITY_SIZE);

        behaviour.targetPoint.set(targetX, targetY);
        behaviour.hasTarget = true;
    }

    private boolean isTargetReached(PositionComponent pos, BehaviourComponent behaviour) {
        if (!behaviour.hasTarget) {
            return true;
        }
        return pos.position.dst(behaviour.targetPoint) < behaviour.targetReachedThreshold;
    }

    private void updatePosition(PositionComponent pos, VelocityComponent vel, float deltaTime) {
        pos.position.x += vel.velocity.x * deltaTime;
        pos.position.y += vel.velocity.y * deltaTime;
    }

    private boolean constrainPositionToBounds(PositionComponent pos) {
        boolean hitBoundary = false;

        if (pos.position.x < MAP_MIN_X) {
            pos.position.x = MAP_MIN_X;
            hitBoundary = true;
        } else if (pos.position.x > MAP_MAX_X - ENTITY_SIZE) {
            pos.position.x = MAP_MAX_X - ENTITY_SIZE;
            hitBoundary = true;
        }

        if (pos.position.y < MAP_MIN_Y) {
            pos.position.y = MAP_MIN_Y;
            hitBoundary = true;
        } else if (pos.position.y > MAP_MAX_Y - ENTITY_SIZE) {
            pos.position.y = MAP_MAX_Y - ENTITY_SIZE;
            hitBoundary = true;
        }

        return hitBoundary;
    }

    private void updateVisionDirection(Entity entity) {
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);
        VisionComponent vision = entity.getComponent(VisionComponent.class);
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);

        if (behaviour.behaviour == EntityBehaviour.IDLE) {
            return;
        }

        if (vision != null && (vel.velocity.x != 0 || vel.velocity.y != 0)) {
            vision.directionAngle = (float) Math.toDegrees(Math.atan2(vel.velocity.y, vel.velocity.x));
        }
    }

    private void moveTowardTarget(Entity entity, Vector2 targetPoint, GenomeComponent dna) {
        moveTowardTarget(entity, targetPoint, dna, 1f);
    }

    private void moveTowardTarget(Entity entity, Vector2 targetPoint, GenomeComponent dna, float speedScale) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);

        Vector2 direction = tmp.set(targetPoint).sub(pos.position).nor();
        float speedMultiplier = Traits.speed(dna) * speedScale;
        vel.velocity.set(direction.x * speedMultiplier, direction.y * speedMultiplier);
    }


    private boolean canSeeTarget(PositionComponent observerPos, VisionComponent vision, PositionComponent targetPos) {
        float observerCenterX = observerPos.position.x + (ENTITY_SIZE / 2f);
        float observerCenterY = observerPos.position.y + (ENTITY_SIZE / 2f);
        float targetCenterX = targetPos.position.x + (ENTITY_SIZE / 2f);
        float targetCenterY = targetPos.position.y + (ENTITY_SIZE / 2f);

        float dx = targetCenterX - observerCenterX;
        float dy = targetCenterY - observerCenterY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance > vision.visionRange) {
            return false;
        }

        float angleToTarget = (float) Math.toDegrees(Math.atan2(dy, dx));
        float angleDifference = Math.abs(normalizeAngle(angleToTarget - vision.directionAngle));
        return angleDifference <= vision.visionAngle / 2f;
    }

    private float normalizeAngle(float angle) {
        float normalized = angle % 360f;
        if (normalized < -180f) {
            normalized += 360f;
        } else if (normalized > 180f) {
            normalized -= 360f;
        }
        return normalized;
    }
}

