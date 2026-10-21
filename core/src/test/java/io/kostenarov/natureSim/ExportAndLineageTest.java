package io.kostenarov.natureSim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.kostenarov.natureSim.Components.AgeComponent;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.LineageComponent;
import io.kostenarov.natureSim.Enums.Gender;
import io.kostenarov.natureSim.Systems.LineageSystem;
import io.kostenarov.natureSim.Systems.MatingSystem;
import io.kostenarov.natureSim.Systems.PopulationHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ExportAndLineageTest extends SimTestSupport {

    @Test
    @DisplayName("the CSV export has a header and one well-formed row per sample")
    void csvExport() {
        Engine e = new Engine();
        PopulationHistory history = new PopulationHistory();
        e.addSystem(history);
        for (int i = 0; i < 5; i++) creature(e, 100 + i * 40, 100, Gender.MALE, false);
        creature(e, 500, 500, Gender.FEMALE, true);
        run(e, 20f);

        String[] lines = HistoryExporter.toCsv(history).split("\n");
        assertEquals(history.sampleCount() + 1, lines.length, "header plus one row per sample");
        String[] header = lines[0].split(",");
        assertEquals("time_seconds", header[0]);
        assertEquals(5 + 3 * PopulationHistory.GENE_COUNT, header.length, "columns");
        for (int i = 1; i < lines.length; i++) {
            assertEquals(header.length, lines[i].split(",").length, "row " + i + " column count");
        }
        String[] last = lines[lines.length - 1].split(",");
        assertEquals("5", last[1], "prey");
        assertEquals("1", last[2], "predators");
        assertFalse(lines[1].contains(";"), "uses commas and dot decimals regardless of locale");
    }

    /** A grown creature built the way the game builds them, so it gets a lineage id. */
    private static Entity adult(Engine e, float x, float y, Gender gender) {
        GenomeComponent dna = new GenomeComponent();
        Arrays.fill(dna.genes, 1f);
        Entity creature = Creatures.create(e, x, y, dna);
        Mappers.gender.get(creature).gender = gender;
        creature.getComponent(AgeComponent.class).age = 60f;
        e.addEntity(creature);
        return creature;
    }

    @Test
    @DisplayName("the family tree is traced through parents who have died")
    void lineageThroughDeadParents() {
        Engine e = new Engine();
        LineageSystem lineage = new LineageSystem();
        e.addSystem(lineage);
        e.addSystem(new MatingSystem());

        Entity grandma = adult(e, 100, 100, Gender.FEMALE);
        adult(e, 110, 100, Gender.MALE);
        e.update(DT);
        Entity child = e.getEntities().get(2);
        LineageComponent childLineage = Mappers.lineage.get(child);
        assertEquals(Mappers.lineage.get(grandma).id, childLineage.motherId, "mother recorded");
        assertEquals(1, Mappers.lineage.get(grandma).children, "children counted");

        // The child grows up and has a child of its own far away, then dies
        Mappers.position.get(child).position.set(2000, 2000);
        Mappers.age.get(child).age = 60f;
        Mappers.stats.get(child).hunger = 100f;
        Mappers.stats.get(child).energy = 100f;
        Mappers.stats.get(child).thirst = 100f;
        Gender other = Mappers.gender.get(child).gender == Gender.MALE ? Gender.FEMALE : Gender.MALE;
        adult(e, 2010, 2000, other);
        e.update(DT);
        assertEquals(5, creatureCount(e), "grandchild born");
        e.removeEntity(child);

        assertEquals(1, lineage.livingDescendants(Mappers.lineage.get(grandma).id), "the grandchild still counts");
    }
}
