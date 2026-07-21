# Beeripherals

Beeripherals integrates Forestry with [CC: Tweaked](https://modrinth.com/mod/cc-tweaked). It requires CC: Tweaked to load.

With it installed, the naturalist chests act as peripherals. A computer wrapping one gets a `bee_chest`, `tree_chest`, or `butterfly_chest` peripheral, depending on the chest. Slots are 1-based, following the standard peripheral convention.

## Peripheral methods

- `size()`: the number of slots in the chest.
- `getSpecimen(slot)`: a table describing the specimen in a slot, or nil if empty. Analyzed specimens include their full genome, the same data the Beealyzer shows: the active and inactive allele of every chromosome, plus produce, specialty combs, and preferred climate.
- `getSpecimens()`: a list of those tables for every occupied slot, each tagged with its `slot`.
- `searchSpecimen(query)`: the subset of `getSpecimens()` whose specimens match every field in the `query` table. Strings match case-insensitively as substrings, numbers by value, and booleans exactly. Fields read from the genome only match analyzed specimens.
- `pushItems(toName, fromSlot [, limit [, toSlot]])` and `pullItems(fromName, fromSlot [, limit [, toSlot]])`: move specimens to or from another inventory peripheral, as on a standard inventory.
