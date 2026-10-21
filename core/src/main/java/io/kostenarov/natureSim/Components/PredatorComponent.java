package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;
import io.kostenarov.natureSim.Balance;

public class PredatorComponent implements Component {
    public float attackRange = Balance.ATTACK_RANGE;
    public float attackDamage = Balance.ATTACK_DAMAGE;      // Per bite
    public float attackCooldown = Balance.ATTACK_COOLDOWN;  // Seconds between bites
    public float attackTimer = 0f;                          // Time until the next bite is ready
    public float eatingTimer = 0f;                          // Time left eating the current kill
    public float killEnergyGain = Balance.KILL_FOOD_GAIN;   // Energy and food gained from a kill
    public float huntSpeedMultiplier = Balance.HUNT_SPEED_MULTIPLIER;
    public float huntEnergyBufferMultiplier = Balance.HUNT_APPETITE; // Hunt only when there's room for this much of a meal
    public float scentRange = 420f;                         // Set from the genome (Traits.scentRange)
    public float scentReachThreshold = Balance.SCENT_REACH;
    public float scentSpeedMultiplier = 1.0f;

    public final Vector2 scentTarget = new Vector2();
    public boolean hasScentTarget = false;
}
