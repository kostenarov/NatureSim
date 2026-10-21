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
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Mappers;
import io.kostenarov.natureSim.Balance;
import io.kostenarov.natureSim.Traits;

public class MatingSystem extends EntitySystem {
    private ImmutableArray<Entity> matingEntities;
    private Engine engine;
    private final Array<Entity> nearby = new Array<>(false, 32);

    private static final float GENE_MIN = 0f;
    private static final float GENE_MAX = 2f;

    // Cooldown, mutation and predator slowdown settings, read live so they can change mid-run
    private final SimulationConfig config;

    public MatingSystem() {
        this(new SimulationConfig());
    }

    public MatingSystem(SimulationConfig config) {
        this.config = config;
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
            ReproductionComponent repro = Mappers.reproduction.get(entity);
            if (repro.matingCooldown > 0) {
                repro.matingCooldown -= deltaTime;
            }
        }

        SpatialIndexSystem index = getEngine().getSystem(SpatialIndexSystem.class);

        // Females choose: each ready female mates with the most attractive ready male close enough
        for (int i = 0; i < matingEntities.size(); i++) {
            Entity female = matingEntities.get(i);
            if (Mappers.gender.get(female).gender != Gender.FEMALE || !isReadyToMate(female)) {
                continue;
            }

            PositionComponent femalePos = Mappers.position.get(female);
            Iterable<Entity> candidates = matingEntities;
            if (index != null) {
                // Only creatures within mating distance can be partners.
                index.creatures().query(femalePos.position.x, femalePos.position.y, Balance.MATING_DISTANCE, nearby);
                candidates = nearby;
            }

            Entity chosen = null;
            float bestScore = -Float.MAX_VALUE;
            for (Entity male : candidates) {
                if (!arePartners(female, male) || !isReadyToMate(male)
                    || femalePos.position.dst(Mappers.position.get(male).position) > Balance.MATING_DISTANCE) {
                    continue;
                }
                float score = Traits.attractiveness(Mappers.genome.get(female), Mappers.genome.get(male));
                if (score > bestScore) {
                    bestScore = score;
                    chosen = male;
                }
            }
            if (chosen != null) {
                mate(female, chosen);
            }
        }
    }

    private void mate(Entity parent1, Entity parent2) {
        // Deduct energy from both parents
        StatsComponent stats1 = Mappers.stats.get(parent1);
        StatsComponent stats2 = Mappers.stats.get(parent2);
        ReproductionComponent repro1 = Mappers.reproduction.get(parent1);
        ReproductionComponent repro2 = Mappers.reproduction.get(parent2);

        stats1.energy -= Balance.MATING_ENERGY_COST;
        stats2.energy -= Balance.MATING_ENERGY_COST;
        // The offspring is built from the parents' food reserves, so births are limited by food supply.
        stats1.hunger -= Balance.MATING_HUNGER_COST;
        stats2.hunger -= Balance.MATING_HUNGER_COST;
        float cooldown = Creatures.isPredator(parent1) ? config.matingCooldown * config.predatorBreedingSlowdown : config.matingCooldown;
        repro1.matingCooldown = cooldown;
        repro2.matingCooldown = cooldown;

        createOffspring(parent1, parent2);
    }

    private void createOffspring(Entity parent1, Entity parent2) {
        // Each gene comes whole from one parent (averaging would shrink variation every generation),
        // then may mutate by a normally distributed amount.
        GenomeComponent parent1Dna = Mappers.genome.get(parent1);
        GenomeComponent parent2Dna = Mappers.genome.get(parent2);
        GenomeComponent offspringDna = new GenomeComponent();

        for (int i = 0; i < offspringDna.genes.length; i++) {
            float gene = MathUtils.randomBoolean() ? parent1Dna.genes[i] : parent2Dna.genes[i];

            if (MathUtils.random() < config.mutationRate) {
                gene += (float) MathUtils.random.nextGaussian() * config.mutationAmount;
                gene = MathUtils.clamp(gene, GENE_MIN, GENE_MAX);
            }

            offspringDna.genes[i] = gene;
        }

        // Born next to parent 1
        PositionComponent parentPos = Mappers.position.get(parent1);
        Entity offspring = Creatures.create(engine,
            parentPos.position.x + MathUtils.random(-20f, 20f),
            parentPos.position.y + MathUtils.random(-20f, 20f),
            offspringDna);

        // Newborns start half fed and a little frail
        StatsComponent offspringStats = Mappers.stats.get(offspring);
        offspringStats.energy = 50f;
        offspringStats.hunger = 2 * Balance.MATING_HUNGER_COST; // Exactly what the parents gave up
        offspringStats.thirst = 50f;
        offspringStats.health = 80f;

        // One generation after the younger-lineage parent
        AgeComponent parent1Age = Mappers.age.get(parent1);
        AgeComponent parent2Age = Mappers.age.get(parent2);
        int parentGeneration = Math.max(parent1Age != null ? parent1Age.generation : 0, parent2Age != null ? parent2Age.generation : 0);
        Mappers.age.get(offspring).generation = parentGeneration + 1;

        // Offspring take their parents' role, and their mother's species until SpeciesSystem next looks
        Creatures.copyRole(parent1, offspring);
        if (Mappers.species.has(parent1) && Mappers.species.has(offspring)) {
            Mappers.species.get(offspring).id = Mappers.species.get(parent1).id;
        }

        LineageSystem lineage = engine.getSystem(LineageSystem.class);
        if (lineage != null) {
            boolean parent1IsMother = Mappers.gender.get(parent1).gender == Gender.FEMALE;
            lineage.recordBirth(offspring, parent1IsMother ? parent1 : parent2, parent1IsMother ? parent2 : parent1);
        }

        engine.addEntity(offspring);
    }

    /**
     * Only adult, well-fed, rested creatures off cooldown reproduce, so reproduction depends on how well
     * a creature survives.
     */
    public static boolean isReadyToMate(Entity creature) {
        ReproductionComponent repro = Mappers.reproduction.get(creature);
        StatsComponent stats = Mappers.stats.get(creature);
        if (repro == null || stats == null || repro.matingCooldown > 0) {
            return false;
        }
        AgeComponent age = Mappers.age.get(creature);
        if (age != null && age.age < Balance.MATURITY_AGE) {
            return false;
        }
        return stats.energy >= Balance.MATING_ENERGY_COST
            && stats.hunger >= (Creatures.isPredator(creature) ? Balance.PREDATOR_MATING_MIN_HUNGER : Balance.MATING_MIN_HUNGER)
            && stats.thirst >= Balance.MATING_MIN_THIRST;
    }

    /**
     * Two different creatures of opposite sex and the same role (prey with prey, predators with predators, ...)
     * whose genes are close enough to breed (see {@link Traits#canInterbreed}).
     */
    public static boolean arePartners(Entity a, Entity b) {
        if (a == b) {
            return false;
        }
        GenderComponent genderA = Mappers.gender.get(a);
        GenderComponent genderB = Mappers.gender.get(b);
        return genderA != null && genderB != null && genderA.gender != genderB.gender
            && Creatures.role(a) == Creatures.role(b)
            && Traits.canInterbreed(Mappers.genome.get(a), Mappers.genome.get(b));
    }
}

