package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.StatsComponent;

/**
 * Keeps every creature out of deep water, however it got there: pushed in by a crowd, born or spawned
 * there. Creatures walking around ponds already slide along the edge (see CreatureSystem); this is the
 * guarantee. Runs after collisions, so it works whether or not collision is enabled.
 */
public class WaterBarrierSystem extends IteratingSystem {
    private final ComponentMapper<PositionComponent> positionMapper = ComponentMapper.getFor(PositionComponent.class);
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
        WaterSourceSystem.pushOutOfDeepWater(ponds, positionMapper.get(entity).position);
    }
}
