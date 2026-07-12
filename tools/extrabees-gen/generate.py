#!/usr/bin/env python3
"""
One-shot translator: Binnie Extra Bees (1.12 source) -> a ForestryCE 1.21 `extrabees` datapack.

The Binnie enums are 1.12 Forge Java and can't be reflected on the 1.21 classpath, so this parses the
enum *source text* and emits JSON that plugs into the datapack knobs already built in ForestryCE core
(bee_species / bee_mutation / taxon / flower_type / comb_type / bee_effect + centrifuge recipes).

Run from the repo root:  python3 tools/extrabees-gen/generate.py
Output:                  tools/extrabees-gen/pack/   (a complete datapack, namespace `extrabees`)
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
from mappings import (  # noqa: E402
    NS, CHROMOSOME, SPEED, LIFESPAN, FERTILITY, FLOWERING, TOLERANCE, TERRITORY,
    FLOWERS_BASE, BOOL, BASE_EFFECTS, TEMPERATURE, HUMIDITY, BASE_FAMILY_TAXON, DEFAULT_TEMPLATE,
    OUTPUT_MAP,
)

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
BINNIE = os.path.join(REPO, "..", "Binnie", "extrabees", "src", "main", "java", "binnie", "extrabees")
GEN = os.path.join(BINNIE, "genetics")
OUT = os.path.join(REPO, "tools", "extrabees-gen", "pack")
DATA = os.path.join(OUT, "data", NS, "forestry")
RECIPE = os.path.join(OUT, "data", NS, "recipe", "centrifuge")

warnings = []
items_needed = set()   # extrabees:* items the module must register


def _load_eb_lang():
    """Old Extra Bees en_US.lang (key=value) — the authoritative display names to match aesthetically."""
    path = os.path.join(REPO, "..", "Binnie", "extrabees", "src", "main", "resources",
                        "assets", "extrabees", "lang", "en_US.lang")
    lang = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                lang[k.strip()] = v.strip()
    return lang


EB_LANG = _load_eb_lang()


def warn(msg):
    warnings.append(msg)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def color(hexlit):
    return "#%06x" % (int(hexlit, 16) & 0xFFFFFF)


def color_int(n):
    return "#%06x" % (int(n) & 0xFFFFFF)


# Flower/effect allele ids MUST be disjoint from species ids: alleles are keyed by id in one shared
# AlleleManager map across all chromosomes, and Extra Bees reuses names (species WATER/ROCK vs flower
# water/rock, water effect). Prefix them so they can never collide with a species id.
def effect_id(name):
    return f"{NS}:effect_{name.lower()}"


def flower_id(name):
    return f"{NS}:flower_{name.lower()}"


def species_ref(tok):
    tok = tok.strip()
    if tok.startswith("BeeDefinition."):
        return "forestry:bee_" + tok.split(".", 1)[1].lower()
    if tok.startswith("ExtraBeeDefinition."):
        return f"{NS}:" + tok.split(".", 1)[1].lower()
    if re.fullmatch(r"[A-Z][A-Z0-9_]*", tok):        # bare sibling constant
        return f"{NS}:" + tok.lower()
    raise ValueError("unresolved species ref: " + tok)


def split_top_commas(s):
    out, depth, cur = [], 0, ""
    for ch in s:
        if ch in "([":
            depth += 1
        elif ch in ")]":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur)
    return out


# ---------------------------------------------------------------------------------------------
# Allele resolution for AlleleHelper.set(template, EnumBeeChromosome.X, VALUE)
# Returns list of (marker, value): marker is a chromosome id, or '__never_sleeps__'.
ALLELE_TABLES = {
    "Speed": SPEED, "Lifespan": LIFESPAN, "Fertility": FERTILITY, "Flowering": FLOWERING,
    "Tolerance": TOLERANCE, "Territory": TERRITORY, "Flowers": FLOWERS_BASE,
}


def resolve_set(chromo, value):
    value = value.strip()
    if chromo == "NEVER_SLEEPS":
        return ("__never_sleeps__", value == "true")
    if chromo in ("CAVE_DWELLING", "TOLERATES_RAIN"):
        return (CHROMOSOME[chromo], BOOL[value == "true"])
    cid = CHROMOSOME.get(chromo)
    if cid is None:
        raise ValueError("unknown chromosome " + chromo)
    m = re.match(r"EnumAllele\.(\w+)\.(\w+)", value)
    if m:
        typ, name = m.groups()
        return (cid, ALLELE_TABLES[typ][name])
    m = re.search(r"ExtraBeesEffect\.(\w+)\.getUID", value)
    if m:
        return (cid, effect_id(m.group(1)))
    m = re.search(r"ExtraBeesFlowers\.(\w+)\.getUID", value)
    if m:
        return (cid, flower_id(m.group(1)))
    m = re.match(r"AlleleEffects\.effect(\w+)", value)
    if m:
        snake = re.sub(r"(?<!^)(?=[A-Z])", "_", m.group(1)).lower()
        return (cid, "forestry:bee_effect_" + snake)
    m = re.search(r'getAllele\(\s*"([^"]+)"\s*\)', value)
    if m:
        uid = m.group(1)
        if uid.startswith("extrabees.effect."):
            return (cid, effect_id(uid.rsplit(".", 1)[1]))
        if uid.startswith("extrabees.flower."):
            return (cid, flower_id(uid.rsplit(".", 1)[1]))
        if uid.startswith("forestry.effect"):
            # forestry.effectDrunkard -> forestry:bee_effect_drunkard
            snake = re.sub(r"(?<!^)(?=[A-Z])", "_", uid[len("forestry.effect"):]).lower()
            return (cid, "forestry:bee_effect_" + snake)
        raise ValueError("unknown getAllele uid: " + uid)
    raise ValueError(f"unresolved allele value for {chromo}: {value}")


def parse_set_lines(body):
    """All AlleleHelper...set(template, EnumBeeChromosome.X, VALUE) in a block -> list of (marker, val)."""
    out = []
    for m in re.finditer(r"set\(\s*(?:template|defaultTemplate)\s*,\s*EnumBeeChromosome\.(\w+)\s*,\s*(.+?)\)\s*;",
                         body, re.S):
        chromo, value = m.group(1), m.group(2).strip()
        try:
            out.append(resolve_set(chromo, value))
        except (ValueError, KeyError) as e:
            warn(f"skipped allele: {e}")
    return out


# ---------------------------------------------------------------------------------------------
# Branch definitions -> per-branch allele overrides + the taxa list.
def parse_branches():
    text = open(os.path.join(GEN, "ExtraBeeBranchDefinition.java")).read()
    body = text[text.index("implements IBranchDefinition"):text.index("private static IAllele[] defaultTemplate")]
    templates = {}      # BRANCH -> list[(marker, val)]
    # entry header: NAME("Scientific")  optionally followed by a { ... } body
    for m in re.finditer(r"\n\t([A-Z][A-Z0-9_]*)\(\"([^\"]+)\"\)", body):
        name = m.group(1)
        # capture body up to the next top-level entry header or end
        rest = body[m.end():]
        nxt = re.search(r"\n\t[A-Z][A-Z0-9_]*\(\"", rest)
        chunk = rest[:nxt.start()] if nxt else rest
        templates[name] = parse_set_lines(chunk)
    return templates


# ---------------------------------------------------------------------------------------------
# Species (ExtraBeeDefinition) + the standalone doInit() mutations.
SPECIES_HEADER = re.compile(
    r"\n\t([A-Z][A-Z0-9_]*)\(\s*(ExtraBeeBranchDefinition|BeeBranchDefinition)\.(\w+)\s*,\s*"
    r"\"([^\"]+)\"\s*,\s*(true|false)\s*,\s*new Color\((0x[0-9a-fA-F]+)\)\s*,\s*new Color\((0x[0-9a-fA-F]+)\)\s*\)"
)


def parse_species(text):
    body = text[text.index("implements IBeeDefinition"):text.index("private final IBranchDefinition branch;")]
    species = []
    matches = list(SPECIES_HEADER.finditer(body))
    for i, m in enumerate(matches):
        start = m.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(body)
        chunk = body[start:end]
        species.append({
            "name": m.group(1),
            "branch_type": m.group(2),
            "branch": m.group(3),
            "binomial": m.group(4),
            "dominant": m.group(5) == "true",
            "primary": m.group(6),
            "secondary": m.group(7),
            "chunk": chunk,
        })
    return species


def extract_block(chunk, method):
    """Return the brace-matched body of `protected void method(...) { ... }` inside chunk, or ''. """
    idx = chunk.find(method)
    if idx < 0:
        return ""
    brace = chunk.find("{", idx)
    depth, i = 0, brace
    while i < len(chunk):
        if chunk[i] == "{":
            depth += 1
        elif chunk[i] == "}":
            depth -= 1
            if depth == 0:
                return chunk[brace + 1:i]
        i += 1
    return chunk[brace + 1:]


def find_calls(block, method):
    """Yield the inner argument string of each `.method( ... )` call (paren-matched)."""
    for m in re.finditer(re.escape("." + method) + r"\(", block):
        i, depth = m.end(), 1
        while i < len(block) and depth:
            if block[i] == "(":
                depth += 1
            elif block[i] == ")":
                depth -= 1
            i += 1
        yield block[m.end():i - 1]


# ForestryCE base EnumHoneyComb variants (for VanillaComb name routing).
BASE_COMB_VARIANTS = {
    "honey", "cocoa", "simmering", "stringy", "frozen", "dripping", "silky", "parched",
    "mysterious", "powdery", "wheaten", "mossy", "mellow", "kaolin", "vintage", "sponge", "sculken",
}


def product_item(expr):
    """A product item expression -> {"item":..., ["tag":...]} spec, or None."""
    expr = expr.strip()
    m = re.match(r"EnumHoneyComb\.(\w+)\.get", expr)
    if m:
        return {"item": "forestry:comb", "tag": {"forestry:comb_type": f"{NS}:{m.group(1).lower()}"}}
    m = re.match(r"ItemHoneyComb\.VanillaComb\.(\w+)\.get", expr)
    if m:
        v = m.group(1).lower()
        if v not in BASE_COMB_VARIANTS:
            warn(f"VanillaComb.{m.group(1)} has no base comb; falling back to bee_comb_honey")
            v = "honey"
        return {"item": f"forestry:bee_comb_{v}"}
    iid = itemstack_item(expr)
    if iid:
        return {"item": iid}
    m = re.match(r"Mods\.Forestry\.(?:stack|item)\(\s*\"(\w+)\"", expr)
    if m:
        return {"item": forestry_item(m.group(1))}
    m = re.match(r"ExtraBeeItems\.(\w+)", expr)
    if m:
        iid = f"{NS}:" + m.group(1).lower()
        items_needed.add(iid)
        return {"item": iid}
    warn("unresolved product item: " + expr[:50])
    return None


def parse_products(block):
    prods, specs = [], []
    for method, dest in (("addProduct", prods), ("addSpecialty", specs)):
        for inner in find_calls(block, method):
            args = split_top_commas(inner)
            if len(args) < 2:
                continue
            spec = product_item(args[0])
            if spec is None:
                continue
            spec = dict(spec)
            spec["chance"] = round(float(args[-1].strip().rstrip("fF")), 3)
            dest.append(spec)
    return prods, specs


# TagKey.codec(BIOME) decodes a PLAIN resource location (no leading '#' — that's the hashed-codec form).
BIOME_TAGS = {"NETHER": "minecraft:is_nether", "OCEAN": "minecraft:is_ocean", "RIVER": "minecraft:is_river"}

# 1.12 Items.DYE metadata -> 1.21 item id.
DYE_META = {
    0: "minecraft:ink_sac", 1: "minecraft:red_dye", 2: "minecraft:green_dye", 3: "minecraft:cocoa_beans",
    4: "minecraft:lapis_lazuli", 5: "minecraft:purple_dye", 6: "minecraft:cyan_dye",
    7: "minecraft:light_gray_dye", 8: "minecraft:gray_dye", 9: "minecraft:pink_dye", 10: "minecraft:lime_dye",
    11: "minecraft:yellow_dye", 12: "minecraft:light_blue_dye", 13: "minecraft:magenta_dye",
    14: "minecraft:orange_dye", 15: "minecraft:bone_meal",
}

# Mods.Forestry.stack("X") -> 1.21 forestry item id (renamed/regrouped items).
FORESTRY_ITEMS = {
    "pollen": "forestry:pollen_cluster_normal",
    "fertilizer_bio": "forestry:fertilizer_bio",
}


def forestry_item(name):
    return FORESTRY_ITEMS.get(name, "forestry:" + name.lower())


def itemstack_item(expr):
    """`new ItemStack(...)` -> concrete 1.21 item id (handles the Items.DYE metadata split)."""
    m = re.match(r"new ItemStack\(\s*Items\.DYE\s*,\s*\d+\s*,\s*(\d+)\)", expr)
    if m:
        return DYE_META[int(m.group(1))]
    m = re.match(r"new ItemStack\(\s*(?:Items|Blocks)\.(\w+)", expr)
    if m:
        return "minecraft:" + m.group(1).lower()
    return None


def parse_mutations(block, result_default):
    """Parse registerMutation(...) statements. Returns list of dicts."""
    out = []
    for stmt in re.split(r"registerMutation\(", block)[1:]:
        # stmt starts right after '('; take up to the matching ')'
        depth, i = 1, 0
        while i < len(stmt) and depth:
            if stmt[i] == "(":
                depth += 1
            elif stmt[i] == ")":
                depth -= 1
            i += 1
        args_str = stmt[:i - 1]
        trailer = stmt[i:stmt.find(";", i) if stmt.find(";", i) >= 0 else len(stmt)]
        args = [a.strip() for a in split_top_commas(args_str)]
        if len(args) == 3:
            a, b, chance = args
            result = result_default
        elif len(args) == 4:
            a, b, result_tok, chance = args
            result = species_ref(result_tok)
        else:
            warn(f"skip registerMutation with {len(args)} args: {args_str.strip()}")
            continue
        mut = {
            "first_parent": species_ref(a),
            "second_parent": species_ref(b),
            "result": result,
            "chance": round(int(chance) / 100.0, 4),
        }
        bm = re.search(r"restrictBiomeType\(BiomeDictionary\.Type\.(\w+)\)", trailer)
        if bm:
            tag = BIOME_TAGS.get(bm.group(1))
            if tag:
                mut["conditions"] = [{"type": "forestry:biome", "biomes": tag}]
            else:
                warn("unmapped biome type: " + bm.group(1))
        if "addMutationCondition" in trailer:
            warn(f"unhandled addMutationCondition on {result}: {trailer.strip()[:80]}")
        out.append(mut)
    return out


def flatten_genome(sp, branch_templates):
    tmpl = dict(DEFAULT_TEMPLATE)
    never_sleeps = False
    nocturnal = "setNocturnal()" in sp["chunk"]

    def apply(sets):
        nonlocal never_sleeps
        for marker, val in sets:
            if marker == "__never_sleeps__":
                never_sleeps = val
            else:
                tmpl[marker] = val

    if sp["branch_type"] == "ExtraBeeBranchDefinition":
        apply(branch_templates.get(sp["branch"], []))
    else:
        warn(f"{sp['name']}: base branch {sp['branch']} defaults not flattened (base Forestry branch)")
    apply(parse_set_lines(extract_block(sp["chunk"], "setAlleles")))

    activity = "cathemeral" if never_sleeps else ("nocturnal" if nocturnal else "diurnal")
    tmpl["forestry:activity"] = "forestry:activity_" + activity
    eff = tmpl.get("forestry:bee_effect", "")
    if eff.startswith(NS + ":effect_"):
        referenced_effects.add(eff[len(NS + ":effect_"):])
    return tmpl


referenced_effects = set()


def genus_of(sp):
    return sp["branch"].lower()


# ---------------------------------------------------------------------------------------------
def emit_species_and_mutations():
    text = open(os.path.join(GEN, "ExtraBeeDefinition.java")).read()
    species = parse_species(text)
    branch_templates = parse_branches()

    genera = set()
    mut_count = 0
    for sp in species:
        sid = sp["name"].lower()
        result_id = f"{NS}:{sid}"
        props = extract_block(sp["chunk"], "setSpeciesProperties")
        prods, specs = parse_products(props)
        temp = re.search(r"setTemperature\(EnumTemperature\.(\w+)\)", props)
        humid = re.search(r"setHumidity\(EnumHumidity\.(\w+)\)", props)

        entry = {
            "genus": genus_of(sp),
            "species": sp["binomial"],
            "dominant": sp["dominant"],
            "authority": "Binnie",
            # NOTE color mapping (tunable): outline=primary (distinctive), body=secondary.
            "outline": color(sp["primary"]),
            "body": color(sp["secondary"]),
        }
        if temp:
            entry["temperature"] = TEMPERATURE[temp.group(1)]
        if humid:
            entry["humidity"] = HUMIDITY[humid.group(1)]

        if prods:
            entry["products"] = prods
        if specs:
            entry["specialties"] = specs
        entry["genome"] = flatten_genome(sp, branch_templates)

        write_json(os.path.join(DATA, "bee_species", sid + ".json"), entry)
        if sp["branch_type"] == "ExtraBeeBranchDefinition":
            genera.add(sp["branch"])

        # per-species (instance) mutations -> result is this species
        muts = parse_mutations(extract_block(sp["chunk"], "registerMutations"), result_id)
        for mut in muts:
            emit_mutation(mut)
            mut_count += 1

    # standalone doInit() cross-mutations (WATER/ROCK/BASALT/MARBLE + NETHER gates)
    doinit = extract_block(text, "public static void doInit()")
    # only the explicit registerMutation(...) with 4 args live here
    for mut in parse_mutations(doinit, result_default=None):
        if mut["result"] is None:
            warn("doInit mutation missing explicit result, skipped")
            continue
        emit_mutation(mut)
        mut_count += 1

    return species, genera, mut_count


_mut_seen = {}


def emit_mutation(mut):
    # deterministic file name; dedupe identical parent/result triples
    base = f"{mut['result'].split(':')[-1]}__{mut['first_parent'].split(':')[-1]}_x_{mut['second_parent'].split(':')[-1]}"
    n = _mut_seen.get(base, 0)
    _mut_seen[base] = n + 1
    fname = base if n == 0 else f"{base}_{n}"
    write_json(os.path.join(DATA, "bee_mutation", fname + ".json"), mut)


def emit_taxa(genera):
    branches = parse_branch_scientifics()
    for g in sorted(genera):
        write_json(os.path.join(DATA, "taxon", g.lower() + ".json"),
                   {"parent": BASE_FAMILY_TAXON, "name": g.lower()})
    return branches


def parse_branch_scientifics():
    text = open(os.path.join(GEN, "ExtraBeeBranchDefinition.java")).read()
    return {m.group(1): m.group(2) for m in re.finditer(r"\n\t([A-Z][A-Z0-9_]*)\(\"([^\"]+)\"\)", text)}


# ---------------------------------------------------------------------------------------------
# Comb types + centrifuge recipes (parsed from EnumHoneyComb).
def parse_combs():
    text = open(os.path.join(BINNIE, "items", "types", "EnumHoneyComb.java")).read()
    body = text[text.index("public enum EnumHoneyComb"):text.index("private final int primaryColor;")]
    combs = []
    # header: NAME(sec, prim) { body } | NAME { } | NAME,
    for m in re.finditer(r"\n\t([A-Z][A-Z0-9_]*)(?:\((\d+),\s*(\d+)\))?", body):
        name = m.group(1)
        if name in ("BRONZE", "MINT", "CITRUS", "PEAT", "BRASS", "ELECTRUM", "STEEL",
                    "IRIDIUM", "OLIVINE", "INVAR", "PULP", "MULCH"):
            # inactive combs (no color / products) — emit a plain comb_type, no recipe
            combs.append({"name": name, "primary": None, "secondary": None, "chunk": ""})
            continue
        if m.group(2) is None:
            continue
        rest = body[m.end():]
        nxt = re.search(r"\n\t[A-Z][A-Z0-9_]*(?:\(|\s*[,{])", rest)
        chunk = rest[:nxt.start()] if nxt else rest
        combs.append({
            "name": name,
            "secondary": int(m.group(2)),   # ctor is (secondaryColor, primaryColor)
            "primary": int(m.group(3)),
            "chunk": chunk,
        })
    return combs


def comb_fluid_extract(chunk):
    """If this comb centrifuges to a fluid intermediary, return its comb_extract component payload, else None."""
    for m in re.finditer(r"EnumPropolis\.(\w+)", chunk):
        if m.group(1) in FLUID_INTERMEDIARIES:
            return extract_component(m.group(1), *FLUID_INTERMEDIARIES[m.group(1)])
    for m in re.finditer(r"EnumHoneyDrop\.(\w+)", chunk):
        if m.group(1) in DROP_FLUIDS:
            return extract_component(m.group(1), *DROP_FLUIDS[m.group(1)])
    return None


def emit_combs():
    combs = parse_combs()
    for c in combs:
        cid = c["name"].lower()
        if c["primary"] is not None:
            ct = {"primary_color": color_int(c["primary"]), "secondary_color": color_int(c["secondary"])}
        else:
            ct = {"primary_color": "#ffffff", "secondary_color": "#ffffff"}
        extract = comb_fluid_extract(c["chunk"])
        if extract:
            ct["extract"] = extract   # lets the creative tab / JEI enumerate the comb's fluid intermediary
        name = EB_LANG.get("extrabees.item.comb." + cid)
        if name:
            ct["name"] = name   # e.g. "Oily Comb" — matches old Extra Bees
        write_json(os.path.join(DATA, "comb_type", cid + ".json"), ct)
        emit_centrifuge(c)
    return combs


HONEYDROP = "forestry:honey_drop"
BEESWAX = "forestry:beeswax"


# EnumPropolis / EnumHoneyDrop that Binnie squeezed to a fluid -> (fluid_id, amount, flavor). These become
# the one generic forestry:comb_extract item carrying a CombExtract component, which a squeezer turns back
# into the fluid (the "implicit propolis" mechanism — no per-fluid item, registry, or squeezer recipe).
FLUID_INTERMEDIARIES = {   # EnumPropolis.<NAME>
    "OIL": ("modern_industrialization:crude_oil", 500, "propolis"),
    "FUEL": ("modern_industrialization:diesel", 500, "propolis"),
    "WATER": ("minecraft:water", 500, "propolis"),
    "CREOSOTE": ("modern_industrialization:creosote", 500, "propolis"),
}
DROP_FLUIDS = {   # EnumHoneyDrop.<NAME> that map to a real pack fluid (others stay module items)
    "SEED": ("forestry:seed_oil", 200, "honey_drop"),
}


def extract_component(enum_name, fluid, amount, flavor):
    comp = {"fluid": {"id": fluid, "amount": amount}, "flavor": flavor, "source": NS}
    prefix = "extrabees.item.propolis." if flavor == "propolis" else "extrabees.item.honeydrop."
    name = EB_LANG.get(prefix + enum_name.lower())
    if name:
        comp["name"] = name   # e.g. "Oily Propolis" / "Nutdew" — matches old Extra Bees
    return comp


def comb_extract_product(enum_name, fluid, amount, flavor):
    return {"item": "forestry:comb_extract",
            "tag": {"forestry:comb_extract": extract_component(enum_name, fluid, amount, flavor)}}


def module_item(iid):
    """Redirect to a real pack item via OUTPUT_MAP, else record it as a needed extrabees module item."""
    mapped = OUTPUT_MAP.get(iid)
    if mapped:
        return {"item": mapped}
    items_needed.add(iid)
    return {"item": iid}


def resolve_output(expr):
    """An EnumHoneyComb product expression -> a product-item spec dict {"item":..,["tag":..]} or None."""
    expr = expr.strip()
    iid = itemstack_item(expr)
    if iid:
        return {"item": iid}
    if "beeswax" in expr:
        return {"item": BEESWAX}
    if "honeyDrop" in expr:
        return {"item": HONEYDROP}
    m = re.match(r"ExtraBeeItems\.(\w+)", expr)
    if m:
        return module_item(f"{NS}:" + m.group(1).lower())
    m = re.match(r"EnumPropolis\.(\w+)", expr)
    if m:
        name = m.group(1)
        if name in FLUID_INTERMEDIARIES:
            return comb_extract_product(name, *FLUID_INTERMEDIARIES[name])
        return module_item(f"{NS}:propolis_" + name.lower())
    m = re.match(r"EnumHoneyDrop\.(\w+)", expr)
    if m:
        name = m.group(1)
        if name in DROP_FLUIDS:
            return comb_extract_product(name, *DROP_FLUIDS[name])
        return module_item(f"{NS}:drop_" + name.lower())
    m = re.match(r"Utils\.get\w+Item\(\s*\"(\w+)\"", expr)   # IC2 / Botania item lookups
    if m:
        return module_item(f"{NS}:" + m.group(1).lower())
    m = re.match(r"\"(\w+)\"", expr)   # ore-dict string output
    if m:
        return module_item(f"{NS}:" + m.group(1).lower())
    warn("unresolved centrifuge output: " + expr[:60])
    return None


def find_calls_any(block, methods):
    """Yield inner arg strings of `<method>(...)` for any name in `methods` (dot optional), paren-matched."""
    pat = re.compile(r"\b(?:" + "|".join(methods) + r")\(")
    for m in pat.finditer(block):
        i, depth = m.end(), 1
        while i < len(block) and depth:
            if block[i] == "(":
                depth += 1
            elif block[i] == ")":
                depth -= 1
            i += 1
        yield block[m.end():i - 1]


def emit_centrifuge(c):
    if not c["chunk"]:
        return
    outputs = []
    if "copyProducts(EnumHoneyComb.STONE)" in c["chunk"]:   # STONE = beeswax 0.5, honeydrop 0.25
        outputs += [{"item": BEESWAX, "chance": 0.5}, {"item": HONEYDROP, "chance": 0.25}]
    if "addDyeSubtypes(" in c["chunk"]:   # shared dye method: honeydrop + beeswax (colored drop needs module)
        outputs += [{"item": HONEYDROP, "chance": 0.8}, {"item": BEESWAX, "chance": 0.8}]
    for inner in find_calls_any(c["chunk"], ("addProduct", "tryAddProduct")):
        args = split_top_commas(inner)
        if len(args) < 2:
            continue
        spec = resolve_output(args[0].strip())
        if spec:
            spec = dict(spec)
            spec["chance"] = round(float(args[-1].strip().rstrip("fF")), 3)
            outputs.append(spec)
    if not outputs:
        return
    cid = c["name"].lower()
    recipe = {
        "type": "forestry:centrifuge",
        "id": f"{NS}:centrifuge/{cid}",
        "time": 20,
        "input": {"type": "neoforge:components", "items": "forestry:comb",
                  "components": {"forestry:comb_type": f"{NS}:{cid}"}},
        "products": outputs,
    }
    write_json(os.path.join(RECIPE, c["name"].lower() + ".json"), recipe)


# ---------------------------------------------------------------------------------------------
# Flower types (ExtraBeesFlowers) — generic block/tag predicate impl (KNOB 2).
FLOWER_ACCEPTED = {
    "water": ["minecraft:lily_pad"],
    "sugar": ["minecraft:sugar_cane"],
    "rock": "#minecraft:base_stone_overworld",
    "book": ["minecraft:bookshelf", "minecraft:chiseled_bookshelf"],
    "redstone": ["minecraft:redstone_torch", "minecraft:redstone_wall_torch",
                 "minecraft:redstone_block", "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore"],
    "dead": ["minecraft:dead_bush"],
    "wood": "#minecraft:logs",
    "leaves": "#minecraft:leaves",
    "sapling": "#minecraft:saplings",
    # FRUIT (IFruitBearer block-entity) + MYSTICAL (Botania + affectProducts) need bespoke code — deferred.
}


def emit_flowers():
    for fid, accepted in FLOWER_ACCEPTED.items():
        write_json(os.path.join(DATA, "flower_type", "flower_" + fid + ".json"),
                   {"accepted": accepted, "dominant": True})
    for deferred in ("fruit", "mystical"):
        warn(f"flower_type '{deferred}' deferred (needs bespoke code, not a block predicate)")


# ---------------------------------------------------------------------------------------------
# Bee effects (ExtraBeesEffect) expressed over the KNOB-1 primitives. Shapes verified in-client
# (see the staged forestry_testbees bee_effect/*.json worked examples + local-docs/extrabees-effect-map.md).
def _potion(effect, duration, throttle=40, amp=0):
    d = {"type": "forestry:apply_potion", "effect": effect, "duration": duration,
         "throttle": throttle, "chance": 1.0}
    if amp:
        d["amplifier"] = amp
    return d


def _spawn(entity, cap):
    return {"type": "forestry:spawn_mob", "entity": entity, "throttle": 40, "chance": 0.5,
            "cap": cap, "player_range": 16}


EFFECTS = {
    "blindness": _potion("minecraft:blindness", 100),
    "confusion": _potion("minecraft:nausea", 100),
    "wither": _potion("minecraft:wither", 100),
    "slow": _potion("minecraft:weakness", 100),
    "hunger": _potion("minecraft:hunger", 100),
    "food": {"type": "forestry:feed", "nutrition": 2, "saturation": 0.2, "throttle": 40},
    "radioactive": {"type": "forestry:damage_entities", "damage": 4.0, "armor_scaling": True,
                    "throttle": 40, "chance": 1.0},
    "spawn_zombie": _spawn("minecraft:zombie", 4),
    "spawn_skeleton": _spawn("minecraft:skeleton", 4),
    "spawn_creeper": _spawn("minecraft:creeper", 3),
    "fireworks": {"type": "forestry:firework", "colors": [16711680, 65280, 255], "shape": "star",
                  "trail": True, "flicker": True, "throttle": 40, "chance": 0.5},
    "gravity": {"type": "forestry:entity_force", "attract": True, "strength": 0.6, "throttle": 10},
    "thief": {"type": "forestry:entity_force", "attract": False, "strength": 0.6, "throttle": 10},
    "lightning": {"type": "forestry:strike_lightning", "throttle": 30, "chance": 0.34},
    "meteor": {"type": "forestry:spawn_projectile", "entity": "minecraft:small_fireball",
               "height": 20, "speed": 0.8, "throttle": 40, "chance": 0.5},
    "water": {"type": "forestry:fill_fluid", "fluid": "minecraft:water", "amount": 200, "throttle": 40},
    "power": {"type": "forestry:inject_energy", "amount": 20, "throttle": 5},
    "acid": {"type": "forestry:transform_block", "throttle": 15, "chance": 0.5, "transforms": [
        {"from": "minecraft:cobblestone", "to": {"Name": "minecraft:gravel"}},
        {"from": "minecraft:stone", "to": {"Name": "minecraft:gravel"}},
        {"from": "minecraft:dirt", "to": {"Name": "minecraft:sand"}},
        {"from": "minecraft:grass_block", "to": {"Name": "minecraft:sand"}}]},
    # ectoplasm placed a bespoke block in Extra Bees; cobweb is the closest vanilla stand-in (tunable).
    "ectoplasm": {"type": "forestry:place_block", "block": {"Name": "minecraft:cobweb"},
                  "throttle": 20, "chance": 0.4},
    "bonemeal_sapling": {"type": "forestry:bonemeal", "throttle": 10, "chance": 0.5},
    "bonemeal_fruit": {"type": "forestry:bonemeal", "throttle": 10, "chance": 0.5},
    "bonemeal_mushroom": {"type": "forestry:bonemeal", "throttle": 10, "chance": 0.5},
}


def emit_effects(referenced):
    for eid in sorted(referenced):
        if eid in EFFECTS:
            write_json(os.path.join(DATA, "bee_effect", "effect_" + eid + ".json"), EFFECTS[eid])
        else:
            warn(f"referenced effect '{eid}' has no primitive mapping (bee will lose its effect)")


# ---------------------------------------------------------------------------------------------
def emit_pack_meta():
    write_json(os.path.join(OUT, "pack.mcmeta"),
               {"pack": {"pack_format": 48, "description": "Extra Bees (generated datapack)"}})


def main():
    emit_pack_meta()
    species, genera, mut_count = emit_species_and_mutations()
    branches = emit_taxa(genera)
    combs = emit_combs()
    emit_flowers()
    emit_effects(referenced_effects)

    # manifest of items the thin extrabees module must register
    write_json(os.path.join(REPO, "tools", "extrabees-gen", "items-needed.json"),
               {"items": sorted(items_needed)})

    print(f"species:   {len(species)}")
    print(f"mutations: {mut_count}")
    print(f"taxa:      {len(genera)}")
    print(f"combs:     {len(combs)}")
    print(f"items module must register: {len(items_needed)}")
    print(f"warnings:  {len(warnings)}")
    if warnings:
        seen = {}
        for w in warnings:
            key = re.sub(r"[0-9]", "#", w)[:50]
            seen[key] = seen.get(key, 0) + 1
        for k, n in sorted(seen.items(), key=lambda kv: -kv[1])[:25]:
            print(f"  [{n:>3}] {k}")


if __name__ == "__main__":
    main()
