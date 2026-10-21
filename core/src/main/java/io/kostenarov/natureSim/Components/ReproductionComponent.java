package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

public class ReproductionComponent implements Component {
    public float matingCooldown = 0f;                 // Time until this entity can mate again
}

