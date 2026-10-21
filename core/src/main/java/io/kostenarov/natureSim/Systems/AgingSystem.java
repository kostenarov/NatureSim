package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Components.AgeComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.SimulationConfig;
import io.kostenarov.natureSim.Mappers;

/**
 * Ages creatures and kills them at the end of their lifespan, so old genomes make way for new ones.
 * Each creature's lifespan is the configured lifespan, give or take 20%.
 */
public class AgingSystem extends IteratingSystem {
    private static final float LIFESPAN_VARIATION = 0.2f;

    private final float baseLifespan; // Seconds

    public AgingSystem() {
        this(new SimulationConfig());
    }

    public AgingSystem(SimulationConfig config) {
        super(Family.all(AgeComponent.class, StatsComponent.class).get());
        this.baseLifespan = config.lifespanMinutes * 60f;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        AgeComponent age = Mappers.age.get(entity);
        if (age.lifespan <= 0f) {
            age.lifespan = baseLifespan * MathUtils.random(1f - LIFESPAN_VARIATION, 1f + LIFESPAN_VARIATION);
        }

        age.age += deltaTime;
        if (age.age >= age.lifespan) {
            Mappers.stats.get(entity).health = 0f; // DeathSystem removes it
        }
    }
}
