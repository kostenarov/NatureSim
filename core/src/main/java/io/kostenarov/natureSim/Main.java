package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.GenderComponent;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Components.VelocityComponent;
import io.kostenarov.natureSim.Components.VisionComponent;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Systems.CameraSystem;
import io.kostenarov.natureSim.Systems.MovementSystem;
import io.kostenarov.natureSim.Systems.VisionRenderingSystem;
import space.earlygrey.shapedrawer.ShapeDrawer;

public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private Texture agentTexture;
    private Engine engine; // The Ashley Engine
    private OrthographicCamera camera;
    private OrthographicCamera uiCamera;
    private CameraSystem cameraSystem;
    private ShapeDrawer shapeDrawer;
    private Texture whitePixel; // 1x1 white pixel texture for ShapeDrawer
    private BitmapFont font;
    private Entity selectedEntity;

    @Override
    public void create() {
        batch = new SpriteBatch();
        agentTexture = new Texture("agent.png"); // Make sure you have an image!
        engine = new Engine();

        // Create a 1x1 white pixel texture for ShapeDrawer
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(1, 1, 1, 1); // White with full opacity
        pixmap.fill();
        whitePixel = new Texture(pixmap);
        pixmap.dispose();

        // Initialize ShapeDrawer for vision cone visualization
        shapeDrawer = new ShapeDrawer(batch, new TextureRegion(whitePixel));

        // Initialize camera (viewport size matches the game window)
        camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2, 0);
        camera.update();

        // UI camera (screen space)
        uiCamera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiCamera.position.set(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2, 0);
        uiCamera.update();

        font = new BitmapFont();

        // 1. Add the systems to the engine
        engine.addSystem(new MovementSystem());
        cameraSystem = new CameraSystem(camera, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        engine.addSystem(cameraSystem);
        engine.addSystem(new VisionRenderingSystem(shapeDrawer, camera));

        // 2. Create the first agents
        createAgent(100, 100, 150, 150f, 120f);
        createAgent(100, 200, 100, 300f, 100f);
    }

    private void createAgent(float x, float y, float speed, float visionRange, float visionAngle) {
        Entity agent = engine.createEntity();
        PositionComponent pos = new PositionComponent();
        pos.position.set(x, y);
        agent.add(pos);

        VelocityComponent vel = new VelocityComponent();
        vel.velocity.set(speed, 0);
        agent.add(vel);

        GenomeComponent dna = new GenomeComponent();
        dna.genes[GenomeComponent.SPEED] = speed / 250f;
        agent.add(dna);

        GenderComponent gender = new GenderComponent();
        agent.add(gender);

        StatsComponent stats = new StatsComponent();
        agent.add(stats);

        VisionComponent vision = new VisionComponent(visionRange, visionAngle);
        agent.add(vision);

        engine.addEntity(agent);
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.30f, 0.40f, 0.30f, 1f); // Muddy background

        // Update the ECS engine (this runs MovementSystem and CameraSystem)
        float deltaTime = Gdx.graphics.getDeltaTime();
        engine.update(deltaTime);

        // Handle selection clicks
        if (Gdx.input.justTouched()) {
            float screenX = Gdx.input.getX();
            float screenY = Gdx.input.getY();
            selectEntityAtScreen(screenX, screenY);
        }

        // Set the camera's projection matrix for all rendering
        batch.setProjectionMatrix(camera.combined);

        // Draw the vision cones (rendered before agents so they appear behind)
        // This needs to be done with the batch active for ShapeDrawer
        batch.begin();
        batch.flush(); // Flush any existing batch rendering

        // Update vision cones
        for (Entity entity : engine.getEntitiesFor(Family.all(PositionComponent.class, VisionComponent.class).get())) {
            PositionComponent pos = entity.getComponent(PositionComponent.class);
            VisionComponent vision = entity.getComponent(VisionComponent.class);

            // Draw the vision cone
            drawVisionCone(pos.position.x + 16, pos.position.y + 16, vision);
        }

        batch.flush(); // Flush before switching to agent rendering

        // Draw the agents
        for (Entity entity : engine.getEntitiesFor(Family.all(PositionComponent.class).get())) {
            PositionComponent pos = entity.getComponent(PositionComponent.class);
            batch.draw(agentTexture, pos.position.x, pos.position.y);
        }
        batch.end();

        // Draw UI on top
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        drawStatsPanel();
        batch.end();
    }

    private void selectEntityAtScreen(float screenX, float screenY) {
        // Convert screen to world coordinates
        Vector3 worldCoords = camera.unproject(new Vector3(screenX, screenY, 0));

        Entity picked = null;
        for (Entity entity : engine.getEntitiesFor(Family.all(PositionComponent.class, StatsComponent.class).get())) {
            PositionComponent pos = entity.getComponent(PositionComponent.class);
            float x = pos.position.x;
            float y = pos.position.y;
            float size = 32f; // Agent sprite size

            if (worldCoords.x >= x && worldCoords.x <= x + size && worldCoords.y >= y && worldCoords.y <= y + size) {
                picked = entity;
                break;
            }
        }

        selectedEntity = picked;
    }

    private void drawStatsPanel() {
        if (selectedEntity == null) {
            return;
        }

        StatsComponent stats = selectedEntity.getComponent(StatsComponent.class);
        if (stats == null) {
            return;
        }

        float panelWidth = 220f;
        float panelHeight = 120f;
        float padding = 10f;
        float x = padding;
        float y = uiCamera.viewportHeight - panelHeight - padding;

        Color panelBg = new Color(0.08f, 0.08f, 0.08f, 0.7f);
        Color panelBorder = new Color(0.6f, 0.6f, 0.6f, 0.8f);

        shapeDrawer.filledRectangle(x, y, panelWidth, panelHeight, panelBg);
        shapeDrawer.rectangle(x, y, panelWidth, panelHeight, panelBorder, 1f);

        font.setColor(Color.WHITE);
        float textX = x + 10f;
        float textY = y + panelHeight - 10f;

        font.draw(batch, "STATS", textX, textY);
        textY -= 20f;
        font.draw(batch, "Hunger: " + (int) stats.hunger, textX, textY);
        textY -= 16f;
        font.draw(batch, "Thirst: " + (int) stats.thirst, textX, textY);
        textY -= 16f;
        font.draw(batch, "Energy: " + (int) stats.energy, textX, textY);
        textY -= 16f;
        font.draw(batch, "Health: " + (int) stats.health, textX, textY);
    }

    /**
     * Draw a cone representing the agent's vision field
     */
    private void drawVisionCone(float centerX, float centerY, VisionComponent vision) {
        float coneAngle = vision.visionAngle;
        float visionRange = vision.visionRange;
        float directionAngle = vision.directionAngle;

        // Calculate the start and end angles of the cone
        float startAngle = directionAngle - (coneAngle / 2);
        float endAngle = directionAngle + (coneAngle / 2);

        // Set color to semi-transparent sandy/dirty tone
        Color visionColor = new Color(0.72f, 0.62f, 0.42f, 0.22f); // Sandy/dirty tone with 22% opacity

        // Draw lines from center to the edges of the cone
        float startRad = (float) Math.toRadians(startAngle);
        float endRad = (float) Math.toRadians(endAngle);

        float startX = centerX + visionRange * (float) Math.cos(startRad);
        float startY = centerY + visionRange * (float) Math.sin(startRad);
        float endX = centerX + visionRange * (float) Math.cos(endRad);
        float endY = centerY + visionRange * (float) Math.sin(endRad);

        // Draw the two edge lines of the cone
        shapeDrawer.line(centerX, centerY, startX, startY, visionColor, 2f);
        shapeDrawer.line(centerX, centerY, endX, endY, visionColor, 2f);

        // Draw an arc for the outer edge of the cone
        drawArc(centerX, centerY, visionRange, startAngle, endAngle, visionColor);

        // Fill the cone with a semi-transparent polygon
        drawFilledCone(centerX, centerY, visionRange, startAngle, endAngle, visionColor);
    }

    private void drawArc(float centerX, float centerY, float radius, float startAngle, float endAngle, Color color) {
        int segments = (int) ((endAngle - startAngle) / 5f) + 1; // 5 degree segments
        segments = Math.max(segments, 2);

        float angle = startAngle;
        float angleStep = (endAngle - startAngle) / segments;

        for (int i = 0; i < segments; i++) {
            float currentAngle = (float) Math.toRadians(angle);
            float nextAngle = (float) Math.toRadians(angle + angleStep);

            float x1 = centerX + radius * (float) Math.cos(currentAngle);
            float y1 = centerY + radius * (float) Math.sin(currentAngle);
            float x2 = centerX + radius * (float) Math.cos(nextAngle);
            float y2 = centerY + radius * (float) Math.sin(nextAngle);

            shapeDrawer.line(x1, y1, x2, y2, color, 2f);

            angle += angleStep;
        }
    }

    private void drawFilledCone(float centerX, float centerY, float radius, float startAngle, float endAngle, Color color) {
        // Semi-transparent version for fill
        Color fillColor = new Color(color.r, color.g, color.b, 0.12f);

        int segments = (int) ((endAngle - startAngle) / 5f) + 1; // 5 degree segments
        segments = Math.max(segments, 2);

        float angle = startAngle;
        float angleStep = (endAngle - startAngle) / segments;

        for (int i = 0; i < segments; i++) {
            float currentAngle = (float) Math.toRadians(angle);
            float nextAngle = (float) Math.toRadians(angle + angleStep);

            float x1 = centerX + radius * (float) Math.cos(currentAngle);
            float y1 = centerY + radius * (float) Math.sin(currentAngle);
            float x2 = centerX + radius * (float) Math.cos(nextAngle);
            float y2 = centerY + radius * (float) Math.sin(nextAngle);

            // Draw a triangle from center to the arc segment
            drawFilledTriangle(centerX, centerY, x1, y1, x2, y2, fillColor);

            angle += angleStep;
        }
    }

    private void drawFilledTriangle(float x1, float y1, float x2, float y2, float x3, float y3, Color color) {
        shapeDrawer.filledTriangle(x1, y1, x2, y2, x3, y3, color);
    }

    @Override
    public void dispose() {
        batch.dispose();
        agentTexture.dispose();
        if (whitePixel != null) {
            whitePixel.dispose();
        }
        if (font != null) {
            font.dispose();
        }
    }
}
