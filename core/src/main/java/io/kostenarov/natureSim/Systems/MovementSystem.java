package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Components.*;
import io.kostenarov.natureSim.Enums.EntityBehaviour;

public class MovementSystem extends IteratingSystem {
    // Map boundaries
    private static final float MAP_MIN_X = 0;
    private static final float MAP_MAX_X = 5120;
    private static final float MAP_MIN_Y = 0;
    private static final float MAP_MAX_Y = 5120;

    private static final float ENTITY_SIZE = 32;

    // Use hysteresis so entities hard-stop at low energy and only resume after recharging.
    private static final float ENERGY_STOP_THRESHOLD = 3f;
    private static final float ENERGY_RESUME_THRESHOLD = 30f;

    private static final float FOOD_SEEK_THRESHOLD = 60f; // Seek food when hunger drops below this
    private static final float FOOD_CONSUMPTION_DISTANCE = 32f; // How close to be to eat food
    private static final float PREY_ESCAPE_SPEED_MULTIPLIER = 1.25f;

    // Base per-second rates (before genome modifiers). Using per-second makes behavior stable regardless of frame rate.
    // Individual agents will have these rates scaled by their genome genes.
    private static final float BASE_HUNGER_DECREASE_PER_SEC = 0.12f;
    private static final float BASE_THIRST_DECREASE_PER_SEC = 0.06f;
    private static final float BASE_ENERGY_DECREASE_PER_SEC = 3f;
    private static final float BASE_ENERGY_RECOVER_PER_SEC = 4f;
    private static final float BASE_HEALTH_DECREASE_PER_SEC = 12f;

    private ImmutableArray<Entity> foodEntities;

    public MovementSystem() {
        super(Family.all(PositionComponent.class, VelocityComponent.class, GenomeComponent.class, VisionComponent.class, GenderComponent.class, StatsComponent.class, BehaviourComponent.class).exclude(PredatorComponent.class).get());
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        foodEntities = engine.getEntitiesFor(Family.all(FoodSourceComponent.class, PositionComponent.class).get());
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
        if (stats == null || behaviour == null) {
            return EntityBehaviour.IDLE;
        }

        Entity threateningPredator = findNearestVisiblePredator(entity);
        if (threateningPredator != null) {
            return EntityBehaviour.FLEEING;
        }

        // Check critical needs first
        if (stats.hunger < FOOD_SEEK_THRESHOLD) {
            return EntityBehaviour.SEEKING_FOOD;
        } else if (stats.thirst < 30f) {
            return EntityBehaviour.SEEKING_WATER;
        }

        // If currently resting, keep resting until resume threshold is reached.
        if (behaviour.behaviour == EntityBehaviour.IDLE) {
            return stats.energy >= ENERGY_RESUME_THRESHOLD ? EntityBehaviour.EXPLORING : EntityBehaviour.IDLE;
        }

        // If currently active, force rest once energy gets too low.
        if (stats.energy <= ENERGY_STOP_THRESHOLD) {
            return EntityBehaviour.IDLE;
        }

        return EntityBehaviour.EXPLORING;
    }

    private void executeBehaviour(Entity entity, EntityBehaviour behaviour, float deltaTime) {
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);
        if (behaviour == EntityBehaviour.FLEEING) {
            fleeFromPredator(entity, deltaTime);
            decreaseStats(entity, deltaTime);
            return;
        }

        if (behaviour == EntityBehaviour.SEEKING_FOOD) {
            seekFood(entity, deltaTime);
            decreaseStats(entity, deltaTime);
            return;
        }

        if (behaviour == EntityBehaviour.SEEKING_WATER) {
            // TODO: Implement water seeking logic (e.g., move towards nearest water source)
            vel.velocity.set(0f, 0f);
            return;
        }

        if (behaviour == EntityBehaviour.IDLE) {
            // Hard stop while resting so there is no residual drift/wobble.
            vel.velocity.set(0f, 0f);
            recoverEnergy(entity, deltaTime);
            return;
        }

