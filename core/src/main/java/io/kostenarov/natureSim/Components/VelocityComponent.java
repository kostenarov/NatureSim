package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;

public class VelocityComponent implements Component {
    public Vector2 velocity = new Vector2(100, 100);

        public VelocityComponent() {
        }

    public VelocityComponent(float speed) {
        // Randomize direction
        float angle = (float) (Math.random() * 2 * Math.PI);
        velocity.x = (float) Math.cos(angle) * speed;
        velocity.y = (float) Math.sin(angle) * speed;
    }
}
