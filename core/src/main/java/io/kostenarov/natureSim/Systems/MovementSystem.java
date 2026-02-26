package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Components.*;
import io.kostenarov.natureSim.Enums.EntityBehaviour;

public class MovementSystem extends IteratingSystem {
    // Map boundaries
    private static final float MAP_MIN_X = 0;
    private static final float MAP_MAX_X = 5120;
    private static final float MAP_MIN_Y = 0;
    private static final float MAP_MAX_Y = 5120;

    private static final float ENTITY_SIZE = 32;

    public MovementSystem() {
        super(Family.all(PositionComponent.class, VelocityComponent.class, GenomeComponent.class, VisionComponent.class, GenderComponent.class, BehaviourComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        BehaviourComponent behaviourComponent = entity.getComponent(BehaviourComponent.class);
        if (behaviourComponent == null) {
            return;
        }

        EntityBehaviour behaviour = decideBehaviour(entity);
        behaviourComponent.update(behaviour, deltaTime);

        executeBehaviour(entity, behaviour, deltaTime);
    }

    private EntityBehaviour decideBehaviour(Entity entity) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        if (stats.hunger < 0.3f) {
            return EntityBehaviour.SEEKING_FOOD;
        } else if (stats.thirst < 0.3f) {
            return EntityBehaviour.SEEKING_WATER;
        } else if (stats.energy < 0.5f) {
            return EntityBehaviour.IDLE;
        } else {
            return EntityBehaviour.EXPLORING;
        }
    }

    private void executeBehaviour(Entity entity, EntityBehaviour behaviour, float deltaTime) {
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);
        switch (behaviour) {
            case SEEKING_FOOD:
                //TODO: Implement food seeking logic (e.g., move towards nearest food source)
                break;
            case SEEKING_WATER:
                //TODO: Implement water seeking logic (e.g., move towards nearest water source)
                break;
            case IDLE:
                vel.velocity.x = 0;
                vel.velocity.y = 0;
                recoverEnergy(entity);
                break;
            case EXPLORING:
                explore(entity, deltaTime);
                decreaseStats(entity);
                break;
        }
    }

    private void explore(Entity entity, float deltaTime) {
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);
        PositionComponent pos = entity.getComponent(PositionComponent.class);

        if (behaviour.behaviour != EntityBehaviour.IDLE) {
            float angleDeg = behaviour.directionAngle;
            float speedMultiplier = 50f + (dna.genes[GenomeComponent.SPEED] * 200f);
            vel.velocity.set(MathUtils.cosDeg(angleDeg) * speedMultiplier,
                             MathUtils.sinDeg(angleDeg) * speedMultiplier);

            updatePosition(pos, vel, deltaTime);

            boolean hitBoundary = constrainPositionToBounds(pos);
            if (hitBoundary) {
                bounceOffBoundary(vel, dna);
            }

            if (!hitBoundary) {
                updateVisionDirection(entity);
            }
        }
    }


    private void applySpeedMultiplier(VelocityComponent vel, GenomeComponent dna) {
        // Ensure velocity is normalized before applying speed multiplier
        if (vel.velocity.len() == 0) {
            vel.velocity.set(1, 0); // Default direction if velocity is zero
        }
        float speedMultiplier = 50f + (dna.genes[GenomeComponent.SPEED] * 200f);
        vel.velocity.nor().scl(speedMultiplier);
    }

    private void updatePosition(PositionComponent pos, VelocityComponent vel, float deltaTime) {
        pos.position.x += vel.velocity.x * deltaTime;
        pos.position.y += vel.velocity.y * deltaTime;
    }

    private boolean constrainPositionToBounds(PositionComponent pos) {
        boolean hitBoundary = false;

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

        return hitBoundary;
    }

    private void bounceOffBoundary(VelocityComponent vel, GenomeComponent dna) {
        float angleDeg = MathUtils.random(0f, 360f);
        float speedMultiplier = 50f + (dna.genes[GenomeComponent.SPEED] * 200f);
        vel.velocity.set(MathUtils.cosDeg(angleDeg) * speedMultiplier,
                         MathUtils.sinDeg(angleDeg) * speedMultiplier);
    }

    private void updateVisionDirection(Entity entity) {
        VelocityComponent vel = entity.getComponent(VelocityComponent.class);
        VisionComponent vision = entity.getComponent(VisionComponent.class);
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);

        if(behaviour.behaviour == EntityBehaviour.IDLE) {
            return;
        }
        if (vision != null && (vel.velocity.x != 0 || vel.velocity.y != 0)) {
            float angle = (float) Math.toDegrees(Math.atan2(vel.velocity.y, vel.velocity.x));
            vision.directionAngle = angle;
        }
    }

    private void recoverEnergy(Entity entity) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        stats.energy += 0.001f;
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
            stats.energy = Math.max(0, stats.energy - 0.01f);
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
