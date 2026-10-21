package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.LongMap;
import io.kostenarov.natureSim.Components.LineageComponent;
import io.kostenarov.natureSim.Mappers;

/**
 * Hands out creature ids and remembers every creature's parents, even after the parents have died,
 * so the family tree of anyone alive can be traced.
 */
public class LineageSystem extends EntitySystem {
    private long nextId = 1;
    // id -> {motherId, fatherId}, for every creature born this run
    private final LongMap<long[]> parents = new LongMap<>();
    private ImmutableArray<Entity> living;

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        living = engine.getEntitiesFor(Family.all(LineageComponent.class).get());
    }

    public long nextId() {
        return nextId++;
    }

    /** The id the next creature will get, for saving a world. */
    public long peekNextId() {
        return nextId;
    }

    public void setNextId(long nextId) {
        this.nextId = nextId;
    }

    /** Every recorded birth as id -> {motherId, fatherId}, for saving a world. */
    public LongMap<long[]> getParents() {
        return parents;
    }

    /** Re-records a birth from a saved world. */
    public void restoreParents(long id, long motherId, long fatherId) {
        parents.put(id, new long[] {motherId, fatherId});
    }

    /** Records a birth and counts it towards both parents' children. */
    public void recordBirth(Entity child, Entity mother, Entity father) {
        LineageComponent childLineage = Mappers.lineage.get(child);
        LineageComponent motherLineage = Mappers.lineage.get(mother);
        LineageComponent fatherLineage = Mappers.lineage.get(father);
        if (childLineage == null || motherLineage == null || fatherLineage == null) {
            return;
        }
        childLineage.motherId = motherLineage.id;
        childLineage.fatherId = fatherLineage.id;
        motherLineage.children++;
        fatherLineage.children++;
        parents.put(childLineage.id, new long[] {motherLineage.id, fatherLineage.id});
    }

    /** How many living creatures descend from {@code ancestorId} (children, grandchildren and so on). */
    public int livingDescendants(long ancestorId) {
        int count = 0;
        LongMap<Boolean> known = new LongMap<>(); // id -> descends from the ancestor? (memo across the walk)
        known.put(ancestorId, true);
        for (Entity creature : living) {
            long id = Mappers.lineage.get(creature).id;
            if (id != ancestorId && descends(id, known)) {
                count++;
            }
        }
        return count;
    }

    private boolean descends(long id, LongMap<Boolean> known) {
        Boolean cached = known.get(id);
        if (cached != null) {
            return cached;
        }
        long[] mumAndDad = parents.get(id);
        boolean result = mumAndDad != null && (descends(mumAndDad[0], known) || descends(mumAndDad[1], known));
        known.put(id, result);
        return result;
    }

    @Override
    public void update(float deltaTime) {
        // Nothing to do each step: lineage is recorded at birth
    }
}
