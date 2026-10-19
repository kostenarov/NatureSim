package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

public class ReproductionComponent implements Component {
    public float matingCooldown = 0f;                 // Time until this entity can mate again
    public static final float MATING_ENERGY_COST = 25f;   // Energy cost to mate
    public static final float MATING_HUNGER_COST = 25f;   // Food each parent gives up; together it's exactly what the newborn starts with
    public static final float MATING_MIN_HUNGER = 50f;    // Must be at least this well fed to mate
    public static final float MATING_MIN_THIRST = 40f;    // Must be at least this well watered to mate
    public static final float MATURITY_AGE = 30f;         // Seconds after birth before a creature can mate
    // Predators are bigger animals: they breed less often and only when well fed, or they wipe out their prey
    public static final float PREDATOR_COOLDOWN_FACTOR = 6f;
    public static final float PREDATOR_MATING_MIN_HUNGER = 70f;
    public static final float MATING_DISTANCE = 50f;      // Distance at which entities can mate
}

