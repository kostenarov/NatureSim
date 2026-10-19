package io.kostenarov.natureSim;

import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.StatsComponent;

/**
 * Turns a genome into a creature's abilities and costs. Every gene that helps also costs something,
 * so natural selection has to find a balance instead of pushing every gene to its maximum.
 * Genes range from 0 to 2; a gene of 1 gives the "average" creature.
 *
 * <ul>
 *   <li>SPEED: moves faster, but energy, hunger and thirst cost grow with speed squared.</li>
 *   <li>VISION: sees farther, but the vision cone gets narrower; better eyes also cost a little food.</li>
 *   <li>METABOLISM: survives starvation and dehydration longer, but burns energy faster while active.</li>
 *   <li>RECOVERY_RATE: regains energy faster while resting, but burns more food while resting.</li>
 *   <li>HUNGER/THIRST_SENSITIVITY: starts looking for food/water earlier. Safer, but spends more time foraging.</li>
 *   <li>SCENT_RANGE (predators): smells prey from farther away, at a food cost.</li>
 * </ul>
 * SIZE and STEALTH are not used yet.
 */
public final class Traits {
    // Average creature (all genes = 1) loses these amounts per second while active.
    private static final float BASE_HUNGER_PER_SEC = 0.18f;
    private static final float BASE_THIRST_PER_SEC = 0.09f;
    private static final float BASE_ENERGY_PER_SEC = 3f;
    private static final float BASE_ENERGY_RECOVER_PER_SEC = 4f;
    private static final float BASE_HEALTH_LOSS_PER_SEC = 12f;
    private static final float RESTING_UPKEEP = 0.4f; // Resting burns food/water at 40% of the active rate

    private static final float AVERAGE_SPEED = 250f;
    private static final float AVERAGE_VISION_RANGE = 190f;
    private static final float BASE_SCENT_RANGE = 420f;

    private Traits() {
    }

    private static float gene(GenomeComponent dna, int index) {
        return dna.genes[index];
    }

    // --- Abilities -------------------------------------------------------------------------------------

    /** Movement speed in pixels per second (50 to 450). */
    public static float speed(GenomeComponent dna) {
        return 50f + gene(dna, GenomeComponent.SPEED) * 200f;
    }

    /** How far the creature can see (80 to 300 px). */
    public static float visionRange(GenomeComponent dna) {
        return 80f + gene(dna, GenomeComponent.VISION) * 110f;
    }

    /** Width of the vision cone in degrees (200 down to 80): long sight comes with tunnel vision. */
    public static float visionAngle(GenomeComponent dna) {
        return 200f - gene(dna, GenomeComponent.VISION) * 60f;
    }

    /** How far a predator can smell prey (210 to 630 px). */
    public static float scentRange(GenomeComponent dna) {
        return BASE_SCENT_RANGE * scentFactor(dna);
    }

    /** Start looking for food when hunger drops below this (30 to 90). */
    public static float foodSeekThreshold(GenomeComponent dna) {
        return 30f + gene(dna, GenomeComponent.HUNGER_SENSITIVITY) * 30f;
    }

    /** Start looking for water when thirst drops below this (15 to 45). */
    public static float waterSeekThreshold(GenomeComponent dna) {
        return 15f + gene(dna, GenomeComponent.THIRST_SENSITIVITY) * 15f;
    }

    // --- Costs -----------------------------------------------------------------------------------------

    private static float speedFactor(GenomeComponent dna) {
        return speed(dna) / AVERAGE_SPEED;
    }

    private static float scentFactor(GenomeComponent dna) {
        return 0.5f + gene(dna, GenomeComponent.SCENT_RANGE) * 0.5f;
    }

    /** Food and water upkeep multiplier (about 1 for an average creature). */
    public static float upkeep(GenomeComponent dna, boolean predator) {
        float speed = speedFactor(dna);
        float upkeep = 0.4f + 0.4f * speed * speed + 0.2f * (visionRange(dna) / AVERAGE_VISION_RANGE);
        if (predator) {
            upkeep += 0.15f * (scentFactor(dna) - 1f);
        }
        return upkeep;
    }

    /** Energy burned per second while moving (about 4.5 for an average creature). */
    public static float energyBurnRate(GenomeComponent dna) {
        float speed = speedFactor(dna);
        float speedCost = 0.4f + 0.6f * speed * speed;
        return BASE_ENERGY_PER_SEC * (0.5f + gene(dna, GenomeComponent.METABOLISM)) * speedCost;
    }

    // --- Metabolism ------------------------------------------------------------------------------------

    /** Drains hunger, thirst and energy for one active (moving) step, then applies starvation damage. */
    public static void burnActive(StatsComponent stats, GenomeComponent dna, boolean predator, float deltaTime) {
        float upkeep = upkeep(dna, predator);
        stats.hunger = Math.max(0f, stats.hunger - BASE_HUNGER_PER_SEC * upkeep * deltaTime);
        stats.thirst = Math.max(0f, stats.thirst - BASE_THIRST_PER_SEC * upkeep * deltaTime);
        stats.energy = Math.max(0f, stats.energy - energyBurnRate(dna) * deltaTime);
        applyStarvation(stats, dna, deltaTime);
    }

    /** Recovers energy while resting; faster recovery burns more food. */
    public static void rest(StatsComponent stats, GenomeComponent dna, boolean predator, float deltaTime) {
        float recoveryGene = 0.5f + gene(dna, GenomeComponent.RECOVERY_RATE);
        float upkeep = upkeep(dna, predator) * RESTING_UPKEEP;
        stats.energy = Math.min(100f, stats.energy + BASE_ENERGY_RECOVER_PER_SEC * recoveryGene * deltaTime);
        stats.hunger = Math.max(0f, stats.hunger - BASE_HUNGER_PER_SEC * upkeep * (recoveryGene / 1.5f) * deltaTime);
        stats.thirst = Math.max(0f, stats.thirst - BASE_THIRST_PER_SEC * upkeep * deltaTime);
        applyStarvation(stats, dna, deltaTime);
    }

    /** Loses health while starving or dehydrated; a higher metabolism holds out longer. */
    private static void applyStarvation(StatsComponent stats, GenomeComponent dna, float deltaTime) {
        if (stats.hunger <= 0f || stats.thirst <= 0f) {
            float healthLossRate = BASE_HEALTH_LOSS_PER_SEC / (0.5f + gene(dna, GenomeComponent.METABOLISM));
            stats.health = Math.max(0f, stats.health - healthLossRate * deltaTime);
        }
    }
}
