package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.VelocityComponent;

public class MovementSystem extends IteratingSystem {
    // Map boundaries
    private static final float MAP_MIN_X = 0;
    private static final float MAP_MAX_X = 1280;
    private static final float MAP_MIN_Y = 0;
    private static final float MAP_MAX_Y = 720;

    // Entity size (in pixels) for boundary collision
    private static final float ENTITY_SIZE = 32;

    public MovementSystem() {
        // This system only cares about entities with BOTH components
        super(Family.all(PositionComponent.class, VelocityComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);

        // Standard Physics: Position = Position + (Velocity * Time)
        pos.position.x += vel.velocity.x * deltaTime;
        pos.position.y += vel.velocity.y * deltaTime;

        // Apply map constraints (boundaries)
        pos.position.x = Math.max(MAP_MIN_X, Math.min(MAP_MAX_X - ENTITY_SIZE, pos.position.x));
        pos.position.y = Math.max(MAP_MIN_Y, Math.min(MAP_MAX_Y - ENTITY_SIZE, pos.position.y));
    }

    // Getter methods for boundaries (useful for camera system)
    public static float getMapMaxX() {
        return MAP_MAX_X;
    }

    public static float getMapMaxY() {
        return MAP_MAX_Y;
    }
}
