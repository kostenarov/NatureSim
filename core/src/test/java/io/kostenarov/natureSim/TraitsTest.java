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
}
