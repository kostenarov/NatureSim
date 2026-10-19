package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.kostenarov.natureSim.World;
import io.kostenarov.natureSim.Components.FoodSourceComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.StatsComponent;

/**
 * Rebuilds the spatial grids at the start of every step so other systems can find nearby
 * creatures and food without scanning the whole world.
 */
public class SpatialIndexSystem extends EntitySystem {
    private static final int RUN_FIRST = Integer.MIN_VALUE;
    private static final float CREATURE_CELL_SIZE = 64f;
    private static final float FOOD_CELL_SIZE = 128f;

    private final SpatialGrid creatures = new SpatialGrid(CREATURE_CELL_SIZE, World.WIDTH, World.HEIGHT);
    private final SpatialGrid food = new SpatialGrid(FOOD_CELL_SIZE, World.WIDTH, World.HEIGHT);
    private ImmutableArray<Entity> creatureEntities;
    private ImmutableArray<Entity> foodEntities;

    public SpatialIndexSystem() {
        super(RUN_FIRST);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        creatureEntities = engine.getEntitiesFor(Family.all(PositionComponent.class, StatsComponent.class).get());
        foodEntities = engine.getEntitiesFor(Family.all(FoodSourceComponent.class, PositionComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        creatures.rebuild(creatureEntities);
        food.rebuild(foodEntities);
    }

    /** All creatures (prey and predators). */
    public SpatialGrid creatures() {
        return creatures;
    }

    public SpatialGrid food() {
        return food;
    }
}
