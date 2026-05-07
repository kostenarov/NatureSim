package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Components.*;

public class MatingSystem extends EntitySystem {
    private ImmutableArray<Entity> matingEntities;
    private Engine engine;

    // Mutation settings
    private static final float MUTATION_RATE = 0.15f;      // 15% chance to mutate each gene
    private static final float MUTATION_AMOUNT = 0.3f;     // Max amount to mutate by
    private static final float GENE_MIN = 0f;
    private static final float GENE_MAX = 2f;

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

        // Try to find and process mating pairs
        for (int i = 0; i < matingEntities.size(); i++) {
            Entity entity1 = matingEntities.get(i);
            ReproductionComponent repro1 = entity1.getComponent(ReproductionComponent.class);
            StatsComponent stats1 = entity1.getComponent(StatsComponent.class);

            // Check if this entity can mate
            if (repro1.matingCooldown > 0 || stats1.energy < ReproductionComponent.MATING_ENERGY_COST) {
                continue;
            }

            // Look for a suitable mate
            for (int j = i + 1; j < matingEntities.size(); j++) {
                Entity entity2 = matingEntities.get(j);
                ReproductionComponent repro2 = entity2.getComponent(ReproductionComponent.class);
                StatsComponent stats2 = entity2.getComponent(StatsComponent.class);
                PositionComponent pos1 = entity1.getComponent(PositionComponent.class);
                PositionComponent pos2 = entity2.getComponent(PositionComponent.class);

                // Check if conditions for mating are met
                if (repro2.matingCooldown > 0 || stats2.energy < ReproductionComponent.MATING_ENERGY_COST) {
                    continue;
                }

                float distance = pos1.position.dst(pos2.position);
                if (distance > ReproductionComponent.MATING_DISTANCE) {
                    continue;
                }

                // Both entities can mate! Check if they have opposite genders
                GenderComponent gender1 = entity1.getComponent(GenderComponent.class);
                GenderComponent gender2 = entity2.getComponent(GenderComponent.class);

                if (gender1.gender == gender2.gender) {
                    continue;
                }

                // Perform mating
                mate(entity1, entity2);

                // Only one mating per frame per entity to avoid excessive population growth
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
        repro1.matingCooldown = ReproductionComponent.MATING_COOLDOWN_TIME;
        repro2.matingCooldown = ReproductionComponent.MATING_COOLDOWN_TIME;

        // Create offspring
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

        // Inherit and blend genes from both parents with mutations
        GenomeComponent parent1Dna = parent1.getComponent(GenomeComponent.class);
        GenomeComponent parent2Dna = parent2.getComponent(GenomeComponent.class);
        GenomeComponent offspringDna = new GenomeComponent();

        for (int i = 0; i < offspringDna.genes.length; i++) {
            // Blend genes: average of both parents
            float blendedGene = (parent1Dna.genes[i] + parent2Dna.genes[i]) / 2f;

            // Apply mutation
            if (MathUtils.random() < MUTATION_RATE) {
                blendedGene += MathUtils.random(-MUTATION_AMOUNT, MUTATION_AMOUNT);
                blendedGene = MathUtils.clamp(blendedGene, GENE_MIN, GENE_MAX);
            }

            offspringDna.genes[i] = blendedGene;
        }
        offspring.add(offspringDna);

        // Random gender
        GenderComponent offspringGender = new GenderComponent();
        offspring.add(offspringGender);

        // Initial stats (healthy and hungry)
        StatsComponent offspringStats = new StatsComponent();
        offspringStats.energy = 50f;    // Start with medium energy
        offspringStats.hunger = 50f;    // Start hungry to encourage foraging
        offspringStats.thirst = 50f;
        offspringStats.health = 80f;
        offspring.add(offspringStats);

        // Vision based on parent genes
        float parentVisionRange = parent1.getComponent(VisionComponent.class).visionRange;
        float parentVisionAngle = parent1.getComponent(VisionComponent.class).visionAngle;
        VisionComponent offspringVision = new VisionComponent(parentVisionRange, parentVisionAngle);
        offspring.add(offspringVision);

        // Behaviour component
        BehaviourComponent offspringBehaviour = new BehaviourComponent();
        offspring.add(offspringBehaviour);

        // Velocity component
        VelocityComponent offspringVel = new VelocityComponent();
        offspring.add(offspringVel);

        // Reproduction component
        ReproductionComponent offspringRepro = new ReproductionComponent();
        offspring.add(offspringRepro);

        engine.addEntity(offspring);
    }
}

