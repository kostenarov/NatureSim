package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.CollisionSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CollisionTest extends SimTestSupport {

    private static float distance(Entity a, Entity b) {
        return a.getComponent(PositionComponent.class).position.dst(b.getComponent(PositionComponent.class).position);
    }

    @Test
    @DisplayName("two creatures on the same spot get pushed apart")
    void stackedPairSeparated() {
        Engine e = new Engine();
        e.addSystem(new CollisionSystem());
        Entity a = creature(e, 500, 500, Gender.MALE, false);
        Entity b = creature(e, 500, 500, Gender.FEMALE, false);
        e.update(DT);
        float d = distance(a, b);
        assertTrue(d >= 25.9f, String.format("distance %.2f", d));
    }

    @Test
    @DisplayName("a crowd of 1000 spreads out with no overlaps")
    void crowdHasNoOverlaps() {
        Engine e = new Engine();
        e.addSystem(new CollisionSystem());
        Random r = new Random(1);
        for (int i = 0; i < 1000; i++) creature(e, 1000 + r.nextFloat() * 600, 1000 + r.nextFloat() * 600, Gender.MALE, false);
        for (int i = 0; i < 60; i++) e.update(DT);
        long t0 = System.nanoTime();
        for (int i = 0; i < 200; i++) e.update(DT);
        double ms = (System.nanoTime() - t0) / 1e6 / 200;

        ImmutableArray<Entity> all = e.getEntities();
        float minD = Float.MAX_VALUE;
        for (int i = 0; i < all.size(); i++)
            for (int j = i + 1; j < all.size(); j++)
                minD = Math.min(minD, distance(all.get(i), all.get(j)));
        System.out.printf("crowd of 1000: min distance %.2f (target 26), %.3f ms/frame%n", minD, ms);
        assertTrue(minD >= 25.5f, String.format("min distance %.2f (target 26)", minD));
    }
}
