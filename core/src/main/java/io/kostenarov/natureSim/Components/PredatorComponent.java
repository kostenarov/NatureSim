package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;

@SuppressWarnings("unused")
public class PredatorComponent implements Component {
    public float attackRange = 28f;
    public float attackDamage = 25f;
    public float killEnergyGain = 35f;
    public float huntSpeedMultiplier = 1.2f;
    public float huntEnergyBufferMultiplier = 1.15f;
    public float scentRange = 320f;
    public float scentReachThreshold = 24f;
    public float scentSpeedMultiplier = 1.05f;

    public final Vector2 scentTarget = new Vector2();
    public boolean hasScentTarget = false;
}
