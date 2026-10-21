package io.kostenarov.natureSim.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import io.kostenarov.natureSim.Balance;
import io.kostenarov.natureSim.Components.GenomeComponent;
import io.kostenarov.natureSim.Components.SpeciesComponent;
import io.kostenarov.natureSim.Creatures;
import io.kostenarov.natureSim.Enums.Role;
import io.kostenarov.natureSim.Mappers;

/**
 * Works out species: groups of creatures of one role whose genes sit close together. Every few seconds each
 * creature joins the species whose average genome is nearest, if it's within {@link Balance#SPECIES_RADIUS}.
 * Creatures that fit no species found a new one, provided there are enough of them and they are alike;
 * otherwise they stay with the nearest species. Species keep their id for as long as they last, so a split
 * shows up as a new species appearing beside the old one.
 * <p>
 * This only names what is there. What actually keeps species apart is that partners must be genetically close
 * to breed ({@link io.kostenarov.natureSim.Traits#canInterbreed}) and females prefer similar males.
 */
public class SpeciesSystem extends EntitySystem {
    /** One species: its role, its average genome and how many members it had at the last count. */
    public static final class Species {
        public final int id;
        public final Role role;
        public final float[] meanGenes = new float[GenomeComponent.COUNT];
        public int size;
        private final float[] sums = new float[GenomeComponent.COUNT];
        private final Array<Entity> founders = new Array<>(false, 16); // Members while it's still a candidate

        Species(int id, Role role) {
            this.id = id;
            this.role = role;
        }

        private void add(float[] genes) {
            for (int g = 0; g < sums.length; g++) {
                sums[g] += genes[g];
            }
            size++;
        }

        private void finishCount() {
            for (int g = 0; g < sums.length; g++) {
                meanGenes[g] = size > 0 ? sums[g] / size : meanGenes[g];
                sums[g] = 0f;
            }
        }
    }

    private static final Family MEMBERS = Family.all(GenomeComponent.class, SpeciesComponent.class).get();

    private final Array<Species> species = new Array<>();
    private final Array<Species> candidates = new Array<>(); // Possible new species found this pass
    private final Array<Entity> misfits = new Array<>(false, 64);
    private ImmutableArray<Entity> creatures;
    private int nextId = 1;
    private float timer = Balance.SPECIES_CHECK_SECONDS; // Classify on the first step

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        creatures = engine.getEntitiesFor(MEMBERS);
    }

    @Override
    public void update(float deltaTime) {
        timer += deltaTime;
        if (timer >= Balance.SPECIES_CHECK_SECONDS) {
            timer = 0f;
            classify();
        }
    }

    /** Living species (with at least one member), oldest first. */
    public Array<Species> getSpecies() {
        return species;
    }

    public int speciesCount(Role role) {
        int count = 0;
        for (Species s : species) {
            if (s.role == role) {
                count++;
            }
        }
        return count;
    }

    /** Sorts every creature into a species. Runs every few seconds; tests can call it directly. */
    public void classify() {
        for (Species s : species) {
            s.size = 0;
        }
        for (Role role : Role.values()) {
            classify(role);
        }
        for (int i = species.size - 1; i >= 0; i--) {
            Species s = species.get(i);
            s.finishCount();
            if (s.size == 0) {
                species.removeIndex(i); // Extinct
            }
        }
        mergeLookalikes();
    }

    private void classify(Role role) {
        candidates.clear();
        misfits.clear();
        if (speciesCount(role) == 0) {
            foundFirstSpecies(role);
            return;
        }
        for (Entity creature : creatures) {
            if (Creatures.role(creature) != role) {
                continue;
            }
            float[] genes = Mappers.genome.get(creature).genes;
            Species home = nearest(species, role, genes, Balance.SPECIES_RADIUS);
            if (home != null) {
                join(home, creature, genes);
                continue;
            }
            // Fits no species: group it with other misfits that are like it
            Species group = nearest(candidates, role, genes, Balance.SPECIES_RADIUS);
            if (group == null) {
                group = new Species(0, role);
                System.arraycopy(genes, 0, group.meanGenes, 0, genes.length); // The first one found leads the group
                candidates.add(group);
            }
            group.founders.add(creature);
        }

        // Big enough groups become new species
        for (Species group : candidates) {
            if (group.founders.size >= Balance.MIN_NEW_SPECIES_SIZE) {
                Species founded = new Species(nextId++, role);
                species.add(founded);
                for (Entity creature : group.founders) {
                    join(founded, creature, Mappers.genome.get(creature).genes);
                }
            } else {
                misfits.addAll(group.founders);
            }
        }
        // Too few to count as a species of their own: they stay with whichever species is nearest
        for (Entity creature : misfits) {
            float[] genes = Mappers.genome.get(creature).genes;
            join(nearest(species, role, genes, Float.MAX_VALUE), creature, genes);
        }
    }

    /** A role with no species yet (the start of a run, or newcomers after it died out) starts as one. */
    private void foundFirstSpecies(Role role) {
        Species first = null;
        for (Entity creature : creatures) {
            if (Creatures.role(creature) == role) {
                if (first == null) {
                    first = new Species(nextId++, role);
                    species.add(first);
                }
                join(first, creature, Mappers.genome.get(creature).genes);
            }
        }
    }

    private static void join(Species s, Entity creature, float[] genes) {
        s.add(genes);
        Mappers.species.get(creature).id = s.id;
    }

    /** Two species whose averages have drifted together become one again (the older one's id is kept). */
    private void mergeLookalikes() {
        float mergeDistance = Balance.SPECIES_RADIUS / 2f;
        for (int i = 0; i < species.size; i++) {
            for (int j = species.size - 1; j > i; j--) {
                Species older = species.get(i);
                Species younger = species.get(j);
                if (older.role == younger.role && distance(older.meanGenes, younger.meanGenes) <= mergeDistance) {
                    for (Entity creature : creatures) {
                        SpeciesComponent member = Mappers.species.get(creature);
                        if (member.id == younger.id) {
                            member.id = older.id;
                        }
                    }
                    older.size += younger.size;
                    species.removeIndex(j);
                }
            }
        }
    }

    private static Species nearest(Array<Species> options, Role role, float[] genes, float maxDistance) {
        Species nearest = null;
        float nearestDistance = maxDistance;
        for (Species s : options) {
            if (s.role != role) {
                continue;
            }
            float d = distance(s.meanGenes, genes);
            if (d <= nearestDistance) {
                nearestDistance = d;
                nearest = s;
            }
        }
        return nearest;
    }

    private static float distance(float[] a, float[] b) {
        float sum = 0f;
        for (int g = 0; g < a.length; g++) {
            float difference = a[g] - b[g];
            sum += difference * difference;
        }
        return (float) Math.sqrt(sum);
    }
}
