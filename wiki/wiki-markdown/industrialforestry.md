# Additions

IndustrialForestry is a fork of [Forestry: Community Edition](https://github.com/thedarkcolour/ForestryCE). This page lists what it adds; everything else in the wiki is core Forestry as CE ships it.

## Data-driven bees

This fork adds a data-driven bee system on top of Forestry: CE. Bee species, mutations, combs, effects, and flower types — previously fixed in the mod's code — can now be defined through datapacks, using datapack registries and datamaps. The system is generic: any datapack or add-on can define its own bees with it, without writing Java.

## Extra Bees

The first content built on that system is a reimplementation of [**Extra Bees**](https://github.com/ForestryMC/Binnie), originally created by **Binnie**. It is bundled into the mod jar, so it is present without a separate download — 122 species and 174 mutations covering metallic, gemstone, mineral, and elemental lines.

They breed with the same tools and rules as the base bees. They are included in the [Bee Species & Mutations](bee-species.html) table and the [Breeding Explorer](explorer.html#bees), and are grouped under `@extrabees` in JEI. This port is currently at an alpha stage.

## Higher-tier backpacks

Two backpack tiers are added above the base Woven tier for the item backpacks (Miner's, Digger's, Forester's, Hunter's, Adventuring, and Builder's): **Ender-Woven** and **Chorus-Woven**, each with more capacity. They behave the same as the backpacks they upgrade and are crafted through the [Carpenter](carpenter.md). See [Backpacks & Crates](storage.md) for backpack behaviour.

## Tin Drill

The **Tin Drill** is a compatibility item for **Modern Industrialization (MI)**; it is only present when MI is installed. It is crafted from MI components and consumed by MI's Quarry, which produces earthy blocks — sand, clay, dirt variants, mud, and moss — along with Forestry's Apatite Ore and Bog Earth. It is part of a set of MI compatibility changes in this fork, which also let Forestry's fluids be used in MI machinery.
