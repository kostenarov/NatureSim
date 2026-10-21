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
    private static final float SLIDER_WIDTH = 420f;
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
        root.add(title).colspan(2).padBottom(4f).row();
        root.add(new VisLabel("Tweak the world, then press Start (or Enter)", Color.LIGHT_GRAY)).colspan(2).padBottom(20f).row();

        // Two columns side by side, so every setting fits on screen
        VisTable left = new VisTable();
        left.defaults().pad(5f);
        VisTable right = new VisTable();
        right.defaults().pad(5f);
        root.add(left).top().padRight(60f);
        root.add(right).top().row();

        addSection(left, "Population");
        addSlider(left, "Prey", 0, 3000, 10, config.preyCount, "%.0f", v -> config.preyCount = (int) v);
        addSlider(left, "Predators", 0, 100, 1, config.predatorCount, "%.0f", v -> config.predatorCount = (int) v);
        addSlider(left, "Scavengers", 0, 100, 1, config.scavengerCount, "%.0f", v -> config.scavengerCount = (int) v);

        addSection(left, "Environment");
        addSlider(left, "Starting food", 0, 1000, 10, config.initialFood, "%.0f", v -> config.initialFood = (int) v);
        addSlider(left, "Max food", 0, 2000, 10, config.maxFood, "%.0f", v -> config.maxFood = (int) v);
        addSlider(left, "Food spawn every", 0.05f, 10f, 0.05f, config.foodSpawnInterval, "%.2f s", v -> config.foodSpawnInterval = v);
        addSlider(left, "Food per item", 5f, 100f, 5f, config.foodNutrition, "%.0f", v -> config.foodNutrition = v);
        addSlider(left, "Ponds", 0, 40, 1, config.pondCount, "%.0f", v -> config.pondCount = (int) v);

        addSection(left, "Day, night & seasons");
        addCheckBox(left, "Day and night (less sight at night)", config.dayNightEnabled, v -> config.dayNightEnabled = v);
        addSlider(left, "Day length", 1f, 20f, 0.5f, config.dayLengthMinutes, "%.1f min", v -> config.dayLengthMinutes = v);
        addCheckBox(left, "Seasons (food grows fast in summer, slowly in winter)", config.seasonsEnabled, v -> config.seasonsEnabled = v);
        addSlider(left, "Year length", 2f, 60f, 1f, config.yearLengthMinutes, "%.0f min", v -> config.yearLengthMinutes = v);

        addSection(right, "Reproduction & evolution");
        addSlider(right, "Lifespan", 1f, 60f, 1f, config.lifespanMinutes, "%.0f min", v -> config.lifespanMinutes = v);
        addSlider(right, "Mating cooldown", 1f, 60f, 1f, config.matingCooldown, "%.0f s", v -> config.matingCooldown = v);
        addSlider(right, "Mutation chance", 0f, 1f, 0.01f, config.mutationRate, "%.2f", v -> config.mutationRate = v);
        addSlider(right, "Mutation size", 0f, 1f, 0.05f, config.mutationAmount, "%.2f", v -> config.mutationAmount = v);

        addSection(right, "Behaviour & balance");
        addCheckBox(right, "Herding (prey flee together, panic spreads)", config.herdingEnabled, v -> config.herdingEnabled = v);
        addCheckBox(right, "Migration (newcomers when a species is nearly gone)", config.migrationEnabled, v -> config.migrationEnabled = v);
        addSlider(right, "Predator breeding slowdown", 1f, 12f, 0.5f, config.predatorBreedingSlowdown, "%.1fx", v -> config.predatorBreedingSlowdown = v);
        addSlider(right, "Predator eating time", 0f, 20f, 0.5f, config.predatorEatingSeconds, "%.1f s", v -> config.predatorEatingSeconds = v);

        addSection(right, "Simulation & display");
        addSeedField(right);
        addCheckBox(right, "Creature collision", config.collisionEnabled, v -> config.collisionEnabled = v);
        addCheckBox(right, "Show vision cones (slow with many creatures)", config.showVisionCones, v -> config.showVisionCones = v);
        addCheckBox(right, "Show predator scent ranges", config.showScentRanges, v -> config.showScentRanges = v);

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
        root.add(buttons).colspan(2).padTop(24f).row();
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
