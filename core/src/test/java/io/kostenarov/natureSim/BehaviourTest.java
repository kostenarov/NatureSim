package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.FoodSourceComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.PredatorComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.MatingSystem;
import io.kostenarov.natureSim.Systems.PredatorSystem;
import io.kostenarov.natureSim.Systems.PreySystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Foraging by sight, courtship and fleeing. */
class BehaviourTest extends SimTestSupport {

    private static Entity food(Engine e, float x, float y) {
        Entity food = e.createEntity();
        PositionComponent pos = new PositionComponent();
        pos.position.set(x, y);
        food.add(pos);
        food.add(new FoodSourceComponent());
        e.addEntity(food);
        return food;
    }

    private static void face(Entity creature, float degrees) {
        creature.getComponent(VisionComponent.class).directionAngle = degrees;
        creature.getComponent(BehaviourComponent.class).directionAngle = degrees;
    }

    @Test
    @DisplayName("hungry prey eat food they can see")
    void eatsVisibleFood() {
        Engine e = new Engine();
        e.addSystem(new PreySystem());
        Entity prey = creature(e, 1000, 1000, Gender.MALE, false);
        face(prey, 0f);
        stats(prey).hunger = 20f;
        Entity snack = food(e, 1120, 1000); // 120 px ahead, inside the 200 px vision cone

        run(e, 2f);
        assertTrue(snack.getComponent(FoodSourceComponent.class).consumed, "food in sight was eaten");
    }

    @Test
    @DisplayName("prey don't know about food they can't see")
    void ignoresFoodOutOfSight() {
        Engine e = new Engine();
        e.addSystem(new PreySystem());
        Entity prey = creature(e, 1000, 1000, Gender.MALE, false);
        stats(prey).hunger = 20f;
        // 1500 px away: walking straight there at 250 px/s would take 6 s, so it can't be reached by chance in 5 s
        Entity farFood = food(e, 2500, 1000);

        run(e, 5f);
        assertFalse(farFood.getComponent(FoodSourceComponent.class).consumed, "food far out of sight not found");
    }

    @Test
    @DisplayName("prey ready to mate walk up to a partner they can see")
    void preyCourtship() {
        Engine e = new Engine();
        e.addSystem(new PreySystem());
        e.addSystem(new MatingSystem());
        Entity male = creature(e, 1000, 1000, Gender.MALE, false);
        Entity female = creature(e, 1150, 1000, Gender.FEMALE, false); // 150 px apart, beyond mating distance
        face(male, 0f);
        face(female, 180f);

        e.update(DT);
        assertEquals(EntityBehaviour.SEEKING_MATE, male.getComponent(BehaviourComponent.class).behaviour);
        run(e, 3f);
        assertEquals(3, creatureCount(e), "they met and had offspring");
    }

    @Test
    @DisplayName("predators find a mate by smell, even out of sight")
    void predatorCourtshipBySmell() {
        Engine e = new Engine();
        e.addSystem(new PredatorSystem());
        e.addSystem(new MatingSystem());
        Entity male = creature(e, 1000, 1000, Gender.MALE, true);
        Entity female = creature(e, 1900, 1000, Gender.FEMALE, true); // 900 px apart, facing away from each other
        face(male, 180f);
        face(female, 0f);

        run(e, 8f);
        long predators = 0;
        for (Entity entity : e.getEntities()) {
            if (entity.getComponent(PredatorComponent.class) != null) predators++;
        }
        assertEquals(3, predators, "the predators found each other and bred");
    }

    @Test
    @DisplayName("prey pinned against a wall run along it instead of into it")
    void fleeAlongWall() {
        Engine e = new Engine();
        e.addSystem(new PreySystem());
        Entity prey = creature(e, 10, 2000, Gender.MALE, false);
        face(prey, 0f); // Looking east, at the predator
        creature(e, 150, 2000, Gender.MALE, true); // Predator to the east; nothing moves it here

        run(e, 1f);
        PositionComponent pos = prey.getComponent(PositionComponent.class);
        assertTrue(Math.abs(pos.position.y - 2000) > 100f, "slid along the wall, y moved " + (pos.position.y - 2000));
        assertTrue(pos.position.x < 150f, "didn't run back toward the predator, x " + pos.position.x);
    }

    @Test
    @DisplayName("fleeing prey keep running instead of twitching between fleeing and other behaviours")
    void fleeingIsSteady() {
        Engine e = new Engine();
        e.addSystem(new PreySystem());
        Entity prey = creature(e, 1000, 1000, Gender.MALE, false);
        face(prey, 0f);
        stats(prey).hunger = 20f;                  // Also hungry, so it has a reason to turn back
        food(e, 1100, 1000);                       // ...toward food next to the predator
        creature(e, 1150, 1000, Gender.MALE, true); // Predator in sight to the east; nothing moves it here

        int changes = 0;
        EntityBehaviour previous = null;
        for (int frame = 0; frame < 60; frame++) {
            e.update(DT);
            EntityBehaviour now = prey.getComponent(BehaviourComponent.class).behaviour;
            if (previous != null && now != previous) changes++;
            previous = now;
        }
        assertEquals(EntityBehaviour.FLEEING, previous, "still fleeing after a second");
        assertEquals(0, changes, "behaviour changes while fleeing");
    }
}
