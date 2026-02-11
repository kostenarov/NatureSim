package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Components.*;

public class MovementSystem extends IteratingSystem {
    // Map boundaries
    private static final float MAP_MIN_X = 0;
    private static final float MAP_MAX_X = 1280;
    private static final float MAP_MIN_Y = 0;
    private static final float MAP_MAX_Y = 720;

    // Entity size (in pixels) for boundary collision
    private static final float ENTITY_SIZE = 32;

    public MovementSystem() {
        super(Family.all(PositionComponent.class, VelocityComponent.class, GenomeComponent.class, VisionComponent.class, GenderComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if(entity.getComponent(StatsComponent.class).energy > 0.2f) {
            PositionComponent pos = entity.getComponent(PositionComponent.class);
            VelocityComponent vel = entity.getComponent(VelocityComponent.class);
            GenomeComponent dna = entity.getComponent(GenomeComponent.class);

            float speedMultiplier = 50f + (dna.genes[GenomeComponent.SPEED] * 200f);

            // Normalize and apply velocity
            vel.velocity.nor().scl(speedMultiplier);

            pos.position.x += vel.velocity.x * deltaTime;
            pos.position.y += vel.velocity.y * deltaTime;

            boolean hitBoundary = false;

            // Apply map constraints (boundaries)
            if (pos.position.x < MAP_MIN_X) {
                pos.position.x = MAP_MIN_X;
                hitBoundary = true;
            } else if (pos.position.x > MAP_MAX_X - ENTITY_SIZE) {
                pos.position.x = MAP_MAX_X - ENTITY_SIZE;
                hitBoundary = true;
            }

            if (pos.position.y < MAP_MIN_Y) {
                pos.position.y = MAP_MIN_Y;
                hitBoundary = true;
            } else if (pos.position.y > MAP_MAX_Y - ENTITY_SIZE) {
                pos.position.y = MAP_MAX_Y - ENTITY_SIZE;
                hitBoundary = true;
            }

            if (hitBoundary) {
                float angleDeg = MathUtils.random(0f, 360f);
                vel.velocity.set(MathUtils.cosDeg(angleDeg), MathUtils.sinDeg(angleDeg));
            }

            // Update vision direction based on velocity direction
            VisionComponent vision = entity.getComponent(VisionComponent.class);
            if (vision != null && (vel.velocity.x != 0 || vel.velocity.y != 0)) {
                // Calculate angle from velocity vector (in degrees)
                float angle = (float) Math.toDegrees(Math.atan2(vel.velocity.y, vel.velocity.x));
                vision.directionAngle = angle;
            }
            decreaseStats(entity);
        }
        else if(entity.getComponent(StatsComponent.class).energy < 0.2f) {
            entity.getComponent(StatsComponent.class).energy += 0.001f;
        }
    }

    public static float getMapMaxX() {
        return MAP_MAX_X;
    }

    public static float getMapMaxY() {
        return MAP_MAX_Y;
    }

    private void decreaseStats(Entity entity) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        if (stats != null) {
            stats.hunger = Math.max(0, stats.hunger - 0.002f);
            stats.thirst = Math.max(0, stats.thirst - 0.001f);
            stats.energy = Math.max(0, stats.energy - 0.1f);
        }
        decreaseHealth(entity);
    }

    private void decreaseHealth(Entity entity) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        if (stats != null) {
            if (stats.hunger <= 0 || stats.thirst <= 0) {
                stats.health = Math.max(0, stats.health - 0.2f);
            }
        }
    }
}
