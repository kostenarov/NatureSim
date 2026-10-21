package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.PreySystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HerdingTest extends SimTestSupport {

    private static void face(Entity creature, float degrees) {
        creature.getComponent(VisionComponent.class).directionAngle = degrees;
        creature.getComponent(BehaviourComponent.class).directionAngle = degrees;
    }

    /** Prey A faces a predator; prey B stands next to A facing the other way, so it can't see the predator itself. */
    private static EntityBehaviour neighbourBehaviourAfterScare(boolean herding) {
        SimulationConfig config = new SimulationConfig();
        config.herdingEnabled = herding;
        Engine e = new Engine();
        e.addSystem(new PreySystem(config));
        Entity watcher = creature(e, 1000, 1000, Gender.MALE, false);
        face(watcher, 0f);
        Entity neighbour = creature(e, 940, 1000, Gender.FEMALE, false);
        face(neighbour, 180f);
        creature(e, 1150, 1000, Gender.MALE, true); // Predator east of the watcher

        e.update(DT); // The watcher sees the predator and starts fleeing
        e.update(DT); // The neighbour notices
        return neighbour.getComponent(BehaviourComponent.class).behaviour;
    }

    @Test
    @DisplayName("panic spreads: a prey flees when a herd-mate nearby is fleeing")
    void panicSpreads() {
        assertEquals(EntityBehaviour.FLEEING, neighbourBehaviourAfterScare(true));
    }

    @Test
    @DisplayName("without herding, a prey that can't see the predator stays calm")
    void noHerdingNoPanic() {
        assertNotEquals(EntityBehaviour.FLEEING, neighbourBehaviourAfterScare(false));
    }
}
