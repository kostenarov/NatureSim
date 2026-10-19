package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.GenderComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.PredatorComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Components.VelocityComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Traits;

/**
 * Predator behaviour: rest, drink, wander while sated, and when hungry hunt prey by sight or follow its scent.
 */
public class PredatorSystem extends CreatureSystem {
    private ImmutableArray<Entity> preyEntities;

    public PredatorSystem() {
        super(Family.all(PositionComponent.class, VelocityComponent.class, GenomeComponent.class, VisionComponent.class,
            GenderComponent.class, StatsComponent.class, BehaviourComponent.class, PredatorComponent.class).get());
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        preyEntities = engine.getEntitiesFor(Family.all(PositionComponent.class, VelocityComponent.class, GenomeComponent.class,
            VisionComponent.class, GenderComponent.class, StatsComponent.class, BehaviourComponent.class)
            .exclude(PredatorComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PredatorComponent predator = entity.getComponent(PredatorComponent.class);
        predator.attackTimer = Math.max(0f, predator.attackTimer - deltaTime);
        super.processEntity(entity, deltaTime);
    }

    @Override
    protected EntityBehaviour decideBehaviour(Entity entity) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);
        PredatorComponent predator = entity.getComponent(PredatorComponent.class);
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);

        // Once resting, keep resting until energy has recovered.
        boolean resting = behaviour.behaviour == EntityBehaviour.IDLE && stats.energy < ENERGY_RESUME_THRESHOLD;
        if (resting || stats.energy <= ENERGY_STOP_THRESHOLD) {
            clearScentTarget(predator);
            return EntityBehaviour.IDLE;
        }

        boolean hungry = isHungry(stats, predator);
        if (wantsWater(stats, behaviour, dna, hungry)) {
            clearScentTarget(predator);
            return EntityBehaviour.SEEKING_WATER;
        }

        // Only hunt once there is room for a full meal; a sated predator just wanders.
        if (!hungry) {
            clearScentTarget(predator);
            return EntityBehaviour.EXPLORING;
        }

        Entity prey = findNearestVisiblePrey(entity);
        if (prey == null) {
            if (!predator.hasScentTarget) {
                Entity smelledPrey = findNearestSmelledPrey(entity);
                if (smelledPrey != null) {
                    rememberScentTarget(predator, smelledPrey.getComponent(PositionComponent.class));
                }
            }
            return EntityBehaviour.EXPLORING; // Explore follows the scent if there is one
        }

