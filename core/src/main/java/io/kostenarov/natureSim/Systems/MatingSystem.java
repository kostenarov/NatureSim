package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import io.kostenarov.natureSim.Components.*;
import io.kostenarov.natureSim.SimulationConfig;
import io.kostenarov.natureSim.Creatures;

public class MatingSystem extends EntitySystem {
    private ImmutableArray<Entity> matingEntities;
    private Engine engine;
    private final Array<Entity> nearby = new Array<>(false, 32);

    private static final float GENE_MIN = 0f;
    private static final float GENE_MAX = 2f;

    private final float matingCooldown;   // Seconds between matings for one creature
    private final float mutationRate;     // Chance to mutate each gene
    private final float mutationAmount;   // Max amount to mutate by

    public MatingSystem() {
        this(new SimulationConfig());
    }

    public MatingSystem(SimulationConfig config) {
        this.matingCooldown = config.matingCooldown;
        this.mutationRate = config.mutationRate;
        this.mutationAmount = config.mutationAmount;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = engine;
        matingEntities = engine.getEntitiesFor(Family.all(
            PositionComponent.class, StatsComponent.class, GenomeComponent.class,
            GenderComponent.class, ReproductionComponent.class
        ).get());
    }

    @Override
    public void update(float deltaTime) {
        // Update mating cooldowns
        for (Entity entity : matingEntities) {
            ReproductionComponent repro = entity.getComponent(ReproductionComponent.class);
            if (repro.matingCooldown > 0) {
                repro.matingCooldown -= deltaTime;
            }
        }

        SpatialIndexSystem index = getEngine().getSystem(SpatialIndexSystem.class);

        for (int i = 0; i < matingEntities.size(); i++) {
            Entity entity1 = matingEntities.get(i);
            if (!isReadyToMate(entity1)) {
                continue;
            }

            PositionComponent pos1 = entity1.getComponent(PositionComponent.class);
            Iterable<Entity> candidates = matingEntities;
            if (index != null) {
                // Only creatures within mating distance can be partners.
                index.creatures().query(pos1.position.x, pos1.position.y, ReproductionComponent.MATING_DISTANCE, nearby);
                candidates = nearby;
            }

            for (Entity entity2 : candidates) {
                if (!arePartners(entity1, entity2) || !isReadyToMate(entity2)) {
                    continue;
                }
                if (pos1.position.dst(entity2.getComponent(PositionComponent.class).position) > ReproductionComponent.MATING_DISTANCE) {
                    continue;
                }

                mate(entity1, entity2);
                break;
            }
        }
    }

    private void mate(Entity parent1, Entity parent2) {
        // Deduct energy from both parents
        StatsComponent stats1 = parent1.getComponent(StatsComponent.class);
        StatsComponent stats2 = parent2.getComponent(StatsComponent.class);
        ReproductionComponent repro1 = parent1.getComponent(ReproductionComponent.class);
        ReproductionComponent repro2 = parent2.getComponent(ReproductionComponent.class);

        stats1.energy -= ReproductionComponent.MATING_ENERGY_COST;
        stats2.energy -= ReproductionComponent.MATING_ENERGY_COST;
        // The offspring is built from the parents' food reserves, so births are limited by food supply.
        stats1.hunger -= ReproductionComponent.MATING_HUNGER_COST;
        stats2.hunger -= ReproductionComponent.MATING_HUNGER_COST;
        float cooldown = Creatures.isPredator(parent1) ? matingCooldown * ReproductionComponent.PREDATOR_COOLDOWN_FACTOR : matingCooldown;
        repro1.matingCooldown = cooldown;
        repro2.matingCooldown = cooldown;

        createOffspring(parent1, parent2);
    }

    private void createOffspring(Entity parent1, Entity parent2) {
        // Each gene comes whole from one parent (averaging would shrink variation every generation),
        // then may mutate by a normally distributed amount.
        GenomeComponent parent1Dna = parent1.getComponent(GenomeComponent.class);
        GenomeComponent parent2Dna = parent2.getComponent(GenomeComponent.class);
        GenomeComponent offspringDna = new GenomeComponent();

        for (int i = 0; i < offspringDna.genes.length; i++) {
            float gene = MathUtils.randomBoolean() ? parent1Dna.genes[i] : parent2Dna.genes[i];

            if (MathUtils.random() < mutationRate) {
                gene += (float) MathUtils.random.nextGaussian() * mutationAmount;
                gene = MathUtils.clamp(gene, GENE_MIN, GENE_MAX);
            }

            offspringDna.genes[i] = gene;
        }

        // Born next to parent 1
        PositionComponent parentPos = parent1.getComponent(PositionComponent.class);
        Entity offspring = Creatures.create(engine,
            parentPos.position.x + MathUtils.random(-20f, 20f),
            parentPos.position.y + MathUtils.random(-20f, 20f),
            offspringDna);

        // Newborns start half fed and a little frail
        StatsComponent offspringStats = offspring.getComponent(StatsComponent.class);
        offspringStats.energy = 50f;
        offspringStats.hunger = 2 * ReproductionComponent.MATING_HUNGER_COST; // Exactly what the parents gave up
        offspringStats.thirst = 50f;
        offspringStats.health = 80f;

        // One generation after the younger-lineage parent
        AgeComponent parent1Age = parent1.getComponent(AgeComponent.class);
        AgeComponent parent2Age = parent2.getComponent(AgeComponent.class);
        int parentGeneration = Math.max(parent1Age != null ? parent1Age.generation : 0, parent2Age != null ? parent2Age.generation : 0);
        offspring.getComponent(AgeComponent.class).generation = parentGeneration + 1;

        // Predator parents produce predator offspring
        if (Creatures.isPredator(parent1)) {
            Creatures.makePredator(offspring);
        }

        engine.addEntity(offspring);
    }

    /**
     * Only adult, well-fed, rested creatures off cooldown reproduce, so reproduction depends on how well
     * a creature survives.
     */
    public static boolean isReadyToMate(Entity creature) {
        ReproductionComponent repro = creature.getComponent(ReproductionComponent.class);
        StatsComponent stats = creature.getComponent(StatsComponent.class);
        if (repro == null || stats == null || repro.matingCooldown > 0) {
            return false;
        }
        AgeComponent age = creature.getComponent(AgeComponent.class);
        if (age != null && age.age < ReproductionComponent.MATURITY_AGE) {
            return false;
        }
        return stats.energy >= ReproductionComponent.MATING_ENERGY_COST
            && stats.hunger >= (Creatures.isPredator(creature) ? ReproductionComponent.PREDATOR_MATING_MIN_HUNGER : ReproductionComponent.MATING_MIN_HUNGER)
            && stats.thirst >= ReproductionComponent.MATING_MIN_THIRST;
    }

    /** Two different creatures of opposite sex and the same role (prey with prey, predators with predators). */
    public static boolean arePartners(Entity a, Entity b) {
        if (a == b) {
            return false;
        }
        GenderComponent genderA = a.getComponent(GenderComponent.class);
        GenderComponent genderB = b.getComponent(GenderComponent.class);
        return genderA != null && genderB != null && genderA.gender != genderB.gender
            && Creatures.isPredator(a) == Creatures.isPredator(b);
    }
}

