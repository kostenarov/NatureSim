package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Components.AgeComponent;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.GenderComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.LineageComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.PredatorComponent;
import io.kostenarov.natureSim.Components.ReproductionComponent;
import io.kostenarov.natureSim.Components.ScavengerComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Components.VelocityComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Enums.Role;
import io.kostenarov.natureSim.Systems.LineageSystem;

/**
 * Builds creature entities. Abilities such as speed, vision and scent always come from the genome
 * (see {@link Traits}), whether the creature is spawned at the start, arrives as a migrant or is born.
 */
public final class Creatures {
    // Every role has the same body; predators and scavengers carry one extra marker component.
    public static final Family PREY = Family.all(PositionComponent.class, VelocityComponent.class, GenomeComponent.class,
        VisionComponent.class, GenderComponent.class, StatsComponent.class, BehaviourComponent.class)
        .exclude(PredatorComponent.class, ScavengerComponent.class).get();
    public static final Family PREDATORS = Family.all(PositionComponent.class, VelocityComponent.class, GenomeComponent.class,
        VisionComponent.class, GenderComponent.class, StatsComponent.class, BehaviourComponent.class, PredatorComponent.class).get();
    public static final Family SCAVENGERS = Family.all(PositionComponent.class, VelocityComponent.class, GenomeComponent.class,
        VisionComponent.class, GenderComponent.class, StatsComponent.class, BehaviourComponent.class, ScavengerComponent.class).get();

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
        creature.add(new AgeComponent());

        LineageSystem lineage = engine.getSystem(LineageSystem.class);
        creature.add(new LineageComponent(lineage != null ? lineage.nextId() : 0L));
        return creature;
    }

    /** Turns a creature into a predator; its scent range comes from its genes. */
    public static void makePredator(Entity creature) {
        PredatorComponent predator = new PredatorComponent();
        predator.scentRange = Traits.scentRange(Mappers.genome.get(creature));
        creature.add(predator);
    }

    /** Turns a creature into a scavenger; it finds carcasses by smell, so its scent range comes from its genes. */
    public static void makeScavenger(Entity creature) {
        ScavengerComponent scavenger = new ScavengerComponent();
        scavenger.scentRange = Traits.scentRange(Mappers.genome.get(creature));
        creature.add(scavenger);
    }

    /** Gives a new creature the same role as {@code parent}. */
    public static void copyRole(Entity parent, Entity creature) {
        switch (role(parent)) {
            case PREDATOR:
                makePredator(creature);
                break;
            case SCAVENGER:
                makeScavenger(creature);
                break;
            default:
                break;
        }
    }

    public static Role role(Entity creature) {
        if (Mappers.predator.get(creature) != null) {
            return Role.PREDATOR;
        }
        if (Mappers.scavenger.get(creature) != null) {
            return Role.SCAVENGER;
        }
        return Role.PREY;
    }

    public static boolean isPredator(Entity creature) {
        return Mappers.predator.get(creature) != null;
    }

    /** Predators and scavengers sniff things out, which costs extra upkeep (see Traits.upkeep). */
    public static boolean usesScent(Entity creature) {
        return role(creature) != Role.PREY;
    }
}
