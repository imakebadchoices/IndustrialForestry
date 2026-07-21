# BadMods

BadMods is a collection of add-ons for, and a light fork of, [Forestry: Community Edition](https://github.com/thedarkcolour/ForestryCE) itself a continuation of the original [Forestry](https://github.com/ForestryMC/ForestryMC). The add-ons are separate jars, each integrating Forestry with other mods; the fork also makes a few player-facing changes to Forestry itself.

Grab the jars from the [Downloads](downloads.md) page.

## Add-ons

Each add-on is its own jar and can be installed independently.

- **[Beegistics](beegistics.md)** [Applied Energistics 2](https://modrinth.com/mod/ae2) integration. Store, browse, and automate bees on an ME network: bee storage cells, an Apiarist's Terminal that reads genomes, a Bee Analyzer, and an Apiary Controller that drives breeding — including full ME autocrafting of a breeding tree. Requires AE2.
- **[Beeripherals](beeripherals.md)** [CC: Tweaked](https://modrinth.com/mod/cc-tweaked) integration. Naturalist chests become computer peripherals that read specimen genomes and move specimens between inventories. Requires CC: Tweaked.
- **[Modern Bees](modern-bees.md)** [Modern Industrialization](https://modrinth.com/mod/modern-industrialization) and [Create Addition](https://modrinth.com/mod/create-crafts-additions) integration. A tin drill for MI's Quarry, Forestry fluids usable as MI fuel and in Create Addition's liquid blaze burner, and flammable fuel buckets. Both mods are optional.
- **[Extra Bees](extra-bees.md)** a reimplementation of Binnie's Extra Bees: 122 bee species and 174 mutations on CE's data-driven genetics, bundled into the jar. Currently requires this fork of Forestry (see the page for details).

## A fork?

Well no, well kinda. I prefer a monorepo design, and keeping all of the addons with the source code of the repo itself is useful.
At times when working on them I need to make an experimental core change, and its easier to just keep it all together.
Along with that the following personal/opinionated changes are annoying/difficult to pull out in to an addon so they stay in a forked core.

### Power generation

Engine fuel outputs were raised to a modern Forge Energy baseline, about ten times the previous values, so the biogas and peat engines stay useful alongside current power mods. Each fuel's output in RF per tick and its burn duration are configurable, as is a global multiplier on the energy every Forestry machine consumes.

### Backpacks

Two tiers were added above the Woven tier for the item backpacks (Miner's, Digger's, Forester's, Hunter's, Adventuring, and Builder's). The Woven tier holds 45 slots. Ender-Woven holds 54 slots, the size of a double chest. Chorus-Woven holds 81 slots, a 9 by 9 grid. Both are crafted in the Carpenter and otherwise behave like the backpacks they upgrade.
