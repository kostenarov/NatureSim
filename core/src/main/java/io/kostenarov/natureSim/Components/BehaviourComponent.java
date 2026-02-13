package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Enums.EntityBehaviour;

public class BehaviourComponent implements Component {
    public EntityBehaviour behaviour;
    public float behaviourTimer = 0;
    public static final float BEHAVIOUR_DURATION = 2.5f;
    public EntityBehaviour nextBehaviour = EntityBehaviour.IDLE;
    public float directionAngle = 0;

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
            directionAngle = MathUtils.random(0f, 360f);
        }
    }
}
