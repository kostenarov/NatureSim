package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;

public class PredatorComponent implements Component {
    public float attackRange = 30f;
    public float attackDamage = 40f;        // Damage dealt per bite
    public float attackCooldown = 0.75f;    // Seconds between bites
    public float attackTimer = 0f;          // Time until the next bite is ready
    public float killEnergyGain = 45f;      // Energy and food gained from a kill
    public float huntSpeedMultiplier = 1.35f;
    public float huntEnergyBufferMultiplier = 1.15f; // Hunt only when there's room for this much of a meal
    public float scentRange = 420f;         // Set from the genome (Traits.scentRange)
    public float scentReachThreshold = 24f;
    public float scentSpeedMultiplier = 1.0f;

    public final Vector2 scentTarget = new Vector2();
    public boolean hasScentTarget = false;
}
