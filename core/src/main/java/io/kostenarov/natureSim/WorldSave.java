package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.SerializationException;
import io.kostenarov.natureSim.Components.AgeComponent;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.CarcassComponent;
import io.kostenarov.natureSim.Components.FoodSourceComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.LineageComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.PredatorComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Components.WaterSourceComponent;
import io.kostenarov.natureSim.Enums.EntityBehaviour;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Enums.Role;
import io.kostenarov.natureSim.Systems.CarcassSystem;
import io.kostenarov.natureSim.Systems.ClockSystem;
import io.kostenarov.natureSim.Systems.FoodSourceSystem;
import io.kostenarov.natureSim.Systems.LineageSystem;
import io.kostenarov.natureSim.Systems.MigrationSystem;
import io.kostenarov.natureSim.Systems.PopulationHistory;
import io.kostenarov.natureSim.Systems.SpeciesSystem;
import io.kostenarov.natureSim.Systems.WaterSourceSystem;

import java.io.StringWriter;

/**
 * Saves a running world as JSON and builds it again from that: the settings, the clock, every pond, food item,
 * carcass and creature (genes, needs, age, family), the family tree, the history graphs and the random number
 * generator's state. What a creature was in the middle of (where it was walking to, a pause to look around)
 * isn't saved; it simply decides again on the first step after loading. Species are worked out afresh, so their
 * numbers may change.
 */
public final class WorldSave {
    private static final int VERSION = 1;
    private static final Family CREATURES = Family.all(PositionComponent.class, StatsComponent.class).get();
    private static final Family FOOD = Family.all(FoodSourceComponent.class, PositionComponent.class).get();

    /** A world built from a save. */
    public static final class Loaded {
        public final Engine engine;
        public final SimulationConfig config;
        public final long seed;

        Loaded(Engine engine, SimulationConfig config, long seed) {
            this.engine = engine;
            this.config = config;
            this.seed = seed;
        }
    }

    private WorldSave() {
    }

    /** The simulation's random number generator (libGDX's default kind, whose state can be saved). */
    private static RandomXS128 random() {
        return (RandomXS128) MathUtils.random;
    }

    // --- Saving -----------------------------------------------------------------------------------------

    public static String save(Engine engine, SimulationConfig config, long seed) {
        StringWriter out = new StringWriter();
        Json json = new Json(JsonWriter.OutputType.json);
        json.setWriter(out);
        json.writeObjectStart();
        json.writeValue("version", VERSION);
        json.writeValue("seed", seed);
        json.writeValue("random0", random().getState(0));
        json.writeValue("random1", random().getState(1));
        json.writeValue("config", config, SimulationConfig.class);
        writeSystems(json, engine);

        json.writeArrayStart("ponds");
        for (Entity pond : engine.getEntitiesFor(WaterSourceSystem.WATER_FAMILY)) {
            PositionComponent pos = Mappers.position.get(pond);
            json.writeValue(new float[] {pos.position.x, pos.position.y, Mappers.waterSource.get(pond).radius});
        }
        json.writeArrayEnd();

        json.writeArrayStart("food");
        for (Entity food : engine.getEntitiesFor(FOOD)) {
            FoodSourceComponent item = Mappers.foodSource.get(food);
            if (!item.consumed) {
                PositionComponent pos = Mappers.position.get(food);
                json.writeValue(new float[] {pos.position.x, pos.position.y, item.nutritionValue});
            }
        }
        json.writeArrayEnd();

        json.writeArrayStart("carcasses");
        for (Entity carcass : engine.getEntitiesFor(CarcassSystem.CARCASSES)) {
            PositionComponent pos = Mappers.position.get(carcass);
            CarcassComponent remains = Mappers.carcass.get(carcass);
            json.writeValue(new float[] {pos.position.x, pos.position.y, remains.food, remains.age});
        }
        json.writeArrayEnd();

        json.writeArrayStart("creatures");
        for (Entity creature : engine.getEntitiesFor(CREATURES)) {
            writeCreature(json, creature);
        }
        json.writeArrayEnd();

        json.writeObjectEnd();
        return out.toString();
    }

