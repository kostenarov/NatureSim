package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.kostenarov.natureSim.Components.*;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Traits;
import io.kostenarov.natureSim.World;

/**
 * Prey behaviour: flee from visible predators, otherwise drink, eat what they can see, court a visible
 * partner, rest or wander.
 */
public class PreySystem extends CreatureSystem {
    private static final float FOOD_CONSUMPTION_DISTANCE = 32f; // How close to be to eat food
    private static final float ESCAPE_SPEED_MULTIPLIER = 1.25f;
    private static final float WALL_AVOID_DISTANCE = 150f;      // Fleeing prey steer away from map edges this close

    private ImmutableArray<Entity> foodEntities;
    private ImmutableArray<Entity> predatorEntities;

    public PreySystem() {
        super(Family.all(PositionComponent.class, VelocityComponent.class, GenomeComponent.class, VisionComponent.class,
            GenderComponent.class, StatsComponent.class, BehaviourComponent.class).exclude(PredatorComponent.class).get());
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        foodEntities = engine.getEntitiesFor(Family.all(FoodSourceComponent.class, PositionComponent.class).get());
        predatorEntities = engine.getEntitiesFor(Family.all(PositionComponent.class, PredatorComponent.class).get());
    }

    @Override
    protected EntityBehaviour decideBehaviour(Entity entity) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);
        GenomeComponent dna = entity.getComponent(GenomeComponent.class);

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
        PositionComponent pos = entity.getComponent(PositionComponent.class);

        Entity food = findNearestVisibleFood(entity);
        if (food == null) {
            roamNearWater(entity, deltaTime);
            return;
        }

        PositionComponent foodPos = food.getComponent(PositionComponent.class);
        if (pos.position.dst(foodPos.position) < FOOD_CONSUMPTION_DISTANCE) {
            StatsComponent stats = entity.getComponent(StatsComponent.class);
            FoodSourceComponent foodComponent = food.getComponent(FoodSourceComponent.class);
            stats.hunger = Math.min(100f, stats.hunger + foodComponent.nutritionValue);
            foodComponent.consumed = true;
            stop(entity);
        } else {
            moveToward(entity, foodPos.position, Traits.speed(entity.getComponent(GenomeComponent.class)), deltaTime);
        }
    }

    /** Nearest uneaten food inside the vision cone, or null. Food right underfoot always counts. */
    private Entity findNearestVisibleFood(Entity entity) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        VisionComponent vision = entity.getComponent(VisionComponent.class);

        Iterable<Entity> candidates = foodEntities;
        SpatialIndexSystem index = getEngine().getSystem(SpatialIndexSystem.class);
        if (index != null) {
            index.food().query(pos.position.x, pos.position.y, vision.visionRange, nearby);
            candidates = nearby;
        }

        Entity nearestFood = null;
        float nearestDistance = Float.MAX_VALUE;
        for (Entity food : candidates) {
            if (food.getComponent(FoodSourceComponent.class).consumed) {
                continue; // Already eaten this frame, removed next frame
            }
            PositionComponent foodPos = food.getComponent(PositionComponent.class);
            float distance = pos.position.dst(foodPos.position);
            boolean visible = distance < FOOD_CONSUMPTION_DISTANCE || isInVisionCone(pos, vision, foodPos, vision.visionRange);
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
        BehaviourComponent behaviour = entity.getComponent(BehaviourComponent.class);
        behaviour.fleeTimer = Math.max(0f, behaviour.fleeTimer - deltaTime);
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
        PositionComponent preyPos = prey.getComponent(PositionComponent.class);
        VisionComponent vision = prey.getComponent(VisionComponent.class);
        BehaviourComponent behaviour = prey.getComponent(BehaviourComponent.class);

        float awayX = 0f;
        float awayY = 0f;
        float nearestDistanceSquared = Float.MAX_VALUE;
        for (Entity predator : predatorEntities) {
            if (!canSee(preyPos, vision, predator)) {
                continue;
            }
            PositionComponent predatorPos = predator.getComponent(PositionComponent.class);
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
            behaviour.fleeTimer = BehaviourComponent.FLEE_MEMORY;
        } else if (behaviour.fleeTimer > 0f) {
            awayX = preyPos.position.x - behaviour.lastThreat.x; // Out of sight, but not forgotten
            awayY = preyPos.position.y - behaviour.lastThreat.y;
        } else {
            return false;
        }

        // Near a wall, don't run into it: drop the part of the escape that points at the wall and slide along it
        float x = preyPos.position.x;
        float y = preyPos.position.y;
        float maxX = World.WIDTH - World.CREATURE_SIZE;
        float maxY = World.HEIGHT - World.CREATURE_SIZE;
        boolean blockedX = (x < WALL_AVOID_DISTANCE && awayX < 0f) || (maxX - x < WALL_AVOID_DISTANCE && awayX > 0f);
        boolean blockedY = (y < WALL_AVOID_DISTANCE && awayY < 0f) || (maxY - y < WALL_AVOID_DISTANCE && awayY > 0f);
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

    private void flee(Entity prey, float deltaTime) {
        if (!fleeDirection(prey)) {
            stop(prey);
            return;
        }
        GenomeComponent dna = prey.getComponent(GenomeComponent.class);
        float speed = Traits.speed(dna) * ESCAPE_SPEED_MULTIPLIER * Traits.pursuitSpeedFactor(dna);
        float directionX = tmp.x;
        float directionY = tmp.y;
        moveInDirection(prey, directionX, directionY, speed, deltaTime);
    }
}
