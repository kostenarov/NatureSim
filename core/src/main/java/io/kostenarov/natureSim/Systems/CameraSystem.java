package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import io.kostenarov.natureSim.Components.PositionComponent;

public class CameraSystem extends EntitySystem {
    private OrthographicCamera camera;
    private float viewportWidth;
    private float viewportHeight;
    private Entity targetEntity;
    private float cameraSmoothness = 0.1f; // Lower = smoother, higher = snappier
    private float cameraSpeed = 200f; // Speed of manual camera movement in pixels/second
    private boolean autoFollowEnabled = true; // Toggle between auto-follow and manual control

    // Map boundaries
    private static final float MAP_MAX_X = MovementSystem.getMapMaxX();
    private static final float MAP_MAX_Y = MovementSystem.getMapMaxY();

    public CameraSystem(OrthographicCamera camera, float viewportWidth, float viewportHeight) {
        this.camera = camera;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        // Target the first entity found with PositionComponent
        for (Entity entity : engine.getEntitiesFor(Family.all(PositionComponent.class).get())) {
            targetEntity = entity;
            break;
        }
    }

    @Override
    public void update(float deltaTime) {
        // Handle keyboard input
        handleKeyboardInput(deltaTime);

        // Auto-follow target entity if enabled
        if (autoFollowEnabled && targetEntity != null) {
            PositionComponent pos = targetEntity.getComponent(PositionComponent.class);

            // Calculate the target camera position (center on the entity)
            float targetX = pos.position.x + 16; // Center of 32x32 sprite
            float targetY = pos.position.y + 16;

            // Smooth camera movement using linear interpolation
            float newCameraX = camera.position.x + (targetX - camera.position.x) * cameraSmoothness;
            float newCameraY = camera.position.y + (targetY - camera.position.y) * cameraSmoothness;

            // Clamp camera position to keep it within map bounds
            newCameraX = Math.max(viewportWidth / 2, Math.min(MAP_MAX_X - viewportWidth / 2, newCameraX));
            newCameraY = Math.max(viewportHeight / 2, Math.min(MAP_MAX_Y - viewportHeight / 2, newCameraY));

            camera.position.x = newCameraX;
            camera.position.y = newCameraY;
        }

        camera.update();
    }

    /**
     * Handle keyboard input for manual camera control
     */
    private void handleKeyboardInput(float deltaTime) {
        float moveAmount = cameraSpeed * deltaTime;

        // Arrow keys or WASD for camera movement
        if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) {
            camera.position.y += moveAmount;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) {
            camera.position.y -= moveAmount;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) {
            camera.position.x -= moveAmount;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) {
            camera.position.x += moveAmount;
        }

        // Toggle auto-follow with spacebar
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            autoFollowEnabled = !autoFollowEnabled;
        }

        // Clamp camera position to keep it within map bounds
        camera.position.x = Math.max(viewportWidth / 2, Math.min(MAP_MAX_X - viewportWidth / 2, camera.position.x));
        camera.position.y = Math.max(viewportHeight / 2, Math.min(MAP_MAX_Y - viewportHeight / 2, camera.position.y));
    }

    public void setTargetEntity(Entity entity) {
        this.targetEntity = entity;
    }

    public void setCameraSmoothness(float smoothness) {
        this.cameraSmoothness = smoothness;
    }

    public void setCameraSpeed(float speed) {
        this.cameraSpeed = speed;
    }

    public void setAutoFollowEnabled(boolean enabled) {
        this.autoFollowEnabled = enabled;
    }

    public boolean isAutoFollowEnabled() {
        return autoFollowEnabled;
    }
}

