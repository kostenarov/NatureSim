package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import io.kostenarov.natureSim.Components.StatsComponent;

/**
 * Removes any creature whose health has reached zero (starvation, predation, ...).
 */
public class DeathSystem extends IteratingSystem {

    public DeathSystem() {
        super(Family.all(StatsComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        if (stats.health <= 0f) {
            getEngine().removeEntity(entity);
        }
    }
}
