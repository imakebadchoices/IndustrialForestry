# Multifarm

The **Multifarm** is Forestry's flagship automation block: a configurable multiblock that plants, tends, fertilizes, and harvests a whole area on its own, then drops the produce into its own inventory for you to pipe away. It turns manual crop and tree work into a hands-off operation, at the cost of **fertilizer** and **water** to keep running.

This page covers what a Multifarm does and the crops it can grow; see **[Farm Structure](farm-structure.md)** for how to actually build one.

## Usage

A built Multifarm is a hollow structure with a central band of soil. What it grows is decided by the **farm logic** you install into it, and it draws two resources to operate:

- **Water**, piped in or supplied through its valve, to hydrate the soil.
- **Fertilizer**, consumed steadily as it works (how fast is governed by Forestry's config).

Everything the farm harvests — logs, crops, fruit, saplings — is collected into its inventory, ready to be pulled out and processed elsewhere.

## Farm types

A Multifarm is specialised by its farm logic. The available types are:

- **Arboreal** — plants and fells trees for logs and saplings.
- **Crops** — wheat, potatoes, carrots and similar tilled crops.
- **Gourd** — pumpkins and melons.
- **Orchard** — fruiting trees and vine crops.
- **Cocoa** — cocoa pods on jungle logs.
- **Poales** — sugar cane and reeds.
- **Succulentes** — cacti and desert plants.
- **Shroom** — mushrooms.
- **Peat** — harvests peat from bog soil, feeding the [Peat Engine](energy.md).
- **Infernal** — Nether growth such as nether wart.
- **Ender** — chorus plants and other End growth.

A single Multifarm can be set up to run more than one type at once, letting one structure tend several kinds of crop from its four sides.
