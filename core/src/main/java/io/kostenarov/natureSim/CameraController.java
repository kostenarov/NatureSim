package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import io.kostenarov.natureSim.Components.PositionComponent;

/**
 * Moves the world camera: WASD/arrows pan, Q/E zoom, and the camera can smoothly follow a creature.
 * Runs on real time (not simulation steps), so it stays responsive while paused or sped up.
 */
public class CameraController {
    private static final float FOLLOW_SMOOTHNESS = 0.1f; // Lower = smoother, higher = snappier
    private static final float PAN_SPEED = 400f;         // Pixels per second at zoom 1
    private static final float ZOOM_SPEED = 0.6f;        // Zoom change per second
    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 2f;

    private final OrthographicCamera camera;
    private Entity target;
    private boolean following = true;

    public CameraController(OrthographicCamera camera) {
        this.camera = camera;
    }

    /** Follows this creature (null stops following anyone). */
    public void follow(Entity creature) {
        target = creature;
        following = creature != null;
    }

    public Entity getTarget() {
        return target;
    }

    public boolean isFollowing() {
        return following && target != null;
    }

    public void update(float deltaTime) {
        handleKeys(deltaTime);

        if (isFollowing()) {
            PositionComponent pos = target.getComponent(PositionComponent.class);
            float targetX = pos.position.x + World.CREATURE_HALF_SIZE;
            float targetY = pos.position.y + World.CREATURE_HALF_SIZE;
            camera.position.x += (targetX - camera.position.x) * FOLLOW_SMOOTHNESS;
            camera.position.y += (targetY - camera.position.y) * FOLLOW_SMOOTHNESS;
        }

        keepInsideMap();
        camera.update();
    }

    private void handleKeys(float deltaTime) {
        float pan = PAN_SPEED * camera.zoom * deltaTime;
        boolean panned = false;
        if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) {
            camera.position.y += pan;
            panned = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) {
            camera.position.y -= pan;
            panned = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) {
            camera.position.x -= pan;
            panned = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) {
            camera.position.x += pan;
            panned = true;
        }
        if (panned) {
            following = false; // Steering by hand takes over from following
        }

        if (Gdx.input.isKeyPressed(Input.Keys.Q)) {
            camera.zoom = Math.min(MAX_ZOOM, camera.zoom + ZOOM_SPEED * deltaTime);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.E)) {
            camera.zoom = Math.max(MIN_ZOOM, camera.zoom - ZOOM_SPEED * deltaTime);
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) && target != null) {
            following = !following;
        }
    }

    /** Keeps the view over the map, taking zoom and window size into account. */
    private void keepInsideMap() {
        float halfWidth = camera.viewportWidth * camera.zoom / 2f;
        float halfHeight = camera.viewportHeight * camera.zoom / 2f;
        camera.position.x = clampAxis(camera.position.x, halfWidth, World.WIDTH);
        camera.position.y = clampAxis(camera.position.y, halfHeight, World.HEIGHT);
    }

    private static float clampAxis(float value, float halfView, float worldSize) {
        if (halfView * 2f >= worldSize) {
            return worldSize / 2f; // View is wider than the map: center it
        }
        return Math.max(halfView, Math.min(worldSize - halfView, value));
    }
}
