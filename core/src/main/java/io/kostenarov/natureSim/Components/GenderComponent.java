package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;
import io.kostenarov.natureSim.Enums.Gender;

public class GenderComponent implements Component {

    public Gender gender;

    public GenderComponent() {
        this.gender = Math.random() < 0.5 ? Gender.MALE : Gender.FEMALE;
    }

    public GenderComponent(Gender gender) {
        this.gender = gender;
    }
}

