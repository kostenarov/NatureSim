package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.GenderComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.PredatorComponent;
import io.kostenarov.natureSim.Components.ReproductionComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Components.VelocityComponent;
import io.kostenarov.natureSim.Components.VisionComponent;

/**
 * Builds creature entities. Abilities such as speed, vision and scent always come from the genome
 * (see {@link Traits}), whether the creature is spawned at the start or born later.
 */
public final class Creatures {
    private Creatures() {
    }

    /** A prey creature with full stats at (x, y). The caller adds it to the engine. */
    public static Entity create(Engine engine, float x, float y, GenomeComponent dna) {
        Entity creature = engine.createEntity();

        PositionComponent pos = new PositionComponent();
        pos.position.set(x, y);
        creature.add(pos);

        creature.add(new VelocityComponent(Traits.speed(dna)));
        creature.add(dna);
        creature.add(new GenderComponent());
        creature.add(new StatsComponent());

        VisionComponent vision = new VisionComponent(Traits.visionRange(dna), Traits.visionAngle(dna));
        BehaviourComponent behaviour = new BehaviourComponent();
        behaviour.directionAngle = MathUtils.random(0f, 360f);
        vision.directionAngle = behaviour.directionAngle;
        creature.add(vision);
        creature.add(behaviour);

        creature.add(new ReproductionComponent());
        return creature;
    }

    /** Turns a creature into a predator; its scent range comes from its genes. */
    public static void makePredator(Entity creature) {
        PredatorComponent predator = new PredatorComponent();
        predator.scentRange = Traits.scentRange(creature.getComponent(GenomeComponent.class));
        creature.add(predator);
    }

    public static boolean isPredator(Entity creature) {
        return creature.getComponent(PredatorComponent.class) != null;
    }
}
