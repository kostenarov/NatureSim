package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.kostenarov.natureSim.Components.FoodSourceComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Components.WaterSourceComponent;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.FoodSourceSystem;
import io.kostenarov.natureSim.Systems.PreySystem;
import io.kostenarov.natureSim.Systems.WaterBarrierSystem;
import io.kostenarov.natureSim.Systems.WaterSourceSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaterDepthTest extends SimTestSupport {
    private static final float POND_X = 1000f;
    private static final float POND_Y = 1000f;
    private static final float POND_RADIUS = 150f;

    private static Entity pond(Engine e) {
        Entity pond = e.createEntity();
        PositionComponent pos = new PositionComponent();
        pos.position.set(POND_X, POND_Y);
        pond.add(pos);
        pond.add(new WaterSourceComponent(POND_RADIUS));
        e.addEntity(pond);
        return pond;
    }

    /** Distance from the pond center to the creature's center. */
    private static float distanceFromPondCenter(Entity creature) {
        PositionComponent pos = creature.getComponent(PositionComponent.class);
        return (float) Math.hypot(pos.position.x + World.CREATURE_HALF_SIZE - POND_X, pos.position.y + World.CREATURE_HALF_SIZE - POND_Y);
    }

    @Test
    @DisplayName("creatures walk around deep water instead of through it")
    void walksAroundDeepWater() {
        Engine e = new Engine();
        e.addSystem(new PreySystem());
        Entity pond = pond(e);
        // Hungry prey west of the pond, food straight across on the east side; long sight so it sees the food
        Entity prey = creature(e, POND_X - 300f - 16f, POND_Y - 16f, Gender.MALE, false);
        prey.getComponent(VisionComponent.class).visionRange = 1000f;
        stats(prey).hunger = 20f;
        Entity food = e.createEntity();
        PositionComponent foodPos = new PositionComponent();
        foodPos.position.set(POND_X + 300f, POND_Y - 8f);
        food.add(foodPos);
        food.add(new FoodSourceComponent());
        e.addEntity(food);

        float deepLimit = WaterSourceSystem.deepRadius(pond);
        float closest = Float.MAX_VALUE;
        for (int frame = 0; frame < 60 * 8 && !food.getComponent(FoodSourceComponent.class).consumed; frame++) {
            e.update(DT);
            closest = Math.min(closest, distanceFromPondCenter(prey));
        }

        assertTrue(food.getComponent(FoodSourceComponent.class).consumed, "reached the food on the far side");
        assertTrue(closest > deepLimit, "closest approach " + closest + " px stayed outside deep water (" + deepLimit + " px)");
        assertTrue(closest < POND_RADIUS, "but it did wade into the shallows: " + closest + " px");
    }

    @Test
    @DisplayName("a creature dropped into deep water is pushed out to the edge")
    void barrierPushesOut() {
        Engine e = new Engine();
        e.addSystem(new WaterBarrierSystem());
        Entity pond = pond(e);
        Entity swimmer = creature(e, POND_X + 10f, POND_Y, Gender.MALE, false);

        e.update(DT);
        assertTrue(distanceFromPondCenter(swimmer) >= WaterSourceSystem.deepRadius(pond), "out of deep water: " + distanceFromPondCenter(swimmer));
    }

    @Test
    @DisplayName("food never grows in deep water")
    void noFoodInDeepWater() {
        SimulationConfig config = new SimulationConfig();
        config.initialFood = 2000;
        Engine e = new Engine();
        e.addSystem(new WaterSourceSystem(config));
        e.addSystem(new FoodSourceSystem(config));
        ImmutableArray<Entity> ponds = e.getEntitiesFor(WaterSourceSystem.WATER_FAMILY);

        int inDeepWater = 0;
        for (Entity item : e.getEntitiesFor(Family.all(FoodSourceComponent.class).get())) {
            PositionComponent pos = item.getComponent(PositionComponent.class);
            if (WaterSourceSystem.isInDeepWater(ponds, pos.position.x + 8f, pos.position.y + 8f, 0f)) inDeepWater++;
        }
        assertEquals(0, inDeepWater, "food items in deep water");
    }
}
