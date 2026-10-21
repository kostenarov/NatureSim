package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.FloatArray;
import io.kostenarov.natureSim.Components.AgeComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Creatures;
import io.kostenarov.natureSim.Mappers;

/**
 * Records how the population and its genes change over simulated time, for the on-screen graphs.
 * Samples at a fixed interval; when the history is full it halves its resolution, so a whole run always fits.
 */
public class PopulationHistory extends EntitySystem {
    public static final int GENE_COUNT = new GenomeComponent().genes.length;
    public static final String[] GENE_NAMES = {
        "Speed", "Vision", "Size", "Metabolism", "Recovery", "Hunger sense", "Thirst sense", "Scent", "Stealth", "Ornament"
    };
    private static final int MAX_SAMPLES = 400;
    private static final float START_INTERVAL = 2f; // Seconds between samples at first

    public final FloatArray times = new FloatArray();       // Simulated seconds at each sample
    public final FloatArray prey = new FloatArray();
    public final FloatArray predators = new FloatArray();
    public final FloatArray scavengers = new FloatArray();
    public final FloatArray maxGeneration = new FloatArray();
    public final FloatArray[] preyGeneMeans = newSeries();
    public final FloatArray[] predatorGeneMeans = newSeries();
    public final FloatArray[] scavengerGeneMeans = newSeries();

    private float sampleInterval = START_INTERVAL;
    private float timer = START_INTERVAL; // Take the first sample straight away
    private float elapsed = 0f;
    private ImmutableArray<Entity> preyEntities;
    private ImmutableArray<Entity> predatorEntities;
    private ImmutableArray<Entity> scavengerEntities;
    private final float[] sums = new float[GENE_COUNT];

    private static FloatArray[] newSeries() {
        FloatArray[] series = new FloatArray[GENE_COUNT];
        for (int i = 0; i < GENE_COUNT; i++) {
            series[i] = new FloatArray();
        }
        return series;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        preyEntities = engine.getEntitiesFor(Creatures.PREY);
        predatorEntities = engine.getEntitiesFor(Creatures.PREDATORS);
        scavengerEntities = engine.getEntitiesFor(Creatures.SCAVENGERS);
    }

    @Override
    public void update(float deltaTime) {
        elapsed += deltaTime;
        timer += deltaTime;
        if (timer < sampleInterval) {
            return;
        }
        timer = 0f;

        times.add(elapsed);
        prey.add(preyEntities.size());
        predators.add(predatorEntities.size());
        scavengers.add(scavengerEntities.size());
        maxGeneration.add(Math.max(highestGeneration(preyEntities),
            Math.max(highestGeneration(predatorEntities), highestGeneration(scavengerEntities))));
        recordGeneMeans(preyEntities, preyGeneMeans);
        recordGeneMeans(predatorEntities, predatorGeneMeans);
        recordGeneMeans(scavengerEntities, scavengerGeneMeans);

        if (prey.size >= MAX_SAMPLES) {
            halveResolution();
        }
    }

    /** Simulated seconds covered by the history. */
    public float getElapsed() {
        return elapsed;
    }

    public int sampleCount() {
        return prey.size;
    }

    /** {elapsed, sampleInterval, timer}, for saving a world. */
    public float[] getTiming() {
        return new float[] {elapsed, sampleInterval, timer};
    }

    /** Puts the sampling clock back to a saved state (the series themselves are public). */
    public void restoreTiming(float elapsed, float sampleInterval, float timer) {
        this.elapsed = elapsed;
        this.sampleInterval = sampleInterval;
        this.timer = timer;
    }

    private void recordGeneMeans(ImmutableArray<Entity> creatures, FloatArray[] series) {
        java.util.Arrays.fill(sums, 0f);
        for (Entity creature : creatures) {
            float[] genes = Mappers.genome.get(creature).genes;
            for (int g = 0; g < GENE_COUNT; g++) {
                sums[g] += genes[g];
            }
        }
        for (int g = 0; g < GENE_COUNT; g++) {
            // With nobody left, carry the last value on so the line doesn't drop to zero
            float last = series[g].size > 0 ? series[g].peek() : 1f;
            series[g].add(creatures.size() > 0 ? sums[g] / creatures.size() : last);
        }
    }

    private static int highestGeneration(ImmutableArray<Entity> creatures) {
        int highest = 0;
        for (Entity creature : creatures) {
            AgeComponent age = Mappers.age.get(creature);
            if (age != null) {
                highest = Math.max(highest, age.generation);
            }
        }
        return highest;
    }

    /** Merges neighbouring samples pairwise and doubles the sampling interval. */
    private void halveResolution() {
        halve(times);
        halve(prey);
        halve(predators);
        halve(scavengers);
        halve(maxGeneration);
        for (int g = 0; g < GENE_COUNT; g++) {
            halve(preyGeneMeans[g]);
            halve(predatorGeneMeans[g]);
            halve(scavengerGeneMeans[g]);
        }
        sampleInterval *= 2f;
    }

    private static void halve(FloatArray series) {
        int half = series.size / 2;
        for (int i = 0; i < half; i++) {
            series.items[i] = (series.items[2 * i] + series.items[2 * i + 1]) / 2f;
        }
        if (series.size % 2 == 1) {
            series.items[half] = series.items[series.size - 1];
            half++;
        }
        series.size = half;
    }
}
