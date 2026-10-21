package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.MigrationSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MigrationTest extends SimTestSupport {

    @Test
    @DisplayName("newcomers arrive when a species is nearly gone, in breeding pairs")
    void newcomersArrive() {
        Engine e = new Engine();
        e.addSystem(new MigrationSystem());

        run(e, Balance.MIGRATION_CHECK_SECONDS + 1f);

        assertEquals(Balance.PREY_MIGRANTS, e.getEntitiesFor(Creatures.PREY).size(), "prey");
        assertEquals(Balance.OTHER_MIGRANTS, e.getEntitiesFor(Creatures.PREDATORS).size(), "predators");
        assertEquals(Balance.OTHER_MIGRANTS, e.getEntitiesFor(Creatures.SCAVENGERS).size(), "scavengers");
        int females = 0;
        for (Entity predator : e.getEntitiesFor(Creatures.PREDATORS)) {
            if (Mappers.gender.get(predator).gender == Gender.FEMALE) females++;
        }
        assertEquals(1, females, "a male and a female predator");
        for (Entity newcomer : e.getEntitiesFor(Creatures.PREY)) {
            assertTrue(Mappers.age.get(newcomer).age >= Balance.MATURITY_AGE, "newcomers are adults");
        }
    }

    @Test
    @DisplayName("no newcomers while every species is doing fine")
    void noMigrationWhenHealthy() {
        Engine e = new Engine();
        e.addSystem(new MigrationSystem());
        for (int i = 0; i < Balance.PREY_MIGRATION_THRESHOLD; i++) creature(e, 100 + i * 40, 100, Gender.MALE, false);
        for (int i = 0; i < Balance.OTHER_MIGRATION_THRESHOLD; i++) {
            creature(e, 100 + i * 40, 300, Gender.MALE, true);
            Creatures.makeScavenger(creature(e, 100 + i * 40, 500, Gender.MALE, false));
        }
        int before = creatureCount(e);

        run(e, Balance.MIGRATION_CHECK_SECONDS + 1f);
        assertEquals(before, creatureCount(e));
        assertEquals(0, e.getSystem(MigrationSystem.class).getMigrants());
    }
}
