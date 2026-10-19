package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

/**
 * A pond. Its PositionComponent is the pond's center (unlike creatures and food, which use the bottom-left corner).
 * Ponds never run dry and creatures can wade through them.
 */
public class WaterSourceComponent implements Component {
    public float radius;

    public WaterSourceComponent(float radius) {
        this.radius = radius;
    }
}
