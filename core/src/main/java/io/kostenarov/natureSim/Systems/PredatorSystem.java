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
import io.kostenarov.natureSim.SimulationConfig;
import io.kostenarov.natureSim.Traits;
import io.kostenarov.natureSim.Creatures;
import io.kostenarov.natureSim.Mappers;
import io.kostenarov.natureSim.Balance;

/**
 * Predator behaviour: rest, drink, wander while sated, and when hungry hunt prey by sight or follow its scent.
 */
public class PredatorSystem extends CreatureSystem {

    private ImmutableArray<Entity> preyEntities;

    private final SimulationConfig config; // Eating time (caps how fast one predator can kill), read live

    public PredatorSystem() {
        this(new SimulationConfig());
    }

    public PredatorSystem(SimulationConfig config) {
        super(Creatures.PREDATORS);
        this.config = config;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        preyEntities = engine.getEntitiesFor(Creatures.PREY);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PredatorComponent predator = Mappers.predator.get(entity);
        predator.attackTimer = Math.max(0f, predator.attackTimer - deltaTime);
        if (predator.eatingTimer > 0f) {
            // Still eating the last kill: stay put (counts as resting for metabolism)
            predator.eatingTimer -= deltaTime;
            Mappers.behaviour.get(entity).behaviour = EntityBehaviour.IDLE;
            stop(entity);
            return;
        }
        super.processEntity(entity, deltaTime);
        if (Mappers.behaviour.get(entity).exhausted) {
            clearScentTarget(predator); // The trail goes cold while it recovers
        }
    }

    @Override
    protected EntityBehaviour decideBehaviour(Entity entity) {
        StatsComponent stats = Mappers.stats.get(entity);
        BehaviourComponent behaviour = Mappers.behaviour.get(entity);
        PredatorComponent predator = Mappers.predator.get(entity);
        GenomeComponent dna = Mappers.genome.get(entity);

        // (Running out of energy is handled before any decision: see CreatureSystem.)
        boolean hungry = isHungry(stats, predator);
        if (wantsWater(stats, behaviour, dna, hungry)) {
            clearScentTarget(predator);
            return EntityBehaviour.SEEKING_WATER;
        }

        // Only hunt once there is room for a full meal; a sated predator looks for a mate or wanders.
        if (!hungry) {
            clearScentTarget(predator);
            return MatingSystem.isReadyToMate(entity) ? EntityBehaviour.SEEKING_MATE : restOrWander(stats, behaviour);
        }

        Entity prey = findNearestVisiblePrey(entity);
        if (prey == null) {
            if (!predator.hasScentTarget) {
                Entity smelledPrey = findNearestSmelledPrey(entity);
                if (smelledPrey != null) {
                    rememberScentTarget(predator, Mappers.position.get(smelledPrey));
                }
            }
            return EntityBehaviour.EXPLORING; // Explore follows the scent if there is one
        }

        float distance = Mappers.position.get(entity).position.dst(Mappers.position.get(prey).position);
        return distance <= predator.attackRange ? EntityBehaviour.ATTACKING : EntityBehaviour.HUNTING;
    }

    /** Predators are few and far between, so they find mates by smell (a mate's scent carries further than prey's). */
    @Override
    protected float mateScentRange(Entity entity) {
        return Mappers.predator.get(entity).scentRange * Balance.MATE_SCENT_FACTOR;
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
            case SEEKING_MATE:
                approachPartner(entity, findVisiblePartner(entity), deltaTime);
                break;
            case EXPLORING:
                // decideBehaviour only sets a scent target while the predator is hungry.
                PredatorComponent predator = Mappers.predator.get(entity);
                if (predator.hasScentTarget) {
                    followScent(entity, deltaTime);
                } else if (isHungry(Mappers.stats.get(entity), predator)) {
                    roamNearWater(entity, deltaTime); // Prey gather at the waterholes
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
        PredatorComponent predatorData = Mappers.predator.get(predator);

        Entity prey = findNearestVisiblePrey(predator);
        if (prey == null) {
            stop(predator);
            return;
        }

        PositionComponent preyPos = Mappers.position.get(prey);
        float distance = Mappers.position.get(predator).position.dst(preyPos.position);
        if (distance <= predatorData.attackRange) {
            if (predatorData.attackTimer <= 0f) {
                attackPrey(predator, prey);
                predatorData.attackTimer = predatorData.attackCooldown;
            }
            stop(predator);
            return;
        }

        GenomeComponent dna = Mappers.genome.get(predator);
        float speed = Traits.speed(dna) * predatorData.huntSpeedMultiplier * Traits.pursuitSpeedFactor(dna);
        moveToward(predator, preyPos.position, speed, deltaTime);
    }

    /** Walk to where prey was last smelled; on arrival, pick up the next scent or give up. */
    private void followScent(Entity predator, float deltaTime) {
        PredatorComponent predatorData = Mappers.predator.get(predator);
        PositionComponent predatorPos = Mappers.position.get(predator);

        if (predatorPos.position.dst(predatorData.scentTarget) <= predatorData.scentReachThreshold) {
            stop(predator);
            if (findNearestVisiblePrey(predator) != null) {
                return; // Prey in sight: next step's decision switches to hunting
            }
            Entity nextSmell = findNearestSmelledPrey(predator);
            if (nextSmell != null) {
                rememberScentTarget(predatorData, Mappers.position.get(nextSmell));
            } else {
                clearScentTarget(predatorData);
            }
            return;
        }

        float speed = Traits.speed(Mappers.genome.get(predator)) * predatorData.scentSpeedMultiplier;
        boolean hitBoundary = moveToward(predator, predatorData.scentTarget, speed, deltaTime);
        if (hitBoundary) {
            stop(predator);
            clearScentTarget(predatorData);
        }
    }

    private void attackPrey(Entity predator, Entity prey) {
        StatsComponent predatorStats = Mappers.stats.get(predator);
        PredatorComponent predatorData = Mappers.predator.get(predator);
        StatsComponent preyStats = Mappers.stats.get(prey);

        float damage = Traits.biteDamage(predatorData.attackDamage, Mappers.genome.get(predator), Mappers.genome.get(prey));
        preyStats.health = Math.max(0f, preyStats.health - damage);
        if (preyStats.health <= 0f) {
            predatorStats.energy = Math.min(100f, predatorStats.energy + predatorData.killEnergyGain);
            predatorStats.hunger = Math.min(100f, predatorStats.hunger + predatorData.killEnergyGain);
            clearScentTarget(predatorData);
            predatorData.eatingTimer = config.predatorEatingSeconds;
            CarcassSystem.leaveCarcass(getEngine(), prey, Balance.KILL_CARCASS_FOOD); // Leftovers for scavengers
            getEngine().removeEntity(prey);
        }
    }

    private Entity findNearestVisiblePrey(Entity predator) {
        PositionComponent predatorPos = Mappers.position.get(predator);
        VisionComponent vision = Mappers.vision.get(predator);

        Entity nearestPrey = null;
        float nearestDistance = Float.MAX_VALUE;
        for (Entity candidate : preyEntities) {
            PositionComponent candidatePos = Mappers.position.get(candidate);
            if (canSee(predatorPos, vision, candidate)) {
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
        PositionComponent predatorPos = Mappers.position.get(predator);
        PredatorComponent predatorData = Mappers.predator.get(predator);

        Entity nearestPrey = null;
        float nearestDistance = Float.MAX_VALUE;
        for (Entity candidate : preyEntities) {
            float distance = predatorPos.position.dst(Mappers.position.get(candidate).position);
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
