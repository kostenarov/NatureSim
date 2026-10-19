package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.kostenarov.natureSim.Components.*;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Traits;
import io.kostenarov.natureSim.World;

/**
 * Prey behaviour: flee from visible predators, otherwise drink, eat, rest or wander.
 */
public class PreySystem extends CreatureSystem {
    private static final float FOOD_CONSUMPTION_DISTANCE = 32f; // How close to be to eat food
    private static final float ESCAPE_SPEED_MULTIPLIER = 1.25f;
    private static final int GRID_FOOD_SEARCH_MIN = 100; // Below this much food, scan the list instead

    private ImmutableArray<Entity> foodEntities;
    private ImmutableArray<Entity> predatorEntities;
    private final SpatialGrid.Filter uneatenFood = food -> !food.getComponent(FoodSourceComponent.class).consumed;

    public PreySystem() {
        super(Family.all(PositionComponent.class, VelocityComponent.class, GenomeComponent.class, VisionComponent.class,
            GenderComponent.class, StatsComponent.class, BehaviourComponent.class).exclude(PredatorComponent.class).get());
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        foodEntities = engine.getEntitiesFor(Family.all(FoodSourceComponent.class, PositionComponent.class).get());
        predatorEntities = engine.getEntitiesFor(Family.all(PositionComponent.class, PredatorComponent.class).get());
    }

    @Override
    protected EntityBehaviour decideBehaviour(Entity entity) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);

        if (findNearestVisiblePredator(entity) != null) {
            return EntityBehaviour.FLEEING;
        }

        // How early a creature reacts to hunger and thirst is genetic (see Traits).
        boolean hungry = stats.hunger < Traits.foodSeekThreshold(dna);
        if (wantsWater(stats, behaviour, dna, hungry)) {
            return EntityBehaviour.SEEKING_WATER;
        }
        if (hungry) {
            return EntityBehaviour.SEEKING_FOOD;
        }

        // Once resting, keep resting until energy has recovered; once active, rest when nearly exhausted.
        if (behaviour.behaviour == EntityBehaviour.IDLE) {
            return stats.energy >= ENERGY_RESUME_THRESHOLD ? EntityBehaviour.EXPLORING : EntityBehaviour.IDLE;
        }
        if (stats.energy <= ENERGY_STOP_THRESHOLD) {
            return EntityBehaviour.IDLE;
        }
        return EntityBehaviour.EXPLORING;
    }

    @Override
    protected void executeBehaviour(Entity entity, EntityBehaviour behaviour, float deltaTime) {
        switch (behaviour) {
            case FLEEING:
                fleeFromPredator(entity, deltaTime);
                break;
            case SEEKING_FOOD:
                seekFood(entity, deltaTime);
                break;
            case SEEKING_WATER:
                seekWater(entity, deltaTime);
                break;
            case EXPLORING:
                explore(entity, deltaTime);
                break;
            default:
                // Resting: hard stop so there is no residual drift.
                stop(entity);
                break;
        }
    }

    private void seekFood(Entity entity, float deltaTime) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);

        Entity nearestFood = findNearestFood(pos);
        if (nearestFood == null) {
            stop(entity); // No food anywhere: wait for some to grow
            return;
        }

        PositionComponent foodPos = nearestFood.getComponent(PositionComponent.class);
        if (pos.position.dst(foodPos.position) < FOOD_CONSUMPTION_DISTANCE) {
            StatsComponent stats = entity.getComponent(StatsComponent.class);
            FoodSourceComponent food = nearestFood.getComponent(FoodSourceComponent.class);
            stats.hunger = Math.min(100f, stats.hunger + food.nutritionValue);
            food.consumed = true;
            stop(entity);
        } else {
            moveToward(entity, foodPos.position, Traits.speed(entity.getComponent(GenomeComponent.class)), deltaTime);
        }
    }

    /** Nearest food that hasn't been eaten yet, anywhere on the map. */
    private Entity findNearestFood(PositionComponent pos) {
        // With only a little food on the map, a plain scan beats walking many empty grid cells.
        SpatialIndexSystem index = getEngine().getSystem(SpatialIndexSystem.class);
        if (index != null && foodEntities.size() >= GRID_FOOD_SEARCH_MIN) {
            return index.food().findNearest(pos.position.x, pos.position.y, World.DIAGONAL, uneatenFood);
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

    private void fleeFromPredator(Entity prey, float deltaTime) {
        Entity predator = findNearestVisiblePredator(prey);
        if (predator == null) {
            stop(prey);
            return;
        }

        PositionComponent preyPos = prey.getComponent(PositionComponent.class);
        PositionComponent predatorPos = predator.getComponent(PositionComponent.class);
        tmp.set(preyPos.position).sub(predatorPos.position).nor(); // Directly away from the predator
        float speed = Traits.speed(prey.getComponent(GenomeComponent.class)) * ESCAPE_SPEED_MULTIPLIER;
        moveInDirection(prey, tmp.x, tmp.y, speed, deltaTime);
    }

    private Entity findNearestVisiblePredator(Entity prey) {
        PositionComponent preyPos = prey.getComponent(PositionComponent.class);
        VisionComponent vision = prey.getComponent(VisionComponent.class);

        Entity nearestPredator = null;
        float nearestDistance = Float.MAX_VALUE;
        for (Entity candidate : predatorEntities) {
            PositionComponent candidatePos = candidate.getComponent(PositionComponent.class);
            if (canSee(preyPos, vision, candidatePos)) {
                float distance = preyPos.position.dst(candidatePos.position);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPredator = candidate;
                }
            }
        }
        return nearestPredator;
    }
}
