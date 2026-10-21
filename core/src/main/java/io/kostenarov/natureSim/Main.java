package io.kostenarov.natureSim;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
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

    /** Where F5 saves the running world, in the user's home folder. */
    public static FileHandle savedWorldFile() {
        return Gdx.files.external("NatureSim/saves/world.json");
    }

    public static boolean hasSavedWorld() {
        return savedWorldFile().exists();
    }

    /**
     * Loads the saved world and switches to it, returning true. If that fails, stays where it is, says why
     * ({@code current} is the running world to show the message in, or null on the start screen) and returns false.
     */
    public boolean loadSavedWorld(SimulationScreen current) {
        String problem;
        try {
            if (hasSavedWorld()) {
                switchTo(new SimulationScreen(this, WorldSave.load(savedWorldFile().readString("UTF-8"))));
                return true;
            }
            problem = "There is no saved world yet (F5 saves one)";
        } catch (RuntimeException e) {
            problem = "Couldn't load the saved world: " + e.getMessage();
        }
        if (current != null) {
            current.showMessage(problem);
        } else {
            Gdx.app.error("NatureSim", problem);
        }
        return false;
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
