# NatureSim

An evolution simulator built with [libGDX](https://libgdx.com/) and the [Ashley](https://github.com/libgdx/ashley) entity-component-system.
Prey, predators and scavengers live, eat, drink, breed and die on a 5120×5120 map with ponds, seasons and a day/night cycle.
Every creature carries a genome, offspring inherit genes from both parents with mutations, and every gene has a cost as well
as a benefit, so you can watch natural selection balance them out in the live graphs.

## Running it

```bash
./gradlew lwjgl3:run
```

(On Windows `gradlew.bat lwjgl3:run`.) A start screen lets you tweak the world; your settings are remembered between launches.

## The ecosystem

- **Prey** eat food they can see, drink at ponds, flee predators (panic spreads through the herd) and court partners.
- **Predators** hunt prey by sight, follow their scent when out of sight, and find mates by smell. They breed slowly and spend a
  few seconds eating each kill, so they don't wipe out their prey.
- **Scavengers** smell out carcasses and trail predators to their kills. Nobody hunts them.
- **Ponds** have wadeable shallows and an impassable deep centre. Most food grows in the fertile band around them.
- **Day and night**: sight shrinks at night (smell doesn't). **Seasons**: food grows fast in summer and barely in winter.
- **Life cycle**: creatures age and die of old age, starvation, thirst or predation, leaving a carcass. They must rest when they run
  out of energy. Newcomers migrate in if a species is nearly gone.

### Genes and their trade-offs

Each gene ranges from 0 to 2 (1 is average); see `Traits.java` for the exact formulas.

| Gene | Benefit | Cost |
|------|---------|------|
| Speed | Moves faster | Energy and food cost grow with speed squared |
| Vision | Sees farther | Narrower vision cone, a little more food |
| Size | Takes less damage, bites harder | Needs more food |
| Stealth | Harder to spot | Slower when chasing or fleeing |
| Metabolism | Survives starvation longer | Burns energy faster |
| Recovery | Recovers energy faster at rest | Burns more food while resting |
| Hunger / thirst sense | Looks for food / water earlier | Spends more time foraging |
| Scent (predators, scavengers) | Smells farther | Costs food |

## Controls

| Key / mouse | Action |
|-------------|--------|
| Esc | Back to the settings |
| P, 1–5 | Pause; run at 1×, 2×, 5×, 10× or 20× speed |
| WASD / arrows, mouse drag | Move the camera |
| Q / E, mouse wheel | Zoom |
| Click a creature | Inspect it (stats, genes, family) and follow it; Space toggles following |
| G, H | Population and average-gene graphs; H switches between prey, predators and scavengers |
| T | Colour creatures by a gene (blue low, red high) |
| M | Minimap (click it to jump there) |
| X | Export the population history as CSV to `~/NatureSim/exports/` |

The **seed** on the start screen makes runs reproducible: the same seed and settings give exactly the same simulation.

## Tests

```bash
./gradlew core:test                 # ~20 s
./gradlew core:test -PincludeSlow   # also runs a 15-minute headless evolution run (~1 min)
```

The tests drive the simulation headless (no window) and cover behaviour, genetics, balance mechanics and the spatial grid.
They run on GitHub Actions for every push to `main` and every pull request.

## How the code is organised

- `core` holds everything; `lwjgl3` is the desktop launcher.
- **Components** (`Components/`) are plain data: position, genome, stats, behaviour, age, lineage, and markers such as
  `PredatorComponent` and `ScavengerComponent`.
- **Systems** (`Systems/`) hold the behaviour. `Simulation.createEngine` adds them in the order they run each step:
  spatial index → clock → lineage → ponds → food → mating → predators → prey → scavengers → metabolism → ageing →
  collision → deep-water barrier → death (leaves carcasses) → carcass rot → migration → history.
- `CreatureSystem` is the shared base for the prey, predator and scavenger systems (movement, wandering, drinking, sight,
  courtship, exhaustion).
- `Traits` turns genes into abilities and costs; `Balance` holds the other tuning numbers; `SimulationConfig` holds the
  settings from the start screen.
- `WorldRenderer`, `GraphRenderer` and `Minimap` draw; `SimulationScreen` runs the fixed 60 Hz simulation step and handles input.
