# Beegistics

Beegistics connects Forestry bees to [Applied Energistics 2](https://modrinth.com/mod/ae2).

## Bee storage cells

<figure class="screenshot">
  <img src="images/beegistics-bee-cell.png" alt="An ME Drive holding a 16k Bee Storage Cell; the tooltip reads 129 bees across 3 kinds, 135 of 16384 bytes used">
  <figcaption>A 16k cell in a drive, holding 129 bees across 3 kinds.</figcaption>
</figure>

Four tiers, 1k, 4k, 16k, and 32k, crafted from the matching AE2 storage components. Capacity is measured in bytes rather than item types: identical bees are nearly free, and each distinct genome is charged according to its size, so closely related bees pack far tighter than a spread of unrelated genomes. There is no 63-type limit, so a cell fills against its byte budget alone.

A cell can be partitioned in a Cell Workbench, but by species rather than exact genome; every analyzed bee is otherwise a unique key. The tooltip reports bytes used and the current bee and kind counts.

## Apiarist's Terminal

<figure class="screenshot">
  <img src="images/beegistics-terminal.png" alt="The Apiarist's Terminal: a bee grid on the left, an inline analyzer, and a genetics readout listing every chromosome's active and inactive allele">
  <figcaption>Hovering a bee lists every chromosome; the inline analyzer sits top-right.</figcaption>
</figure>

An ME terminal for bees. Hovering a bee lists every chromosome's active and inactive allele with the dominant allele in bold. Unanalyzed bees show their species and are marked *Unanalyzed*; the rest of the genome stays hidden until analysis. The grid defaults to showing only bees, with a toggle for all items.

An inline analyzer occupies two slots beside the grid: drop an unanalyzed bee into the input and it is analyzed in place, paid for with network energy, and moved to the output.

## Bee Analyzer

A grid block with no GUI. It scans network storage for unanalyzed bees and analyzes them in place, drawing Honey from the network's fluid storage (buffered internally) plus energy per bee, at the same rates as a standalone Analyzer. Placed on a network, it keeps stored bees analyzed so the terminal and any automation read full genetics.

## Bee Filter card

Encodes a bee predicate; any combination of life stage, active species, genus, and analyzed or pristine state. Right-click to open its configuration screen; species and genus are captured from a bee held in your other hand. It is the matching primitive the Apiary Controller uses in card mode.

## Apiary Controller

<figure class="screenshot">
  <img src="images/beegistics-apiary-controller.png" alt="The Apiary Controller GUI: Princess, Drone and Target filter-card slots, a 3x3 pattern grid, a status band reading Apiary linked, and a bee inventory">
  <figcaption>Pattern mode: princess, drone, and target filter cards, with a pattern grid alongside.</figcaption>
</figure>

A block that joins the ME grid and drives an adjacent apiary. It runs in one of two modes, depending on what it holds.

**Card mode** takes three Bee Filter cards: a princess parent, a drone parent, and a target. It keeps the apiary stocked with matching parents pulled from the network and returns the offspring and produce to storage. Once the network holds at least a set number of the target species it stops pulling parents, halting the loop on its own; it resumes automatically if the count later drops back below the threshold. The threshold ("Stop at") is editable in the GUI. The mutation runs on the apiary itself, so its chance, timing, and climate are unchanged.

**Pattern mode** takes encoded Bee Mutation Patterns and registers the controller as an AE2 crafting provider. Request a species in the crafting terminal and AE2 schedules the whole breeding tree, breeding each step on the apiary. A current limitation is that autocrafted bees are normalized to the species' default genome. This is done to normalize the requirements through the AE2 autocrafting internals, this is subject to change in the future. A mutation pattern is encoded by holding it with the result bee in your other hand and right-clicking.
