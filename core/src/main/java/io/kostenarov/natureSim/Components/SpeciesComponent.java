package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

/** Which species a creature belongs to, as worked out by SpeciesSystem from its genes. 0 = not known yet. */
public class SpeciesComponent implements Component {
    public int id = 0;
}
