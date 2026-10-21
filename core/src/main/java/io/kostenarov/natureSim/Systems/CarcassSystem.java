package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import io.kostenarov.natureSim.Balance;
import io.kostenarov.natureSim.Components.CarcassComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Mappers;

/**
 * Carcasses rot: their food runs down over {@link Balance#CARCASS_ROT_SECONDS} and then they disappear.
 * Scavengers eat them before that if they can.
 */
public class CarcassSystem extends IteratingSystem {
    public static final Family CARCASSES = Family.all(CarcassComponent.class, PositionComponent.class).get();
    private static final float ROT_PER_SECOND = Balance.CARCASS_FOOD / Balance.CARCASS_ROT_SECONDS;

    public CarcassSystem() {
        super(CARCASSES);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CarcassComponent carcass = Mappers.carcass.get(entity);
        carcass.age += deltaTime;
        carcass.food -= ROT_PER_SECOND * deltaTime;
        if (carcass.food <= 0f) {
            getEngine().removeEntity(entity);
        }
    }

    /** Leaves a carcass where {@code creature} died. */
    public static void leaveCarcass(Engine engine, Entity creature, float food) {
        if (food <= 0f) {
            return;
        }
        Entity carcass = engine.createEntity();
        PositionComponent pos = new PositionComponent();
        pos.position.set(Mappers.position.get(creature).position);
        carcass.add(pos);
        carcass.add(new CarcassComponent(food));
        engine.addEntity(carcass);
    }
}
