# Bee Housing

A bee does nothing on its own — it only works once it is housed. Forestry gives you three tiers of housing, and the block you choose changes not just *how much* a bee produces but *how it behaves*: whether it can mutate, how long it lives, how far it pollinates, and how large a territory it disturbs.

## At a glance

Every housing block applies a set of modifiers to the bee inside it. These are the exact multipliers Forestry uses:

| Modifier | Bee House | Apiary | Alveary |
|---|---|---|---|
| Territory | ×1.0 | ×1.0 | ×2.0 |
| Production | ×0.25 | ×0.1 – 0.8 | ×1.0 |
| Mutation | ×0.0 | ×1.0 | ×1.0 |
| Lifespan | ×3.0 | ×1.0 | ×1.0 |
| Pollination | ×3.0 | ×1.0 | ×1.0 |
| Frames | — | 3 slots | — |

The single most important row is **Mutation**: it is zero in a Bee House, so **new species can only be bred in an Apiary or an Alveary**.

## Bee House

The Bee House is the cheapest shelter and the natural home for your first bee or two. It runs without frames, ignores climate entirely, and connects to nothing — you cannot automate it. It produces slowly (a quarter of the base rate) and **bees can never mutate inside it**.

What it *is* good at is keeping a bee alive: it triples both lifespan and pollination speed. That makes it a fine long-term home for a species you have already finished breeding and simply want to keep working quietly.

## Apiary

The Apiary is the workhorse of beekeeping. It mates a Princess and Drone into a Queen, runs breeding cycles at full mutation chance, collects combs and drops, and — unlike the Bee House — can be piped into and automated.

On its own the Apiary is actually the *slowest* producer (×0.1), but it has three **frame** slots, and that is where its real output comes from.

### Frames

Each frame slotted into an Apiary **doubles** the bee's production speed while it lasts, and the effect stacks across all three slots. That is exactly where the "×0.1 to ×0.8" range comes from: base ×0.1, doubled once per frame, up to ×0.8 with all three filled.

Frames wear out as bees work and eventually break. They differ in how long they last and in how gently they treat a bee's genetics — a lower **genetic decay** value preserves recessive traits better across generations:

| Frame | Uses (durability) | Genetic decay |
|---|---|---|
| Untreated | 80 | 0.9 |
| Impregnated | 240 | 0.4 |
| Proven | 720 | 0.3 |

The **Proven Frame** lasts nearly ten times as long as the Untreated one and is the kindest to your bloodlines, so it is the frame to settle on once you can craft it. (There is also a Creative frame for testing, with unlimited durability.)

## Alveary

The Alveary is the endgame of beekeeping: a **3×3×3 multiblock** of Alveary blocks, capped with a roof, that runs bees at full production (×1.0) over double their normal territory. Assemble twenty-seven Alveary blocks into a cube, block off the top layer, and the structure forms itself, leaving the small openings where bees come and go.

The Alveary does **not** take frames. Instead, several of its blocks can be swapped for **component blocks** that override a bee's needs and add new behaviour:

- **Swarmer** — periodically spawns extra princesses, so a productive line can seed itself.
- **Sieve** — collects pollen and silk from the bees passing through.
- **Hygroregulator** — consumes water or ice/lava to shift the internal humidity and temperature.
- **Heater** and **Fan** — raise or lower temperature, letting you run a bee well outside its natural climate.
- **Stabiliser** — steadies genetic behaviour within the structure.

Because these components let you dial in any climate, the Alveary is where you finally run the volatile Nether, End, and frozen bees at full output without their environment fighting you.

---

See **[Beekeeping](apiculture.md)** for the breeding overview and **[Beekeeping Tools](bee-tools.md)** for the Scoop, Beealyzer and Apiarist's armour.
