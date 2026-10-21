package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import io.kostenarov.natureSim.Balance;
import io.kostenarov.natureSim.Components.*;
import io.kostenarov.natureSim.Creatures;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Enums.Role;
import io.kostenarov.natureSim.Mappers;
import io.kostenarov.natureSim.SimulationConfig;
import io.kostenarov.natureSim.Traits;
import io.kostenarov.natureSim.World;

/**
 * Prey behaviour: flee from visible predators, otherwise drink, eat what they can see, court a visible
 * partner, rest or wander.
 */
public class PreySystem extends CreatureSystem {

    private final boolean herding;
    private final Vector2 herdPull = new Vector2();
    private ImmutableArray<Entity> foodEntities;
    private ImmutableArray<Entity> predatorEntities;

    public PreySystem() {
        this(new SimulationConfig());
    }

    public PreySystem(SimulationConfig config) {
        super(Creatures.PREY);
        this.herding = config.herdingEnabled;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        foodEntities = engine.getEntitiesFor(Family.all(FoodSourceComponent.class, PositionComponent.class).get());
        predatorEntities = engine.getEntitiesFor(Family.all(PositionComponent.class, PredatorComponent.class).get());
    }

    @Override
    protected EntityBehaviour decideBehaviour(Entity entity) {
        StatsComponent stats = Mappers.stats.get(entity);
        BehaviourComponent behaviour = Mappers.behaviour.get(entity);
        GenomeComponent dna = Mappers.genome.get(entity);

        if (fleeDirection(entity)) {
            return EntityBehaviour.FLEEING;
        }

        // How early a creature reacts to hunger and thirst is genetic (see Traits).
        boolean hungry = stats.hunger < Traits.foodSeekThreshold(dna);
        if (wantsWater(stats, behaviour, dna, hungry)) {
            return EntityBehaviour.SEEKING_WATER;
        }
        if (hungry) {
            return EntityBehaviour.SEEKING_FOOD;
        }

        // (Running out of energy is handled before any decision: see CreatureSystem.)
        // Adults ready to breed go courting (toward a partner in sight, or to a waterhole to find one)
        if (MatingSystem.isReadyToMate(entity)) {
            return EntityBehaviour.SEEKING_MATE;
        }
        // Nothing to do: rest up rather than burn energy wandering, then wander once fresh
        return restOrWander(stats, behaviour);
    }

    @Override
    protected void executeBehaviour(Entity entity, EntityBehaviour behaviour, float deltaTime) {
        switch (behaviour) {
            case FLEEING:
                flee(entity, deltaTime);
                break;
            case SEEKING_FOOD:
                seekFood(entity, deltaTime);
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
                // Resting: hard stop so there is no residual drift.
                stop(entity);
                break;
        }
    }

    // --- Food -------------------------------------------------------------------------------------------

    /** Go for the nearest food in sight; with none in sight, look around the waterholes, where most food grows. */
    private void seekFood(Entity entity, float deltaTime) {
        PositionComponent pos = Mappers.position.get(entity);

        Entity food = findNearestVisibleFood(entity);
        if (food == null) {
            roamNearWater(entity, deltaTime);
            return;
        }

        PositionComponent foodPos = Mappers.position.get(food);
        if (pos.position.dst(foodPos.position) < Balance.FOOD_CONSUMPTION_DISTANCE) {
            StatsComponent stats = Mappers.stats.get(entity);
            FoodSourceComponent foodComponent = Mappers.foodSource.get(food);
            stats.hunger = Math.min(100f, stats.hunger + foodComponent.nutritionValue);
            foodComponent.consumed = true;
            stop(entity);
        } else {
            moveToward(entity, foodPos.position, Traits.speed(Mappers.genome.get(entity)), deltaTime);
            BehaviourComponent behaviour = Mappers.behaviour.get(entity);
            if (isMakingNoProgress(behaviour, pos.position, deltaTime)) {
                // Others are crowding this food: leave it to them for a while and look for another
                behaviour.ignoredFood = food;
                behaviour.ignoreFoodTimer = Balance.IGNORE_BLOCKED_FOOD_SECONDS;
            }
        }
    }

