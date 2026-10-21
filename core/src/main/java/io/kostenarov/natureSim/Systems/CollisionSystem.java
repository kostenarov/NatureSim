package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.kostenarov.natureSim.World;
import io.kostenarov.natureSim.Components.PositionComponent;
import io.kostenarov.natureSim.Mappers;
import io.kostenarov.natureSim.Components.StatsComponent;
import io.kostenarov.natureSim.Components.VelocityComponent;

import java.util.Arrays;

/**
 * Keeps creatures from overlapping by treating each one as a circle and pushing overlapping pairs apart.
 * Runs after the movement systems. Uses a uniform grid so only nearby creatures are compared.
 */
public class CollisionSystem extends EntitySystem {
    // Slightly smaller than the 32px sprite so predators (attack range 28+) can still reach prey.
    private static final float RADIUS = 13f;
    private static final float MIN_DISTANCE = RADIUS * 2f;
    private static final float CELL_SIZE = MIN_DISTANCE; // Any overlapping pair is at most one cell apart
    private static final int SOLVER_PASSES = 4; // Extra passes let tight crowds settle within a few frames


    private ImmutableArray<Entity> bodies;
    private int columns;
    private int rows;
    private int[] cellHead;            // First body index in each cell, or -1
    private int[] nextInCell = new int[0];
    private Vector2[] positions = new Vector2[0];
    private boolean[] anchored = new boolean[0]; // Standing still this step (resting, eating, drinking)

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        bodies = engine.getEntitiesFor(Family.all(PositionComponent.class, StatsComponent.class).get());
        columns = (int) Math.ceil(World.WIDTH / CELL_SIZE);
        rows = (int) Math.ceil(World.HEIGHT / CELL_SIZE);
        cellHead = new int[columns * rows];
    }

    @Override
    public void update(float deltaTime) {
        int count = bodies.size();
        if (nextInCell.length < count) {
            nextInCell = new int[count * 2];
            positions = new Vector2[count * 2];
            anchored = new boolean[count * 2];
        }
        for (int i = 0; i < count; i++) {
            Entity body = bodies.get(i);
            positions[i] = Mappers.position.get(body).position;
            VelocityComponent velocity = Mappers.velocity.get(body);
            anchored[i] = velocity != null && velocity.velocity.isZero();
        }

        for (int pass = 0; pass < SOLVER_PASSES; pass++) {
            resolveOverlaps(count);
        }

        for (int i = 0; i < count; i++) {
            positions[i] = null;
        }
    }

    private void resolveOverlaps(int count) {
        // Bucket every creature into its grid cell.
        Arrays.fill(cellHead, -1);
        for (int i = 0; i < count; i++) {
            Vector2 position = positions[i];
            int cell = cellIndex(columnOf(position.x), rowOf(position.y));
            nextInCell[i] = cellHead[cell];
            cellHead[cell] = i;
        }

        // Compare each creature against the creatures in its own and the 8 neighbouring cells.
        for (int i = 0; i < count; i++) {
            int column = columnOf(positions[i].x);
            int row = rowOf(positions[i].y);
            for (int neighbourRow = row - 1; neighbourRow <= row + 1; neighbourRow++) {
                if (neighbourRow < 0 || neighbourRow >= rows) {
                    continue;
                }
                for (int neighbourColumn = column - 1; neighbourColumn <= column + 1; neighbourColumn++) {
                    if (neighbourColumn < 0 || neighbourColumn >= columns) {
                        continue;
                    }
                    for (int j = cellHead[cellIndex(neighbourColumn, neighbourRow)]; j != -1; j = nextInCell[j]) {
                        if (j > i) {
                            separate(i, j);
                        }
                    }
                }
            }
        }

        for (int i = 0; i < count; i++) {
            clampToMap(positions[i]);
        }
    }

    private void separate(int i, int j) {
        Vector2 a = positions[i];
        Vector2 b = positions[j];
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float distanceSquared = dx * dx + dy * dy;
        if (distanceSquared >= MIN_DISTANCE * MIN_DISTANCE) {
            return;
        }

        float distance = (float) Math.sqrt(distanceSquared);
        float normalX;
        float normalY;
        if (distance < 0.0001f) {
            // Exactly on top of each other: pick a random direction to split them.
            float angle = MathUtils.random(MathUtils.PI2);
            normalX = MathUtils.cos(angle);
            normalY = MathUtils.sin(angle);
        } else {
            normalX = dx / distance;
            normalY = dy / distance;
        }

        // Resting creatures hold their ground: whoever is moving gets pushed around them.
        // Two moving (or two resting) creatures each move half of the overlap.
        float overlap = MIN_DISTANCE - distance;
        float shareA = 0.5f;
        if (anchored[i] != anchored[j]) {
            shareA = anchored[i] ? 0f : 1f;
        }
        a.sub(normalX * overlap * shareA, normalY * overlap * shareA);
        b.add(normalX * overlap * (1f - shareA), normalY * overlap * (1f - shareA));
    }

    private void clampToMap(Vector2 position) {
        position.x = MathUtils.clamp(position.x, 0f, World.WIDTH - World.CREATURE_SIZE);
        position.y = MathUtils.clamp(position.y, 0f, World.HEIGHT - World.CREATURE_SIZE);
    }

    private int columnOf(float x) {
        return MathUtils.clamp((int) (x / CELL_SIZE), 0, columns - 1);
    }

    private int rowOf(float y) {
        return MathUtils.clamp((int) (y / CELL_SIZE), 0, rows - 1);
    }

    private int cellIndex(int column, int row) {
        return row * columns + column;
    }
}
