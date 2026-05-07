package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Components.FoodSourceComponent;
import io.kostenarov.natureSim.Components.PositionComponent;

public class FoodSourceSystem extends EntitySystem {
    private static final int MAX_FOOD_SOURCES = 150;
    private static final float SPAWN_INTERVAL = 3f; // Spawn food every 3 seconds
    private static final float MAP_MAX_X = 5120;
    private static final float MAP_MAX_Y = 5120;
    private static final float FOOD_SIZE = 16f;

    private float spawnTimer = 0f;
    private ImmutableArray<Entity> foodEntities;

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        foodEntities = engine.getEntitiesFor(Family.all(FoodSourceComponent.class, PositionComponent.class).get());

        // Spawn initial food sources
        for (int i = 0; i < 60; i++) {
            spawnFoodSource();
        }
    }

    @Override
    public void update(float deltaTime) {
        // Remove consumed food
        for (Entity food : foodEntities) {
            FoodSourceComponent foodComp = food.getComponent(FoodSourceComponent.class);
            if (foodComp.consumed) {
                getEngine().removeEntity(food);
            }
        }

        // Spawn new food periodically
        spawnTimer += deltaTime;
        if (spawnTimer >= SPAWN_INTERVAL) {
            spawnTimer = 0f;
            if (foodEntities.size() < MAX_FOOD_SOURCES) {
                spawnFoodSource();
            }
        }
    }

    private void spawnFoodSource() {
        Entity food = getEngine().createEntity();

        PositionComponent pos = new PositionComponent();
        pos.position.set(
            MathUtils.random(0, MAP_MAX_X - FOOD_SIZE),
            MathUtils.random(0, MAP_MAX_Y - FOOD_SIZE)
        );
        food.add(pos);

        FoodSourceComponent foodComp = new FoodSourceComponent();
        food.add(foodComp);

        getEngine().addEntity(food);
    }
}

