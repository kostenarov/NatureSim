package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

/** Who a creature is and who its parents were, for the family panel. */
public class LineageComponent implements Component {
    public final long id;        // Unique within a run (0 when no LineageSystem is running)
    public long motherId = -1;   // -1 for starting creatures and migrants
    public long fatherId = -1;
    public int children = 0;

    public LineageComponent(long id) {
        this.id = id;
    }
}
