package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.PredatorComponent;
import io.kostenarov.natureSim.Components.ReproductionComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.MatingSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class MatingTest extends SimTestSupport {

    @Test
    @DisplayName("predator and prey of opposite sex do not mate")
    void noPredatorPreyMating() {
        Engine e = new Engine();
        e.addSystem(new MatingSystem());
        creature(e, 100, 100, Gender.MALE, true);
        creature(e, 110, 100, Gender.FEMALE, false);
        run(e, 1f);
        assertEquals(2, creatureCount(e), "entities");
    }

    @Test
    @DisplayName("two predators produce a predator")
    void predatorOffspringIsPredator() {
        Engine e = new Engine();
        e.addSystem(new MatingSystem());
        creature(e, 100, 100, Gender.MALE, true);
        creature(e, 110, 100, Gender.FEMALE, true);
        run(e, 1f);
        int predators = e.getEntitiesFor(Family.all(PredatorComponent.class).get()).size();
        assertEquals(3, creatureCount(e), "entities");
        assertEquals(3, predators, "predators");
    }

    /** Mates a parent with all genes 0.2 and one with all genes 1.8 (no mutation) and returns the child. */
    private static Entity crossChild() {
        SimulationConfig cfg = new SimulationConfig();
        cfg.mutationRate = 0f;
        Engine e = new Engine();
        e.addSystem(new MatingSystem(cfg));
        Entity a = creature(e, 100, 100, Gender.MALE, false);
        Entity b = creature(e, 110, 100, Gender.FEMALE, false);
        Arrays.fill(a.getComponent(GenomeComponent.class).genes, 0.2f);
        Arrays.fill(b.getComponent(GenomeComponent.class).genes, 1.8f);
        e.update(DT);
        assertEquals(3, creatureCount(e), "parents did not mate");
        return e.getEntities().get(2);
    }

    @Test
    @DisplayName("each child gene comes whole from one parent, mixing both parents")
    void genesInheritedWholeFromBothParents() {
        int mixed = 0, pure = 0, trials = 50;
        for (int t = 0; t < trials; t++) {
            GenomeComponent dna = crossChild().getComponent(GenomeComponent.class);
            boolean fromParents = true, sawLow = false, sawHigh = false;
            for (float g : dna.genes) {
                fromParents &= (g == 0.2f || g == 1.8f);
                sawLow |= g == 0.2f;
                sawHigh |= g == 1.8f;
            }
            if (fromParents) pure++;
            if (sawLow && sawHigh) mixed++;
        }
        assertEquals(trials, pure, "children with only parental genes");
        assertTrue(mixed >= trials - 2, mixed + "/" + trials + " children mix both parents");
    }

    @Test
    @DisplayName("child vision comes from its own genes")
    void childVisionFromGenes() {
        for (int t = 0; t < 50; t++) {
            Entity child = crossChild();
            GenomeComponent dna = child.getComponent(GenomeComponent.class);
            assertEquals(Traits.visionRange(dna), child.getComponent(VisionComponent.class).visionRange, "trial " + t);
        }
    }

    @Test
    @DisplayName("hungry creatures can't mate, and mating costs the parents food")
    void matingNeedsFoodAndCostsFood() {
        Engine e = new Engine();
        e.addSystem(new MatingSystem());
        Entity a = creature(e, 100, 100, Gender.MALE, false);
        creature(e, 110, 100, Gender.FEMALE, false);
        stats(a).hunger = 40f;
        e.update(DT);
        int whileHungry = creatureCount(e);
        stats(a).hunger = 100f;
        e.update(DT);

        assertEquals(2, whileHungry, "entities while hungry");
        assertEquals(3, creatureCount(e), "entities after feeding");
        assertEquals(100f - Balance.MATING_HUNGER_COST, stats(a).hunger, "parent hunger after mating");
    }
}
