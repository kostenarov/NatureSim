package io.kostenarov.natureSim;

import com.badlogic.gdx.graphics.Color;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisWindow;

/**
 * The in-game settings window (O): the settings that can change while the world runs, such as food, mutation,
 * day length and display options. Changes apply at once. Settings that only matter when a world is built
 * (starting numbers, ponds, collision, migration, seed) are only on the start screen.
 */
class LiveSettingsWindow extends VisWindow {
    LiveSettingsWindow(SimulationConfig config) {
        super("Settings (changes apply now)");
        addCloseButton(); // Esc is handled by SimulationScreen, which also uses it to leave the world
        setMovable(true);

        VisTable left = new VisTable();
        left.defaults().pad(4f);
        VisTable right = new VisTable();
        right.defaults().pad(4f);
        SettingsForm.environment(left, config, true);
        SettingsForm.dayAndSeasons(left, config);
        SettingsForm.evolution(right, config);
        SettingsForm.behaviour(right, config, true);
        SettingsForm.display(right, config, true);

        add(left).top().padRight(40f);
        add(right).top().row();
        add(new VisLabel("Hover over a setting to see what it does.  O or Esc closes this window.", Color.GRAY))
            .colspan(2).padTop(8f);
        pack();
    }

    @Override
    protected void close() {
        // Hide rather than remove, so the window can be opened again with O
        setVisible(false);
    }
}
