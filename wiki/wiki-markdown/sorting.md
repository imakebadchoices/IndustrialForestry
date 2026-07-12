# Genetic Filter

The **Genetic Filter** is a routing block for living cargo. Where an ordinary item filter sorts by item type, the Genetic Filter reads the **genome** of the bees, trees, and butterflies passing through it and directs them according to rules you set.

## Usage

The filter inspects each genetic specimen that enters and routes it out of a chosen side based on its traits. Rules are configured **per direction**, so each face of the block can be given its own criteria — for example, sending one species out the north face, drones of another out the east, and everything unmatched straight through.

Rules can key on genetic properties such as:

- **Species** — route a specific bee, tree, or butterfly species.
- **Active or inactive alleles** — match on either the expressed or the hidden trait, which lets you separate carriers of a recessive gene from the rest.

## Purpose

Piped into an [Apiary](bee-housing.md) or Alveary array, a Genetic Filter can automatically pull finished queens off to storage, divert the right drones back into breeding, and keep unwanted offspring out of your working stock. It works on all three genetic families — bees, trees, and butterflies.
