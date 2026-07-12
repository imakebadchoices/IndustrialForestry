#!/usr/bin/env python3
"""
species_data.py — the datamap-aware species model shared by the wiki build.

Unifies the two homes species now live in and exposes, per explorer category
(bees / trees / butterflies):

* ``graph`` — the breeding-explorer payload (nodes, muts, genera, resourceIndex,
  and for bees beeProducts).
* ``rows`` — one record per species for the generated appendix tables
  (label, genus, climate, products, notable traits).

Sources:
1. **Code-registered species** (base bees, all trees, all butterflies) — from
   ``build/wiki-data/species-dump.json`` produced by the ``ForestrySpeciesDumpProvider``
   datagen provider (run ``./gradlew runData`` first). The live registry, no parsing.
2. **Datapack species** (Extra Bees, any future pack) — read from
   ``src/main/resources/data/<ns>/forestry/{bee_species,bee_mutation}`` with names
   resolved from that pack's lang.

Datagen does not load datapack registries, so (2) is folded in here rather than in the
provider. Both are keyed by full resource-location id so mutations line up. As
trees/butterflies migrate to datapacks they move from (1) to (2) with no other change.
"""
import glob
import json
import re
from collections import Counter
from pathlib import Path

CATEGORY_BY_TYPE = {
    "forestry:bee_species": "bees",
    "forestry:tree_species": "trees",
    "forestry:butterfly_species": "butterflies",
}
SPECIES_REGISTRY = {"bees": "bee_species", "trees": "tree_species", "butterflies": "butterfly_species"}
MUTATION_REGISTRY = {"bees": "bee_mutation", "trees": "tree_mutation", "butterflies": "butterfly_mutation"}

# Friendlier display overrides for scientific genera. The runtime genus is the Latin
# name (e.g. "apis"); the classic branch labels ("Honey") were only Java constant names,
# absent at runtime. Extend to taste — anything not listed is Title-cased.
GENUS_LABELS = {}

TEMPERATURE_LABEL = {"ICY": "Icy", "COLD": "Cold", "NORMAL": "Normal", "WARM": "Warm", "HOT": "Hot", "HELLISH": "Hellish"}
HUMIDITY_LABEL = {"ARID": "Arid", "NORMAL": "Normal", "DAMP": "Damp"}

# Chromosomes that never belong in a "notable traits" column: the species identity
# itself, and the climate chromosomes already shown in the Climate column.
_SKIP_TRAIT_CHROMOSOMES = {"temperature", "humidity"}


def prettify(path: str) -> str:
    """'bee_forest' / 'excited' -> 'Forest' / 'Excited'."""
    for pfx in ("bee_", "tree_", "butterfly_"):
        if path.startswith(pfx):
            path = path[len(pfx):]
            break
    return " ".join(w.capitalize() for w in re.split(r"[_\s]+", path) if w)


def prettify_allele(allele_id: str) -> str:
    """Best-effort readable name for an allele id when lang has no entry.

    Strips the namespace and the trailing type codes Forestry appends to numeric
    alleles ('3i' -> '3', '0.3fd' -> '0.3', 'tolerance_down_1d' -> 'Tolerance Down 1')."""
    path = allele_id.split(":", 1)[-1]
    path = re.sub(r"(?<=[0-9])(fd|id|i|d|f)$", "", path)
    if re.fullmatch(r"[0-9.]+", path):
        return path
    return prettify(path)


