package io.kostenarov.natureSim;

import com.badlogic.gdx.Preferences;

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
    public float lifespanMinutes;     // Average natural lifespan
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
        predatorCount = 15; // Fewer than ~10 tend to die out before they find each other
        // About 4 food per second: enough for several hundred prey, so food (not a hard cap) limits the population.
        initialFood = 400;
        maxFood = 800;
        foodSpawnInterval = 0.25f;
        pondCount = 14;
        lifespanMinutes = 10f;
        matingCooldown = 10f;
        mutationRate = 0.15f;
        mutationAmount = 0.2f;
        seed = 0;
        collisionEnabled = true;
        showVisionCones = true;
        showScentRanges = true;
    }

    /** Loads saved settings; anything missing keeps its default. */
    public void load(Preferences prefs) {
        preyCount = prefs.getInteger("preyCount", preyCount);
        predatorCount = prefs.getInteger("predatorCount", predatorCount);
        initialFood = prefs.getInteger("initialFood", initialFood);
        maxFood = prefs.getInteger("maxFood", maxFood);
        foodSpawnInterval = prefs.getFloat("foodSpawnInterval", foodSpawnInterval);
        pondCount = prefs.getInteger("pondCount", pondCount);
        lifespanMinutes = prefs.getFloat("lifespanMinutes", lifespanMinutes);
        matingCooldown = prefs.getFloat("matingCooldown", matingCooldown);
        mutationRate = prefs.getFloat("mutationRate", mutationRate);
        mutationAmount = prefs.getFloat("mutationAmount", mutationAmount);
        seed = prefs.getLong("seed", seed);
        collisionEnabled = prefs.getBoolean("collisionEnabled", collisionEnabled);
        showVisionCones = prefs.getBoolean("showVisionCones", showVisionCones);
        showScentRanges = prefs.getBoolean("showScentRanges", showScentRanges);
    }

    /** Saves these settings so the start screen remembers them next time. */
    public void save(Preferences prefs) {
        prefs.putInteger("preyCount", preyCount);
        prefs.putInteger("predatorCount", predatorCount);
        prefs.putInteger("initialFood", initialFood);
        prefs.putInteger("maxFood", maxFood);
        prefs.putFloat("foodSpawnInterval", foodSpawnInterval);
        prefs.putInteger("pondCount", pondCount);
        prefs.putFloat("lifespanMinutes", lifespanMinutes);
        prefs.putFloat("matingCooldown", matingCooldown);
        prefs.putFloat("mutationRate", mutationRate);
        prefs.putFloat("mutationAmount", mutationAmount);
        prefs.putLong("seed", seed);
        prefs.putBoolean("collisionEnabled", collisionEnabled);
        prefs.putBoolean("showVisionCones", showVisionCones);
        prefs.putBoolean("showScentRanges", showScentRanges);
        prefs.flush();
    }
}
