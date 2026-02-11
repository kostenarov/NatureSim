package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

public class VisionComponent implements Component {
    // Vision cone parameters
    public float visionRange = 100f; // Base vision range in pixels
    public float visionAngle = 120f; // Total cone angle in degrees (120 degrees = 60 degrees on each side)
    public float directionAngle = 0f; // Direction the agent is facing (0 = right, 90 = up, etc.)

    // Will be set based on genome
    public VisionComponent() {
    }

    public VisionComponent(float visionRange, float visionAngle) {
        this.visionRange = visionRange;
        this.visionAngle = visionAngle;
    }
}

