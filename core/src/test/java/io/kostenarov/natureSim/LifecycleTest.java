package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.kostenarov.natureSim.Components.AgeComponent;
import io.kostenarov.natureSim.Components.GenderComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.ReproductionComponent;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.AgingSystem;
import io.kostenarov.natureSim.Systems.DeathSystem;
import io.kostenarov.natureSim.Systems.MatingSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleTest extends SimTestSupport {

    /** A creature built the way the game builds them (with an age), all genes at 1. */
    private static Entity grownCreature(Engine e, float x, float y, Gender gender, float age, int generation) {
        GenomeComponent dna = new GenomeComponent();
        Arrays.fill(dna.genes, 1f);
        Entity creature = Creatures.create(e, x, y, dna);
        creature.getComponent(GenderComponent.class).gender = gender;
        AgeComponent ageComponent = creature.getComponent(AgeComponent.class);
        ageComponent.age = age;
        ageComponent.generation = generation;
        e.addEntity(creature);
        return creature;
    }

    @Test
    @DisplayName("creatures die of old age at the end of their lifespan")
    void oldAgeKills() {
        Engine e = new Engine();
        e.addSystem(new AgingSystem());
        e.addSystem(new DeathSystem());
        Entity old = grownCreature(e, 500, 500, Gender.MALE, 0f, 0);
        old.getComponent(AgeComponent.class).lifespan = 1f;

        run(e, 0.5f);
        assertEquals(1, creatureCount(e), "still alive halfway through its lifespan");
        run(e, 1f);
        assertEquals(0, creatureCount(e), "removed after its lifespan");
    }

    @Test
    @DisplayName("each lifespan is the configured lifespan, give or take 20%")
    void lifespanFollowsConfig() {
        SimulationConfig config = new SimulationConfig();
        config.lifespanMinutes = 5f;
        Engine e = new Engine();
        e.addSystem(new AgingSystem(config));
        Entity creature = grownCreature(e, 500, 500, Gender.MALE, 0f, 0);
        e.update(DT);

        float lifespan = creature.getComponent(AgeComponent.class).lifespan;
        assertTrue(lifespan >= 240f && lifespan <= 360f, "lifespan " + lifespan + "s");
    }

    @Test
    @DisplayName("offspring are one generation after their most advanced parent")
    void offspringGeneration() {
        Engine e = new Engine();
        e.addSystem(new MatingSystem());
        grownCreature(e, 100, 100, Gender.MALE, 60f, 3);
        grownCreature(e, 110, 100, Gender.FEMALE, 60f, 5);
        e.update(DT);

        assertEquals(3, creatureCount(e), "one offspring born");
        assertEquals(6, e.getEntities().get(2).getComponent(AgeComponent.class).generation);
    }

    @Test
    @DisplayName("young creatures can't mate until they are mature")
    void immatureCantMate() {
        Engine e = new Engine();
        e.addSystem(new MatingSystem());
        float young = Balance.MATURITY_AGE / 2f;
        grownCreature(e, 100, 100, Gender.MALE, young, 0);
        grownCreature(e, 110, 100, Gender.FEMALE, young, 0);
        e.update(DT);

        assertEquals(2, creatureCount(e), "no offspring from immature parents");
    }
}
