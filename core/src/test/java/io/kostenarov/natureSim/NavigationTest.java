package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.FoodSourceComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Components.WaterSourceComponent;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.CollisionSystem;
import io.kostenarov.natureSim.Systems.PreySystem;
import io.kostenarov.natureSim.Systems.SpatialIndexSystem;
import io.kostenarov.natureSim.Systems.WaterSourceSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Creatures getting where they're going: around ponds, crowds and standing creatures, without getting stuck. */
class NavigationTest extends SimTestSupport {

    private static Entity pond(Engine e, float x, float y, float radius) {
        Entity pond = e.createEntity();
        PositionComponent pos = new PositionComponent();
        pos.position.set(x, y);
        pond.add(pos);
        pond.add(new WaterSourceComponent(radius));
        e.addEntity(pond);
        return pond;
    }

    private static Entity food(Engine e, float x, float y) {
        Entity food = e.createEntity();
        PositionComponent pos = new PositionComponent();
        pos.position.set(x, y);
        food.add(pos);
        food.add(new FoodSourceComponent());
        e.addEntity(food);
        return food;
    }

    /** A creature that has run out of energy, so it stands still (rests) for the length of a test. */
    private static Entity standingCreature(Engine e, float x, float y) {
        Entity creature = creature(e, x, y, Gender.MALE, false);
        stats(creature).energy = 0f;
        return creature;
    }

    @Test
    @DisplayName("a hungry prey looking around a waterhole keeps moving instead of twitching at its edge")
    void roamingNearWaterDoesNotStall() {
        Engine e = new Engine();
        e.addSystem(new PreySystem());
        Entity pond = pond(e, 1000, 1000, 150);
        // Hungry, no food anywhere, standing right at the edge of the waterhole range (the old flip-flop line)
        Entity prey = creature(e, 1000 + 150 + Balance.WATERHOLE_RANGE - 16, 984, Gender.MALE, false);
        stats(prey).hunger = 10f;

        run(e, 2f);
        Vector2 before = new Vector2(Mappers.position.get(prey).position);
        run(e, 3f);
        float moved = before.dst(Mappers.position.get(prey).position);
        assertTrue(moved > 50f, "moved only " + moved + " px in 3 s");
        assertTrue(WaterSourceSystem.distanceFromShore(pond, Mappers.position.get(prey)) <= Balance.WATERHOLE_RANGE + 50f,
            "stayed around the waterhole");
    }

    @Test
    @DisplayName("a walker goes around a creature standing in its way, without shoving it")
    void walksAroundStandingCreature() {
        Engine e = new Engine();
        e.addSystem(new SpatialIndexSystem());
        e.addSystem(new PreySystem());
        e.addSystem(new CollisionSystem());
        Entity walker = creature(e, 1000, 1000, Gender.MALE, false);
        walker.getComponent(VisionComponent.class).directionAngle = 0f;
        stats(walker).hunger = 20f;
        Entity blocker = standingCreature(e, 1060, 1000); // Directly in the path
        Entity snack = food(e, 1180, 1008);

        run(e, 3f);
        assertTrue(snack.getComponent(FoodSourceComponent.class).consumed, "got around and ate");
        assertEquals(1060f, Mappers.position.get(blocker).position.x, 0.001f, "the standing creature wasn't pushed");
        assertEquals(1000f, Mappers.position.get(blocker).position.y, 0.001f, "the standing creature wasn't pushed");
    }

    @Test
    @DisplayName("a prey gives up on food it can't reach through a crowd and looks elsewhere")
    void givesUpOnBlockedFood() {
        Engine e = new Engine();
        e.addSystem(new SpatialIndexSystem());
        e.addSystem(new PreySystem());
        e.addSystem(new CollisionSystem());
        Entity snack = food(e, 1500, 1500);
        for (int i = 0; i < 10; i++) { // A tight ring of resting creatures around the food
            float angle = i * 36f;
            standingCreature(e, 1500 + 40 * (float) Math.cos(Math.toRadians(angle)), 1500 + 40 * (float) Math.sin(Math.toRadians(angle)));
        }
        Entity prey = creature(e, 1350, 1500, Gender.MALE, false);
        prey.getComponent(VisionComponent.class).directionAngle = 0f;
        stats(prey).hunger = 20f;

        run(e, 4f);
        BehaviourComponent behaviour = prey.getComponent(BehaviourComponent.class);
        assertFalse(snack.getComponent(FoodSourceComponent.class).consumed, "the food really was out of reach");
        assertEquals(snack, behaviour.ignoredFood, "gave up on it");
    }

    @Test
    @DisplayName("panic caught from a herd-mate doesn't flicker on and off")
    void caughtPanicIsSteady() {
        Engine e = new Engine();
        e.addSystem(new PreySystem());
        Entity watcher = creature(e, 1000, 1000, Gender.MALE, false);
        watcher.getComponent(VisionComponent.class).directionAngle = 0f;
        Entity neighbour = creature(e, 940, 1000, Gender.FEMALE, false);
        neighbour.getComponent(VisionComponent.class).directionAngle = 180f;
        creature(e, 1150, 1000, Gender.MALE, true);

        int changes = 0;
        EntityBehaviour previous = null;
        for (int frame = 0; frame < 120; frame++) {
            e.update(DT);
            EntityBehaviour now = neighbour.getComponent(BehaviourComponent.class).behaviour;
            if (previous != null && now != previous) changes++;
            previous = now;
        }
        assertTrue(changes <= 2, "neighbour's behaviour changed " + changes + " times in 2 s");
    }
}
