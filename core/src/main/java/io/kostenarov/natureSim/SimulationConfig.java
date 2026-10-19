package io.kostenarov.natureSim;

/**
 * Tunable settings for one simulation run. Edited on the start screen and read by the systems when the world is built.
 */
public class SimulationConfig {
    // Population
    public int preyCount;
    public int predatorCount;

    // Environment
    public int initialFood;
    public int maxFood;
    public float foodSpawnInterval;   // Seconds between food spawns
    public int pondCount;

    // Reproduction and evolution
    public float matingCooldown;      // Seconds between matings for one creature
    public float mutationRate;        // Chance that each gene mutates in an offspring
    public float mutationAmount;      // Standard deviation of a mutation (genes range 0 to 2)

    // Simulation and display
    public long seed;                 // Random seed: same seed + same settings gives the same run; 0 = random
    public boolean collisionEnabled;
    public boolean showVisionCones;
    public boolean showScentRanges;

    public SimulationConfig() {
        resetToDefaults();
    }

    public void resetToDefaults() {
        preyCount = 1000;
        predatorCount = 4;
        // About 4 food per second: enough for several hundred prey, so food (not a hard cap) limits the population.
        initialFood = 400;
        maxFood = 800;
        foodSpawnInterval = 0.25f;
        pondCount = 14;
        matingCooldown = 10f;
        mutationRate = 0.15f;
        mutationAmount = 0.2f;
        seed = 0;
        collisionEnabled = true;
        showVisionCones = true;
        showScentRanges = true;
    }
}
