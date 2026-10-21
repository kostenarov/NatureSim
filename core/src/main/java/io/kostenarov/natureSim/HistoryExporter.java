package io.kostenarov.natureSim;

import com.badlogic.gdx.utils.FloatArray;
import io.kostenarov.natureSim.Systems.PopulationHistory;

import java.util.Locale;

/**
 * Turns the recorded population history into CSV, one row per sample, for analysis outside the game
 * (spreadsheets, Python, R...). Columns: time, population of each species, highest generation, then the
 * average of every gene for prey, predators and scavengers.
 */
public final class HistoryExporter {
    private HistoryExporter() {
    }

    public static String toCsv(PopulationHistory history) {
        StringBuilder csv = new StringBuilder();

        csv.append("time_seconds,prey,predators,scavengers,max_generation");
        appendGeneHeaders(csv, "prey");
        appendGeneHeaders(csv, "predator");
        appendGeneHeaders(csv, "scavenger");
        csv.append('\n');

        for (int i = 0; i < history.sampleCount(); i++) {
            csv.append(format(history.times.get(i))).append(',')
                .append((int) history.prey.get(i)).append(',')
                .append((int) history.predators.get(i)).append(',')
                .append((int) history.scavengers.get(i)).append(',')
                .append((int) history.maxGeneration.get(i));
            appendGeneValues(csv, history.preyGeneMeans, i);
            appendGeneValues(csv, history.predatorGeneMeans, i);
            appendGeneValues(csv, history.scavengerGeneMeans, i);
            csv.append('\n');
        }
        return csv.toString();
    }

    private static void appendGeneHeaders(StringBuilder csv, String species) {
        for (String gene : PopulationHistory.GENE_NAMES) {
            csv.append(',').append(species).append('_').append(gene.toLowerCase(Locale.ROOT).replace(' ', '_'));
        }
    }

    private static void appendGeneValues(StringBuilder csv, FloatArray[] means, int sample) {
        for (FloatArray series : means) {
            csv.append(',').append(format(series.get(sample)));
        }
    }

    /** Always a dot as decimal separator, whatever the system language, so the file reads anywhere. */
    private static String format(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