class Resolver:
    """Resolves translation keys / ids to English names from the merged lang tables."""

    def __init__(self, repo: Path):
        self.lang = {}
        for p in sorted(glob.glob(str(repo / "src/main/resources/assets/*/lang/en_us.json"))):
            try:
                self.lang.update(json.load(open(p, encoding="utf-8")))
            except (OSError, ValueError):
                pass

    def species_name(self, species_id: str, category: str, translation_key=None) -> str:
        if translation_key and translation_key in self.lang:
            return self.lang[translation_key]
        ns, _, path = species_id.partition(":")
        reg = SPECIES_REGISTRY[category]
        for key in (f"allele.forestry.{reg}.{ns}.{path}", f"allele.forestry.{reg}.{path}"):
            if key in self.lang:
                return self.lang[key]
        return prettify(path)

    def item_name(self, item_id: str, comb_type=None) -> str:
        if comb_type:
            ns, _, path = comb_type.partition(":")
            if (key := f"comb.{ns}.{path}") in self.lang:
                return self.lang[key]
            return prettify(path) + " Comb"
        ns, _, path = item_id.partition(":")
        for key in (f"item.{ns}.{path}", f"block.{ns}.{path}"):
            if key in self.lang:
                return self.lang[key]
        return prettify(path)

    def fluid_name(self, fluid_id: str) -> str:
        ns, _, path = fluid_id.partition(":")
        for key in (f"fluid_type.{ns}.{path}", f"block.{ns}.fluid.{path}", f"block.{ns}.{path}"):
            if key in self.lang:
                return self.lang[key]
        return prettify(path)

    def product_name(self, product: dict) -> str:
        comb_type = (product.get("tag") or {}).get("forestry:comb_type")
        if comb_type:
            return self.item_name(product.get("item", ""), comb_type)
        extract = (product.get("tag") or {}).get("forestry:comb_extract")
        if extract and isinstance(extract.get("fluid"), dict):
            return self.fluid_name(extract["fluid"].get("id", ""))
        return self.item_name(product.get("item", ""))

    def chromosome_name(self, chromosome_id: str) -> str:
        ns, _, path = chromosome_id.partition(":")
        return self.lang.get(f"chromosome.{ns}.{path}", prettify(path))

    def allele_name(self, chromosome_id: str, allele_id: str, key=None) -> str:
        if key and key in self.lang:
            return self.lang[key]
        chromo_path = chromosome_id.split(":", 1)[-1]
        ns, _, path = allele_id.partition(":")
        for k in (f"allele.forestry.{chromo_path}.{ns}.{path}", f"allele.forestry.{chromo_path}.{path}"):
            if k in self.lang:
                return self.lang[k]
        return prettify_allele(allele_id)


def genus_label(raw: str) -> str:
    return GENUS_LABELS.get(raw, raw[:1].upper() + raw[1:] if raw else raw)


def climate_string(temperature: str, humidity: str) -> str:
    """'any', 'Icy', 'Warm / Arid', ..."""
    if not temperature and not humidity:
        return ""
    t = TEMPERATURE_LABEL.get((temperature or "").upper(), (temperature or "").title())
    h = HUMIDITY_LABEL.get((humidity or "").upper(), (humidity or "").title())
    parts = [p for p in (t if t != "Normal" else "", h if h != "Normal" else "") if p]
    return " / ".join(parts) if parts else "any"


def product_string(products, resolver: Resolver) -> str:
    """'Honey Comb 30%, Glowstone Dust 15%'."""
    return ", ".join(f"{resolver.product_name(p)} {round(p.get('chance', 0) * 100)}%" for p in products or [])


# --- centrifuge (comb -> resources) --------------------------------------------
def load_centrifuge(repo: Path, resolver: Resolver):
    by_item, by_comb_type = {}, {}
    patterns = [
        repo / "src/generated/resources/data/*/recipe/centrifuge/*.json",
        repo / "src/main/resources/data/*/recipe/centrifuge/*.json",
    ]
    for pattern in patterns:
        for path in glob.glob(str(pattern)):
            try:
                r = json.load(open(path, encoding="utf-8"))
            except (OSError, ValueError):
                continue
            if r.get("type") != "forestry:centrifuge":
                continue
            outputs = [resolver.product_name(p) for p in r.get("products", [])]
            inp = r.get("input", {})
            comb_type = (inp.get("components") or {}).get("forestry:comb_type")
            if comb_type:
                by_comb_type.setdefault(comb_type, []).extend(outputs)
            elif inp.get("item"):
                by_item.setdefault(inp["item"], []).extend(outputs)
    return by_item, by_comb_type


def bee_yields(products, resolver, by_item, by_comb_type):
    combs, direct, resources = [], [], []
    for p in products or []:
        comb_type = (p.get("tag") or {}).get("forestry:comb_type")
        name = resolver.product_name(p)
        outs = None
        if comb_type:
            combs.append(name)
            outs = by_comb_type.get(comb_type)
        elif p.get("item"):
            outs = by_item.get(p["item"])
            (combs if outs else direct).append(name)
        for o in (outs or []):
            if o not in resources:
                resources.append(o)
    return combs, direct, resources


# --- source loading ------------------------------------------------------------
def _norm_genome_from_dump(entries):
    """Dump genome: [{chromosome, allele, key}] -> [(chromosome_id, allele_id, key)]."""
    return [(e["chromosome"], e["allele"], e.get("key")) for e in entries or []]


def _norm_genome_from_datapack(genome):
    """Datapack genome: {chromosome_id: allele_id} -> [(chromosome_id, allele_id, None)]."""
    return [(cid, aid, None) for cid, aid in (genome or {}).items()]


