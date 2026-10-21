package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.MathUtils;

public class GenomeComponent implements Component {
    // What each gene does, and what it costs, is defined in Traits.
    public static final int SPEED = 0;                // Faster movement; energy/food cost grows with speed squared
    public static final int VISION = 1;               // Longer sight range but a narrower vision cone
    public static final int SIZE = 2;                 // Tougher body and harder bite, but needs more food
    public static final int METABOLISM = 3;           // Survives starvation longer, burns energy faster
    public static final int RECOVERY_RATE = 4;        // Recovers energy faster at rest, burns more food at rest
    public static final int HUNGER_SENSITIVITY = 5;   // How early the creature starts looking for food
    public static final int THIRST_SENSITIVITY = 6;   // How early the creature starts looking for water
    public static final int SCENT_RANGE = 7;          // How far a predator can smell prey (costs food)
    public static final int STEALTH = 8;              // Harder to spot, but slower when chasing or fleeing
    public static final int ORNAMENT = 9;             // Showier: females prefer it, but it's easier to spot and costs food
    public static final int COUNT = 10;

    public float[] genes = new float[COUNT];

    public GenomeComponent() {
        for (int i = 0; i < genes.length; i++) {
            genes[i] = MathUtils.random(0f, 2f);
        }
    }

    public GenomeComponent(float[] genes) {
        this.genes = genes;
    }
}