    /** Nearest uneaten food inside the vision cone, or null. Food right underfoot always counts. */
    private Entity findNearestVisibleFood(Entity entity) {
        PositionComponent pos = Mappers.position.get(entity);
        VisionComponent vision = Mappers.vision.get(entity);

        Iterable<Entity> candidates = foodEntities;
        SpatialIndexSystem index = getEngine().getSystem(SpatialIndexSystem.class);
        if (index != null) {
            index.food().query(pos.position.x, pos.position.y, sightRange(vision), nearby);
            candidates = nearby;
        }

        BehaviourComponent behaviour = Mappers.behaviour.get(entity);
        Entity ignored = behaviour.ignoreFoodTimer > 0f ? behaviour.ignoredFood : null;
        Entity nearestFood = null;
        float nearestDistance = Float.MAX_VALUE;
        for (Entity food : candidates) {
            // Skip food eaten this frame (removed next frame) and food a crowd kept us from
            if (Mappers.foodSource.get(food).consumed || food == ignored) {
                continue;
            }
            PositionComponent foodPos = Mappers.position.get(food);
            float distance = pos.position.dst(foodPos.position);
            boolean visible = distance < Balance.FOOD_CONSUMPTION_DISTANCE || isInVisionCone(pos, vision, foodPos, sightRange(vision));
            if (visible && distance < nearestDistance) {
                nearestDistance = distance;
                nearestFood = food;
            }
        }
        return nearestFood;
    }

