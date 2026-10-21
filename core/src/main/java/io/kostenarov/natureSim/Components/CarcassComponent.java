package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

/** The remains of a dead creature: food for scavengers until eaten or rotted away. */
public class CarcassComponent implements Component {
    public float food;      // Hunger it can still restore
    public float age = 0f;  // Seconds since death; it rots as it ages

    public CarcassComponent(float food) {
        this.food = food;
    }
}
