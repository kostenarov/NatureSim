package io.kostenarov.natureSim;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.kotcrab.vis.ui.VisUI;

/**
 * Entry point. Starts on the settings screen and switches between it and the running simulation.
 */
public class Main extends Game {
    private final SimulationConfig config = new SimulationConfig();

    @Override
    public void create() {
        VisUI.load(VisUI.SkinScale.X2);
        showStartScreen();
    }

    public void showStartScreen() {
        switchTo(new StartScreen(this, config));
    }

    public void startSimulation() {
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
