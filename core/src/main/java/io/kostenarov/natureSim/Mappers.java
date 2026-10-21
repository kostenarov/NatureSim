package io.kostenarov.natureSim;

import com.badlogic.ashley.core.ComponentMapper;
import io.kostenarov.natureSim.Components.AgeComponent;
import io.kostenarov.natureSim.Components.BehaviourComponent;
import io.kostenarov.natureSim.Components.CarcassComponent;
import io.kostenarov.natureSim.Components.FoodSourceComponent;
import io.kostenarov.natureSim.Components.GenderComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.LineageComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.PredatorComponent;
import io.kostenarov.natureSim.Components.ReproductionComponent;
import io.kostenarov.natureSim.Components.ScavengerComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Components.VelocityComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Components.WaterSourceComponent;

/**
 * One {@link ComponentMapper} per component type: Ashley's fast, typed way to read a component from an
 * entity ({@code Mappers.position.get(entity)}), instead of looking it up by class each time.
 * {@code get} returns null if the entity doesn't have the component.
 */
public final class Mappers {
    public static final ComponentMapper<AgeComponent> age = ComponentMapper.getFor(AgeComponent.class);
    public static final ComponentMapper<BehaviourComponent> behaviour = ComponentMapper.getFor(BehaviourComponent.class);
    public static final ComponentMapper<CarcassComponent> carcass = ComponentMapper.getFor(CarcassComponent.class);
    public static final ComponentMapper<FoodSourceComponent> foodSource = ComponentMapper.getFor(FoodSourceComponent.class);
    public static final ComponentMapper<GenderComponent> gender = ComponentMapper.getFor(GenderComponent.class);
    public static final ComponentMapper<GenomeComponent> genome = ComponentMapper.getFor(GenomeComponent.class);
    public static final ComponentMapper<LineageComponent> lineage = ComponentMapper.getFor(LineageComponent.class);
    public static final ComponentMapper<PositionComponent> position = ComponentMapper.getFor(PositionComponent.class);
    public static final ComponentMapper<PredatorComponent> predator = ComponentMapper.getFor(PredatorComponent.class);
    public static final ComponentMapper<ReproductionComponent> reproduction = ComponentMapper.getFor(ReproductionComponent.class);
    public static final ComponentMapper<ScavengerComponent> scavenger = ComponentMapper.getFor(ScavengerComponent.class);
    public static final ComponentMapper<StatsComponent> stats = ComponentMapper.getFor(StatsComponent.class);
    public static final ComponentMapper<VelocityComponent> velocity = ComponentMapper.getFor(VelocityComponent.class);
    public static final ComponentMapper<VisionComponent> vision = ComponentMapper.getFor(VisionComponent.class);
    public static final ComponentMapper<WaterSourceComponent> waterSource = ComponentMapper.getFor(WaterSourceComponent.class);

    private Mappers() {
    }
}
