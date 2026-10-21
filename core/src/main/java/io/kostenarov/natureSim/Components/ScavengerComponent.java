package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

/** Marks a scavenger: it lives on carcasses, which it finds by smell. */
public class ScavengerComponent implements Component {
    public float scentRange = 420f; // Set from the genome (Traits.scentRange)
}
