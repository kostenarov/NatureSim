package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import io.kostenarov.natureSim.Components.FoodSourceComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.LineageComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Systems.CarcassSystem;
import io.kostenarov.natureSim.Systems.ClockSystem;
import io.kostenarov.natureSim.Systems.FoodSourceSystem;
import io.kostenarov.natureSim.Systems.LineageSystem;
import io.kostenarov.natureSim.Systems.PopulationHistory;
import io.kostenarov.natureSim.Systems.WaterSourceSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Saving and loading a world, and settings that change while it runs. */
class SaveAndSettingsTest extends SimTestSupport {
    private static final Family FOOD = Family.all(FoodSourceComponent.class).get();

    private static SimulationConfig smallWorld() {
        SimulationConfig config = new SimulationConfig();
        config.preyCount = 60;
        config.predatorCount = 4;
        config.scavengerCount = 4;
        config.pondCount = 4;
        config.initialFood = 80;
        return config;
    }

    /** Food eaten this step is only removed next step, and isn't saved. */
    private static int uneatenFood(Engine e) {
        int count = 0;
        for (Entity food : e.getEntitiesFor(FOOD)) {
            if (!food.getComponent(FoodSourceComponent.class).consumed) {
                count++;
            }
        }
        return count;
    }

    private static Entity withId(Engine e, long id) {
        for (Entity creature : e.getEntitiesFor(Family.all(LineageComponent.class).get())) {
            if (creature.getComponent(LineageComponent.class).id == id) {
                return creature;
            }
        }
        return null;
    }

    @Test
    @DisplayName("a saved world loads back with the same creatures, food, ponds, clock, family tree and history")
    void saveAndLoadRoundTrip() {
        SimulationConfig config = smallWorld();
        Engine original = Simulation.createEngine(config);
        Simulation.spawnStartingCreatures(original, config);
        run(original, 20f);

        String saved = WorldSave.save(original, config, 42L);
        WorldSave.Loaded loaded = WorldSave.load(saved);
        Engine copy = loaded.engine;

        assertEquals(42L, loaded.seed);
        assertEquals(config.preyCount, loaded.config.preyCount);
        assertEquals(creatureCount(original), creatureCount(copy), "creatures");
        assertEquals(uneatenFood(original), copy.getEntitiesFor(FOOD).size(), "food");
        assertEquals(original.getEntitiesFor(WaterSourceSystem.WATER_FAMILY).size(), copy.getEntitiesFor(WaterSourceSystem.WATER_FAMILY).size(), "ponds");
        assertEquals(original.getEntitiesFor(CarcassSystem.CARCASSES).size(), copy.getEntitiesFor(CarcassSystem.CARCASSES).size(), "carcasses");
        assertEquals(original.getSystem(ClockSystem.class).getTime(), copy.getSystem(ClockSystem.class).getTime(), 1e-4f, "clock");
        assertEquals(original.getSystem(LineageSystem.class).peekNextId(), copy.getSystem(LineageSystem.class).peekNextId(), "next id");
        assertEquals(original.getSystem(PopulationHistory.class).sampleCount(), copy.getSystem(PopulationHistory.class).sampleCount(), "history");

        // Every creature comes back with its own genes and needs
        for (Entity creature : original.getEntitiesFor(Family.all(LineageComponent.class).get())) {
            Entity twin = withId(copy, creature.getComponent(LineageComponent.class).id);
            assertNotNull(twin, "creature #" + creature.getComponent(LineageComponent.class).id);
            assertArrayEquals(creature.getComponent(GenomeComponent.class).genes, twin.getComponent(GenomeComponent.class).genes);
            assertEquals(Creatures.role(creature), Creatures.role(twin));
            assertEquals(creature.getComponent(StatsComponent.class).hunger, twin.getComponent(StatsComponent.class).hunger, 1e-4f);
            assertEquals(Mappers.age.get(creature).age, Mappers.age.get(twin).age, 1e-4f);
        }

        run(copy, 5f); // And the loaded world runs
        assertTrue(creatureCount(copy) > 0);
    }

    @Test
    @DisplayName("loading something that isn't a save fails cleanly")
    void loadRejectsOtherJson() {
        assertThrows(RuntimeException.class, () -> WorldSave.load("{\"hello\": 1}"));
    }

    @Test
    @DisplayName("a world's settings are its own copy")
    void configCopyIsIndependent() {
        SimulationConfig original = new SimulationConfig();
        SimulationConfig copy = original.copy();
        copy.maxFood = 5;
        copy.herdingEnabled = !original.herdingEnabled;
        assertNotEquals(5, original.maxFood);
        assertNotEquals(copy.herdingEnabled, original.herdingEnabled);
        assertEquals(original.preyCount, copy.preyCount);
    }

    @Test
    @DisplayName("food settings changed mid-run take effect straight away")
    void foodSettingsApplyLive() {
        SimulationConfig config = new SimulationConfig();
        config.initialFood = 0;
        config.maxFood = 10;
        config.foodSpawnInterval = 0.05f;
        config.seasonsEnabled = false;
        Engine e = new Engine();
        e.addSystem(new FoodSourceSystem(config));
        run(e, 2f);
        assertEquals(10, e.getEntitiesFor(FOOD).size(), "capped at the max");

        config.maxFood = 30;
        run(e, 2f);
        assertEquals(30, e.getEntitiesFor(FOOD).size(), "new cap used at once");
    }

    @Test
    @DisplayName("making days longer mid-run doesn't make the time of day jump")
    void dayLengthChangeIsSmooth() {
        SimulationConfig config = new SimulationConfig();
        config.dayLengthMinutes = 1f;
        Engine e = new Engine();
        ClockSystem clock = new ClockSystem(config);
        e.addSystem(clock);
        run(e, 20f);
        float before = clock.dayPhase();

        config.dayLengthMinutes = 4f;
        e.update(DT);
        assertEquals(before, clock.dayPhase(), 0.001f, "no jump");
        run(e, 60f);
        assertEquals(before + 0.25f, clock.dayPhase(), 0.01f, "then a quarter of a (now 4-minute) day per minute");
    }
}
