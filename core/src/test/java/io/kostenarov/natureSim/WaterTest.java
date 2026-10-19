package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.kostenarov.natureSim.Components.*;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaterTest extends SimTestSupport {

    private Engine engine;
    private Entity prey;

    /** Puts a thirsty prey on the map and runs until it is back above 98 thirst (or 120 s pass). */
    private float drinkUntilFull(boolean[] sawSeeking) {
        engine = new Engine();
        engine.addSystem(new WaterSourceSystem());
        engine.addSystem(new PreySystem());
        prey = creature(engine, 2000, 2000, Gender.MALE, false);
        stats(prey).thirst = 10f;
        float t = 0;
        while (stats(prey).thirst < 98f && t < 120f) {
            engine.update(DT);
            t += DT;
            sawSeeking[0] |= prey.getComponent(BehaviourComponent.class).behaviour == EntityBehaviour.SEEKING_WATER;
        }
        return t;
    }

    @Test
    @DisplayName("thirsty prey walks to the nearest pond and drinks to full")
    void thirstyPreyDrinksToFull() {
        boolean[] sawSeeking = {false};
        float t = drinkUntilFull(sawSeeking);
        PositionComponent pos = prey.getComponent(PositionComponent.class);
        ImmutableArray<Entity> ponds = engine.getEntitiesFor(WaterSourceSystem.WATER_FAMILY);
        Entity pond = WaterSourceSystem.findNearestWater(ponds, pos);

        assertTrue(sawSeeking[0], "prey never went into SEEKING_WATER");
        assertTrue(stats(prey).thirst >= 98f,
            String.format("thirst %.1f after %.1fs, ponds on map %d", stats(prey).thirst, t, ponds.size()));
        assertTrue(WaterSourceSystem.canDrinkFrom(pond, pos), "prey is not at the nearest pond");
    }

    @Test
    @DisplayName("prey leaves the water once full")
    void preyLeavesWaterWhenFull() {
        drinkUntilFull(new boolean[1]);
        run(engine, 0.5f);
        assertNotEquals(EntityBehaviour.SEEKING_WATER, prey.getComponent(BehaviourComponent.class).behaviour);
    }

    @Test
    @DisplayName("thirsty predator drinks to full")
    void thirstyPredatorDrinksToFull() {
        Engine e = new Engine();
        e.addSystem(new WaterSourceSystem());
        e.addSystem(new PredatorSystem());
        Entity p = creature(e, 2000, 2000, Gender.MALE, true);
        stats(p).thirst = 10f;
        float t = 0;
        while (stats(p).thirst < 98f && t < 120f) {
            e.update(DT);
            t += DT;
        }
        assertTrue(stats(p).thirst >= 98f, String.format("thirst %.1f after %.1fs", stats(p).thirst, t));
    }

    @Test
    @DisplayName("most food grows in patches near water")
    void foodGrowsNearPonds() {
        SimulationConfig cfg = new SimulationConfig();
        cfg.initialFood = 2000;
        Engine e = new Engine();
        e.addSystem(new WaterSourceSystem(cfg));
        e.addSystem(new FoodSourceSystem(cfg));
        ImmutableArray<Entity> ponds = e.getEntitiesFor(WaterSourceSystem.WATER_FAMILY);
        ImmutableArray<Entity> foods = e.getEntitiesFor(Family.all(FoodSourceComponent.class).get());
        int near = 0;
        for (Entity f : foods) {
            PositionComponent fp = f.getComponent(PositionComponent.class);
            for (Entity p : ponds) {
                float reach = p.getComponent(WaterSourceComponent.class).radius + 360f;
                if (fp.position.dst(p.getComponent(PositionComponent.class).position) <= reach) {
                    near++;
                    break;
                }
            }
        }
        float share = near / (float) foods.size();
        assertTrue(share > 0.7f, String.format("%.0f%% of %d food items near a pond", share * 100, foods.size()));
    }
}
