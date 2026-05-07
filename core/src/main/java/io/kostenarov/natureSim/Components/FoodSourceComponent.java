package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

public class FoodSourceComponent implements Component {
    public float nutritionValue = 30f; // How much hunger this food restores
    public boolean consumed = false; // Mark when eaten so it can be removed
}

