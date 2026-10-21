package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import io.kostenarov.natureSim.Enums.EntityBehaviour;

public class BehaviourComponent implements Component {
    public EntityBehaviour behaviour = EntityBehaviour.IDLE;
    public boolean exhausted = false; // Ran out of energy; rests until recovered (see CreatureSystem)
    public float directionAngle = 0;

    // Target point for wandering
    public Vector2 targetPoint = new Vector2();
    // Whether a valid targetPoint is set. Using a separate flag avoids treating (0,0) as "no target".
    public boolean hasTarget = false;
    public float targetReachedThreshold = 20f; // Distance to consider target as reached

    // Pause/scan behavior after reaching a target.
    public boolean waitingAtTarget = false;
    public float waitAtTargetTimer = 0f;

    // Prey keep running from a threat for a while after losing sight of it (turning away to flee hides it)
    public final Vector2 lastThreat = new Vector2();
    public float fleeTimer = 0f;

    // Wandering gives up on a target it can't reach (see CreatureSystem): where it last made progress, and since when
    public final Vector2 progressAnchor = new Vector2();
    public float progressTimer = 0f;
    // Food that a crowd kept this creature from reaching; ignored until the timer runs out
    public Entity ignoredFood = null;
    public float ignoreFoodTimer = 0f;
}
