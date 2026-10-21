package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.CarcassComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.CarcassSystem;
import io.kostenarov.natureSim.Systems.DeathSystem;
import io.kostenarov.natureSim.Systems.PredatorSystem;
import io.kostenarov.natureSim.Systems.ScavengerSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Carcasses and the scavengers that live on them. */
class ScavengerTest extends SimTestSupport {

    private static Entity scavenger(Engine e, float x, float y) {
        Entity scavenger = creature(e, x, y, Gender.MALE, false);
        Creatures.makeScavenger(scavenger);
        return scavenger;
    }

    private static Entity firstCarcass(Engine e) {
        return e.getEntitiesFor(CarcassSystem.CARCASSES).size() > 0 ? e.getEntitiesFor(CarcassSystem.CARCASSES).first() : null;
    }

    @Test
    @DisplayName("a creature that dies leaves a carcass, which rots away")
    void deathLeavesCarcassThatRots() {
        Engine e = new Engine();
        e.addSystem(new DeathSystem());
        e.addSystem(new CarcassSystem());
        Entity doomed = creature(e, 500, 500, Gender.MALE, false);
        stats(doomed).health = 0f;

        e.update(DT);
        assertNotNull(firstCarcass(e), "carcass left behind");
        assertEquals(Balance.CARCASS_FOOD, Mappers.carcass.get(firstCarcass(e)).food, 0.5f);

        run(e, Balance.CARCASS_ROT_SECONDS + 1f);
        assertNull(firstCarcass(e), "rotted away");
    }

    @Test
    @DisplayName("a predator's kill leaves leftovers for scavengers")
    void killLeavesLeftovers() {
        Engine e = new Engine();
        e.addSystem(new PredatorSystem());
        Entity predator = creature(e, 1000, 1000, Gender.MALE, true);
        predator.getComponent(VisionComponent.class).directionAngle = 0f;
        stats(predator).hunger = 20f;
        Entity prey = creature(e, 1020, 1000, Gender.FEMALE, false);
        stats(prey).health = 1f;

        e.update(DT);
        assertEquals(1, creatureCount(e), "prey killed");
        assertEquals(Balance.KILL_CARCASS_FOOD, Mappers.carcass.get(firstCarcass(e)).food, 0.01f, "leftovers");
    }

    @Test
    @DisplayName("a hungry scavenger smells out a carcass and eats it")
    void scavengerEats() {
        Engine e = new Engine();
        e.addSystem(new ScavengerSystem());
        Entity scavenger = scavenger(e, 1000, 1000);
        stats(scavenger).hunger = 30f;
        CarcassSystem.leaveCarcass(e, creature(e, 1300, 1000, Gender.MALE, false), 40f); // 300 px away, out of sight

        run(e, 4f);
        assertTrue(stats(scavenger).hunger > 50f, "hunger went from 30 to " + stats(scavenger).hunger);
        assertTrue(Mappers.carcass.get(firstCarcass(e)).food < 40f, "carcass partly eaten");
    }

    @Test
    @DisplayName("predators don't hunt scavengers")
    void scavengersArentPrey() {
        Engine e = new Engine();
        e.addSystem(new PredatorSystem());
        Entity predator = creature(e, 1000, 1000, Gender.MALE, true);
        predator.getComponent(VisionComponent.class).directionAngle = 0f;
        stats(predator).hunger = 20f;
        scavenger(e, 1100, 1000);

        e.update(DT);
        EntityBehaviour behaviour = predator.getComponent(BehaviourComponent.class).behaviour;
        assertTrue(behaviour != EntityBehaviour.HUNTING && behaviour != EntityBehaviour.ATTACKING, "was " + behaviour);
    }
}