        float distance = entity.getComponent(PositionComponent.class).position.dst(prey.getComponent(PositionComponent.class).position);
        return distance <= predator.attackRange ? EntityBehaviour.ATTACKING : EntityBehaviour.HUNTING;
    }

    private boolean isHungry(StatsComponent stats, PredatorComponent predator) {
        float missingHunger = 100f - stats.hunger;
        return missingHunger >= predator.killEnergyGain * predator.huntEnergyBufferMultiplier;
    }

    @Override
    protected void executeBehaviour(Entity entity, EntityBehaviour behaviour, float deltaTime) {
        switch (behaviour) {
            case HUNTING:
            case ATTACKING:
                huntPrey(entity, deltaTime);
                break;
            case SEEKING_WATER:
                seekWater(entity, deltaTime);
                break;
            case EXPLORING:
                // decideBehaviour only sets a scent target while the predator is hungry.
                if (entity.getComponent(PredatorComponent.class).hasScentTarget) {
                    followScent(entity, deltaTime);
                } else {
                    explore(entity, deltaTime);
                }
                break;
            default:
                stop(entity);
                break;
        }
    }

    private void huntPrey(Entity predator, float deltaTime) {
        PredatorComponent predatorData = predator.getComponent(PredatorComponent.class);

        Entity prey = findNearestVisiblePrey(predator);
        if (prey == null) {
            stop(predator);
            return;
        }

        PositionComponent preyPos = prey.getComponent(PositionComponent.class);
        float distance = predator.getComponent(PositionComponent.class).position.dst(preyPos.position);
        if (distance <= predatorData.attackRange) {
            if (predatorData.attackTimer <= 0f) {
                attackPrey(predator, prey);
                predatorData.attackTimer = predatorData.attackCooldown;
            }
            stop(predator);
            return;
        }

        float speed = Traits.speed(predator.getComponent(GenomeComponent.class)) * predatorData.huntSpeedMultiplier;
        moveToward(predator, preyPos.position, speed, deltaTime);
    }

    /** Walk to where prey was last smelled; on arrival, pick up the next scent or give up. */
    private void followScent(Entity predator, float deltaTime) {
        PredatorComponent predatorData = predator.getComponent(PredatorComponent.class);
        PositionComponent predatorPos = predator.getComponent(PositionComponent.class);

        if (predatorPos.position.dst(predatorData.scentTarget) <= predatorData.scentReachThreshold) {
            stop(predator);
            if (findNearestVisiblePrey(predator) != null) {
                return; // Prey in sight: next step's decision switches to hunting
            }
            Entity nextSmell = findNearestSmelledPrey(predator);
            if (nextSmell != null) {
                rememberScentTarget(predatorData, nextSmell.getComponent(PositionComponent.class));
            } else {
                clearScentTarget(predatorData);
            }
            return;
        }

        float speed = Traits.speed(predator.getComponent(GenomeComponent.class)) * predatorData.scentSpeedMultiplier;
        boolean hitBoundary = moveToward(predator, predatorData.scentTarget, speed, deltaTime);
        if (hitBoundary) {
            stop(predator);
            clearScentTarget(predatorData);
        }
    }

    private void attackPrey(Entity predator, Entity prey) {
        StatsComponent predatorStats = predator.getComponent(StatsComponent.class);
        PredatorComponent predatorData = predator.getComponent(PredatorComponent.class);
        StatsComponent preyStats = prey.getComponent(StatsComponent.class);

        preyStats.health = Math.max(0f, preyStats.health - predatorData.attackDamage);
        if (preyStats.health <= 0f) {
            predatorStats.energy = Math.min(100f, predatorStats.energy + predatorData.killEnergyGain);
            predatorStats.hunger = Math.min(100f, predatorStats.hunger + predatorData.killEnergyGain);
            clearScentTarget(predatorData);
            getEngine().removeEntity(prey);
        }
    }

    private Entity findNearestVisiblePrey(Entity predator) {
        PositionComponent predatorPos = predator.getComponent(PositionComponent.class);
        VisionComponent vision = predator.getComponent(VisionComponent.class);

        Entity nearestPrey = null;
        float nearestDistance = Float.MAX_VALUE;
        for (Entity candidate : preyEntities) {
            PositionComponent candidatePos = candidate.getComponent(PositionComponent.class);
            if (canSee(predatorPos, vision, candidatePos)) {
                float distance = predatorPos.position.dst(candidatePos.position);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPrey = candidate;
                }
            }
        }
        return nearestPrey;
    }

    private Entity findNearestSmelledPrey(Entity predator) {
        PositionComponent predatorPos = predator.getComponent(PositionComponent.class);
        PredatorComponent predatorData = predator.getComponent(PredatorComponent.class);

        Entity nearestPrey = null;
        float nearestDistance = Float.MAX_VALUE;
        for (Entity candidate : preyEntities) {
            float distance = predatorPos.position.dst(candidate.getComponent(PositionComponent.class).position);
            if (distance <= predatorData.scentRange && distance < nearestDistance) {
                nearestDistance = distance;
                nearestPrey = candidate;
            }
        }
        return nearestPrey;
    }

    private void rememberScentTarget(PredatorComponent predatorData, PositionComponent preyPos) {
        predatorData.scentTarget.set(preyPos.position);
        predatorData.hasScentTarget = true;
    }

    private void clearScentTarget(PredatorComponent predatorData) {
        predatorData.hasScentTarget = false;
        predatorData.scentTarget.setZero();
    }
}
