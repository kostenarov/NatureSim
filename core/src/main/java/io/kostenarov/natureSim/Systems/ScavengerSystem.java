package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import io.kostenarov.natureSim.Balance;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.CarcassComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Creatures;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Mappers;
import io.kostenarov.natureSim.Traits;

/**
 * Scavenger behaviour: live on carcasses, found by smell. When hungry with nothing to smell, trail a
 * predator at a distance, since predators lead to fresh kills. Nobody hunts scavengers.
 */
public class ScavengerSystem extends CreatureSystem {
    private ImmutableArray<Entity> carcasses;
    private ImmutableArray<Entity> predators;

    public ScavengerSystem() {
        super(Creatures.SCAVENGERS);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        carcasses = engine.getEntitiesFor(CarcassSystem.CARCASSES);
        predators = engine.getEntitiesFor(Creatures.PREDATORS);
    }

    @Override
    protected EntityBehaviour decideBehaviour(Entity entity) {
        StatsComponent stats = Mappers.stats.get(entity);
        BehaviourComponent behaviour = Mappers.behaviour.get(entity);
        GenomeComponent dna = Mappers.genome.get(entity);

        boolean hungry = stats.hunger < Traits.foodSeekThreshold(dna);
        if (wantsWater(stats, behaviour, dna, hungry)) {
            return EntityBehaviour.SEEKING_WATER;
        }
        if (hungry) {
            return EntityBehaviour.SEEKING_FOOD;
        }
        if (MatingSystem.isReadyToMate(entity)) {
            return EntityBehaviour.SEEKING_MATE;
        }
        return restOrWander(stats, behaviour);
    }

    @Override
    protected void executeBehaviour(Entity entity, EntityBehaviour behaviour, float deltaTime) {
        switch (behaviour) {
            case SEEKING_FOOD:
                scavenge(entity, deltaTime);
                break;
            case SEEKING_WATER:
                seekWater(entity, deltaTime);
                break;
            case SEEKING_MATE:
                approachPartner(entity, findVisiblePartner(entity), deltaTime);
                break;
            case EXPLORING:
                explore(entity, deltaTime);
                break;
            default:
                stop(entity);
                break;
        }
    }

    @Override
    protected float mateScentRange(Entity entity) {
        return Mappers.scavenger.get(entity).scentRange * Balance.MATE_SCENT_FACTOR;
    }

    /** Eat the nearest carcass in smelling range; failing that, trail a predator; failing that, try the waterholes. */
    private void scavenge(Entity scavenger, float deltaTime) {
        Vector2 pos = Mappers.position.get(scavenger).position;
        float speed = Traits.speed(Mappers.genome.get(scavenger));

        Entity carcass = nearestWithin(carcasses, pos, Mappers.scavenger.get(scavenger).scentRange);
        if (carcass != null) {
            Vector2 carcassPos = Mappers.position.get(carcass).position;
            if (pos.dst(carcassPos) < Balance.FOOD_CONSUMPTION_DISTANCE) {
                stop(scavenger);
                eat(Mappers.stats.get(scavenger), Mappers.genome.get(scavenger), Mappers.carcass.get(carcass), deltaTime);
            } else {
                moveToward(scavenger, carcassPos, speed, deltaTime);
            }
            return;
        }

        // Predators make carcasses: keep one in range, but not too close
        Entity predator = nearestWithin(predators, pos, Mappers.scavenger.get(scavenger).scentRange * 2f);
        if (predator != null) {
            Vector2 predatorPos = Mappers.position.get(predator).position;
            if (pos.dst(predatorPos) > Balance.SCAVENGER_FOLLOW_DISTANCE) {
                moveToward(scavenger, predatorPos, speed, deltaTime);
            } else {
                stop(scavenger);
            }
            return;
        }

        roamNearWater(scavenger, deltaTime);
    }

    private static void eat(StatsComponent stats, GenomeComponent dna, CarcassComponent carcass, float deltaTime) {
        float bite = Math.min(Traits.scavengerBiteRate(dna) * deltaTime, Math.min(carcass.food, 100f - stats.hunger));
        stats.hunger += bite;
        carcass.food -= bite; // CarcassSystem removes it once it's gone
    }

    private static Entity nearestWithin(ImmutableArray<Entity> candidates, Vector2 pos, float range) {
        Entity nearest = null;
        float nearestDistanceSquared = range * range;
        for (Entity candidate : candidates) {
            float distanceSquared = pos.dst2(Mappers.position.get(candidate).position);
            if (distanceSquared <= nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = candidate;
            }
        }
        return nearest;
    }
}
