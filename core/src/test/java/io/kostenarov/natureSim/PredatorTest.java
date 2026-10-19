package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PredatorTest extends SimTestSupport {

    @Test
    @DisplayName("bites are rate-limited: 40 damage every 0.75 s takes about 1.5 s to kill")
    void killTakesSeveralBites() {
        Engine e = new Engine();
        e.addSystem(new PredatorSystem());
        e.addSystem(new CollisionSystem());
        e.addSystem(new MetabolismSystem());
        e.addSystem(new DeathSystem());
        Entity p = creature(e, 1000, 1000, Gender.MALE, true);
        stats(p).hunger = 30f;
        creature(e, 1020, 1000, Gender.FEMALE, false);
        int frames = 0;
        while (e.getEntities().size() == 2 && frames < 600) {
            e.update(DT);
            frames++;
        }
        float t = frames * DT;
        assertTrue(t > 1.3f && t < 1.8f, String.format("prey died after %.2fs", t));
    }

    @Test
    @DisplayName("a sated predator ignores prey; a hungry one hunts")
    void huntIsHungerDriven() {
        Engine e = new Engine();
        e.addSystem(new PredatorSystem());
        Entity p = creature(e, 1000, 1000, Gender.MALE, true);
        creature(e, 1100, 1000, Gender.FEMALE, false);
        e.update(DT);
        EntityBehaviour sated = p.getComponent(BehaviourComponent.class).behaviour;
        stats(p).hunger = 30f;
        e.update(DT);
        EntityBehaviour hungry = p.getComponent(BehaviourComponent.class).behaviour;

        assertEquals(EntityBehaviour.EXPLORING, sated, "sated behaviour");
        assertEquals(EntityBehaviour.HUNTING, hungry, "hungry behaviour");
    }
}
