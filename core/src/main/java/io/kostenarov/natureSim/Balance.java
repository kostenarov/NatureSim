package io.kostenarov.natureSim;

/**
 * The simulation's tuning numbers in one place. Change these to rebalance the ecosystem.
 * The most commonly adjusted ones are on the start screen instead (see {@link SimulationConfig}),
 * and how genes turn into abilities and costs lives in {@link Traits}.
 * Hunger, thirst, energy and health all range from 0 to 100.
 */
public final class Balance {
    private Balance() {
    }

    // --- Reproduction -----------------------------------------------------------------------------------
    public static final float MATING_DISTANCE = 50f;            // How close two partners must be to mate
    public static final float MATING_ENERGY_COST = 25f;         // Energy each parent spends
    public static final float MATING_HUNGER_COST = 25f;         // Food each parent gives up; a newborn starts with both shares
    public static final float MATING_MIN_HUNGER = 50f;          // Must be at least this well fed to mate
    public static final float PREDATOR_MATING_MIN_HUNGER = 70f; // Predators must be better fed, or they out-breed their prey
    public static final float MATING_MIN_THIRST = 40f;          // Must be at least this well watered to mate
    public static final float MATURITY_AGE = 30f;               // Seconds after birth before a creature can mate

    // --- Evolution and species --------------------------------------------------------------------------
    public static final float FOUNDER_SPREAD = 0.5f;            // Starting genes vary this much either side of average (1)
    public static final float MAX_MATE_DISTANCE = 2f;           // Partners whose genes differ more than this can't breed
    public static final float MATE_SIMILARITY_PREFERENCE = 0.5f; // How much females favour similar males over showy ones
    public static final float SPECIES_RADIUS = 1.5f;            // Creatures this close to a species' average genes belong to it
    public static final int MIN_NEW_SPECIES_SIZE = 8;           // A new species needs at least this many members to count
    public static final float SPECIES_CHECK_SECONDS = 5f;       // How often species are worked out

    // --- Energy and rest --------------------------------------------------------------------------------
    // A creature that runs out of energy rests, whatever else it needs, until it has recovered this much.
    // Without the gap it would stagger: one step, out of energy, one step...
    public static final float EXHAUSTION_RECOVERY_THRESHOLD = 30f;
    public static final float IDLE_RESUME_THRESHOLD = 80f;      // With nothing to do, a resting creature stays put until this fresh
    public static final float LOOK_AROUND_DURATION = 0.5f;      // Pause at each wander target before picking the next

    // --- Getting around ---------------------------------------------------------------------------------
    public static final float AVOID_RANGE = 48f;                // Walkers swerve around standing creatures this close ahead
    public static final float AVOID_STRENGTH = 1.5f;            // How hard they swerve
    public static final float STUCK_DISTANCE = 8f;             // Moving less than this...
    public static final float STUCK_SECONDS = 1.5f;             // ...for this long means a target is unreachable
    public static final float IGNORE_BLOCKED_FOOD_SECONDS = 3f; // Food blocked by a crowd is left alone this long

    // --- Prey -------------------------------------------------------------------------------------------
    public static final float ESCAPE_SPEED_MULTIPLIER = 1.25f;  // Prey run faster than they walk
    public static final float FLEE_MEMORY = 2f;                 // Seconds a prey keeps fleeing after losing sight of a threat
    public static final float WALL_AVOID_DISTANCE = 150f;       // Fleeing prey slide along map edges this close
    public static final float FOOD_CONSUMPTION_DISTANCE = 32f;  // How close prey must be to eat
    public static final float HERD_RADIUS = 120f;               // Prey this close count as part of the herd
    public static final float HERD_COHESION = 0.4f;             // How strongly fleeing prey lean toward their herd
    public static final float ALARM_DECAY = 0.4f;               // Seconds of fear lost each time panic spreads to a neighbour
    public static final float MIN_ALARM = 0.5f;                 // Panic caught from a neighbour lasts at least this long
    public static final float PREY_SCENT_SHARE = 0.2f;          // Prey smell predators at this share of a predator's scent range

    // --- Predators --------------------------------------------------------------------------------------
    public static final float ATTACK_RANGE = 30f;
    public static final float ATTACK_DAMAGE = 40f;              // Per bite, before size differences (see Traits.biteDamage)
    public static final float ATTACK_COOLDOWN = 0.75f;          // Seconds between bites
    public static final float KILL_FOOD_GAIN = 45f;             // Energy and food a predator gets from a kill
    public static final float HUNT_SPEED_MULTIPLIER = 1.35f;    // Predators sprint when chasing
    public static final float HUNT_APPETITE = 1.15f;            // Hunt only when there's room for this many meals' worth of food
    public static final float SCENT_REACH = 24f;                // How close to a scent counts as arriving at it
    public static final float MATE_SCENT_FACTOR = 3f;           // A mate's scent carries this much further than prey's

    // --- Carcasses and scavengers -----------------------------------------------------------------------
    public static final float CARCASS_FOOD = 40f;               // Food left by a creature that died naturally
    public static final float KILL_CARCASS_FOOD = 25f;          // Leftovers after a predator has eaten its kill
    public static final float CARCASS_ROT_SECONDS = 60f;        // A carcass rots away completely in this time
    public static final float SCAVENGER_BITE_RATE = 20f;        // Food a scavenger eats per second
    public static final float SCAVENGER_FOLLOW_DISTANCE = 180f; // Hungry scavengers trail predators at this distance

    // --- Migration --------------------------------------------------------------------------------------
    public static final float MIGRATION_CHECK_SECONDS = 20f;    // How often a near-extinct species may get newcomers
    public static final int PREY_MIGRATION_THRESHOLD = 20;      // Below this many prey, a group of newcomers arrives
    public static final int PREY_MIGRANTS = 10;
    public static final int OTHER_MIGRATION_THRESHOLD = 3;      // Same for predators and scavengers
    public static final int OTHER_MIGRANTS = 2;                 // Always a male and a female

    // --- Water ------------------------------------------------------------------------------------------
    public static final float DRINK_RATE = 25f;                 // Thirst restored per second while drinking
    public static final float WATER_FULL_THRESHOLD = 98f;       // Stop drinking once thirst is back above this
    public static final float DRINK_MARGIN = 24f;               // How far outside the shore a creature can still drink
    public static final float SHALLOW_WIDTH = 40f;              // Wadeable band inside the shore; deeper water is impassable
    public static final float WATERHOLE_RANGE = 300f;           // Within this of a shore counts as "at the waterhole"

    // --- Day, night and seasons -------------------------------------------------------------------------
    public static final float NIGHT_VISION = 0.45f;             // Share of normal sight range left at midnight
    public static final float SUMMER_GROWTH = 1.6f;             // Food grows this many times faster at midsummer...
    public static final float WINTER_GROWTH = 0.25f;            // ...and this much slower in deep winter

    // --- Food -------------------------------------------------------------------------------------------
    public static final float NEAR_WATER_CHANCE = 0.7f;         // Share of food that grows in the fertile band around ponds
    public static final float FERTILE_BAND_WIDTH = 350f;        // How far from the shore that band reaches
}
