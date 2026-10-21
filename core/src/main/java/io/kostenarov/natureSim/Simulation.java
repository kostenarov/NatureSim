package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Enums.Role;
import io.kostenarov.natureSim.Systems.AgingSystem;
import io.kostenarov.natureSim.Systems.CarcassSystem;
import io.kostenarov.natureSim.Systems.ClockSystem;
import io.kostenarov.natureSim.Systems.CollisionSystem;
import io.kostenarov.natureSim.Systems.DeathSystem;
import io.kostenarov.natureSim.Systems.FoodSourceSystem;
import io.kostenarov.natureSim.Systems.LineageSystem;
import io.kostenarov.natureSim.Systems.MatingSystem;
import io.kostenarov.natureSim.Systems.MetabolismSystem;
import io.kostenarov.natureSim.Systems.MigrationSystem;
import io.kostenarov.natureSim.Systems.PopulationHistory;
import io.kostenarov.natureSim.Systems.PredatorSystem;
import io.kostenarov.natureSim.Systems.PreySystem;
import io.kostenarov.natureSim.Systems.ScavengerSystem;
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
        engine.addSystem(new ClockSystem(config));         // Then the time of day and year
        engine.addSystem(new LineageSystem());
        engine.addSystem(new WaterSourceSystem(config));   // Ponds before food: food grows around them
        engine.addSystem(new FoodSourceSystem(config));
        engine.addSystem(new MatingSystem(config));
        engine.addSystem(new PredatorSystem(config));
        engine.addSystem(new PreySystem(config));
        engine.addSystem(new ScavengerSystem());
        engine.addSystem(new MetabolismSystem());          // After behaviour: upkeep depends on what creatures did
        engine.addSystem(new AgingSystem(config));
        if (config.collisionEnabled) {
            engine.addSystem(new CollisionSystem());
        }
        engine.addSystem(new WaterBarrierSystem());        // After collisions: nobody ends up in deep water
        engine.addSystem(new DeathSystem());               // Leaves carcasses...
        engine.addSystem(new CarcassSystem());             // ...which rot
        if (config.migrationEnabled) {
            engine.addSystem(new MigrationSystem(config));
        }
        engine.addSystem(new PopulationHistory());
        return engine;
    }

    /**
     * Adds the starting creatures, with ages spread out so they don't all die of old age at once.
     */
    public static void spawnStartingCreatures(Engine engine, SimulationConfig config) {
        float lifespan = config.lifespanMinutes * 60f;
        for (int i = 0; i < config.preyCount; i++) {
            spawn(engine, Role.PREY, randomSpawnX(), randomSpawnY(), MathUtils.random(0f, lifespan * 0.5f));
        }
        for (int i = 0; i < config.predatorCount; i++) {
            spawn(engine, Role.PREDATOR, randomSpawnX(), randomSpawnY(), MathUtils.random(0f, lifespan * 0.5f));
        }
        for (int i = 0; i < config.scavengerCount; i++) {
            spawn(engine, Role.SCAVENGER, randomSpawnX(), randomSpawnY(), MathUtils.random(0f, lifespan * 0.5f));
        }
    }

    /**
     * Adds a first-generation creature of the given role. Speed and vision start close to the original
     * hand-tuned creatures for that role; every other gene is random (0 to 2) and evolution takes it from there.
     */
    public static Entity spawn(Engine engine, Role role, float x, float y, float age) {
        GenomeComponent dna = new GenomeComponent();
        switch (role) {
            case PREDATOR:
                dna.genes[GenomeComponent.SPEED] = MathUtils.random(0.7f, 0.85f);  // ~190-220 px/s
                dna.genes[GenomeComponent.VISION] = MathUtils.random(1.3f, 2f);    // ~225-300 px
                break;
            case SCAVENGER:
                dna.genes[GenomeComponent.SPEED] = MathUtils.random(0.5f, 0.8f);   // ~150-210 px/s
                dna.genes[GenomeComponent.VISION] = MathUtils.random(0.5f, 1.2f);  // ~135-210 px
                break;
            default:
                dna.genes[GenomeComponent.SPEED] = MathUtils.random(0.35f, 0.85f); // ~120-220 px/s
                dna.genes[GenomeComponent.VISION] = MathUtils.random(0.1f, 1.4f);  // ~90-235 px
                break;
        }
        Entity creature = Creatures.create(engine, x, y, dna);
        Mappers.age.get(creature).age = age;
        if (role == Role.PREDATOR) {
            Creatures.makePredator(creature);
        } else if (role == Role.SCAVENGER) {
            Creatures.makeScavenger(creature);
        }
        engine.addEntity(creature);
        return creature;
    }

    private static float randomSpawnX() {
        return MathUtils.random(SPAWN_MARGIN, World.WIDTH - SPAWN_MARGIN);
    }

    private static float randomSpawnY() {
        return MathUtils.random(SPAWN_MARGIN, World.HEIGHT - SPAWN_MARGIN);
    }
}