    // --- Predators --------------------------------------------------------------------------------------

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        BehaviourComponent behaviour = Mappers.behaviour.get(entity);
        behaviour.fleeTimer = Math.max(0f, behaviour.fleeTimer - deltaTime);
        behaviour.ignoreFoodTimer = Math.max(0f, behaviour.ignoreFoodTimer - deltaTime);
        super.processEntity(entity, deltaTime);
    }

    /**
     * Works out the escape direction into {@code tmp}: away from every visible predator (closer ones count
     * more) and along nearby map edges, so prey don't get pinned against a wall. A prey that has just lost
     * sight of a predator keeps running from where it last saw it; otherwise turning away to flee would hide
     * the predator, the prey would turn back, see it again, and twitch on the spot.
     * Returns false if there is no threat.
     */
    private boolean fleeDirection(Entity prey) {
        PositionComponent preyPos = Mappers.position.get(prey);
        VisionComponent vision = Mappers.vision.get(prey);
        BehaviourComponent behaviour = Mappers.behaviour.get(prey);

        float awayX = 0f;
        float awayY = 0f;
        float nearestDistanceSquared = Float.MAX_VALUE;
        for (Entity predator : predatorEntities) {
            if (!canSee(preyPos, vision, predator)) {
                continue;
            }
            PositionComponent predatorPos = Mappers.position.get(predator);
            float dx = preyPos.position.x - predatorPos.position.x;
            float dy = preyPos.position.y - predatorPos.position.y;
            float distanceSquared = Math.max(1f, dx * dx + dy * dy);
            // Unit vector away from the predator, weighted by 1/distance
            awayX += dx / distanceSquared;
            awayY += dy / distanceSquared;
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                behaviour.lastThreat.set(predatorPos.position);
            }
        }

        if (nearestDistanceSquared < Float.MAX_VALUE) {
            behaviour.fleeTimer = Balance.FLEE_MEMORY;
        } else if (behaviour.fleeTimer > 0f || (herding && catchAlarm(prey, behaviour))) {
            awayX = preyPos.position.x - behaviour.lastThreat.x; // Out of sight, but not forgotten
            awayY = preyPos.position.y - behaviour.lastThreat.y;
        } else {
            return false;
        }

        if (herding) {
            // Run with the herd: lean toward other fleeing prey nearby, so the group sticks together
            float length = (float) Math.sqrt(awayX * awayX + awayY * awayY);
            if (length > 0f && herdCenter(prey, preyPos)) {
                awayX = awayX / length + herdPull.x * Balance.HERD_COHESION;
                awayY = awayY / length + herdPull.y * Balance.HERD_COHESION;
            }
        }

        // Near a wall, don't run into it: drop the part of the escape that points at the wall and slide along it
        float x = preyPos.position.x;
        float y = preyPos.position.y;
        float maxX = World.WIDTH - World.CREATURE_SIZE;
        float maxY = World.HEIGHT - World.CREATURE_SIZE;
        boolean blockedX = (x < Balance.WALL_AVOID_DISTANCE && awayX < 0f) || (maxX - x < Balance.WALL_AVOID_DISTANCE && awayX > 0f);
        boolean blockedY = (y < Balance.WALL_AVOID_DISTANCE && awayY < 0f) || (maxY - y < Balance.WALL_AVOID_DISTANCE && awayY > 0f);
        if (blockedX) {
            awayX = 0f;
        }
        if (blockedY) {
            awayY = 0f;
        }

        tmp.set(awayX, awayY);
        if (tmp.isZero(0.000001f)) {
            // Pinned head-on against a wall: run along it, toward the middle of that wall
            if (blockedX && !blockedY) {
                tmp.set(0f, y < World.HEIGHT / 2f ? 1f : -1f);
            } else if (blockedY && !blockedX) {
                tmp.set(x < World.WIDTH / 2f ? 1f : -1f, 0f);
            } else {
                tmp.set(World.WIDTH / 2f - x, World.HEIGHT / 2f - y); // Cornered: head for open ground
                if (tmp.isZero()) {
                    tmp.set(1f, 0f);
                }
            }
        }
        tmp.nor();
        return true;
    }

    // --- Herding ----------------------------------------------------------------------------------------

    /** Nearby prey (within herding range), from the spatial grid when available. */
    private Iterable<Entity> nearbyPrey(PositionComponent pos) {
        SpatialIndexSystem index = getEngine().getSystem(SpatialIndexSystem.class);
        if (index == null) {
            return getEntities();
        }
        index.creatures().query(pos.position.x, pos.position.y, Balance.HERD_RADIUS, nearby);
        return nearby;
    }

    private boolean isNearbyPrey(Entity self, Entity other, PositionComponent pos) {
        return other != self && Creatures.role(other) == Role.PREY
            && Mappers.position.get(other).position.dst2(pos.position) <= Balance.HERD_RADIUS * Balance.HERD_RADIUS;
    }

    /**
     * Panic spreads: a prey that sees a herd-mate fleeing flees too, from the threat that herd-mate saw.
     * Each hop passes on a slightly shorter memory, so a scare dies out once nobody can see the predator.
     */
    private boolean catchAlarm(Entity prey, BehaviourComponent behaviour) {
        PositionComponent pos = Mappers.position.get(prey);
        BehaviourComponent freshest = null;
        for (Entity other : nearbyPrey(pos)) {
            if (!isNearbyPrey(prey, other, pos)) {
                continue;
            }
            BehaviourComponent otherBehaviour = Mappers.behaviour.get(other);
            // Only a fresh scare spreads, and it always lasts a while; passing on a few milliseconds of fear made
            // prey flicker between fleeing and not
            if (otherBehaviour.fleeTimer > Balance.ALARM_DECAY + Balance.MIN_ALARM && (freshest == null || otherBehaviour.fleeTimer > freshest.fleeTimer)) {
                freshest = otherBehaviour;
            }
        }
        if (freshest == null) {
            return false;
        }
        behaviour.lastThreat.set(freshest.lastThreat);
        behaviour.fleeTimer = freshest.fleeTimer - Balance.ALARM_DECAY;
        return true;
    }

    /** Direction toward the middle of the other fleeing prey nearby, into {@code herdPull}. False if alone. */
    private boolean herdCenter(Entity prey, PositionComponent pos) {
        float sumX = 0f;
        float sumY = 0f;
        int count = 0;
        for (Entity other : nearbyPrey(pos)) {
            if (isNearbyPrey(prey, other, pos) && Mappers.behaviour.get(other).fleeTimer > 0f) {
                Vector2 otherPos = Mappers.position.get(other).position;
                sumX += otherPos.x;
                sumY += otherPos.y;
                count++;
            }
        }
        if (count == 0) {
            return false;
        }
        herdPull.set(sumX / count - pos.position.x, sumY / count - pos.position.y);
        if (herdPull.isZero(0.001f)) {
            return false;
        }
        herdPull.nor();
        return true;
    }

    private void flee(Entity prey, float deltaTime) {
        if (!fleeDirection(prey)) {
            stop(prey);
            return;
        }
        GenomeComponent dna = Mappers.genome.get(prey);
        float speed = Traits.speed(dna) * Balance.ESCAPE_SPEED_MULTIPLIER * Traits.pursuitSpeedFactor(dna);
        float directionX = tmp.x;
        float directionY = tmp.y;
        moveInDirection(prey, directionX, directionY, speed, deltaTime);
    }
}
