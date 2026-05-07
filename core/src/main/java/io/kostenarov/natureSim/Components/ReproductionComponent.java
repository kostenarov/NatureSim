package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

public class ReproductionComponent implements Component {
    public float matingCooldown = 0f;                 // Time until this entity can mate again
    public static final float MATING_COOLDOWN_TIME = 10f; // 10 seconds between mates
    public static final float MATING_ENERGY_COST = 25f;   // Energy cost to mate
    public static final float MATING_DISTANCE = 50f;      // Distance at which entities can mate
}

