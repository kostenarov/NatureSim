package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

public class AgeComponent implements Component {
    public float age = 0f;         // Seconds lived
    public float lifespan = 0f;    // Seconds until death of old age; 0 = not assigned yet (AgingSystem picks one)
    public int generation = 0;     // 0 for starting creatures, parents' highest generation + 1 for offspring
}
