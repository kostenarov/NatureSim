package io.kostenarov.natureSim;

/**
 * Fixed dimensions of the simulated world, shared by every system.
 */
public final class World {
    public static final float WIDTH = 5120f;
    public static final float HEIGHT = 5120f;
    public static final float DIAGONAL = (float) Math.hypot(WIDTH, HEIGHT);

    /** Creature sprites are 32x32; a creature's position is its bottom-left corner. */
    public static final float CREATURE_SIZE = 32f;
    public static final float CREATURE_HALF_SIZE = CREATURE_SIZE / 2f;

    private World() {
    }
}
