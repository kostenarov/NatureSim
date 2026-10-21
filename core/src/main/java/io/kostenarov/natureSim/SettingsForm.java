package io.kostenarov.natureSim;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.Separator;
import com.kotcrab.vis.ui.widget.Tooltip;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSlider;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextField;

/**
 * Builds rows of settings (a label, a slider and its value, or a checkbox) into a three-column table, each with
 * a tooltip explaining what it does. Used by the start screen and by the in-game settings window, which shows
 * only the settings that can change while a world is running ({@code inGame}).
 */
final class SettingsForm {
    private static final float LABEL_WIDTH = 320f;
    private static final float SLIDER_WIDTH = 420f;
    private static final float VALUE_WIDTH = 140f;
    private static final float TOOLTIP_WIDTH = 700f;

    interface ValueSetter {
        void set(float value);
    }

    interface ToggleSetter {
        void set(boolean value);
    }

    private SettingsForm() {
    }

    static void section(VisTable table, String name) {
        table.add(new VisLabel(name, Color.SKY)).colspan(3).left().padTop(18f).row();
        table.add(new Separator()).colspan(3).growX().row();
    }

    static VisSlider slider(VisTable table, String name, String help, float min, float max, float step, float value,
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

        VisLabel label = new VisLabel(name);
        tooltip(label, help);
        tooltip(slider, help);
        table.add(label).left().minWidth(LABEL_WIDTH);
        table.add(slider).width(SLIDER_WIDTH);
        table.add(valueLabel).width(VALUE_WIDTH).right().row();
        return slider;
    }

