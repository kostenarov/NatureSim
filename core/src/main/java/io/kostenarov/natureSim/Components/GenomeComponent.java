package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

public class GenomeComponent implements Component {
    public static final int SPEED = 0;
    public static final int VISION = 1;
    public static final int SIZE = 2;

    public float[] genes = new float[3];

    public GenomeComponent() {
        for (int i = 0; i < genes.length; i++) {
            genes[i] = (float) Math.random() * 2;
        }
    }
}
