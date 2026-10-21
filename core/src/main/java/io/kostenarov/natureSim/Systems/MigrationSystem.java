package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Balance;
import io.kostenarov.natureSim.Creatures;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Enums.Role;
import io.kostenarov.natureSim.Mappers;
import io.kostenarov.natureSim.Simulation;
import io.kostenarov.natureSim.SimulationConfig;
import io.kostenarov.natureSim.World;

/**
 * Keeps a species from vanishing for good: when one is down to a handful, a small group of newcomers wanders
 * in from the edge of the map. Newcomers are first-generation adults with fresh random genes, so they also
 * bring new variation. Checks only every {@link Balance#MIGRATION_CHECK_SECONDS}, so a recovering species
 * gets a chance first.
 */
public class MigrationSystem extends EntitySystem {
    private static final float EDGE_INSET = 60f;
    private static final float GROUP_SPREAD = 80f;

    private final float configuredScavengers;
    private float timer = 0f;
    private int migrants = 0;
    private ImmutableArray<Entity> prey;
    private ImmutableArray<Entity> predators;
    private ImmutableArray<Entity> scavengers;

    public MigrationSystem() {
        this(new SimulationConfig());
    }

    public MigrationSystem(SimulationConfig config) {
        // A world started without scavengers shouldn't suddenly get them
        this.configuredScavengers = config.scavengerCount;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        prey = engine.getEntitiesFor(Creatures.PREY);
        predators = engine.getEntitiesFor(Creatures.PREDATORS);
        scavengers = engine.getEntitiesFor(Creatures.SCAVENGERS);
    }

    @Override
    public void update(float deltaTime) {
        timer += deltaTime;
        if (timer < Balance.MIGRATION_CHECK_SECONDS) {
            return;
        }
        timer = 0f;

        if (prey.size() < Balance.PREY_MIGRATION_THRESHOLD) {
            arrive(Role.PREY, Balance.PREY_MIGRANTS);
        }
        if (predators.size() < Balance.OTHER_MIGRATION_THRESHOLD) {
            arrive(Role.PREDATOR, Balance.OTHER_MIGRANTS);
        }
        if (configuredScavengers > 0 && scavengers.size() < Balance.OTHER_MIGRATION_THRESHOLD) {
            arrive(Role.SCAVENGER, Balance.OTHER_MIGRANTS);
        }
    }

    /** Total newcomers so far this run. */
    public int getMigrants() {
        return migrants;
    }

    /** A group arrives together at a random point along the map edge, half male and half female. */
    private void arrive(Role role, int count) {
        float x;
        float y;
        if (MathUtils.randomBoolean()) {
            x = MathUtils.randomBoolean() ? EDGE_INSET : World.WIDTH - World.CREATURE_SIZE - EDGE_INSET;
            y = MathUtils.random(EDGE_INSET, World.HEIGHT - World.CREATURE_SIZE - EDGE_INSET);
        } else {
            x = MathUtils.random(EDGE_INSET, World.WIDTH - World.CREATURE_SIZE - EDGE_INSET);
            y = MathUtils.randomBoolean() ? EDGE_INSET : World.HEIGHT - World.CREATURE_SIZE - EDGE_INSET;
        }

        for (int i = 0; i < count; i++) {
            float memberX = MathUtils.clamp(x + MathUtils.random(-GROUP_SPREAD, GROUP_SPREAD), 0f, World.WIDTH - World.CREATURE_SIZE);
            float memberY = MathUtils.clamp(y + MathUtils.random(-GROUP_SPREAD, GROUP_SPREAD), 0f, World.HEIGHT - World.CREATURE_SIZE);
            Entity newcomer = Simulation.spawn(getEngine(), role, memberX, memberY, Balance.MATURITY_AGE); // Adults, ready to breed
            Mappers.gender.get(newcomer).gender = i % 2 == 0 ? Gender.MALE : Gender.FEMALE;
            migrants++;
        }
    }
}
