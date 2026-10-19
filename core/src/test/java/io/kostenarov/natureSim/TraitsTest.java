package io.kostenarov.natureSim;

import io.kostenarov.natureSim.Components.GenomeComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TraitsTest extends SimTestSupport {

    /** A genome with every gene at 1 except one. */
    private static GenomeComponent genomeWith(int gene, float value) {
        GenomeComponent dna = new GenomeComponent();
        Arrays.fill(dna.genes, 1f);
        dna.genes[gene] = value;
        return dna;
    }

    @Test
    @DisplayName("fast creatures pay for speed in energy and upkeep")
    void fastCreaturesPayForSpeed() {
        GenomeComponent slow = genomeWith(GenomeComponent.SPEED, 0.3f);
        GenomeComponent fast = genomeWith(GenomeComponent.SPEED, 1.7f);
        float eSlow = Traits.energyBurnRate(slow), eFast = Traits.energyBurnRate(fast);
        float uSlow = Traits.upkeep(slow, false), uFast = Traits.upkeep(fast, false);
        String detail = String.format("energy/s slow %.2f fast %.2f, upkeep slow %.2f fast %.2f", eSlow, eFast, uSlow, uFast);

        assertTrue(eFast > 2 * eSlow, detail);
        assertTrue(uFast > uSlow, detail);
    }

    @Test
    @DisplayName("far sight narrows the vision cone and costs food")
    void farSightNarrowsConeAndCostsFood() {
        GenomeComponent near = genomeWith(GenomeComponent.VISION, 0.2f);
        GenomeComponent far = genomeWith(GenomeComponent.VISION, 1.8f);
        String detail = String.format("near: %.0fpx/%.0f deg, far: %.0fpx/%.0f deg",
            Traits.visionRange(near), Traits.visionAngle(near), Traits.visionRange(far), Traits.visionAngle(far));

        assertTrue(Traits.visionAngle(far) < Traits.visionAngle(near), detail);
        assertTrue(Traits.upkeep(far, false) > Traits.upkeep(near, false), detail);
    }

    @Test
    @DisplayName("big bodies take less damage and bite harder, but cost more food")
    void sizeTradeOff() {
        GenomeComponent average = genomeWith(GenomeComponent.SIZE, 1f);
        GenomeComponent small = genomeWith(GenomeComponent.SIZE, 0.2f);
        GenomeComponent big = genomeWith(GenomeComponent.SIZE, 1.8f);

        assertEquals(40f, Traits.biteDamage(40f, average, average), 0.001f, "average vs average is the base damage");
        assertTrue(Traits.biteDamage(40f, average, big) < 40f, "big prey take less damage");
        assertTrue(Traits.biteDamage(40f, average, small) > 40f, "small prey take more damage");
        assertTrue(Traits.biteDamage(40f, big, average) > 40f, "big predators bite harder");
        assertTrue(Traits.upkeep(big, false) > Traits.upkeep(small, false), "big bodies need more food");
    }

    @Test
    @DisplayName("stealthy creatures are harder to spot but slower in a chase")
    void stealthTradeOff() {
        GenomeComponent obvious = genomeWith(GenomeComponent.STEALTH, 0.2f);
        GenomeComponent stealthy = genomeWith(GenomeComponent.STEALTH, 1.8f);

        assertTrue(Traits.visibility(stealthy) < Traits.visibility(obvious), "stealthy is harder to see");
        assertTrue(Traits.pursuitSpeedFactor(stealthy) < Traits.pursuitSpeedFactor(obvious), "stealthy is slower when chasing or fleeing");
    }

    @Test
    @DisplayName("wounds heal while fed and watered, not while starving")
    void healing() {
        GenomeComponent dna = genomeWith(GenomeComponent.SIZE, 1f);
        io.kostenarov.natureSim.Components.StatsComponent fed = new io.kostenarov.natureSim.Components.StatsComponent(100f, 100f, 100f, 50f);
        io.kostenarov.natureSim.Components.StatsComponent starving = new io.kostenarov.natureSim.Components.StatsComponent(0f, 100f, 100f, 50f);
        for (int i = 0; i < 300; i++) {
            Traits.rest(fed, dna, false, DT);
            Traits.rest(starving, dna, false, DT);
        }
        assertTrue(fed.health > 50f, "fed creature healed to " + fed.health);
        assertTrue(starving.health < 50f, "starving creature kept losing health: " + starving.health);
    }
}
