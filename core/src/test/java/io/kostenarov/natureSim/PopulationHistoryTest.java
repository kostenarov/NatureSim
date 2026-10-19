package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.PopulationHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PopulationHistoryTest extends SimTestSupport {

    @Test
    @DisplayName("history records counts and gene averages, and stays bounded on long runs")
    void recordsAndStaysBounded() {
        Engine e = new Engine();
        PopulationHistory history = new PopulationHistory();
        e.addSystem(history);
        for (int i = 0; i < 10; i++) creature(e, 100 + i * 40, 100, Gender.MALE, false);
        creature(e, 1000, 1000, Gender.MALE, true);

        run(e, 1000f); // Would be 500 samples at the starting rate

        assertTrue(history.sampleCount() > 100 && history.sampleCount() < 400, "samples kept: " + history.sampleCount());
        assertEquals(10f, history.prey.peek(), "prey count");
        assertEquals(1f, history.predators.peek(), "predator count");
        assertEquals(1f, history.preyGeneMeans[0].peek(), 0.0001f, "all test creatures have genes of 1");
        assertEquals(1000f, history.getElapsed(), 1f, "elapsed simulated time");
    }
}
