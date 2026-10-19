package io.kostenarov.natureSim;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.FloatArray;
import io.kostenarov.natureSim.Systems.PopulationHistory;
import space.earlygrey.shapedrawer.ShapeDrawer;

/**
 * Draws the history graphs: population of prey and predators, and the average value of each gene
 * over time. Must be called between batch.begin() and batch.end() with a screen-space projection.
 */
public class GraphRenderer {
    /** One color per gene, in GenomeComponent order. */
    public static final Color[] GENE_COLORS = {
        new Color(0.95f, 0.35f, 0.35f, 1f), // Speed
        new Color(0.35f, 0.65f, 1.00f, 1f), // Vision
        new Color(0.85f, 0.65f, 0.30f, 1f), // Size
        new Color(1.00f, 0.85f, 0.25f, 1f), // Metabolism
        new Color(0.45f, 0.90f, 0.45f, 1f), // Recovery
        new Color(0.95f, 0.55f, 0.85f, 1f), // Hunger sense
        new Color(0.40f, 0.90f, 0.90f, 1f), // Thirst sense
        new Color(0.70f, 0.50f, 1.00f, 1f), // Scent
        new Color(0.75f, 0.75f, 0.75f, 1f), // Stealth
    };
    private static final Color PREY_COLOR = new Color(0.45f, 0.90f, 0.45f, 1f);
    private static final Color PREDATOR_COLOR = new Color(0.95f, 0.35f, 0.35f, 1f);
    private static final Color PANEL_COLOR = new Color(0.05f, 0.07f, 0.06f, 0.8f);
    private static final Color GRID_COLOR = new Color(1f, 1f, 1f, 0.12f);
    private static final float PADDING = 16f;

    private final SpriteBatch batch;
    private final ShapeDrawer shapes;
    private final BitmapFont font;

    public GraphRenderer(SpriteBatch batch, ShapeDrawer shapes, BitmapFont font) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
    }

    /** Draws the panel with its bottom-left corner at (x, y). */
    public void render(PopulationHistory history, boolean showPredatorGenes, float x, float y, float width, float height) {
        shapes.filledRectangle(x, y, width, height, PANEL_COLOR);

        float lineHeight = font.getLineHeight();
        float chartWidth = width - 2 * PADDING - 230f; // Room for the legend on the right
        float chartX = x + PADDING;
        float legendX = chartX + chartWidth + PADDING;

        // Top half: population
        float popTop = y + height - PADDING;
        float popHeight = height / 2f - 2 * PADDING - lineHeight;
        float popBottom = popTop - lineHeight - popHeight;
        font.setColor(Color.WHITE);
        font.draw(batch, "Population   (" + formatTime(history.getElapsed()) + ")", chartX, popTop);
        drawFrame(chartX, popBottom, chartWidth, popHeight);
        float preyMax = drawScaledLine(history.prey, chartX, popBottom, chartWidth, popHeight, PREY_COLOR);
        float predatorMax = drawScaledLine(history.predators, chartX, popBottom, chartWidth, popHeight, PREDATOR_COLOR);
        float legendY = popTop - lineHeight;
        legendY = legendEntry("Prey " + (int) last(history.prey) + " (peak " + (int) preyMax + ")", PREY_COLOR, legendX, legendY);
        legendY = legendEntry("Predators " + (int) last(history.predators) + " (peak " + (int) predatorMax + ")", PREDATOR_COLOR, legendX, legendY);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "Generation " + (int) last(history.maxGeneration), legendX, legendY);

        // Bottom half: average genes (0 to 2)
        float geneTop = y + height / 2f - PADDING / 2f;
        float geneHeight = height / 2f - 2 * PADDING - lineHeight;
        float geneBottom = geneTop - lineHeight - geneHeight;
        FloatArray[] means = showPredatorGenes ? history.predatorGeneMeans : history.preyGeneMeans;
        font.setColor(Color.WHITE);
        font.draw(batch, (showPredatorGenes ? "Predator" : "Prey") + " average genes   (H: switch)", chartX, geneTop);
        drawFrame(chartX, geneBottom, chartWidth, geneHeight);
        shapes.line(chartX, geneBottom + geneHeight / 2f, chartX + chartWidth, geneBottom + geneHeight / 2f, GRID_COLOR, 1f); // Gene = 1
        legendY = geneTop - lineHeight;
        for (int g = 0; g < means.length; g++) {
            drawLine(means[g], 2f, chartX, geneBottom, chartWidth, geneHeight, GENE_COLORS[g]);
            legendY = legendEntry(PopulationHistory.GENE_NAMES[g] + String.format(" %.2f", last(means[g])), GENE_COLORS[g], legendX, legendY);
        }
        font.setColor(Color.WHITE);
    }

    private void drawFrame(float x, float y, float width, float height) {
        shapes.rectangle(x, y, width, height, GRID_COLOR, 1f);
    }

    /** Draws a series scaled to its own maximum; returns that maximum. */
    private float drawScaledLine(FloatArray series, float x, float y, float width, float height, Color color) {
        float max = 1f;
        for (int i = 0; i < series.size; i++) {
            max = Math.max(max, series.items[i]);
        }
        drawLine(series, max * 1.1f, x, y, width, height, color);
        return max;
    }

    private void drawLine(FloatArray series, float maxValue, float x, float y, float width, float height, Color color) {
        if (series.size < 2) {
            return;
        }
        float stepX = width / (series.size - 1);
        for (int i = 1; i < series.size; i++) {
            float y1 = y + Math.min(1f, series.items[i - 1] / maxValue) * height;
            float y2 = y + Math.min(1f, series.items[i] / maxValue) * height;
            shapes.line(x + (i - 1) * stepX, y1, x + i * stepX, y2, color, 2f);
        }
    }

    private float legendEntry(String text, Color color, float x, float y) {
        shapes.filledRectangle(x, y - font.getCapHeight(), 12f, font.getCapHeight(), color);
        font.setColor(color);
        font.draw(batch, text, x + 18f, y);
        return y - font.getLineHeight();
    }

    private static float last(FloatArray series) {
        return series.size > 0 ? series.peek() : 0f;
    }

    private static String formatTime(float seconds) {
        int total = (int) seconds;
        return String.format("%d:%02d", total / 60, total % 60);
    }
}
