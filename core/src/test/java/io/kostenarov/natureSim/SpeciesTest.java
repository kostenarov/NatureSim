package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.CarcassComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.ReproductionComponent;
import io.kostenarov.natureSim.Components.SpeciesComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Enums.Role;
import io.kostenarov.natureSim.Systems.MatingSystem;
import io.kostenarov.natureSim.Systems.PreySystem;
import io.kostenarov.natureSim.Systems.ScavengerSystem;
import io.kostenarov.natureSim.Systems.SpeciesSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Mate choice, the species barrier, species tracking, and genes that work differently by role. */
class SpeciesTest extends SimTestSupport {

    private static float[] genes(Entity creature) {
        return creature.getComponent(GenomeComponent.class).genes;
    }

    private static boolean mated(Entity creature) {
        return creature.getComponent(ReproductionComponent.class).matingCooldown > 0f;
    }

    @Test
    @DisplayName("a female mates with the showier of two males")
    void femalePrefersOrnament() {
        Engine e = new Engine();
        e.addSystem(new MatingSystem());
        creature(e, 100, 100, Gender.FEMALE, false);
        Entity showy = creature(e, 130, 100, Gender.MALE, false);
        Entity plain = creature(e, 70, 100, Gender.MALE, false);
        genes(showy)[GenomeComponent.ORNAMENT] = 1.8f;
        genes(plain)[GenomeComponent.ORNAMENT] = 0.2f;

        e.update(DT);
        assertTrue(mated(showy), "the showy male mated");
        assertFalse(mated(plain), "the plain male didn't");
    }

    @Test
    @DisplayName("a female prefers a male like her over a slightly showier but different one")
    void femalePrefersSimilar() {
        Engine e = new Engine();
        e.addSystem(new MatingSystem());
        creature(e, 100, 100, Gender.FEMALE, false);
        Entity alike = creature(e, 130, 100, Gender.MALE, false);
        Entity different = creature(e, 70, 100, Gender.MALE, false);
        genes(different)[GenomeComponent.ORNAMENT] = 1.4f;
        for (int g = 0; g < 5; g++) {
            genes(different)[g] = 1.5f;
        }

        e.update(DT);
        assertTrue(mated(alike), "the similar male mated");
        assertFalse(mated(different), "the different one didn't");
    }

    @Test
    @DisplayName("creatures too different genetically can't breed")
    void speciesBarrier() {
        Engine e = new Engine();
        e.addSystem(new MatingSystem());
        creature(e, 100, 100, Gender.FEMALE, false);
        Entity stranger = creature(e, 110, 100, Gender.MALE, false);
        Arrays.fill(genes(stranger), 1.7f); // Distance about 2.2, over the limit of 2

        run(e, 1f);
        assertEquals(2, creatureCount(e), "no offspring");
    }

    @Test
    @DisplayName("an offspring belongs to its mother's species")
    void offspringInheritsSpecies() {
        Engine e = new Engine();
        e.addSystem(new MatingSystem());
        Entity mother = creature(e, 100, 100, Gender.FEMALE, false);
        creature(e, 110, 100, Gender.MALE, false);
        mother.getComponent(SpeciesComponent.class).id = 7;

        e.update(DT);
        Entity child = e.getEntities().get(2);
        assertEquals(7, child.getComponent(SpeciesComponent.class).id);
    }

    /** {@code count} prey with every gene near {@code value}. */
    private static void group(Engine e, int count, float value, float x) {
        for (int i = 0; i < count; i++) {
            Entity c = creature(e, x + i * 30, 500, i % 2 == 0 ? Gender.MALE : Gender.FEMALE, false);
            Arrays.fill(genes(c), value + (i % 3) * 0.05f);
        }
    }

    @Test
    @DisplayName("one gene pool is one species; a second, distinct group becomes a second species")
    void speciesSplitWhenGroupsDrift() {
        Engine e = new Engine();
        SpeciesSystem species = new SpeciesSystem();
        e.addSystem(species);
        group(e, 20, 1.0f, 100);
        species.classify();
        assertEquals(1, species.speciesCount(Role.PREY), "one species to start with");

        group(e, 20, 1.8f, 2000); // Genetically far from the first group
        species.classify();
        assertEquals(2, species.speciesCount(Role.PREY), "the distinct group is a new species");

        int firstId = e.getEntities().get(0).getComponent(SpeciesComponent.class).id;
        int secondId = e.getEntities().get(30).getComponent(SpeciesComponent.class).id;
        assertNotEquals(firstId, secondId);
    }

    @Test
    @DisplayName("a few odd ones out don't make a species of their own")
    void tooFewForNewSpecies() {
        Engine e = new Engine();
        SpeciesSystem species = new SpeciesSystem();
        e.addSystem(species);
        group(e, 20, 1.0f, 100);
        species.classify();
        group(e, Balance.MIN_NEW_SPECIES_SIZE - 1, 1.8f, 2000);
        species.classify();
        assertEquals(1, species.speciesCount(Role.PREY));
    }

    @Test
    @DisplayName("prey smell a predator right behind them, which they can't see")
    void preySmellPredators() {
        Engine e = new Engine();
        e.addSystem(new PreySystem());
        Entity prey = creature(e, 1000, 1000, Gender.MALE, false);
        prey.getComponent(VisionComponent.class).directionAngle = 0f; // Facing away from the predator
        float smell = Traits.preySmellRange(prey.getComponent(GenomeComponent.class));
        creature(e, 1000 - smell * 0.7f, 1000, Gender.MALE, true);

        e.update(DT);
        assertEquals(EntityBehaviour.FLEEING, prey.getComponent(BehaviourComponent.class).behaviour);
    }

    @Test
    @DisplayName("a predator behind a prey and beyond its smell goes unnoticed")
    void preyCantSmellFarPredators() {
        Engine e = new Engine();
        e.addSystem(new PreySystem());
        Entity prey = creature(e, 1000, 1000, Gender.MALE, false);
        prey.getComponent(VisionComponent.class).directionAngle = 0f;
        float smell = Traits.preySmellRange(prey.getComponent(GenomeComponent.class));
        creature(e, 1000 - smell * 1.5f, 1000, Gender.MALE, true);

        e.update(DT);
        assertNotEquals(EntityBehaviour.FLEEING, prey.getComponent(BehaviourComponent.class).behaviour);
    }

    @Test
    @DisplayName("bigger scavengers eat a carcass faster")
    void bigScavengersEatFaster() {
        float[] eaten = new float[2];
        float[] sizes = {0.5f, 1.5f};
        for (int i = 0; i < 2; i++) {
            Engine e = new Engine();
            e.addSystem(new ScavengerSystem());
            Entity scavenger = creature(e, 500, 500, Gender.MALE, false);
            Creatures.makeScavenger(scavenger);
            genes(scavenger)[GenomeComponent.SIZE] = sizes[i];
            stats(scavenger).hunger = 10f;
            Entity carcass = e.createEntity();
            PositionComponent pos = new PositionComponent();
            pos.position.set(505, 500);
            carcass.add(pos);
            carcass.add(new CarcassComponent(80f));
            e.addEntity(carcass);

            run(e, 1f);
            eaten[i] = 80f - carcass.getComponent(CarcassComponent.class).food;
        }
        assertTrue(eaten[1] > eaten[0] * 1.3f, "small ate " + eaten[0] + ", big ate " + eaten[1]);
    }
}
