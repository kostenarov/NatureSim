package io.kostenarov.natureSim.Enums;

@SuppressWarnings("all")
public final class EntityBehaviour {
    public static final EntityBehaviour EXPLORING = new EntityBehaviour("EXPLORING");
    public static final EntityBehaviour SEEKING_FOOD = new EntityBehaviour("SEEKING_FOOD");
    public static final EntityBehaviour SEEKING_WATER = new EntityBehaviour("SEEKING_WATER");
    public static final EntityBehaviour SEEKING_MATE = new EntityBehaviour("SEEKING_MATE");
    public static final EntityBehaviour FLEEING = new EntityBehaviour("FLEEING");
    public static final EntityBehaviour IDLE = new EntityBehaviour("IDLE");
    public static final EntityBehaviour HUNTING = new EntityBehaviour("HUNTING");
    public static final EntityBehaviour ATTACKING = new EntityBehaviour("ATTACKING");

    private final String name;

    private EntityBehaviour(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
