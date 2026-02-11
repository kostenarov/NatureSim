package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import space.earlygrey.shapedrawer.ShapeDrawer;

/**
 * VisionRenderingSystem - Handles vision cone visualization for agents.
 * Note: Vision cone rendering is currently handled in Main.java render loop
 * to ensure proper batch management with ShapeDrawer.
 */
public class VisionRenderingSystem extends IteratingSystem {
    private ShapeDrawer shapeDrawer;
    private OrthographicCamera camera;

    public VisionRenderingSystem(ShapeDrawer shapeDrawer, OrthographicCamera camera) {
        super(Family.all(PositionComponent.class, VisionComponent.class).get());
        this.shapeDrawer = shapeDrawer;
        this.camera = camera;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        // This system exists for potential future vision-related processing
    }
}

