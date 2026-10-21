package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Mappers;

/**
 * Keeps every creature out of deep water, however it got there: pushed in by a crowd, born or spawned
 * there. Creatures walking around ponds already slide along the edge (see CreatureSystem); this is the
 * guarantee. Runs after collisions, so it works whether or not collision is enabled.
 */
public class WaterBarrierSystem extends IteratingSystem {
    private ImmutableArray<Entity> ponds;

    public WaterBarrierSystem() {
        super(Family.all(PositionComponent.class, StatsComponent.class).get());
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        ponds = engine.getEntitiesFor(WaterSourceSystem.WATER_FAMILY);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        WaterSourceSystem.pushOutOfDeepWater(ponds, Mappers.position.get(entity).position);
    }
}
