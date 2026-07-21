#!/usr/bin/env python3
"""
species_data.py — the datamap-aware species model shared by the wiki build.

Unifies the two homes species now live in and exposes, per explorer category
(bees / trees / butterflies):

* ``graph`` — the breeding-explorer payload (nodes, muts, genera, resourceIndex,
  and for bees beeProducts).
* ``rows`` — one record per species for the generated appendix tables
  (label, genus, climate, products, notable traits).

Sources: since base CE became data-driven, every species (base + add-on) is on-disk JSON in one
schema, read uniformly from the ``.../data`` roots of every Gradle source set (see ``data_roots``):

* **base forestry** — datagen'd under ``src/generated/resources/data/forestry`` + hand-authored under
  ``src/main/resources/data/forestry``.
* **bundled add-ons** (Extra Bees, and any future pack) — each in its own source set, e.g.
  ``src/extrabees/resources/data/extrabees`` + ``src/extrabees/generated/data/extrabees``.

All are keyed by full resource-location id so mutations line up, and names resolve from every source
set's lang. Add-on source sets are discovered by globbing, so a new add-on appears with no code change.
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


def prettify_allele(token: str) -> str:
    """Readable name for an allele value token when lang has no entry.

    Reference-chromosome ids fall back to their prettified path; inline value tokens render directly:
    'true'/'false' -> 'Yes'/'No', '9x6x9' -> '9 × 6 × 9', numbers as-is, enums like 'DOWN_2' -> 'Down 2'."""
    if token == "true":
        return "Yes"
    if token == "false":
        return "No"
    m = re.fullmatch(r"(\d+)x(\d+)x(\d+)", token)
    if m:
        return " × ".join(m.groups())
    path = token.split(":", 1)[-1]
    if re.fullmatch(r"-?[0-9.]+", path):
        return path
    return prettify(path)


class Resolver:
    """Resolves translation keys / ids to English names from the merged lang tables."""

    def __init__(self, repo: Path):
        self.lang = {}
        # Names live in every source set's assets, not just base forestry's — add-on species (extrabees, …)
        # carry their own lang under src/<addon>/resources/assets/<ns>/lang, so glob all source sets.
        for p in sorted(glob.glob(str(repo / "src/*/resources/assets/*/lang/en_us.json"))):
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

    # Readable names for tag-based outputs (e.g. an ``extrabees:tag`` centrifuge product
    # `c:dusts/nickel`). Keyed by the tag's category segment; anything unlisted falls back to
    # "<material> <Category>". Material-only tags (no '/') just get their path prettified.
    _TAG_TEMPLATE = {
        "dusts": "{} Dust", "tiny_dusts": "Tiny {} Dust", "ingots": "{} Ingot",
        "nuggets": "{} Nugget", "plates": "{} Plate", "gears": "{} Gear",
        "gems": "{}", "crops": "{}",
    }

    def tag_name(self, tag_id: str) -> str:
        path = tag_id.split(":", 1)[-1]            # c:dusts/nickel -> dusts/nickel
        category, _, material = path.partition("/")
        if material:
            template = self._TAG_TEMPLATE.get(category, "{} " + prettify(category))
            return template.format(prettify(material)).strip()
        return prettify(category)                  # c:sawdust -> Sawdust

    def product_name(self, product: dict) -> str:
        tag = product.get("tag")
        if isinstance(tag, str):                   # tag output (extrabees:tag centrifuge products)
            return self.tag_name(tag)
        if isinstance(tag, dict):                  # legacy comb_type / comb_extract wrapper
            comb_type = tag.get("forestry:comb_type")
            if comb_type:
                return self.item_name(product.get("item", ""), comb_type)
            extract = tag.get("forestry:comb_extract")
            if extract and isinstance(extract.get("fluid"), dict):
                return self.fluid_name(extract["fluid"].get("id", ""))
        if product.get("item"):
            return self.item_name(product["item"])
        # Item-less special product (e.g. {"type": "forestry:firework"}): name it from its type.
        return prettify(product.get("type", "").split(":", 1)[-1]) or "—"

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


def _percent(chance) -> str:
    """Whole-percent label, but never round a real drop down to a misleading '0%'."""
    pct = (chance or 0) * 100
    return "<1%" if 0 < pct < 1 else f"{round(pct)}%"


def product_string(products, resolver: Resolver) -> str:
    """'Honey Comb 30%, Glowstone Dust 15%'."""
    return ", ".join(f"{resolver.product_name(p)} {_percent(p.get('chance', 0))}" for p in products or [])


# --- centrifuge (comb -> resources) --------------------------------------------
def load_centrifuge(repo: Path, resolver: Resolver):
    by_item, by_comb_type = {}, {}
    patterns = [repo / root / "*/recipe/centrifuge/*.json" for root in data_roots(repo)]
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
        tag = p.get("tag")
        comb_type = tag.get("forestry:comb_type") if isinstance(tag, dict) else None
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
# Since base CE became data-driven, every species (base + add-on) is on-disk JSON in the same schema, so the
# wiki reads them uniformly instead of merging a code dump with datapack JSON. The data lives across several
# Gradle source sets: base forestry under src/main + src/generated, and each bundled add-on (extrabees, …) in
# its own source set (src/<addon>/resources + src/<addon>/generated). Both hand-authored and datagen'd JSON use
# the same upstream inline-value genome + mutation-recipe schema, so the wiki globs every source set's data
# roots — hand-authored (`resources/data`) and datagen'd (`generated/[resources/]data`) — rather than hardcoding
# forestry's two, which silently dropped add-on species (see the `data_roots` discovery below).


def data_roots(repo: Path):
    """Every ``.../data`` directory across all Gradle source sets that ships species/recipe JSON.

    Base forestry keeps datagen output under ``src/generated/resources/data`` and hand-authored data under
    ``src/main/resources/data``; each add-on source set mirrors this as ``src/<name>/generated/data`` and
    ``src/<name>/resources/data``. We discover them by globbing rather than listing so a new add-on shows up in
    the wiki with no change here. Returned as repo-relative POSIX strings (stable ordering)."""
    roots = set()
    for pattern in ("src/*/resources/data", "src/*/generated/data", "src/*/generated/resources/data"):
        for path in glob.glob(str(repo / pattern)):
            roots.add(str(Path(path).relative_to(repo).as_posix()))
    return sorted(roots)


def _value_token(value):
    """Canonical string token for an inline genome value, for both display and modal comparison.

    Reference-chromosome values are allele ids (contain ':', resolved via lang); value-chromosome values are
    raw (float/int/bool/enum string/Vec3i list) and render directly."""
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, list):
        return "x".join(str(v) for v in value)
    return str(value)


def _norm_genome(genome):
    """Inline-value genome {chromosome_id: {"value": V, ("dominant": D)}} -> [(chromosome_id, token, None)]."""
    out = []
    for cid, entry in (genome or {}).items():
        value = entry.get("value") if isinstance(entry, dict) else entry
        out.append((cid, _value_token(value), None))
    return out


def load_species(repo: Path, model):
    roots = data_roots(repo)
    for cat, reg in SPECIES_REGISTRY.items():
        for root in roots:
            for path in glob.glob(str(repo / root / f"*/{reg}/*.json")):
                parts = Path(path).parts
                ns = parts[parts.index("data") + 1]
                sid = f"{ns}:{Path(path).stem}"
                try:
                    s = json.load(open(path, encoding="utf-8"))
                except (OSError, ValueError):
                    continue
                # A species yields both its common ``products`` and rarer ``specialties``;
                # players care about both, so the wiki lists them together.
                model[cat]["species"][sid] = {
                    "id": sid, "translationKey": None, "genus": s.get("genus", ""),
                    "temperature": s.get("temperature", ""), "humidity": s.get("humidity", ""),
                    "products": (s.get("products") or []) + (s.get("specialties") or []),
                    "genome": _norm_genome(s.get("genome")),
                }
    for cat, reg in MUTATION_REGISTRY.items():
        for root in roots:
            for path in glob.glob(str(repo / root / f"*/recipe/{reg}/*.json")):
                try:
                    m = json.load(open(path, encoding="utf-8"))
                except (OSError, ValueError):
                    continue
                if "first" not in m or "second" not in m or "result" not in m:
                    continue
                model[cat]["mutations"].append({
                    "a": m["first"], "b": m["second"], "r": m["result"],
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
def build_model(repo: Path, dump_path: Path = None):
    """Returns {cat: {"graph": {...}, "rows": [...]}} for all three categories.

    dump_path is accepted (and ignored) for backwards compatibility; species now come entirely from on-disk JSON."""
    resolver = Resolver(repo)
    model = {cat: {"species": {}, "mutations": []} for cat in CATEGORY_BY_TYPE.values()}
    load_species(repo, model)
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
