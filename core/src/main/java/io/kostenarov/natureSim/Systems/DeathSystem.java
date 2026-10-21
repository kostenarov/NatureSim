package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import io.kostenarov.natureSim.Balance;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Mappers;

/**
 * Removes any creature whose health has reached zero (starvation, thirst, old age), leaving a carcass behind.
 * Predator kills are handled by PredatorSystem, which leaves only the leftovers.
 */
public class DeathSystem extends IteratingSystem {

    public DeathSystem() {
        super(Family.all(StatsComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        StatsComponent stats = Mappers.stats.get(entity);
        if (stats.health <= 0f) {
            CarcassSystem.leaveCarcass(getEngine(), entity, Balance.CARCASS_FOOD);
            getEngine().removeEntity(entity);
        }
    }
}
