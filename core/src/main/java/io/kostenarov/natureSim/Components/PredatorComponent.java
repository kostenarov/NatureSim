package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;

@SuppressWarnings("unused")
public class PredatorComponent implements Component {
    public float attackRange = 28f;
    public float attackDamage = 25f;        // Damage dealt per bite
    public float attackCooldown = 0.75f;    // Seconds between bites
    public float attackTimer = 0f;          // Time until the next bite is ready
    public float killEnergyGain = 35f;
    public float huntSpeedMultiplier = 1.2f;
    public float huntEnergyBufferMultiplier = 1.15f;
    public float scentRange = 320f;
    public float scentReachThreshold = 24f;
    public float scentSpeedMultiplier = 1.05f;

    public final Vector2 scentTarget = new Vector2();
    public boolean hasScentTarget = false;

    public PredatorComponent() {
    }

    /** Copies the inheritable predator traits (not the transient hunt state) from a parent. */
    public PredatorComponent(PredatorComponent parent) {
        this.attackRange = parent.attackRange;
        this.attackDamage = parent.attackDamage;
        this.attackCooldown = parent.attackCooldown;
        this.killEnergyGain = parent.killEnergyGain;
        this.huntSpeedMultiplier = parent.huntSpeedMultiplier;
        this.huntEnergyBufferMultiplier = parent.huntEnergyBufferMultiplier;
        this.scentRange = parent.scentRange;
        this.scentReachThreshold = parent.scentReachThreshold;
        this.scentSpeedMultiplier = parent.scentSpeedMultiplier;
    }
}
