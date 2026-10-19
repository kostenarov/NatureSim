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

public class FoodSourceSystem extends EntitySystem {
    private static final float FOOD_SIZE = 16f;
    private static final float NEAR_WATER_CHANCE = 0.7f;      // Most food grows in the fertile band around ponds
    private static final float FERTILE_BAND_WIDTH = 350f;     // How far from the shore that band reaches

    private final int initialFood;
    private final int maxFood;
    private final float spawnInterval; // Seconds between food spawns

    private float spawnTimer = 0f;
    private ImmutableArray<Entity> foodEntities;

    public FoodSourceSystem() {
        this(new SimulationConfig());
    }

    public FoodSourceSystem(SimulationConfig config) {
        this.initialFood = config.initialFood;
        this.maxFood = config.maxFood;
        this.spawnInterval = config.foodSpawnInterval;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        foodEntities = engine.getEntitiesFor(Family.all(FoodSourceComponent.class, PositionComponent.class).get());

        // Spawn initial food sources
        for (int i = 0; i < initialFood; i++) {
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

        // Spawn new food periodically (several per frame if the interval is shorter than a frame)
        spawnTimer += deltaTime;
        while (spawnTimer >= spawnInterval) {
            spawnTimer -= spawnInterval;
            if (foodEntities.size() < maxFood) {
                spawnFoodSource();
            }
        }
    }

    private void spawnFoodSource() {
        Entity food = getEngine().createEntity();

        PositionComponent pos = new PositionComponent();
        ImmutableArray<Entity> ponds = getEngine().getEntitiesFor(WaterSourceSystem.WATER_FAMILY);
        if (ponds.size() > 0 && MathUtils.random() < NEAR_WATER_CHANCE) {
            // Fertile ground: a band around a random pond's shore
            Entity pond = ponds.random();
            PositionComponent pondPos = pond.getComponent(PositionComponent.class);
            float distance = pond.getComponent(WaterSourceComponent.class).radius + MathUtils.random(0f, FERTILE_BAND_WIDTH);
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
        food.add(pos);

        FoodSourceComponent foodComp = new FoodSourceComponent();
        food.add(foodComp);

        getEngine().addEntity(food);
    }
}