    static VisCheckBox checkBox(VisTable table, String name, String help, boolean checked, ToggleSetter setter) {
        VisCheckBox checkBox = new VisCheckBox(name, checked);
        checkBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setter.set(checkBox.isChecked());
            }
        });
        tooltip(checkBox, help);
        table.add(checkBox).colspan(3).left().row();
        return checkBox;
    }

    /** A label and any widget (e.g. a text field) on one row. */
    static void row(VisTable table, String name, String help, Actor widget) {
        VisLabel label = new VisLabel(name);
        tooltip(label, help);
        tooltip(widget, help);
        table.add(label).left().minWidth(LABEL_WIDTH);
        table.add(widget).width(SLIDER_WIDTH).left();
        table.add().row();
    }

    // --- The settings, grouped as on the start screen ---------------------------------------------------

    static void population(VisTable t, SimulationConfig c) {
        section(t, "Population");
        slider(t, "Prey", "How many prey the world starts with.",
            0, 3000, 10, c.preyCount, "%.0f", v -> c.preyCount = (int) v);
        slider(t, "Predators", "How many predators the world starts with. Fewer than about 10 often die out before they find each other.",
            0, 100, 1, c.predatorCount, "%.0f", v -> c.predatorCount = (int) v);
        slider(t, "Scavengers", "How many scavengers the world starts with. They live on carcasses.",
            0, 100, 1, c.scavengerCount, "%.0f", v -> c.scavengerCount = (int) v);
    }

    static void environment(VisTable t, SimulationConfig c, boolean inGame) {
        section(t, "Environment");
        if (!inGame) {
            slider(t, "Starting food", "Food items on the map when the world begins.",
                0, 1000, 10, c.initialFood, "%.0f", v -> c.initialFood = (int) v);
        }
        slider(t, "Max food", "Food stops growing once this many items are on the map.",
            0, 2000, 10, c.maxFood, "%.0f", v -> c.maxFood = (int) v);
        slider(t, "Food spawn every", "Seconds between new food items (before seasons speed it up or slow it down). "
                + "Lower means more food and more prey.",
            0.05f, 10f, 0.05f, c.foodSpawnInterval, "%.2f s", v -> c.foodSpawnInterval = v);
        slider(t, "Food per item", "How much hunger one food item restores (hunger runs from 0 to 100).",
            5f, 100f, 5f, c.foodNutrition, "%.0f", v -> c.foodNutrition = v);
        if (!inGame) {
            slider(t, "Ponds", "Number of ponds. Creatures drink there, and most food grows around them.",
                0, 40, 1, c.pondCount, "%.0f", v -> c.pondCount = (int) v);
        }
    }

    static void dayAndSeasons(VisTable t, SimulationConfig c) {
        section(t, "Day, night & seasons");
        checkBox(t, "Day and night (less sight at night)", "At night everyone sees less far. Smell works as well as by day.",
            c.dayNightEnabled, v -> c.dayNightEnabled = v);
        slider(t, "Day length", "Real minutes (at 1x speed) for one full day and night.",
            1f, 20f, 0.5f, c.dayLengthMinutes, "%.1f min", v -> c.dayLengthMinutes = v);
        checkBox(t, "Seasons (food grows fast in summer, slowly in winter)", "Food grows faster in summer and barely at all in winter.",
            c.seasonsEnabled, v -> c.seasonsEnabled = v);
        slider(t, "Year length", "Real minutes (at 1x speed) for spring, summer, autumn and winter.",
            2f, 60f, 1f, c.yearLengthMinutes, "%.0f min", v -> c.yearLengthMinutes = v);
    }

    static void evolution(VisTable t, SimulationConfig c) {
        section(t, "Reproduction & evolution");
        slider(t, "Lifespan", "Average natural lifespan. Each creature's own lifespan varies a little around it.",
            1f, 60f, 1f, c.lifespanMinutes, "%.0f min", v -> c.lifespanMinutes = v);
        slider(t, "Mating cooldown", "Seconds a creature must wait between matings.",
            1f, 60f, 1f, c.matingCooldown, "%.0f s", v -> c.matingCooldown = v);
        slider(t, "Mutation chance", "Chance that each gene of a newborn mutates. More mutation means faster but noisier evolution.",
            0f, 1f, 0.01f, c.mutationRate, "%.2f", v -> c.mutationRate = v);
        slider(t, "Mutation size", "How big a mutation typically is. Genes range from 0 to 2.",
            0f, 1f, 0.05f, c.mutationAmount, "%.2f", v -> c.mutationAmount = v);
    }

    static void behaviour(VisTable t, SimulationConfig c, boolean inGame) {
        section(t, "Behaviour & balance");
        checkBox(t, "Herding (prey flee together, panic spreads)", "A prey that sees a herd-mate flee runs too, and fleeing prey stick together.",
            c.herdingEnabled, v -> c.herdingEnabled = v);
        if (!inGame) {
            checkBox(t, "Migration (newcomers when a species is nearly gone)",
                "When a species is down to its last few, a small group of newcomers arrives at the map edge.",
                c.migrationEnabled, v -> c.migrationEnabled = v);
        }
        slider(t, "Predator breeding slowdown", "Predators wait this many times longer than prey between matings. Too low and they out-breed their prey.",
            1f, 12f, 0.5f, c.predatorBreedingSlowdown, "%.1fx", v -> c.predatorBreedingSlowdown = v);
        slider(t, "Predator eating time", "Seconds a predator spends eating each kill, during which it doesn't hunt.",
            0f, 20f, 0.5f, c.predatorEatingSeconds, "%.1f s", v -> c.predatorEatingSeconds = v);
    }

    static void display(VisTable t, SimulationConfig c, boolean inGame) {
        section(t, inGame ? "Display" : "Simulation & display");
        if (!inGame) {
            seedField(t, c);
            checkBox(t, "Creature collision", "Creatures can't overlap. Turning it off is faster, but crowds pile up into blobs.",
                c.collisionEnabled, v -> c.collisionEnabled = v);
        }
        checkBox(t, "Show vision cones (slow with many creatures)", "Draws what each creature can see. Slow with thousands of creatures.",
            c.showVisionCones, v -> c.showVisionCones = v);
        checkBox(t, "Show predator scent ranges", "Draws the circle within which each predator can smell prey.",
            c.showScentRanges, v -> c.showScentRanges = v);
    }

    /** Same seed + same settings replays the same run; 0 picks a new seed each time. */
    private static void seedField(VisTable t, SimulationConfig c) {
        VisTextField seedField = new VisTextField(c.seed == 0 ? "" : Long.toString(c.seed));
        seedField.setMessageText("random");
        seedField.setMaxLength(9);
        seedField.setTextFieldFilter(new VisTextField.TextFieldFilter.DigitsOnlyFilter());
        seedField.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String text = seedField.getText();
                c.seed = text.isEmpty() ? 0 : Long.parseLong(text);
            }
        });
        row(t, "Seed (empty = random)", "The same seed with the same settings plays out the same run. Leave it empty for a new run each time.", seedField);
    }

    private static void tooltip(Actor target, String help) {
        if (help != null) {
            new Tooltip.Builder(help).target(target).width(TOOLTIP_WIDTH).build();
        }
    }
}
