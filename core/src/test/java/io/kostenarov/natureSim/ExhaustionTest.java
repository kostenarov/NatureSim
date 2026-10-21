package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.FoodSourceComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.CreatureSystem;
import io.kostenarov.natureSim.Systems.MetabolismSystem;
import io.kostenarov.natureSim.Systems.PredatorSystem;
import io.kostenarov.natureSim.Systems.PreySystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Running out of energy forces a rest, whatever else the creature needs, until it has recovered. */
class ExhaustionTest extends SimTestSupport {

    private static EntityBehaviour behaviour(Entity creature) {
        return creature.getComponent(BehaviourComponent.class).behaviour;
    }

    private static void face(Entity creature, float degrees) {
        creature.getComponent(VisionComponent.class).directionAngle = degrees;
        creature.getComponent(BehaviourComponent.class).directionAngle = degrees;
    }

    @Test
    @DisplayName("an exhausted prey rests, even when hungry with food in sight, until it reaches the recovery threshold")
    void exhaustedPreyRestsUntilRecovered() {
        Engine e = new Engine();
        e.addSystem(new PreySystem());
        e.addSystem(new MetabolismSystem());
        Entity prey = creature(e, 1000, 1000, Gender.MALE, false);
        face(prey, 0f);
        stats(prey).hunger = 20f;
        stats(prey).energy = 0f;
        Entity food = e.createEntity();
        PositionComponent foodPos = new PositionComponent();
        foodPos.position.set(1120, 1000);
        food.add(foodPos);
        food.add(new FoodSourceComponent());
        e.addEntity(food);

        float startX = prey.getComponent(PositionComponent.class).position.x;
        float energyWhenItGotUp = -1f;
        for (int frame = 0; frame < 60 * 10 && energyWhenItGotUp < 0f; frame++) {
            float energyBeforeStep = stats(prey).energy; // What the decision saw (the step then spends some)
            e.update(DT);
            if (behaviour(prey) == EntityBehaviour.IDLE) {
                assertEquals(startX, prey.getComponent(PositionComponent.class).position.x, 0.0001f, "doesn't move while resting");
            } else {
                energyWhenItGotUp = energyBeforeStep;
            }
        }

        assertTrue(energyWhenItGotUp >= Balance.EXHAUSTION_RECOVERY_THRESHOLD,
            "got up at energy " + energyWhenItGotUp + ", not before " + Balance.EXHAUSTION_RECOVERY_THRESHOLD);
        assertEquals(EntityBehaviour.SEEKING_FOOD, behaviour(prey), "then went for the food");
        run(e, 2f);
        assertTrue(food.getComponent(FoodSourceComponent.class).consumed, "and ate it");
    }

    @Test
    @DisplayName("an exhausted predator ignores prey right in front of it")
    void exhaustedPredatorDoesntHunt() {
        Engine e = new Engine();
        e.addSystem(new PredatorSystem());
        e.addSystem(new MetabolismSystem());
        Entity predator = creature(e, 1000, 1000, Gender.MALE, true);
        face(predator, 0f);
        stats(predator).hunger = 20f;
        stats(predator).energy = 0f;
        creature(e, 1100, 1000, Gender.FEMALE, false);

        run(e, 1f);
        assertEquals(EntityBehaviour.IDLE, behaviour(predator), "resting, not hunting");
        assertEquals(2, creatureCount(e), "prey untouched");
    }

    @Test
    @DisplayName("low energy alone doesn't force a rest; only running out does")
    void lowEnergyStillActs() {
        Engine e = new Engine();
        e.addSystem(new PreySystem());
        Entity prey = creature(e, 1000, 1000, Gender.MALE, false);
        stats(prey).hunger = 20f;
        stats(prey).energy = 10f;

        e.update(DT);
        assertEquals(EntityBehaviour.SEEKING_FOOD, behaviour(prey), "still looks for food at 10 energy");
    }
}
