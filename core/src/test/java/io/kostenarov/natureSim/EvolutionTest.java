package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Components.*;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Whole-ecosystem runs with every simulation system active. */
class EvolutionTest extends SimTestSupport {

    private static final Family CREATURES = Family.all(StatsComponent.class).get();

    private static void addSimulationSystems(Engine e, SimulationConfig cfg) {
        e.addSystem(new WaterSourceSystem(cfg));
        e.addSystem(new FoodSourceSystem(cfg));
        e.addSystem(new MatingSystem(cfg));
        e.addSystem(new PredatorSystem());
        e.addSystem(new PreySystem());
        e.addSystem(new CollisionSystem());
        e.addSystem(new MetabolismSystem());
        e.addSystem(new DeathSystem());
    }

    /** Runs 20 simulated seconds of the full system set and returns every creature's position. */
    private static float[] seededRun(long seed) {
        MathUtils.random.setSeed(seed);
        SimulationConfig cfg = new SimulationConfig();
        Engine e = new Engine();
        e.addSystem(new SpatialIndexSystem());
        e.addSystem(new WaterSourceSystem(cfg));
        e.addSystem(new FoodSourceSystem(cfg));
        e.addSystem(new MatingSystem(cfg));
        e.addSystem(new PredatorSystem());
        e.addSystem(new PreySystem());
        e.addSystem(new MetabolismSystem());
        e.addSystem(new CollisionSystem());
        e.addSystem(new DeathSystem());
        for (int i = 0; i < 200; i++) {
            Entity c = Creatures.create(e, MathUtils.random(200f, 4900f), MathUtils.random(200f, 4900f), new GenomeComponent());
            if (i < 4) Creatures.makePredator(c);
            e.addEntity(c);
        }
        for (int f = 0; f < 20 * 60; f++) e.update(1f / 60f);

        ImmutableArray<Entity> cs = e.getEntitiesFor(CREATURES);
        float[] positions = new float[cs.size() * 2];
        for (int i = 0; i < cs.size(); i++) {
            positions[2 * i] = cs.get(i).getComponent(PositionComponent.class).position.x;
            positions[2 * i + 1] = cs.get(i).getComponent(PositionComponent.class).position.y;
        }
        return positions;
    }

    @Test
    @DisplayName("same seed replays exactly the same run")
    void sameSeedIsDeterministic() {
        float[] first = seededRun(4242);
        float[] second = seededRun(4242);
        assertEquals(first.length / 2, second.length / 2, "creature count");
        assertArrayEquals(first, second, "creature positions after 20 sim-seconds");
    }

    @Test
    @DisplayName("ecosystem smoke run: 300 prey and 4 predators for 3 sim-minutes")
    void ecosystemSmokeRun() {
        Engine e = new Engine();
        addSimulationSystems(e, new SimulationConfig());
        Random r = new Random(2);
        for (int i = 0; i < 300; i++) {
            Entity a = creature(e, 200 + r.nextFloat() * 4500, 200 + r.nextFloat() * 4500, r.nextBoolean() ? Gender.MALE : Gender.FEMALE, false);
            stats(a).thirst = 20 + r.nextFloat() * 80;
        }
        for (int i = 0; i < 4; i++) creature(e, 2000 + i * 100, 2000, i % 2 == 0 ? Gender.MALE : Gender.FEMALE, true);

        ImmutableArray<Entity> creatures = e.getEntitiesFor(CREATURES);
        int maxSeeking = 0;
        for (int f = 0; f < 180 * 30; f++) {
            e.update(1f / 30f);
            int seeking = 0;
            for (Entity c : creatures) if (c.getComponent(BehaviourComponent.class).behaviour == EntityBehaviour.SEEKING_WATER) seeking++;
            maxSeeking = Math.max(maxSeeking, seeking);
        }
        float minThirst = 100;
        for (Entity c : creatures) minThirst = Math.min(minThirst, stats(c).thirst);
        String detail = String.format("creatures %d, peak seeking water %d, lowest thirst %.1f", creatures.size(), maxSeeking, minThirst);
        System.out.println(detail);

        assertTrue(creatures.size() > 0, detail);
        assertTrue(maxSeeking > 0, detail);
    }

    @Test
    @Tag("slow")
    @DisplayName("evolution run: 400 prey and 4 predators keep a living population for 15 sim-minutes")
    void evolutionRunKeepsPopulationAlive() {
        Engine e = new Engine();
        addSimulationSystems(e, new SimulationConfig());
        Random r = new Random(3);
        for (int i = 0; i < 400; i++) {
            Entity a = creature(e, 200 + r.nextFloat() * 4700, 200 + r.nextFloat() * 4700, r.nextBoolean() ? Gender.MALE : Gender.FEMALE, false);
            GenomeComponent dna = a.getComponent(GenomeComponent.class);
            for (int g = 0; g < dna.genes.length; g++) dna.genes[g] = r.nextFloat() * 2f;
            dna.genes[GenomeComponent.SPEED] = 0.35f + r.nextFloat() * 0.5f;
            dna.genes[GenomeComponent.VISION] = 0.1f + r.nextFloat() * 1.3f;
            VisionComponent v = a.getComponent(VisionComponent.class);
            v.visionRange = Traits.visionRange(dna);
            v.visionAngle = Traits.visionAngle(dna);
        }
        for (int i = 0; i < 4; i++) creature(e, 1000 + i * 900, 2500, i % 2 == 0 ? Gender.MALE : Gender.FEMALE, true);

        ImmutableArray<Entity> prey = e.getEntitiesFor(Family.all(StatsComponent.class).exclude(PredatorComponent.class).get());
        ImmutableArray<Entity> preds = e.getEntitiesFor(Family.all(PredatorComponent.class).get());
        ImmutableArray<Entity> food = e.getEntitiesFor(Family.all(FoodSourceComponent.class).get());
        String[] names = {"speed", "vision", "size", "metab", "recov", "hungerSens", "thirstSens"};
        for (int minute = 0; minute <= 15; minute++) {
            if (minute % 3 == 0) {
                // Log mean prey genes so drift is visible in the test report
                float[] mean = new float[names.length];
                for (Entity c : prey) {
                    float[] gs = c.getComponent(GenomeComponent.class).genes;
                    for (int g = 0; g < names.length; g++) mean[g] += gs[g] / prey.size();
                }
                StringBuilder sb = new StringBuilder(String.format("t=%2dmin prey %4d predators %2d food %3d |",
                    minute, prey.size(), preds.size(), food.size()));
                for (int g = 0; g < names.length; g++) sb.append(String.format(" %s %.2f", names[g], mean[g]));
                System.out.println(sb);
            }
            if (minute < 15) for (int f = 0; f < 60 * 30; f++) e.update(1f / 30f);
        }
        assertTrue(prey.size() > 0, prey.size() + " prey alive after 15 sim-minutes");
    }
}
