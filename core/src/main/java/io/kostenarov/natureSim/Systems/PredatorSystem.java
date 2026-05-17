package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.GenderComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.PredatorComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Components.VelocityComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Enums.EntityBehaviour;

public class PredatorSystem extends IteratingSystem {
    private static final float MAP_MIN_X = 0f;
    private static final float MAP_MIN_Y = 0f;
    private static final float MAP_MAX_X = MovementSystem.getMapMaxX();
    private static final float MAP_MAX_Y = MovementSystem.getMapMaxY();
    private static final float ENTITY_SIZE = 32f;

    private static final float ENERGY_STOP_THRESHOLD = 3f;
    private static final float BASE_HUNGER_DECREASE_PER_SEC = 0.12f;
    private static final float BASE_THIRST_DECREASE_PER_SEC = 0.06f;
    private static final float BASE_ENERGY_DECREASE_PER_SEC = 3f;
    private static final float BASE_ENERGY_RECOVER_PER_SEC = 4f;
    private static final float BASE_HEALTH_DECREASE_PER_SEC = 12f;

    private static final float LOOK_AROUND_DURATION = BehaviourComponent.WAIT_AT_TARGET_DURATION;

    private ImmutableArray<Entity> preyEntities;

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
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        BehaviourComponent behaviourComponent = entity.getComponent(BehaviourComponent.class);
        if (behaviourComponent == null) {
            return;
        }

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

        float missingEnergy = 100f - stats.energy;

        if (stats.energy <= ENERGY_STOP_THRESHOLD) {
            predator.hasScentTarget = false;
            return EntityBehaviour.IDLE;
        }

        Entity prey = findNearestVisiblePrey(entity);
        if (prey == null) {
            if (predator.hasScentTarget) {
                return EntityBehaviour.EXPLORING;
            }

            Entity smelledPrey = findNearestSmelledPrey(entity);
            if (smelledPrey != null) {
                rememberScentTarget(predator, smelledPrey.getComponent(PositionComponent.class));
                return EntityBehaviour.EXPLORING;
            }

            return EntityBehaviour.EXPLORING;
        }

        PositionComponent predatorPos = entity.getComponent(PositionComponent.class);
        PositionComponent preyPos = prey.getComponent(PositionComponent.class);
        float distance = predatorPos.position.dst(preyPos.position);

        if (missingEnergy <= predator.killEnergyGain * predator.huntEnergyBufferMultiplier) {
            return distance <= predator.attackRange ? EntityBehaviour.ATTACKING : EntityBehaviour.HUNTING;
        }

        rememberScentTarget(predator, preyPos);
        return EntityBehaviour.EXPLORING;
    }

    private void executeBehaviour(Entity entity, EntityBehaviour behaviour, float deltaTime) {
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);
        if (behaviour == EntityBehaviour.HUNTING || behaviour == EntityBehaviour.ATTACKING) {
            huntPrey(entity, deltaTime);
            decreaseStats(entity, deltaTime);
        } else if (behaviour == EntityBehaviour.IDLE) {
            vel.velocity.set(0f, 0f);
            recoverEnergy(entity, deltaTime);
        } else if (behaviour == EntityBehaviour.EXPLORING) {
            if (entity.getComponent(PredatorComponent.class) != null) {
                PredatorComponent predatorData = entity.getComponent(PredatorComponent.class);
                if (predatorData.hasScentTarget || findNearestSmelledPrey(entity) != null) {
                    sniffForPrey(entity, deltaTime);
                } else {
                    explore(entity, deltaTime);
                }
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
            float speedMultiplier = 50f + (dna.genes[GenomeComponent.SPEED] * 200f);
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
            attackPrey(predator, prey);
            vel.velocity.set(0f, 0f);
            return;
        }

        com.badlogic.gdx.math.Vector2 direction = new com.badlogic.gdx.math.Vector2(preyPos.position).sub(predatorPos.position).nor();
        float speedMultiplier = (50f + (dna.genes[GenomeComponent.SPEED] * 200f)) * predatorData.huntSpeedMultiplier;
        vel.velocity.set(direction.x * speedMultiplier, direction.y * speedMultiplier);

        updatePosition(predatorPos, vel, deltaTime);
        constrainPositionToBounds(predatorPos);
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
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);
        if (stats != null && dna != null) {
            float recoveryRate = BASE_ENERGY_RECOVER_PER_SEC * (0.5f + dna.genes[GenomeComponent.RECOVERY_RATE]);
            stats.energy = Math.min(100f, stats.energy + recoveryRate * deltaTime);
        }
    }

    private void decreaseStats(Entity entity, float deltaTime) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);
        if (stats != null && dna != null) {
            float hungerRate = BASE_HUNGER_DECREASE_PER_SEC * (0.5f + dna.genes[GenomeComponent.HUNGER_SENSITIVITY]);
            float thirstRate = BASE_THIRST_DECREASE_PER_SEC * (0.5f + dna.genes[GenomeComponent.THIRST_SENSITIVITY]);
            float energyRate = BASE_ENERGY_DECREASE_PER_SEC * (0.5f + dna.genes[GenomeComponent.METABOLISM]);

            stats.hunger = Math.max(0f, stats.hunger - hungerRate * deltaTime);
            stats.thirst = Math.max(0f, stats.thirst - thirstRate * deltaTime);
            stats.energy = Math.max(0f, stats.energy - energyRate * deltaTime);
        }
        decreaseHealth(entity, deltaTime);
    }

    private void decreaseHealth(Entity entity, float deltaTime) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);
        if (stats != null && dna != null && (stats.hunger <= 0f || stats.thirst <= 0f)) {
            float healthLossRate = BASE_HEALTH_DECREASE_PER_SEC / (0.5f + dna.genes[GenomeComponent.METABOLISM]);
            stats.health = Math.max(0f, stats.health - healthLossRate * deltaTime);
        }
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

    private void moveTowardTarget(Entity entity, com.badlogic.gdx.math.Vector2 targetPoint, GenomeComponent dna) {
        moveTowardTarget(entity, targetPoint, dna, 1f);
    }

    private void moveTowardTarget(Entity entity, com.badlogic.gdx.math.Vector2 targetPoint, GenomeComponent dna, float speedScale) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);

        com.badlogic.gdx.math.Vector2 direction = new com.badlogic.gdx.math.Vector2(targetPoint).sub(pos.position).nor();
        float speedMultiplier = (50f + (dna.genes[GenomeComponent.SPEED] * 200f)) * speedScale;
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

