package io.kostenarov.natureSim.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.MathUtils;
import io.kostenarov.natureSim.Enums.Gender;

public class GenderComponent implements Component {

    public Gender gender;

    public GenderComponent() {
        this.gender = MathUtils.randomBoolean() ? Gender.MALE : Gender.FEMALE;
    }

    public GenderComponent(Gender gender) {
        this.gender = gender;
    }
}

