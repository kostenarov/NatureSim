package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Systems.SpatialGrid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SpatialGridTest extends SimTestSupport {

    @Test
    @DisplayName("spatial grid matches brute force for nearest search and radius query")
    void gridMatchesBruteForce() {
        Engine e = new Engine();
        Random r = new Random(11);
        for (int i = 0; i < 3000; i++) {
            Entity f = e.createEntity();
            PositionComponent p = new PositionComponent();
            p.position.set(r.nextFloat() * 5120, r.nextFloat() * 5120);
            f.add(p);
            e.addEntity(f);
        }
        ImmutableArray<Entity> all = e.getEntities();
        SpatialGrid grid = new SpatialGrid(128f, 5120, 5120);
        Array<Entity> out = new Array<>();
        int nearestMismatches = 0, queryMisses = 0;
        for (int q = 0; q < 500; q++) {
            // Keep only a random subset "alive" each round so sparse and dense cases are both covered
            float keep = q < 250 ? 1f : 0.003f;
            Set<Entity> alive = new HashSet<>();
            for (Entity en : all) if (r.nextFloat() < keep) alive.add(en);
            grid.rebuild(all);
            float x = r.nextFloat() * 5120, y = r.nextFloat() * 5120;

            Entity brute = null;
            float best = Float.MAX_VALUE;
            for (Entity en : alive) {
                float d = en.getComponent(PositionComponent.class).position.dst2(x, y);
                if (d < best) {
                    best = d;
                    brute = en;
                }
            }
            if (grid.findNearest(x, y, 8000f, alive::contains) != brute) nearestMismatches++;

            grid.query(x, y, 150f, out);
            for (Entity en : all)
                if (en.getComponent(PositionComponent.class).position.dst(x, y) <= 150f && !out.contains(en, true)) queryMisses++;
        }
        assertEquals(0, nearestMismatches, "nearest-search mismatches out of 500");
        assertEquals(0, queryMisses, "radius-query misses");
    }
}