    private static void writeSystems(Json json, Engine engine) {
        ClockSystem clock = engine.getSystem(ClockSystem.class);
        if (clock != null) {
            json.writeValue("clock", new float[] {clock.getTime(), clock.getDays(), clock.getYears()});
        }
        FoodSourceSystem food = engine.getSystem(FoodSourceSystem.class);
        if (food != null) {
            json.writeValue("foodTimer", food.getSpawnTimer());
        }
        MigrationSystem migration = engine.getSystem(MigrationSystem.class);
        if (migration != null) {
            json.writeValue("migrationTimer", migration.getTimer());
            json.writeValue("migrants", migration.getMigrants());
        }
        LineageSystem lineage = engine.getSystem(LineageSystem.class);
        if (lineage != null) {
            json.writeValue("nextId", lineage.peekNextId());
            json.writeArrayStart("parents"); // Flattened: id, mother, father, id, mother, father...
            for (LongMap.Entry<long[]> birth : lineage.getParents()) {
                json.writeValue(birth.key);
                json.writeValue(birth.value[0]);
                json.writeValue(birth.value[1]);
            }
            json.writeArrayEnd();
        }
        PopulationHistory history = engine.getSystem(PopulationHistory.class);
        if (history != null) {
            json.writeObjectStart("history");
            json.writeValue("timing", history.getTiming());
            json.writeValue("times", history.times.toArray());
            json.writeValue("prey", history.prey.toArray());
            json.writeValue("predators", history.predators.toArray());
            json.writeValue("scavengers", history.scavengers.toArray());
            json.writeValue("maxGeneration", history.maxGeneration.toArray());
            writeSeries(json, "preyGenes", history.preyGeneMeans);
            writeSeries(json, "predatorGenes", history.predatorGeneMeans);
            writeSeries(json, "scavengerGenes", history.scavengerGeneMeans);
            json.writeObjectEnd();
        }
    }

    private static void writeSeries(Json json, String name, FloatArray[] series) {
        json.writeArrayStart(name);
        for (FloatArray gene : series) {
            json.writeValue(gene.toArray());
        }
        json.writeArrayEnd();
    }

    private static void writeCreature(Json json, Entity creature) {
        PositionComponent pos = Mappers.position.get(creature);
        StatsComponent stats = Mappers.stats.get(creature);
        BehaviourComponent behaviour = Mappers.behaviour.get(creature);
        AgeComponent age = Mappers.age.get(creature);
        LineageComponent family = Mappers.lineage.get(creature);

        json.writeObjectStart();
        json.writeValue("role", Creatures.role(creature).name());
        json.writeValue("gender", Mappers.gender.get(creature).gender.name());
        json.writeValue("x", pos.position.x);
        json.writeValue("y", pos.position.y);
        json.writeValue("genes", Mappers.genome.get(creature).genes);
        json.writeValue("stats", new float[] {stats.hunger, stats.thirst, stats.energy, stats.health});
        json.writeValue("facing", Mappers.vision.get(creature).directionAngle);
        json.writeValue("behaviour", behaviour.behaviour.name());
        json.writeValue("exhausted", behaviour.exhausted);
        json.writeValue("fleeTimer", behaviour.fleeTimer);
        json.writeValue("lastThreat", new float[] {behaviour.lastThreat.x, behaviour.lastThreat.y});
        json.writeValue("matingCooldown", Mappers.reproduction.get(creature).matingCooldown);
        if (age != null) {
            json.writeValue("age", new float[] {age.age, age.lifespan, age.generation});
        }
        if (family != null) {
            json.writeValue("family", new long[] {family.id, family.motherId, family.fatherId, family.children});
        }
        PredatorComponent predator = Mappers.predator.get(creature);
        if (predator != null) {
            json.writeValue("attackTimer", predator.attackTimer);
            json.writeValue("eatingTimer", predator.eatingTimer);
        }
        json.writeObjectEnd();
    }

    // --- Loading ----------------------------------------------------------------------------------------

    /** Builds the saved world. Throws a SerializationException if the text isn't a save this version can read. */
    public static Loaded load(String text) {
        JsonValue root = new JsonReader().parse(text);
        if (root == null || root.getInt("version", -1) != VERSION) {
            throw new SerializationException("not a NatureSim save (or from another version)");
        }
        Json json = new Json();
        SimulationConfig config = json.readValue(SimulationConfig.class, root.get("config"));

        // Build the systems without their usual starting ponds and food: the saved ones are added instead
        int pondCount = config.pondCount;
        int initialFood = config.initialFood;
        config.pondCount = 0;
        config.initialFood = 0;
        Engine engine = Simulation.createEngine(config);
        config.pondCount = pondCount;
        config.initialFood = initialFood;

        for (JsonValue pond : root.get("ponds")) {
            float[] p = pond.asFloatArray();
            Entity entity = engine.createEntity();
            PositionComponent pos = new PositionComponent();
            pos.position.set(p[0], p[1]);
            entity.add(pos);
            entity.add(new WaterSourceComponent(p[2]));
            engine.addEntity(entity);
        }
        for (JsonValue food : root.get("food")) {
            float[] f = food.asFloatArray();
            Entity entity = engine.createEntity();
            PositionComponent pos = new PositionComponent();
            pos.position.set(f[0], f[1]);
            entity.add(pos);
            FoodSourceComponent item = new FoodSourceComponent();
            item.nutritionValue = f[2];
            entity.add(item);
            engine.addEntity(entity);
        }
        for (JsonValue carcass : root.get("carcasses")) {
            float[] c = carcass.asFloatArray();
            Entity entity = engine.createEntity();
            PositionComponent pos = new PositionComponent();
            pos.position.set(c[0], c[1]);
            entity.add(pos);
            CarcassComponent remains = new CarcassComponent(c[2]);
            remains.age = c[3];
            entity.add(remains);
            engine.addEntity(entity);
        }
        for (JsonValue creature : root.get("creatures")) {
            readCreature(engine, creature);
        }
        readSystems(engine, root);

        // Last, so building the world above doesn't use up random numbers the saved run hadn't
        random().setState(root.getLong("random0"), root.getLong("random1"));
        SpeciesSystem species = engine.getSystem(SpeciesSystem.class);
        if (species != null) {
            species.classify();
        }
        return new Loaded(engine, config, root.getLong("seed"));
    }

