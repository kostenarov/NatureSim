package io.kostenarov.natureSim;

import com.badlogic.gdx.Preferences;

/**
 * Tunable settings for one simulation run. Edited on the start screen, saved between launches, and read by
 * the systems when the world is built. Fixed tuning numbers live in {@link Balance}.
 */
public class SimulationConfig {
    // Population
    public int preyCount;
    public int predatorCount;
    public int scavengerCount;

    // Environment
    public int initialFood;
    public int maxFood;
    public float foodSpawnInterval;   // Seconds between food spawns
    public float foodNutrition;       // Hunger restored by one food item
    public int pondCount;
    public boolean dayNightEnabled;   // Vision shrinks at night
    public float dayLengthMinutes;    // One full day and night
    public boolean seasonsEnabled;    // Food grows fast in summer, barely in winter
    public float yearLengthMinutes;   // One full cycle of seasons

    // Reproduction and evolution
    public float lifespanMinutes;     // Average natural lifespan
    public float matingCooldown;      // Seconds between matings for one creature
    public float mutationRate;        // Chance that each gene mutates in an offspring
    public float mutationAmount;      // Standard deviation of a mutation (genes range 0 to 2)

    // Behaviour and balance
    public boolean herdingEnabled;          // Prey bunch together when a predator is near
    public boolean migrationEnabled;        // A few newcomers arrive when a species is nearly gone
    public float predatorBreedingSlowdown;  // Predators wait this many times longer than prey between matings
    public float predatorEatingSeconds;     // Time a predator spends eating each kill

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
        scavengerCount = 20;
        // About 4 food per second: enough for several hundred prey, so food (not a hard cap) limits the population.
        initialFood = 400;
        maxFood = 800;
        foodSpawnInterval = 0.25f;
        foodNutrition = 30f;
        pondCount = 14;
        dayNightEnabled = true;
        dayLengthMinutes = 4f;
        seasonsEnabled = true;
        yearLengthMinutes = 16f;
        lifespanMinutes = 10f;
        matingCooldown = 10f;
        mutationRate = 0.15f;
        mutationAmount = 0.2f;
        herdingEnabled = true;
        migrationEnabled = true;
        predatorBreedingSlowdown = 6f;
        predatorEatingSeconds = 6f;
        seed = 0;
        collisionEnabled = true;
        showVisionCones = true;
        showScentRanges = true;
    }

    /** Loads saved settings; anything missing keeps its default. */
    public void load(Preferences prefs) {
        preyCount = prefs.getInteger("preyCount", preyCount);
        predatorCount = prefs.getInteger("predatorCount", predatorCount);
        scavengerCount = prefs.getInteger("scavengerCount", scavengerCount);
        initialFood = prefs.getInteger("initialFood", initialFood);
        maxFood = prefs.getInteger("maxFood", maxFood);
        foodSpawnInterval = prefs.getFloat("foodSpawnInterval", foodSpawnInterval);
        foodNutrition = prefs.getFloat("foodNutrition", foodNutrition);
        pondCount = prefs.getInteger("pondCount", pondCount);
        dayNightEnabled = prefs.getBoolean("dayNightEnabled", dayNightEnabled);
        dayLengthMinutes = prefs.getFloat("dayLengthMinutes", dayLengthMinutes);
        seasonsEnabled = prefs.getBoolean("seasonsEnabled", seasonsEnabled);
        yearLengthMinutes = prefs.getFloat("yearLengthMinutes", yearLengthMinutes);
        lifespanMinutes = prefs.getFloat("lifespanMinutes", lifespanMinutes);
        matingCooldown = prefs.getFloat("matingCooldown", matingCooldown);
        mutationRate = prefs.getFloat("mutationRate", mutationRate);
        mutationAmount = prefs.getFloat("mutationAmount", mutationAmount);
        herdingEnabled = prefs.getBoolean("herdingEnabled", herdingEnabled);
        migrationEnabled = prefs.getBoolean("migrationEnabled", migrationEnabled);
        predatorBreedingSlowdown = prefs.getFloat("predatorBreedingSlowdown", predatorBreedingSlowdown);
        predatorEatingSeconds = prefs.getFloat("predatorEatingSeconds", predatorEatingSeconds);
        seed = prefs.getLong("seed", seed);
        collisionEnabled = prefs.getBoolean("collisionEnabled", collisionEnabled);
        showVisionCones = prefs.getBoolean("showVisionCones", showVisionCones);
        showScentRanges = prefs.getBoolean("showScentRanges", showScentRanges);
    }

    /** Saves these settings so the start screen remembers them next time. */
    public void save(Preferences prefs) {
        prefs.putInteger("preyCount", preyCount);
        prefs.putInteger("predatorCount", predatorCount);
        prefs.putInteger("scavengerCount", scavengerCount);
        prefs.putInteger("initialFood", initialFood);
        prefs.putInteger("maxFood", maxFood);
        prefs.putFloat("foodSpawnInterval", foodSpawnInterval);
        prefs.putFloat("foodNutrition", foodNutrition);
        prefs.putInteger("pondCount", pondCount);
        prefs.putBoolean("dayNightEnabled", dayNightEnabled);
        prefs.putFloat("dayLengthMinutes", dayLengthMinutes);
        prefs.putBoolean("seasonsEnabled", seasonsEnabled);
        prefs.putFloat("yearLengthMinutes", yearLengthMinutes);
        prefs.putFloat("lifespanMinutes", lifespanMinutes);
        prefs.putFloat("matingCooldown", matingCooldown);
        prefs.putFloat("mutationRate", mutationRate);
        prefs.putFloat("mutationAmount", mutationAmount);
        prefs.putBoolean("herdingEnabled", herdingEnabled);
        prefs.putBoolean("migrationEnabled", migrationEnabled);
        prefs.putFloat("predatorBreedingSlowdown", predatorBreedingSlowdown);
        prefs.putFloat("predatorEatingSeconds", predatorEatingSeconds);
        prefs.putLong("seed", seed);
        prefs.putBoolean("collisionEnabled", collisionEnabled);
        prefs.putBoolean("showVisionCones", showVisionCones);
        prefs.putBoolean("showScentRanges", showScentRanges);
        prefs.flush();
    }
}
