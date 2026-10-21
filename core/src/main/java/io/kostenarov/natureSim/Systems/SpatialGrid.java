package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Mappers;

import java.util.Arrays;

/**
 * Uniform grid over the map for fast "what is near this point" queries. Rebuilt once per frame,
 * so entities may have moved a few pixels since; queries add a small margin to cover that.
 */
public class SpatialGrid {
    /** Accepts or rejects a candidate during a nearest-entity search. */
    public interface Filter {
        boolean accept(Entity entity);
    }

    private static final float STALE_MARGIN = 16f; // Covers movement since the grid was rebuilt

    private final float cellSize;
    private final int columns;
    private final int rows;
    private final int[] cellHead;      // First entity index in each cell, or -1
    private int[] nextInCell = new int[0];
    private Entity[] entities = new Entity[0];

    public SpatialGrid(float cellSize, float worldWidth, float worldHeight) {
        this.cellSize = cellSize;
        this.columns = (int) Math.ceil(worldWidth / cellSize);
        this.rows = (int) Math.ceil(worldHeight / cellSize);
        this.cellHead = new int[columns * rows];
        Arrays.fill(cellHead, -1);
    }

    public void rebuild(ImmutableArray<Entity> source) {
        int count = source.size();
        if (entities.length < count) {
            entities = new Entity[count * 2];
            nextInCell = new int[count * 2];
        }
        Arrays.fill(entities, null);
        Arrays.fill(cellHead, -1);

        for (int i = 0; i < count; i++) {
            Entity entity = source.get(i);
            Vector2 position = Mappers.position.get(entity).position;
            int cell = cellIndex(columnOf(position.x), rowOf(position.y));
            entities[i] = entity;
            nextInCell[i] = cellHead[cell];
            cellHead[cell] = i;
        }
    }

    /**
     * Adds every entity in the cells overlapping the circle to {@code out} (cleared first).
     * Results are candidates only: callers still check the exact distance.
     */
    public void query(float x, float y, float radius, Array<Entity> out) {
        out.clear();
        float reach = radius + STALE_MARGIN;
        int minColumn = columnOf(x - reach);
        int maxColumn = columnOf(x + reach);
        int minRow = rowOf(y - reach);
        int maxRow = rowOf(y + reach);
        for (int row = minRow; row <= maxRow; row++) {
            for (int column = minColumn; column <= maxColumn; column++) {
                for (int i = cellHead[cellIndex(column, row)]; i != -1; i = nextInCell[i]) {
                    out.add(entities[i]);
                }
            }
        }
    }

    /**
     * Nearest accepted entity within {@code maxRadius}, searching outward ring by ring and stopping
     * as soon as no unvisited cell could hold anything closer. Returns null if none is found.
     */
    public Entity findNearest(float x, float y, float maxRadius, Filter filter) {
        int centerColumn = columnOf(x);
        int centerRow = rowOf(y);
        int maxRing = (int) Math.ceil(maxRadius / cellSize) + 1;

        Entity nearest = null;
        float nearestDistanceSquared = maxRadius * maxRadius;
        for (int ring = 0; ring <= maxRing; ring++) {
            for (int row = centerRow - ring; row <= centerRow + ring; row++) {
                if (row < 0 || row >= rows) {
                    continue;
                }
                boolean edgeRow = row == centerRow - ring || row == centerRow + ring;
                // Middle rows of a ring only have their two edge cells; edge rows are visited in full.
                int step = edgeRow ? 1 : Math.max(1, 2 * ring);
                for (int column = centerColumn - ring; column <= centerColumn + ring; column += step) {
                    if (column < 0 || column >= columns) {
                        continue;
                    }
                    for (int i = cellHead[cellIndex(column, row)]; i != -1; i = nextInCell[i]) {
                        Entity candidate = entities[i];
                        if (!filter.accept(candidate)) {
                            continue;
                        }
                        float distanceSquared = Mappers.position.get(candidate).position.dst2(x, y);
                        if (distanceSquared < nearestDistanceSquared) {
                            nearestDistanceSquared = distanceSquared;
                            nearest = candidate;
                        }
                    }
                }
            }
            // Everything in later rings is at least (ring * cellSize - margin) away.
            float closestUnvisited = ring * cellSize - STALE_MARGIN;
            if (nearest != null && closestUnvisited > 0 && nearestDistanceSquared <= closestUnvisited * closestUnvisited) {
                break;
            }
        }
        return nearest;
    }

    private int columnOf(float x) {
        return MathUtils.clamp((int) (x / cellSize), 0, columns - 1);
    }

    private int rowOf(float y) {
        return MathUtils.clamp((int) (y / cellSize), 0, rows - 1);
    }

    private int cellIndex(int column, int row) {
        return row * columns + column;
    }
}
