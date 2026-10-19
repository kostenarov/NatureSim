package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SurvivalTest extends SimTestSupport {

    private static Engine metabolismEngine() {
        Engine e = new Engine();
        e.addSystem(new PreySystem());
        e.addSystem(new MetabolismSystem());
        e.addSystem(new DeathSystem());
        return e;
    }

    @Test
    @DisplayName("dehydrated prey dies and is removed")
    void dehydratedPreyDies() {
        Engine e = metabolismEngine();
        Entity a = creature(e, 2000, 2000, Gender.MALE, false);
        stats(a).thirst = 0f;
        stats(a).health = 5f;
        run(e, 3f);
        assertEquals(0, e.getEntities().size(), "entities left");
    }

    @Test
    @DisplayName("starving prey dies and is removed")
    void starvingPreyDies() {
        Engine e = metabolismEngine();
        Entity a = creature(e, 2000, 2000, Gender.MALE, false);
        stats(a).hunger = 0f;
        stats(a).health = 5f;
        run(e, 3f);
        assertEquals(0, e.getEntities().size(), "entities left");
    }
}
