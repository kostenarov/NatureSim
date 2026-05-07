package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.kostenarov.natureSim.Enums.EntityBehaviour;

public class BehaviourComponent implements Component {
    public EntityBehaviour behaviour;
    public float behaviourTimer = 0;
    public static final float BEHAVIOUR_DURATION = 2.5f;
    public EntityBehaviour nextBehaviour = EntityBehaviour.IDLE;
    public float directionAngle = 0;

    // Target point for movement
    public Vector2 targetPoint = new Vector2();
    // Whether a valid targetPoint is set. Using a separate flag avoids treating (0,0) as "no target".
    public boolean hasTarget = false;
    public float targetReachedThreshold = 20f; // Distance to consider target as reached

    // Pause/scan behavior after reaching a target.
    public boolean waitingAtTarget = false;
    public float waitAtTargetTimer = 0f;
    public static final float WAIT_AT_TARGET_DURATION = 0.5f;

    public BehaviourComponent() {
        this.behaviour = EntityBehaviour.IDLE;
    }

    public BehaviourComponent(EntityBehaviour behaviour) {
        this.behaviour = behaviour;
    }

    public void update(EntityBehaviour behaviour, float delta) {
        behaviourTimer += delta;
        nextBehaviour = behaviour;
        if (behaviourTimer >= BEHAVIOUR_DURATION) {
            this.behaviour = nextBehaviour;
            behaviourTimer = 0;
        }
    }
}
