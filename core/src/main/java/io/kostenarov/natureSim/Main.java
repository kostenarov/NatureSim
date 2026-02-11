package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.VelocityComponent;
import io.kostenarov.natureSim.Systems.CameraSystem;
import io.kostenarov.natureSim.Systems.MovementSystem;

public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private Texture agentTexture;
    private Engine engine; // The Ashley Engine
    private OrthographicCamera camera;
    private CameraSystem cameraSystem;

    @Override
    public void create() {
        batch = new SpriteBatch();
        agentTexture = new Texture("agent.png"); // Make sure you have an image!
        engine = new Engine();

        // Initialize camera (viewport size matches the game window)
        camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2, 0);
        camera.update();

        // 1. Add the systems to the engine
        engine.addSystem(new MovementSystem());
        cameraSystem = new CameraSystem(camera, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        engine.addSystem(cameraSystem);

        // 2. Create the first agent entity
        Entity agent = engine.createEntity();
        agent.add(new PositionComponent());
        agent.add(new VelocityComponent());

        engine.addEntity(agent);
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.1f, 0.1f, 0.1f, 1);

        // Update the ECS engine (this runs MovementSystem and CameraSystem)
        float deltaTime = Gdx.graphics.getDeltaTime();
        engine.update(deltaTime);

        // Update the batch's projection matrix with the camera
        batch.setProjectionMatrix(camera.combined);

        // Draw the agents
        batch.begin();
        for (Entity entity : engine.getEntitiesFor(Family.all(PositionComponent.class).get())) {
            PositionComponent pos = entity.getComponent(PositionComponent.class);
            batch.draw(agentTexture, pos.position.x, pos.position.y);
        }
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        agentTexture.dispose();
    }
}
