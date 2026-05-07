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
    private static final float ENERGY_RESUME_THRESHOLD = 10f;

    private static final float FOOD_SEEK_THRESHOLD = 60f; // Seek food when hunger drops below this
    private static final float FOOD_CONSUMPTION_DISTANCE = 32f; // How close to be to eat food

    private ImmutableArray<Entity> foodEntities;

    public MovementSystem() {
        super(Family.all(PositionComponent.class, VelocityComponent.class, GenomeComponent.class, VisionComponent.class, GenderComponent.class, BehaviourComponent.class).get());
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
        behaviourComponent.update(behaviour, deltaTime);

        executeBehaviour(entity, behaviour, deltaTime);
    }

    private EntityBehaviour decideBehaviour(Entity entity) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);

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
        switch (behaviour) {
            case SEEKING_FOOD:
                seekFood(entity, deltaTime);
                decreaseStats(entity);
                break;
            case SEEKING_WATER:
                //TODO: Implement water seeking logic (e.g., move towards nearest water source)
                break;
            case IDLE:
                // Hard stop while resting so there is no residual drift/wobble.
                vel.velocity.set(0f, 0f);
                recoverEnergy(entity);
                break;
            case EXPLORING:
                explore(entity, deltaTime);
                decreaseStats(entity);
                break;
        }
    }

    private void explore(Entity entity, float deltaTime) {
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VisionComponent vision = entity.getComponent(VisionComponent.class);

        if (behaviour.behaviour != EntityBehaviour.IDLE) {
            // If agent is in the "look around" pause, keep it stationary until timer expires.
            if (behaviour.waitingAtTarget) {
                vel.velocity.set(0f, 0f);
                behaviour.waitAtTargetTimer -= deltaTime;
                if (behaviour.waitAtTargetTimer <= 0f) {
                    behaviour.waitingAtTarget = false;
                    // Pick a new random heading, then the next target is chosen from it.
                    behaviour.directionAngle = MathUtils.random(0f, 360f);
                    // Update vision direction immediately so the "look around" is visible
                    vision.directionAngle = behaviour.directionAngle;
                    behaviour.targetPoint.setZero();
                }
                return;
            }

            // If current target is reached, start pause/scan state.
            if (behaviour.targetPoint.len() != 0 && isTargetReached(pos, behaviour)) {
                vel.velocity.set(0f, 0f);
                behaviour.waitingAtTarget = true;
                behaviour.waitAtTargetTimer = BehaviourComponent.WAIT_AT_TARGET_DURATION;
                return;
            }

            // Pick a new target point if one doesn't exist.
            if (behaviour.targetPoint.len() == 0) {
                pickTargetPointInVisibleArea(entity);
            }

            // Move toward the target point
            moveTowardTarget(entity, behaviour.targetPoint, deltaTime, dna);

            updatePosition(pos, vel, deltaTime);

            boolean hitBoundary = constrainPositionToBounds(pos);
            if (hitBoundary) {
                // Stop, re-orient randomly, then re-target in that direction.
                vel.velocity.set(0f, 0f);
                behaviour.directionAngle = MathUtils.random(0f, 360f);
                vision.directionAngle = behaviour.directionAngle;
                behaviour.targetPoint.setZero();
                pickTargetPointInVisibleArea(entity);
            }

            if (!hitBoundary) {
                updateVisionDirection(entity);
            }
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
            float angle = (float) Math.toDegrees(Math.atan2(vel.velocity.y, vel.velocity.x));
            vision.directionAngle = angle;
        }
    }

    private void recoverEnergy(Entity entity) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        stats.energy = Math.min(100f, stats.energy + 0.5f);
    }

    public static float getMapMaxX() {
        return MAP_MAX_X;
    }

    public static float getMapMaxY() {
        return MAP_MAX_Y;
    }

    private void decreaseStats(Entity entity) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        if (stats != null) {
            stats.hunger = Math.max(0, stats.hunger - 0.002f);
            stats.thirst = Math.max(0, stats.thirst - 0.001f);
            stats.energy = Math.max(0, stats.energy - 0.1f);
        }
        decreaseHealth(entity);
    }

    private void decreaseHealth(Entity entity) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        if (stats != null) {
            if (stats.hunger <= 0 || stats.thirst <= 0) {
                stats.health = Math.max(0, stats.health - 0.2f);
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
    }

    private boolean isTargetReached(PositionComponent pos, BehaviourComponent behaviour) {
        if (behaviour.targetPoint.len() == 0) {
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
        VisionComponent vision = entity.getComponent(VisionComponent.class);

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

    private void moveTowardTarget(Entity entity, com.badlogic.gdx.math.Vector2 targetPoint, float deltaTime, GenomeComponent dna) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);

        // Calculate direction from current position to target
        com.badlogic.gdx.math.Vector2 direction = new com.badlogic.gdx.math.Vector2(targetPoint).sub(pos.position).nor();

        // Apply speed multiplier from genome
        float speedMultiplier = 50f + (dna.genes[GenomeComponent.SPEED] * 200f);
        vel.velocity.set(direction.x * speedMultiplier, direction.y * speedMultiplier);
    }
}
