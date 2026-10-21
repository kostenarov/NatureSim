package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Components.*;
import io.kostenarov.natureSim.Enums.Gender;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared setup for headless simulation tests. They drive an Ashley {@link Engine} directly,
 * with no window and no Gdx backend.
 */
public abstract class SimTestSupport {
    protected static final float DT = 1f / 60f;
    protected static final long SEED = 12345L;

    // Every test starts from the same random state, so ponds, food and mutations are reproducible
    @BeforeEach
    void seedRandom() {
        MathUtils.random.setSeed(SEED);
    }

    /** A creature with all genes at 1, standing at (x, y). Predators bite for 40 and gain 45 energy per kill. */
    protected static Entity creature(Engine e, float x, float y, Gender g, boolean predator) {
        Entity a = e.createEntity();
        PositionComponent p = new PositionComponent();
        p.position.set(x, y);
        a.add(p);
        a.add(new VelocityComponent(100f));
        GenomeComponent dna = new GenomeComponent();
        for (int i = 0; i < dna.genes.length; i++) dna.genes[i] = 1f;
        a.add(dna);
        a.add(new GenderComponent(g));
        a.add(new StatsComponent());
        a.add(new VisionComponent(200f, 120f));
        a.add(new BehaviourComponent());
        a.add(new ReproductionComponent());
        if (predator) {
            PredatorComponent pc = new PredatorComponent();
            pc.attackDamage = 40f;
            pc.killEnergyGain = 45f;
            pc.attackRange = 30f;
            a.add(pc);
        }
        e.addEntity(a);
        return a;
    }

    /** Steps the engine at a fixed 60 Hz for the given simulated time. */
    protected static void run(Engine e, float seconds) {
        for (int i = 0; i < (int) (seconds / DT); i++) e.update(DT);
    }

    /** Living creatures only: dead ones leave carcasses, which are entities too. */
    protected static int creatureCount(Engine e) {
        return e.getEntitiesFor(com.badlogic.ashley.core.Family.all(StatsComponent.class).get()).size();
    }

    protected static StatsComponent stats(Entity e) {
        return e.getComponent(StatsComponent.class);
    }
}
