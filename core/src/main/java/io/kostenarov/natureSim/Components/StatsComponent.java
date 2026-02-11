package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

public class StatsComponent implements Component {
    public float hunger;
    public float thirst;
    public float energy;
    public float health;

    public StatsComponent() {
        this(100f, 100f, 100f, 100f);
    }

    public StatsComponent(float hunger, float thirst, float energy, float health) {
        this.hunger = hunger;
        this.thirst = thirst;
        this.energy = energy;
        this.health = health;
    }
}

