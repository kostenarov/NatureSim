package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Components.AgeComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Systems.AgingSystem;
import io.kostenarov.natureSim.Systems.CollisionSystem;
import io.kostenarov.natureSim.Systems.DeathSystem;
import io.kostenarov.natureSim.Systems.FoodSourceSystem;
import io.kostenarov.natureSim.Systems.MatingSystem;
import io.kostenarov.natureSim.Systems.MetabolismSystem;
import io.kostenarov.natureSim.Systems.PopulationHistory;
import io.kostenarov.natureSim.Systems.PredatorSystem;
import io.kostenarov.natureSim.Systems.PreySystem;
import io.kostenarov.natureSim.Systems.SpatialIndexSystem;
import io.kostenarov.natureSim.Systems.WaterBarrierSystem;
import io.kostenarov.natureSim.Systems.WaterSourceSystem;

/**
 * Builds a simulated world from a {@link SimulationConfig}. Used by the game and by the tests,
 * so both always run the same set of systems in the same order.
 */
public final class Simulation {
    private static final float SPAWN_MARGIN = 200f;

    private Simulation() {
    }

    /** An engine with every simulation system, in the order they must run each step. */
    public static Engine createEngine(SimulationConfig config) {
        Engine engine = new Engine();
        engine.addSystem(new SpatialIndexSystem());        // Always runs first each step
        engine.addSystem(new WaterSourceSystem(config));   // Ponds before food: food grows around them
        engine.addSystem(new FoodSourceSystem(config));
        engine.addSystem(new MatingSystem(config));
        engine.addSystem(new PredatorSystem());
        engine.addSystem(new PreySystem());
        engine.addSystem(new MetabolismSystem());          // After behaviour: upkeep depends on what creatures did
        engine.addSystem(new AgingSystem(config));
        if (config.collisionEnabled) {
            engine.addSystem(new CollisionSystem());
        }
        engine.addSystem(new WaterBarrierSystem());        // After collisions: nobody ends up in deep water
        engine.addSystem(new DeathSystem());
        engine.addSystem(new PopulationHistory());
        return engine;
    }

    /**
     * Adds the starting creatures. Their speed and vision start close to the original hand-tuned creatures,
     * all other genes are random (0 to 2), and their ages are spread out so they don't all die of old age at once.
     */
    public static void spawnStartingCreatures(Engine engine, SimulationConfig config) {
        float lifespan = config.lifespanMinutes * 60f;

        for (int i = 0; i < config.preyCount; i++) {
            GenomeComponent dna = new GenomeComponent();
            dna.genes[GenomeComponent.SPEED] = MathUtils.random(0.35f, 0.85f);   // ~120-220 px/s
            dna.genes[GenomeComponent.VISION] = MathUtils.random(0.1f, 1.4f);    // ~90-235 px
            Entity prey = Creatures.create(engine, randomSpawnX(), randomSpawnY(), dna);
            prey.getComponent(AgeComponent.class).age = MathUtils.random(0f, lifespan * 0.5f);
            engine.addEntity(prey);
        }

        for (int i = 0; i < config.predatorCount; i++) {
            GenomeComponent dna = new GenomeComponent();
            dna.genes[GenomeComponent.SPEED] = MathUtils.random(0.7f, 0.85f);    // ~190-220 px/s
            dna.genes[GenomeComponent.VISION] = MathUtils.random(1.3f, 2f);      // ~225-300 px
            Entity predator = Creatures.create(engine, randomSpawnX(), randomSpawnY(), dna);
            predator.getComponent(AgeComponent.class).age = MathUtils.random(0f, lifespan * 0.5f);
            Creatures.makePredator(predator);
            engine.addEntity(predator);
        }
    }

    private static float randomSpawnX() {
        return MathUtils.random(SPAWN_MARGIN, World.WIDTH - SPAWN_MARGIN);
    }

    private static float randomSpawnY() {
        return MathUtils.random(SPAWN_MARGIN, World.HEIGHT - SPAWN_MARGIN);
    }
}