def load_code_species(dump_path: Path):
    data = json.load(open(dump_path, encoding="utf-8"))
    result = {cat: {"species": {}, "mutations": []} for cat in CATEGORY_BY_TYPE.values()}
    for type_id, block in data.get("speciesTypes", {}).items():
        cat = CATEGORY_BY_TYPE.get(type_id)
        if not cat:
            continue
        for s in block.get("species", []):
            result[cat]["species"][s["id"]] = {
                "id": s["id"], "translationKey": s.get("translationKey"),
                "genus": s.get("genus", ""), "temperature": s.get("temperature", ""),
                "humidity": s.get("humidity", ""), "products": s.get("products", []),
                "genome": _norm_genome_from_dump(s.get("genome")),
            }
        for m in block.get("mutations", []):
            result[cat]["mutations"].append(
                {"a": m["first"], "b": m["second"], "r": m["result"], "c": round(m.get("chance", 0) * 100)})
    return result


def load_datapack_species(repo: Path, model):
    for cat, reg in SPECIES_REGISTRY.items():
        for path in glob.glob(str(repo / f"src/main/resources/data/*/forestry/{reg}/*.json")):
            parts = Path(path).parts
            ns = parts[parts.index("data") + 1]
            sid = f"{ns}:{Path(path).stem}"
            try:
                s = json.load(open(path, encoding="utf-8"))
            except (OSError, ValueError):
                continue
            model[cat]["species"][sid] = {
                "id": sid, "translationKey": None, "genus": s.get("genus", ""),
                "temperature": s.get("temperature", ""), "humidity": s.get("humidity", ""),
                "products": s.get("products", []), "genome": _norm_genome_from_datapack(s.get("genome")),
            }
    for cat, reg in MUTATION_REGISTRY.items():
        for path in glob.glob(str(repo / f"src/main/resources/data/*/forestry/{reg}/*.json")):
            try:
                m = json.load(open(path, encoding="utf-8"))
            except (OSError, ValueError):
                continue
            model[cat]["mutations"].append({
                "a": m["first_parent"], "b": m["second_parent"], "r": m["result"],
                "c": round(m.get("chance", 0) * 100)})


# --- notable traits (deviations from the category's most common allele) ---------
def _modal_alleles(species_map):
    counts = {}
    for s in species_map.values():
        for cid, aid, _ in s["genome"]:
            counts.setdefault(cid, Counter())[aid] += 1
    return {cid: c.most_common(1)[0][0] for cid, c in counts.items()}


def _notable_traits(species, modal, resolver):
    traits = []
    for cid, aid, key in species["genome"]:
        chromo_path = cid.split(":", 1)[-1]
        if chromo_path.endswith("_species") or chromo_path in _SKIP_TRAIT_CHROMOSOMES:
            continue
        if aid == modal.get(cid):
            continue
        traits.append(f"{resolver.chromosome_name(cid)}: {resolver.allele_name(cid, aid, key)}")
    return traits


# --- assembly ------------------------------------------------------------------
def build_model(repo: Path, dump_path: Path):
    """Returns {cat: {"graph": {...}, "rows": [...]}} for all three categories."""
    resolver = Resolver(repo)
    model = load_code_species(dump_path)
    load_datapack_species(repo, model)
    by_item, by_comb_type = load_centrifuge(repo, resolver)

    out = {}
    for cat, block in model.items():
        species_map = block["species"]
        modal = _modal_alleles(species_map)
        nodes, rows, genera = [], [], set()
        bee_products, resource_index = {}, {}

        for sid, s in sorted(species_map.items(), key=lambda kv: kv[0]):
            label = resolver.species_name(sid, cat, s.get("translationKey"))
            genus = genus_label(s.get("genus", ""))
            climate = climate_string(s.get("temperature"), s.get("humidity"))
            products = product_string(s.get("products"), resolver)
            if genus:
                genera.add(genus)
            nodes.append({"id": sid, "label": label, "genus": genus,
                          "products": products, "climate": climate, "wood": ""})
            rows.append({"id": sid, "label": label, "genus": genus, "climate": climate,
                         "products": products, "traits": _notable_traits(s, modal, resolver)})
            if cat == "bees":
                combs, direct, resources = bee_yields(s.get("products"), resolver, by_item, by_comb_type)
                bee_products[sid] = {"combs": combs, "direct": direct, "resources": resources}
                for resource in resources + direct:
                    index = resource_index.setdefault(resource, [])
                    if label not in index:
                        index.append(label)

        known = set(species_map.keys())
        muts = [m for m in block["mutations"] if m["a"] in known and m["b"] in known and m["r"] in known]
        graph = {"nodes": nodes, "muts": muts, "genera": sorted(genera),
                 "resourceIndex": {k: sorted(v) for k, v in sorted(resource_index.items())}}
        if cat == "bees":
            graph["beeProducts"] = bee_products

        rows.sort(key=lambda r: (r["genus"], r["label"]))
        out[cat] = {"graph": graph, "rows": rows}
    return out
