package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.World;
import io.kostenarov.natureSim.Components.FoodSourceComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.WaterSourceComponent;
import io.kostenarov.natureSim.SimulationConfig;
import io.kostenarov.natureSim.Mappers;
import io.kostenarov.natureSim.Balance;

public class FoodSourceSystem extends EntitySystem {
    private static final float FOOD_SIZE = 16f;
    private static final int MAX_PLACEMENT_ATTEMPTS = 20;

    private final SimulationConfig config; // Food settings are read live, so they can change mid-run

    private float spawnTimer = 0f;
    private ImmutableArray<Entity> foodEntities;

    public FoodSourceSystem() {
        this(new SimulationConfig());
    }

    public FoodSourceSystem(SimulationConfig config) {
        this.config = config;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        foodEntities = engine.getEntitiesFor(Family.all(FoodSourceComponent.class, PositionComponent.class).get());

        // Spawn initial food sources
        for (int i = 0; i < config.initialFood; i++) {
            spawnFoodSource();
        }
    }

    @Override
    public void update(float deltaTime) {
        // Remove consumed food
        for (Entity food : foodEntities) {
            FoodSourceComponent foodComp = Mappers.foodSource.get(food);
            if (foodComp.consumed) {
                getEngine().removeEntity(food);
            }
        }

        // Spawn new food periodically (several per frame if the interval is shorter than a frame)
        // Seasons: food grows faster in summer and barely at all in winter
        ClockSystem clock = getEngine().getSystem(ClockSystem.class);
        spawnTimer += deltaTime * (clock != null ? clock.growthFactor() : 1f);
        float spawnInterval = Math.max(0.01f, config.foodSpawnInterval);
        while (spawnTimer >= spawnInterval) {
            spawnTimer -= spawnInterval;
            if (foodEntities.size() < config.maxFood) {
                spawnFoodSource();
            }
        }
    }

    /** Progress toward the next food item, for saving a world. */
    public float getSpawnTimer() {
        return spawnTimer;
    }

    public void setSpawnTimer(float spawnTimer) {
        this.spawnTimer = spawnTimer;
    }

    private void spawnFoodSource() {
        ImmutableArray<Entity> ponds = getEngine().getEntitiesFor(WaterSourceSystem.WATER_FAMILY);
        PositionComponent pos = new PositionComponent();

        // Food never grows in deep water, where nobody could reach it
        int attempts = 0;
        do {
            pickFoodPosition(ponds, pos);
        } while (WaterSourceSystem.isInDeepWater(ponds, pos.position.x + FOOD_SIZE / 2f, pos.position.y + FOOD_SIZE / 2f, FOOD_SIZE)
            && ++attempts < MAX_PLACEMENT_ATTEMPTS);
        if (attempts == MAX_PLACEMENT_ATTEMPTS) {
            return;
        }

        Entity food = getEngine().createEntity();
        food.add(pos);
        FoodSourceComponent item = new FoodSourceComponent();
        item.nutritionValue = config.foodNutrition;
        food.add(item);
        getEngine().addEntity(food);
    }

    private void pickFoodPosition(ImmutableArray<Entity> ponds, PositionComponent pos) {
        if (ponds.size() > 0 && MathUtils.random() < Balance.NEAR_WATER_CHANCE) {
            // Fertile ground: a band around a random pond's shore
            Entity pond = ponds.random();
            PositionComponent pondPos = Mappers.position.get(pond);
            float distance = Mappers.waterSource.get(pond).radius + MathUtils.random(0f, Balance.FERTILE_BAND_WIDTH);
            float angle = MathUtils.random(MathUtils.PI2);
            pos.position.set(
                MathUtils.clamp(pondPos.position.x + MathUtils.cos(angle) * distance, 0, World.WIDTH - FOOD_SIZE),
                MathUtils.clamp(pondPos.position.y + MathUtils.sin(angle) * distance, 0, World.HEIGHT - FOOD_SIZE)
            );
        } else {
            pos.position.set(
                MathUtils.random(0, World.WIDTH - FOOD_SIZE),
                MathUtils.random(0, World.HEIGHT - FOOD_SIZE)
            );
        }
    }
}

