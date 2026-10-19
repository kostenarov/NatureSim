package io.kostenarov.natureSim;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.Separator;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSlider;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;

/**
 * Settings screen shown before a run: tweak the {@link SimulationConfig}, then start the simulation.
 */
public class StartScreen extends ScreenAdapter {
    private static final float SLIDER_WIDTH = 520f;
    private static final float VALUE_WIDTH = 140f;

    private final Main game;
    private final SimulationConfig config;
    private final Stage stage = new Stage(new ScreenViewport());

    private interface ValueSetter {
        void set(float value);
    }

    private interface ToggleSetter {
        void set(boolean value);
    }

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
        root.add(title).colspan(3).padBottom(4f).row();
        root.add(new VisLabel("Tweak the world, then press Start (or Enter)", Color.LIGHT_GRAY)).colspan(3).padBottom(20f).row();

        addSection(root, "Population");
        addSlider(root, "Prey", 0, 3000, 10, config.preyCount, "%.0f", v -> config.preyCount = (int) v);
        addSlider(root, "Predators", 0, 100, 1, config.predatorCount, "%.0f", v -> config.predatorCount = (int) v);

        addSection(root, "Environment");
        addSlider(root, "Starting food", 0, 1000, 10, config.initialFood, "%.0f", v -> config.initialFood = (int) v);
        addSlider(root, "Max food", 0, 2000, 10, config.maxFood, "%.0f", v -> config.maxFood = (int) v);
        addSlider(root, "Food spawn every", 0.05f, 10f, 0.05f, config.foodSpawnInterval, "%.2f s", v -> config.foodSpawnInterval = v);
        addSlider(root, "Ponds", 0, 40, 1, config.pondCount, "%.0f", v -> config.pondCount = (int) v);

        addSection(root, "Reproduction & evolution");
        addSlider(root, "Lifespan", 1f, 60f, 1f, config.lifespanMinutes, "%.0f min", v -> config.lifespanMinutes = v);
        addSlider(root, "Mating cooldown", 1f, 60f, 1f, config.matingCooldown, "%.0f s", v -> config.matingCooldown = v);
        addSlider(root, "Mutation chance", 0f, 1f, 0.01f, config.mutationRate, "%.2f", v -> config.mutationRate = v);
        addSlider(root, "Mutation size", 0f, 1f, 0.05f, config.mutationAmount, "%.2f", v -> config.mutationAmount = v);

        addSection(root, "Simulation & display");
        addSeedField(root);
        addCheckBox(root, "Creature collision", config.collisionEnabled, v -> config.collisionEnabled = v);
        addCheckBox(root, "Show vision cones (slow with many creatures)", config.showVisionCones, v -> config.showVisionCones = v);
        addCheckBox(root, "Show predator scent ranges", config.showScentRanges, v -> config.showScentRanges = v);

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
        buttons.add(startButton);
        root.add(buttons).colspan(3).padTop(24f).row();
    }

    private void addSection(VisTable table, String name) {
        table.add(new VisLabel(name, Color.SKY)).colspan(3).left().padTop(18f).row();
        table.add(new Separator()).colspan(3).growX().row();
    }

    private void addSlider(VisTable table, String name, float min, float max, float step, float value,
                           String format, ValueSetter setter) {
        VisSlider slider = new VisSlider(min, max, step, false);
        slider.setValue(value);
        VisLabel valueLabel = new VisLabel(String.format(format, slider.getValue()));
        valueLabel.setAlignment(Align.right);

        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setter.set(slider.getValue());
                valueLabel.setText(String.format(format, slider.getValue()));
            }
        });

        table.add(new VisLabel(name)).left().minWidth(320f);
        table.add(slider).width(SLIDER_WIDTH);
        table.add(valueLabel).width(VALUE_WIDTH).right().row();
    }

    /** Same seed + same settings replays the same run; 0 picks a new seed each time. */
    private void addSeedField(VisTable table) {
        VisTextField seedField = new VisTextField(config.seed == 0 ? "" : Long.toString(config.seed));
        seedField.setMessageText("random");
        seedField.setMaxLength(9);
        seedField.setTextFieldFilter(new VisTextField.TextFieldFilter.DigitsOnlyFilter());
        seedField.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String text = seedField.getText();
                config.seed = text.isEmpty() ? 0 : Long.parseLong(text);
            }
        });

        table.add(new VisLabel("Seed (empty = random)")).left().minWidth(320f);
        table.add(seedField).width(SLIDER_WIDTH).left();
        table.add().row();
    }

    private void addCheckBox(VisTable table, String name, boolean checked, ToggleSetter setter) {
        VisCheckBox checkBox = new VisCheckBox(name, checked);
        checkBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setter.set(checkBox.isChecked());
            }
        });
        table.add(checkBox).colspan(3).left().row();
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