    private static void readSystems(Engine engine, JsonValue root) {
        ClockSystem clock = engine.getSystem(ClockSystem.class);
        if (clock != null && root.has("clock")) {
            float[] c = root.get("clock").asFloatArray();
            clock.restore(c[0], c[1], c[2]);
        }
        FoodSourceSystem food = engine.getSystem(FoodSourceSystem.class);
        if (food != null) {
            food.setSpawnTimer(root.getFloat("foodTimer", 0f));
        }
        MigrationSystem migration = engine.getSystem(MigrationSystem.class);
        if (migration != null) {
            migration.restore(root.getFloat("migrationTimer", 0f), root.getInt("migrants", 0));
        }
        LineageSystem lineage = engine.getSystem(LineageSystem.class);
        if (lineage != null) {
            lineage.setNextId(root.getLong("nextId", 1L));
            long[] parents = root.get("parents").asLongArray();
            for (int i = 0; i + 2 < parents.length; i += 3) {
                lineage.restoreParents(parents[i], parents[i + 1], parents[i + 2]);
            }
        }
        PopulationHistory history = engine.getSystem(PopulationHistory.class);
        JsonValue saved = root.get("history");
        if (history != null && saved != null) {
            float[] timing = saved.get("timing").asFloatArray();
            history.restoreTiming(timing[0], timing[1], timing[2]);
            history.times.addAll(saved.get("times").asFloatArray());
            history.prey.addAll(saved.get("prey").asFloatArray());
            history.predators.addAll(saved.get("predators").asFloatArray());
            history.scavengers.addAll(saved.get("scavengers").asFloatArray());
            history.maxGeneration.addAll(saved.get("maxGeneration").asFloatArray());
            readSeries(saved.get("preyGenes"), history.preyGeneMeans);
            readSeries(saved.get("predatorGenes"), history.predatorGeneMeans);
            readSeries(saved.get("scavengerGenes"), history.scavengerGeneMeans);
        }
    }

    private static void readSeries(JsonValue saved, FloatArray[] series) {
        int g = 0;
        for (JsonValue gene : saved) {
            if (g < series.length) {
                series[g++].addAll(gene.asFloatArray());
            }
        }
    }

    private static void readCreature(Engine engine, JsonValue saved) {
        GenomeComponent dna = new GenomeComponent(saved.get("genes").asFloatArray());
        Entity creature = Creatures.create(engine, saved.getFloat("x"), saved.getFloat("y"), dna);
        Role role = Role.valueOf(saved.getString("role"));
        if (role == Role.PREDATOR) {
            Creatures.makePredator(creature);
            PredatorComponent predator = Mappers.predator.get(creature);
            predator.attackTimer = saved.getFloat("attackTimer", 0f);
            predator.eatingTimer = saved.getFloat("eatingTimer", 0f);
        } else if (role == Role.SCAVENGER) {
            Creatures.makeScavenger(creature);
        }
        Mappers.gender.get(creature).gender = Gender.valueOf(saved.getString("gender"));

        float[] s = saved.get("stats").asFloatArray();
        StatsComponent stats = Mappers.stats.get(creature);
        stats.hunger = s[0];
        stats.thirst = s[1];
        stats.energy = s[2];
        stats.health = s[3];

        float facing = saved.getFloat("facing", 0f);
        Mappers.vision.get(creature).directionAngle = facing;
        Mappers.velocity.get(creature).velocity.setZero();
        BehaviourComponent behaviour = Mappers.behaviour.get(creature);
        behaviour.directionAngle = facing;
        behaviour.behaviour = EntityBehaviour.valueOf(saved.getString("behaviour", EntityBehaviour.IDLE.name()));
        behaviour.exhausted = saved.getBoolean("exhausted", false);
        behaviour.fleeTimer = saved.getFloat("fleeTimer", 0f);
        float[] threat = saved.get("lastThreat").asFloatArray();
        behaviour.lastThreat.set(threat[0], threat[1]);
        Mappers.reproduction.get(creature).matingCooldown = saved.getFloat("matingCooldown", 0f);

        if (saved.has("age")) {
            float[] a = saved.get("age").asFloatArray();
            AgeComponent age = Mappers.age.get(creature);
            age.age = a[0];
            age.lifespan = a[1];
            age.generation = (int) a[2];
        }
        if (saved.has("family")) {
            long[] f = saved.get("family").asLongArray();
            LineageComponent family = new LineageComponent(f[0]); // Replaces the new id Creatures.create handed out
            family.motherId = f[1];
            family.fatherId = f[2];
            family.children = (int) f[3];
            creature.add(family);
        }
        engine.addEntity(creature);
    }
}
