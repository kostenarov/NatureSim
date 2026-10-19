package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.PredatorComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Traits;

/**
 * Applies hunger, thirst and energy upkeep to every creature, based on what it did this step:
 * resting recovers energy cheaply, anything else burns energy and food (rates come from Traits).
 * Runs after the behaviour systems have decided what each creature is doing.
 */
public class MetabolismSystem extends IteratingSystem {

    public MetabolismSystem() {
        super(Family.all(StatsComponent.class, GenomeComponent.class, BehaviourComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);
        boolean predator = entity.getComponent(PredatorComponent.class) != null;

        if (entity.getComponent(BehaviourComponent.class).behaviour == EntityBehaviour.IDLE) {
            Traits.rest(stats, dna, predator, deltaTime);
        } else {
            Traits.burnActive(stats, dna, predator, deltaTime);
        }
    }
}
