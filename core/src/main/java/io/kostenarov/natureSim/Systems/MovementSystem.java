package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.kostenarov.natureSim.Components.*;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Traits;

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

    private static final float FOOD_CONSUMPTION_DISTANCE = 32f; // How close to be to eat food
    private static final float PREY_ESCAPE_SPEED_MULTIPLIER = 1.25f;

    private static final float MAP_DIAGONAL = (float) Math.hypot(MAP_MAX_X, MAP_MAX_Y);
    private static final int GRID_FOOD_SEARCH_MIN = 100; // Below this much food, scan the list instead

    private ImmutableArray<Entity> foodEntities;
    private ImmutableArray<Entity> waterEntities;
    private ImmutableArray<Entity> predatorEntities;
    private final Vector2 waterTarget = new Vector2();
    private final Vector2 tmp = new Vector2(); // Scratch vector, avoids allocating every frame
    private final SpatialGrid.Filter uneatenFood = food -> !food.getComponent(FoodSourceComponent.class).consumed;

    public MovementSystem() {
        super(Family.all(PositionComponent.class, VelocityComponent.class, GenomeComponent.class, VisionComponent.class, GenderComponent.class, StatsComponent.class, BehaviourComponent.class).exclude(PredatorComponent.class).get());
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        foodEntities = engine.getEntitiesFor(Family.all(FoodSourceComponent.class, PositionComponent.class).get());
        waterEntities = engine.getEntitiesFor(WaterSourceSystem.WATER_FAMILY);
        predatorEntities = engine.getEntitiesFor(Family.all(PositionComponent.class, PredatorComponent.class).get());
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

        // Check critical needs first. Once drinking, keep drinking until fully quenched.
        // How early a creature reacts to hunger and thirst is genetic (see Traits).
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);
        boolean keepDrinking = behaviour.behaviour == EntityBehaviour.SEEKING_WATER && stats.thirst < WaterSourceSystem.WATER_FULL_THRESHOLD;
        boolean thirsty = stats.thirst < Traits.waterSeekThreshold(dna);
        boolean hungry = stats.hunger < Traits.foodSeekThreshold(dna);
        if (keepDrinking || (thirsty && (!hungry || stats.thirst < stats.hunger))) {
            return EntityBehaviour.SEEKING_WATER;
        }
        if (hungry) {
            return EntityBehaviour.SEEKING_FOOD;
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
            seekWater(entity, deltaTime);
            decreaseStats(entity, deltaTime);
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
            float speedMultiplier = Traits.speed(dna);
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
        Traits.rest(entity.getComponent(StatsComponent.class), entity.getComponent(GenomeComponent.class), false, deltaTime);
    }

    public static float getMapMaxX() {
        return MAP_MAX_X;
    }

    public static float getMapMaxY() {
        return MAP_MAX_Y;
    }

    private void decreaseStats(Entity entity, float deltaTime) {
        Traits.burnActive(entity.getComponent(StatsComponent.class), entity.getComponent(GenomeComponent.class), false, deltaTime);
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

        Entity nearestFood = findNearestFood(pos);

        if (nearestFood != null) {
            PositionComponent foodPos = nearestFood.getComponent(PositionComponent.class);
            FoodSourceComponent foodComp = nearestFood.getComponent(FoodSourceComponent.class);

            // If close enough, consume the food
            if (pos.position.dst(foodPos.position) < FOOD_CONSUMPTION_DISTANCE) {
                stats.hunger = Math.min(100f, stats.hunger + foodComp.nutritionValue);
                foodComp.consumed = true;
                vel.velocity.set(0f, 0f);
            } else {
                // Move toward the food
                Vector2 direction = tmp.set(foodPos.position).sub(pos.position).nor();
                float speedMultiplier = Traits.speed(dna);
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

    /** Nearest food that hasn't been eaten yet, anywhere on the map. */
    private Entity findNearestFood(PositionComponent pos) {
        // With only a little food on the map, a plain scan beats walking many empty grid cells.
        SpatialIndexSystem index = getEngine().getSystem(SpatialIndexSystem.class);
        if (index != null && foodEntities.size() >= GRID_FOOD_SEARCH_MIN) {
            return index.food().findNearest(pos.position.x, pos.position.y, MAP_DIAGONAL, uneatenFood);
        }

        Entity nearestFood = null;
        float nearestDistance = Float.MAX_VALUE;
        for (Entity food : foodEntities) {
            if (!uneatenFood.accept(food)) {
                continue;
            }
            float distance = pos.position.dst(food.getComponent(PositionComponent.class).position);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestFood = food;
            }
        }
        return nearestFood;
    }

    private void seekWater(Entity entity, float deltaTime) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);
        StatsComponent stats = entity.getComponent(StatsComponent.class);

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

        moveTowardTarget(entity, WaterSourceSystem.approachPoint(pond, waterTarget), dna);
        updatePosition(pos, vel, deltaTime);
        constrainPositionToBounds(pos);
        updateVisionDirection(entity);
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
        Vector2 direction = tmp.set(preyPos.position).sub(predatorPos.position).nor();
        float speedMultiplier = Traits.speed(dna) * PREY_ESCAPE_SPEED_MULTIPLIER;
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

        for (Entity candidate : predatorEntities) {
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

    private void moveTowardTarget(Entity entity, Vector2 targetPoint, GenomeComponent dna) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);

        // Calculate direction from current position to target
        Vector2 direction = tmp.set(targetPoint).sub(pos.position).nor();

        // Apply speed multiplier from genome
        float speedMultiplier = Traits.speed(dna);
        vel.velocity.set(direction.x * speedMultiplier, direction.y * speedMultiplier);
    }
}
