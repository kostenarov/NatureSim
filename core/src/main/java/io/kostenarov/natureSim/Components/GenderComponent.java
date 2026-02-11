package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;

public class GenderComponent implements Component {
    public enum Gender {
        MALE,
        FEMALE
    }

    public Gender gender;

    public GenderComponent() {
        this.gender = Math.random() < 0.5 ? Gender.MALE : Gender.FEMALE;
    }

    public GenderComponent(Gender gender) {
        this.gender = gender;
    }
}

