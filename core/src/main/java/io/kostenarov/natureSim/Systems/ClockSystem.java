package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Balance;
import io.kostenarov.natureSim.SimulationConfig;

/**
 * Keeps simulated time: the hour of the day and the season of the year.
 * Night shrinks how far creatures can see (smell is unaffected); seasons change how fast food grows.
 * Either cycle can be switched off, in which case it is always midday / always an average season.
 */
public class ClockSystem extends EntitySystem {
    public static final String[] SEASONS = {"Spring", "Summer", "Autumn", "Winter"};
    private static final int RUN_FIRST = Integer.MIN_VALUE + 1; // Just after the spatial index
    private static final float START_OF_DAY = 0.3f;             // Runs begin in the morning

    private final SimulationConfig config; // Read live: day and year settings can change mid-run
    private float time = 0f;
    // Days and years passed so far. Kept as running totals rather than time / length, so that changing the
    // length of a day or year mid-run changes how fast time passes from then on instead of jumping.
    private float days = START_OF_DAY;
    private float years = 0f;

    public ClockSystem() {
        this(new SimulationConfig());
    }

    public ClockSystem(SimulationConfig config) {
        super(RUN_FIRST);
        this.config = config;
    }

    @Override
    public void update(float deltaTime) {
        time += deltaTime;
        days += deltaTime / Math.max(1f, config.dayLengthMinutes * 60f);
        years += deltaTime / Math.max(1f, config.yearLengthMinutes * 60f);
    }

    /** Simulated seconds since the start. */
    public float getTime() {
        return time;
    }

    /** Days passed, counting from midnight before the run began (the run starts in the morning). */
    public float getDays() {
        return days;
    }

    /** Years passed since the run began. */
    public float getYears() {
        return years;
    }

    /** Puts the clock back to a saved time (see WorldSave). */
    public void restore(float time, float days, float years) {
        this.time = time;
        this.days = days;
        this.years = years;
    }

    /** 0 at midnight, 0.5 at noon, continuing to 1 at the next midnight. */
    public float dayPhase() {
        return days % 1f;
    }

    /** How light it is: 1 at noon, 0 at midnight (always 1 with day/night switched off). */
    public float daylight() {
        if (!config.dayNightEnabled) {
            return 1f;
        }
        return 0.5f - 0.5f * MathUtils.cos(MathUtils.PI2 * dayPhase());
    }

    /** Multiplier for sight range: full by day, {@link Balance#NIGHT_VISION} at midnight. */
    public float visionScale() {
        return Balance.NIGHT_VISION + (1f - Balance.NIGHT_VISION) * daylight();
    }

    /** 0 at the start of spring (the run starts there), continuing to 1 a year later. */
    public float yearPhase() {
        return years % 1f;
    }

    /** Food growth multiplier: peaks at midsummer, bottoms out in deep winter (always 1 with seasons off). */
    public float growthFactor() {
        if (!config.seasonsEnabled) {
            return 1f;
        }
        float middle = (Balance.SUMMER_GROWTH + Balance.WINTER_GROWTH) / 2f;
        float swing = (Balance.SUMMER_GROWTH - Balance.WINTER_GROWTH) / 2f;
        return middle + swing * MathUtils.sin(MathUtils.PI2 * yearPhase()); // Max a quarter-year in, min three quarters in
    }

    /** 0 spring, 1 summer, 2 autumn, 3 winter, each centered on its peak. */
    public int season() {
        return (int) ((yearPhase() + 0.125f) * 4f) % 4;
    }

    public boolean isDayNightEnabled() {
        return config.dayNightEnabled;
    }

    public boolean isSeasonsEnabled() {
        return config.seasonsEnabled;
    }

    /** e.g. "Day 3  14:20" */
    public String describeTime() {
        int day = (int) days + 1;
        int minutesOfDay = (int) (dayPhase() * 24f * 60f);
        return String.format("Day %d  %02d:%02d", day, minutesOfDay / 60, minutesOfDay % 60);
    }
}
