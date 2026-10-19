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
import io.kostenarov.natureSim.Traits;

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
            ReproductionComponent repro1 = entity1.getComponent(ReproductionComponent.class);
            StatsComponent stats1 = entity1.getComponent(StatsComponent.class);

            if (repro1.matingCooldown > 0 || !isFitToMate(stats1)) {
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
                ReproductionComponent repro2 = entity2.getComponent(ReproductionComponent.class);
                if (entity2 == entity1 || repro2 == null) {
                    continue;
                }
                StatsComponent stats2 = entity2.getComponent(StatsComponent.class);
                PositionComponent pos2 = entity2.getComponent(PositionComponent.class);

                if (repro2.matingCooldown > 0 || !isFitToMate(stats2)) {
                    continue;
                }

                float distance = pos1.position.dst(pos2.position);
                if (distance > ReproductionComponent.MATING_DISTANCE) {
                    continue;
                }

                GenderComponent gender1 = entity1.getComponent(GenderComponent.class);
                GenderComponent gender2 = entity2.getComponent(GenderComponent.class);

                if (gender1.gender == gender2.gender) {
                    continue;
                }

                // Predators only mate with predators, prey only with prey.
                if (isPredator(entity1) != isPredator(entity2)) {
                    continue;
                }

                // Perform mating
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
        repro1.matingCooldown = matingCooldown;
        repro2.matingCooldown = matingCooldown;

        createOffspring(parent1, parent2);
    }

    private void createOffspring(Entity parent1, Entity parent2) {
        Entity offspring = engine.createEntity();

        // Inherit position (near parent 1)
        PositionComponent parentPos = parent1.getComponent(PositionComponent.class);
        PositionComponent offspringPos = new PositionComponent();
        float offsetX = MathUtils.random(-20f, 20f);
        float offsetY = MathUtils.random(-20f, 20f);
        offspringPos.position.set(parentPos.position.x + offsetX, parentPos.position.y + offsetY);
        offspring.add(offspringPos);

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
        offspring.add(offspringDna);

        GenderComponent offspringGender = new GenderComponent();
        offspring.add(offspringGender);

        // Initial stats (healthy and hungry)
        StatsComponent offspringStats = new StatsComponent();
        offspringStats.energy = 50f;    // Start with medium energy
        offspringStats.hunger = 50f;    // Start hungry to encourage foraging
        offspringStats.thirst = 50f;
        offspringStats.health = 80f;
        offspring.add(offspringStats);

        // Vision comes from the offspring's own genes
        offspring.add(new VisionComponent(Traits.visionRange(offspringDna), Traits.visionAngle(offspringDna)));

        // Behaviour component
        BehaviourComponent offspringBehaviour = new BehaviourComponent();
        offspring.add(offspringBehaviour);

        // Velocity component
        VelocityComponent offspringVel = new VelocityComponent();
        offspring.add(offspringVel);

        // Reproduction component
        ReproductionComponent offspringRepro = new ReproductionComponent();
        offspring.add(offspringRepro);

        // Predator parents produce predator offspring
        PredatorComponent parentPredator = parent1.getComponent(PredatorComponent.class);
        if (parentPredator != null) {
            PredatorComponent offspringPredator = new PredatorComponent(parentPredator);
            offspringPredator.scentRange = Traits.scentRange(offspringDna);
            offspring.add(offspringPredator);
        }

        engine.addEntity(offspring);
    }

    /** Only well-fed, rested creatures reproduce, so reproduction depends on how well a creature survives. */
    private boolean isFitToMate(StatsComponent stats) {
        return stats.energy >= ReproductionComponent.MATING_ENERGY_COST
            && stats.hunger >= ReproductionComponent.MATING_MIN_HUNGER
            && stats.thirst >= ReproductionComponent.MATING_MIN_THIRST;
    }

    private boolean isPredator(Entity entity) {
        return entity.getComponent(PredatorComponent.class) != null;
    }
}