        if (behaviour == EntityBehaviour.EXPLORING) {
            explore(entity, deltaTime);
            decreaseStats(entity, deltaTime);
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

        // If agent is in the "look around" pause, keep it stationary until timer expires.
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

        // If current target is reached, start pause/scan state.
        if (behaviour.hasTarget && isTargetReached(pos, behaviour)) {
            vel.velocity.set(0f, 0f);
            behaviour.waitingAtTarget = true;
            behaviour.waitAtTargetTimer = BehaviourComponent.WAIT_AT_TARGET_DURATION;
            return;
        }

        // Pick a new target point if one doesn't exist.
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
                behaviour.waitAtTargetTimer = BehaviourComponent.WAIT_AT_TARGET_DURATION;
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
        }

        if (!hitBoundary) {
            updateVisionDirection(entity);
        }
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

        if(behaviour.behaviour == EntityBehaviour.IDLE) {
            return;
        }
        if (vision != null && (vel.velocity.x != 0 || vel.velocity.y != 0)) {
            vision.directionAngle = (float) Math.toDegrees(Math.atan2(vel.velocity.y, vel.velocity.x));
        }
    }

    private void recoverEnergy(Entity entity, float deltaTime) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);
        if (stats != null && dna != null) {
            // Each agent recovers energy at a rate based on their RECOVERY_RATE gene
            // genes are typically 0-2, so recovery ranges from BASE to BASE * 2
            float recoveryRate = BASE_ENERGY_RECOVER_PER_SEC * (0.5f + dna.genes[GenomeComponent.RECOVERY_RATE]);
            stats.energy = Math.min(100f, stats.energy + recoveryRate * deltaTime);
        }
    }

    public static float getMapMaxX() {
        return MAP_MAX_X;
    }

    public static float getMapMaxY() {
        return MAP_MAX_Y;
    }

    private void decreaseStats(Entity entity, float deltaTime) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);
        if (stats != null && dna != null) {
            // Each agent's stat decrease rates are scaled by their genome genes
            // genes are typically 0-2, so rates range from BASE * 0.5 to BASE * 2
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
        if (stats != null && dna != null) {
            if (stats.hunger <= 0f || stats.thirst <= 0f) {
                // Health loss rate is also scaled by metabolism (more efficient agents lose health slower)
                float healthLossRate = BASE_HEALTH_DECREASE_PER_SEC / (0.5f + dna.genes[GenomeComponent.METABOLISM]);
                stats.health = Math.max(0f, stats.health - healthLossRate * deltaTime);
            }
        }
    }

    private void pickTargetPointInVisibleArea(Entity entity) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VisionComponent vision = entity.getComponent(VisionComponent.class);
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);

        if (vision == null || pos == null || behaviour == null) {
            return;
        }

        // Pick a random distance within vision range
        float distance = MathUtils.random(vision.visionRange * 0.5f, vision.visionRange);

        // Pick a random angle within the vision cone
        float halfVisionAngle = vision.visionAngle / 2f;
        float angleOffset = MathUtils.random(-halfVisionAngle, halfVisionAngle);
        float targetAngle = behaviour.directionAngle + angleOffset;

        // Calculate target point relative to agent's current position
        float targetX = pos.position.x + MathUtils.cosDeg(targetAngle) * distance;
        float targetY = pos.position.y + MathUtils.sinDeg(targetAngle) * distance;

        // Clamp target to map bounds
        targetX = MathUtils.clamp(targetX, MAP_MIN_X, MAP_MAX_X - ENTITY_SIZE);
        targetY = MathUtils.clamp(targetY, MAP_MIN_Y, MAP_MAX_Y - ENTITY_SIZE);

        behaviour.targetPoint.set(targetX, targetY);
        behaviour.hasTarget = true;
    }

    private boolean isTargetReached(PositionComponent pos, BehaviourComponent behaviour) {
        if (!behaviour.hasTarget) {
            return true;
        }
        float distance = pos.position.dst(behaviour.targetPoint);
        return distance < behaviour.targetReachedThreshold;
    }

    private void seekFood(Entity entity, float deltaTime) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);
        StatsComponent stats = entity.getComponent(StatsComponent.class);

        // Find nearest food source
        Entity nearestFood = null;
        float nearestDistance = Float.MAX_VALUE;

        if (foodEntities != null) {
            for (Entity food : foodEntities) {
                PositionComponent foodPos = food.getComponent(PositionComponent.class);
                float distance = pos.position.dst(foodPos.position);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestFood = food;
                }
            }
        }

        if (nearestFood != null) {
            PositionComponent foodPos = nearestFood.getComponent(PositionComponent.class);
            FoodSourceComponent foodComp = nearestFood.getComponent(FoodSourceComponent.class);

            // If close enough, consume the food
            if (nearestDistance < FOOD_CONSUMPTION_DISTANCE) {
                stats.hunger = Math.min(100f, stats.hunger + foodComp.nutritionValue);
                foodComp.consumed = true;
                vel.velocity.set(0f, 0f);
            } else {
                // Move toward the food
                com.badlogic.gdx.math.Vector2 direction = new com.badlogic.gdx.math.Vector2(foodPos.position).sub(pos.position).nor();
                float speedMultiplier = 50f + (dna.genes[GenomeComponent.SPEED] * 200f);
                vel.velocity.set(direction.x * speedMultiplier, direction.y * speedMultiplier);

                updatePosition(pos, vel, deltaTime);
                constrainPositionToBounds(pos);
                updateVisionDirection(entity);
            }
        } else {
            // No food found, just stop and wait
            vel.velocity.set(0f, 0f);
        }
    }

    private void fleeFromPredator(Entity prey, float deltaTime) {
        PositionComponent preyPos = prey.getComponent(PositionComponent.class);
        VelocityComponent vel = prey.getComponent(VelocityComponent.class);
        GenomeComponent dna = prey.getComponent(GenomeComponent.class);

        Entity threateningPredator = findNearestVisiblePredator(prey);
        if (threateningPredator == null) {
            vel.velocity.set(0f, 0f);
            return;
        }

        PositionComponent predatorPos = threateningPredator.getComponent(PositionComponent.class);
        com.badlogic.gdx.math.Vector2 direction = new com.badlogic.gdx.math.Vector2(preyPos.position).sub(predatorPos.position).nor();
        float speedMultiplier = (50f + (dna.genes[GenomeComponent.SPEED] * 200f)) * PREY_ESCAPE_SPEED_MULTIPLIER;
        vel.velocity.set(direction.x * speedMultiplier, direction.y * speedMultiplier);

        updatePosition(preyPos, vel, deltaTime);
        constrainPositionToBounds(preyPos);
        updateVisionDirection(prey);
    }


    private Entity findNearestVisiblePredator(Entity prey) {
        PositionComponent preyPos = prey.getComponent(PositionComponent.class);
        VisionComponent vision = prey.getComponent(VisionComponent.class);
        if (preyPos == null || vision == null) {
            return null;
        }

        Entity nearestPredator = null;
        float nearestDistance = Float.MAX_VALUE;

        for (Entity candidate : getEngine().getEntitiesFor(Family.all(PositionComponent.class, PredatorComponent.class).get())) {
            PositionComponent candidatePos = candidate.getComponent(PositionComponent.class);
            if (canSeeTarget(preyPos, vision, candidatePos)) {
                float distance = preyPos.position.dst(candidatePos.position);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPredator = candidate;
                }
            }
        }

        return nearestPredator;
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

    private void moveTowardTarget(Entity entity, com.badlogic.gdx.math.Vector2 targetPoint, GenomeComponent dna) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);

        // Calculate direction from current position to target
        com.badlogic.gdx.math.Vector2 direction = new com.badlogic.gdx.math.Vector2(targetPoint).sub(pos.position).nor();

        // Apply speed multiplier from genome
        float speedMultiplier = 50f + (dna.genes[GenomeComponent.SPEED] * 200f);
        vel.velocity.set(direction.x * speedMultiplier, direction.y * speedMultiplier);
    }
}
