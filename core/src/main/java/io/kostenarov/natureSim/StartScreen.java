package io.kostenarov.natureSim;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;

/**
 * Settings screen shown before a run: tweak the {@link SimulationConfig}, then start the simulation.
 */
public class StartScreen extends ScreenAdapter {
    private final Main game;
    private final SimulationConfig config;
    private final Stage stage = new Stage(new ScreenViewport());

    public StartScreen(Main game, SimulationConfig config) {
        this.game = game;
        this.config = config;
        buildUi();
    }

    private void buildUi() {
        stage.clear();

        VisTable root = new VisTable();
        root.setFillParent(true);
        root.defaults().pad(6f);
        stage.addActor(root);

        VisLabel title = new VisLabel("NatureSim");
        title.setFontScale(2f);
        root.add(title).colspan(2).padBottom(4f).row();
        root.add(new VisLabel("Tweak the world, then press Start (or Enter)", Color.LIGHT_GRAY)).colspan(2).padBottom(20f).row();

        // Two columns side by side, so every setting fits on screen
        VisTable left = new VisTable();
        left.defaults().pad(5f);
        VisTable right = new VisTable();
        right.defaults().pad(5f);
        root.add(left).top().padRight(60f);
        root.add(right).top().row();

        SettingsForm.population(left, config);
        SettingsForm.environment(left, config, false);
        SettingsForm.dayAndSeasons(left, config);
        SettingsForm.evolution(right, config);
        SettingsForm.behaviour(right, config, false);
        SettingsForm.display(right, config, false);
        root.add(new VisLabel("Hover over a setting to see what it does", Color.GRAY)).colspan(2).padTop(10f).row();

        VisTextButton resetButton = new VisTextButton("Reset to defaults");
        resetButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                // Rebuild after the click finishes, not while the stage is still handling it.
                Gdx.app.postRunnable(() -> {
                    config.resetToDefaults();
                    buildUi();
                });
            }
        });

        VisTextButton startButton = new VisTextButton("Start simulation");
        startButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                // Starting disposes this screen's stage, so wait until the click has been fully handled.
                Gdx.app.postRunnable(game::startSimulation);
            }
        });

        VisTable buttons = new VisTable();
        buttons.defaults().pad(8f).minWidth(300f).height(70f);
        buttons.add(resetButton);
        if (Main.hasSavedWorld()) {
            VisTextButton loadButton = new VisTextButton("Load saved world");
            loadButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    Gdx.app.postRunnable(() -> game.loadSavedWorld(null));
                }
            });
            buttons.add(loadButton);
        }
        buttons.add(startButton);
        root.add(buttons).colspan(2).padTop(24f).row();
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            // Switching screens disposes this one, so stop rendering right away.
            game.startSimulation();
            return;
        }

        ScreenUtils.clear(0.10f, 0.16f, 0.12f, 1f);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
