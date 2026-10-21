package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.FoodSourceComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.ClockSystem;
import io.kostenarov.natureSim.Systems.FoodSourceSystem;
import io.kostenarov.natureSim.Systems.PreySystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Day and night, and the seasons. */
class ClockTest extends SimTestSupport {
    // A 1-minute day starting at 07:12 (phase 0.3): midnight comes 42 s in, noon 12 s in
    private static final float NOON = 12f;
    private static final float MIDNIGHT = 42f;

    private static SimulationConfig oneMinuteDays() {
        SimulationConfig config = new SimulationConfig();
        config.dayLengthMinutes = 1f;
        return config;
    }

    @Test
    @DisplayName("sight is full at noon and shortest at midnight")
    void nightShortensSight() {
        ClockSystem clock = new ClockSystem(oneMinuteDays());
        clock.update(NOON);
        assertEquals(1f, clock.visionScale(), 0.01f, "noon");
        clock.update(MIDNIGHT - NOON);
        assertEquals(Balance.NIGHT_VISION, clock.visionScale(), 0.01f, "midnight");
    }

    @Test
    @DisplayName("with day and night off, it's always light")
    void dayNightOff() {
        SimulationConfig config = oneMinuteDays();
        config.dayNightEnabled = false;
        ClockSystem clock = new ClockSystem(config);
        clock.update(MIDNIGHT);
        assertEquals(1f, clock.visionScale(), 0.0001f);
    }

    @Test
    @DisplayName("a prey that sees a predator by day misses it at night")
    void preyMissPredatorsAtNight() {
        assertEquals(EntityBehaviour.FLEEING, preyReactionAt(NOON), "noon");
        assertNotEquals(EntityBehaviour.FLEEING, preyReactionAt(MIDNIGHT), "midnight");
    }

    private static EntityBehaviour preyReactionAt(float time) {
        Engine e = new Engine();
        ClockSystem clock = new ClockSystem(oneMinuteDays());
        clock.update(time);
        e.addSystem(clock);
        e.addSystem(new PreySystem());
        Entity prey = creature(e, 1000, 1000, Gender.MALE, false); // Sees 200 px by day, 90 px at midnight
        prey.getComponent(VisionComponent.class).directionAngle = 0f;
        creature(e, 1150, 1000, Gender.MALE, true);              // Predator 150 px ahead
        e.update(DT);
        return prey.getComponent(BehaviourComponent.class).behaviour;
    }

    @Test
    @DisplayName("food grows much faster in summer than in winter")
    void seasonsChangeFoodGrowth() {
        int summer = foodGrownInAMinuteAt(0.25f);
        int winter = foodGrownInAMinuteAt(0.75f);
        assertTrue(summer > 3 * winter, "summer " + summer + " vs winter " + winter);
    }

    private static int foodGrownInAMinuteAt(float yearPhase) {
        SimulationConfig config = new SimulationConfig();
        config.yearLengthMinutes = 60f; // Long year, so the season barely changes during the test
        config.initialFood = 0;
        config.maxFood = 10_000;
        config.foodSpawnInterval = 0.5f;
        config.pondCount = 0;
        Engine e = new Engine();
        ClockSystem clock = new ClockSystem(config);
        clock.update(yearPhase * 3600f);
        e.addSystem(clock);
        e.addSystem(new FoodSourceSystem(config));
        run(e, 60f);
        return e.getEntitiesFor(Family.all(FoodSourceComponent.class).get()).size();
    }

    @Test
    @DisplayName("seasons come round in order")
    void seasonNames() {
        SimulationConfig config = new SimulationConfig();
        config.yearLengthMinutes = 4f; // A minute per season
        ClockSystem clock = new ClockSystem(config);
        StringBuilder seen = new StringBuilder();
        for (int quarter = 0; quarter < 4; quarter++) {
            seen.append(ClockSystem.SEASONS[clock.season()]).append(' ');
            clock.update(60f);
        }
        assertEquals("Spring Summer Autumn Winter ", seen.toString());
    }
}
