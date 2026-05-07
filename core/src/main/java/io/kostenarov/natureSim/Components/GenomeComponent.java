package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

public class GenomeComponent implements Component {
    public static final int SPEED = 0;                //Affects speed
    public static final int VISION = 1;               //Affects how far the creature can see resources and threats
    public static final int SIZE = 2;                 //Affects how much energy the creature can store and how much it consumes while moving
    public static final int METABOLISM = 3;           // Affects energy consumption rate
    public static final int RECOVERY_RATE = 4;        // Affects energy recovery rate
    public static final int HUNGER_SENSITIVITY = 5;   // Affects how quickly hunger increases
    public static final int THIRST_SENSITIVITY = 6;   // Affects how quickly thirst increases

    public float[] genes = new float[7];

    public GenomeComponent() {
        for (int i = 0; i < genes.length; i++) {
            genes[i] = (float) Math.random() * 2;
        }
    }
}
