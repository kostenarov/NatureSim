package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

public class ReproductionComponent implements Component {
    public float matingCooldown = 0f;                 // Time until this entity can mate again
    public static final float MATING_ENERGY_COST = 25f;   // Energy cost to mate
    public static final float MATING_HUNGER_COST = 15f;   // Food each parent gives up to make the offspring
    public static final float MATING_MIN_HUNGER = 50f;    // Must be at least this well fed to mate
    public static final float MATING_MIN_THIRST = 40f;    // Must be at least this well watered to mate
    public static final float MATING_DISTANCE = 50f;      // Distance at which entities can mate
}

