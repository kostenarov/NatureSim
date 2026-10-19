package io.kostenarov.natureSim;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.kotcrab.vis.ui.VisUI;

/**
 * Entry point. Starts on the settings screen and switches between it and the running simulation.
 */
public class Main extends Game {
    private final SimulationConfig config = new SimulationConfig();
    private Preferences settings;

    @Override
    public void create() {
        VisUI.load(VisUI.SkinScale.X2);
        // The start screen remembers the last settings used
        settings = Gdx.app.getPreferences("NatureSim");
        config.load(settings);
        showStartScreen();
    }

    public void showStartScreen() {
        switchTo(new StartScreen(this, config));
    }

    public void startSimulation() {
        config.save(settings);
        switchTo(new SimulationScreen(this, config));
    }

    private void switchTo(Screen next) {
        Screen previous = getScreen();
        setScreen(next);
        if (previous != null) {
            previous.dispose();
        }
    }

    @Override
    public void dispose() {
        if (getScreen() != null) {
            getScreen().dispose();
        }
        VisUI.dispose();
    }
}
