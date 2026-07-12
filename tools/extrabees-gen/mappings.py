"""
Confirmed translation tables: Binnie Extra Bees (1.12 Forge) -> ForestryCE 1.21 datapack.

Every value here was verified against the ForestryCE source (see PLAN.md / the migration memory):
  - allele id derivation:
      int   -> forestry:<value>id (dominant) | forestry:<value>i  (recessive)   (IntegerAllele.createId)
      float -> forestry:<value>fd (dominant) | forestry:<value>f  (recessive)   (FloatAllele.createId)
      bool  -> forestry:trued/falsed (dominant) | forestry:true/false (recessive) (BooleanAllele.createId)
      value -> forestry:tolerance_<name>[d] / forestry:<x>_<y>_<z>[d]           (IAlleleNaming)
      registry allele id == the registry value's id (flower_type/effect/activity)
  - chromosome ids: BeeChromosomes.java
  - base species ids: BeeDefinition.<NAME> -> forestry:bee_<name.lower()>  (ForestryBeeSpecies.java)
"""

NS = "extrabees"

# --- EnumBeeChromosome -> new chromosome id -------------------------------------------------
CHROMOSOME = {
    "SPEED": "forestry:speed",
    "LIFESPAN": "forestry:lifespan",
    "FERTILITY": "forestry:fertility",
    "TEMPERATURE_TOLERANCE": "forestry:temperature_tolerance",
    "HUMIDITY_TOLERANCE": "forestry:humidity_tolerance",
    "CAVE_DWELLING": "forestry:cave_dwelling",
    "TOLERATES_RAIN": "forestry:tolerates_rain",
    "FLOWER_PROVIDER": "forestry:flower_type",
    "FLOWERING": "forestry:pollination",
    "EFFECT": "forestry:bee_effect",
    "TERRITORY": "forestry:territory",
    # NEVER_SLEEPS + species .setNocturnal() are folded into the ACTIVITY chromosome (see activity()).
}

# --- EnumAllele.<Type>.<NAME> -> allele id --------------------------------------------------
SPEED = {
    "SLOWEST": "forestry:0.3fd", "SLOWER": "forestry:0.6fd", "SLOW": "forestry:0.8fd",
    "NORMAL": "forestry:1.0f", "FAST": "forestry:1.2fd", "FASTER": "forestry:1.4f",
    "FASTEST": "forestry:1.7f",
}
LIFESPAN = {
    "SHORTEST": "forestry:10id", "SHORTER": "forestry:20id", "SHORT": "forestry:30id",
    "SHORTENED": "forestry:35id", "NORMAL": "forestry:40i", "ELONGATED": "forestry:45id",
    "LONG": "forestry:50i", "LONGER": "forestry:60i", "LONGEST": "forestry:70i",
}
# ForestryCE fertility scale is shifted vs 1.12 (base Forest = FERTILITY_3). Kept on the base
# scale so Extra Bees sit alongside base bees. TUNABLE — verify drone yields in-client.
FERTILITY = {
    "LOW": "forestry:2id", "NORMAL": "forestry:3i", "HIGH": "forestry:4i", "MAXIMUM": "forestry:4i",
}
# POLLINATION (old FLOWERING). POLLINATION_SLOWEST=5(dom), MAXIMUM=99(dom); rest recessive.
FLOWERING = {
    "SLOWEST": "forestry:5id", "SLOWER": "forestry:10i", "SLOW": "forestry:15i",
    "AVERAGE": "forestry:20i", "FAST": "forestry:25i", "FASTER": "forestry:30i",
    "FASTEST": "forestry:35i", "MAXIMUM": "forestry:99id",
}
# Tolerance value alleles; *_1 variants are dominant (get the trailing 'd').
TOLERANCE = {
    "NONE": "forestry:tolerance_none",
    "BOTH_1": "forestry:tolerance_both_1d", "BOTH_2": "forestry:tolerance_both_2",
    "BOTH_3": "forestry:tolerance_both_3", "BOTH_4": "forestry:tolerance_both_4",
    "BOTH_5": "forestry:tolerance_both_5",
    "UP_1": "forestry:tolerance_up_1d", "UP_2": "forestry:tolerance_up_2",
    "UP_3": "forestry:tolerance_up_3", "UP_4": "forestry:tolerance_up_4", "UP_5": "forestry:tolerance_up_5",
    "DOWN_1": "forestry:tolerance_down_1d", "DOWN_2": "forestry:tolerance_down_2",
    "DOWN_3": "forestry:tolerance_down_3", "DOWN_4": "forestry:tolerance_down_4",
    "DOWN_5": "forestry:tolerance_down_5",
}
TERRITORY = {
    "AVERAGE": "forestry:9_6_9", "LARGE": "forestry:11_8_11",
    "LARGER": "forestry:13_12_13", "LARGEST": "forestry:15_13_15",
}
# EnumAllele.Flowers.<NAME> (base Forestry flower types referenced directly by branches).
FLOWERS_BASE = {
    "VANILLA": "forestry:flower_type_vanilla", "NETHER": "forestry:flower_type_nether",
    "JUNGLE": "forestry:flower_type_jungle", "END": "forestry:flower_type_end",
    "CACTI": "forestry:flower_type_cacti", "MUSHROOMS": "forestry:flower_type_mushrooms",
    "SNOW": "forestry:flower_type_snow", "WHEAT": "forestry:flower_type_wheat",
    "GOURD": "forestry:flower_type_gourd",
}
BOOL = {True: "forestry:trued", False: "forestry:falsed"}  # base bees use ForestryAlleles.TRUE (dominant)

# Centrifuge output overrides: map an Extra Bees output that would need a new item to a real item that
# already exists in the pack (mostly Modern Industrialization). Mapped ids are NOT added to items-needed;
# anything not listed here stays an extrabees:* item the thin module must register. `mi:` = MI.
MI = "modern_industrialization"
OUTPUT_MAP = {
    # metal dusts -> MI full dusts
    "extrabees:coal_dust": f"{MI}:coal_dust",
    "extrabees:iron_dust": f"{MI}:iron_dust",
    "extrabees:gold_dust": f"{MI}:gold_dust",
    "extrabees:copper_dust": f"{MI}:copper_dust",
    "extrabees:tin_dust": f"{MI}:tin_dust",
    "extrabees:silver_dust": f"{MI}:silver_dust",
    "extrabees:lead_dust": f"{MI}:lead_dust",
    "extrabees:nickel_dust": f"{MI}:nickel_dust",
    "extrabees:platinum_dust": f"{MI}:platinum_dust",
    "extrabees:titanium_dust": f"{MI}:titanium_dust",
    "extrabees:tungsten_dust": f"{MI}:tungsten_dust",
    # gem "shards" -> MI tiny (fragment) dusts
    "extrabees:emerald_shard": f"{MI}:emerald_tiny_dust",
    "extrabees:ruby_shard": f"{MI}:ruby_tiny_dust",
    "extrabees:diamond_shard": f"{MI}:diamond_tiny_dust",
    # ore-dict dusts
    "extrabees:dustsulfur": f"{MI}:sulfur_dust",
    "extrabees:crusheduranium": f"{MI}:uranium_dust",
    # ore-dict small dusts -> MI tiny dusts
    "extrabees:dustsmallaluminum": f"{MI}:aluminum_tiny_dust",
    "extrabees:dustsmallbauxite": f"{MI}:bauxite_tiny_dust",
    "extrabees:dustsmalliron": f"{MI}:iron_tiny_dust",
    # filler minerals MI lacks -> USEFUL MI materials that have NO Extra Bees bee yet (new surface area,
    # not the already-covered iron/redstone/aluminum). Kept as tiny dusts of valuable materials.
    "extrabees:dustsmallcinnabar": f"{MI}:chromium_tiny_dust",
    "extrabees:dustsmallpyrite": f"{MI}:manganese_tiny_dust",
    "extrabees:dustsmallsodalite": f"{MI}:beryllium_tiny_dust",
    "extrabees:dustsmallsphalerite": f"{MI}:cadmium_tiny_dust",   # sphalerite is literally a cadmium ore
    "extrabees:zinc_dust": "create:zinc_ingot",
    "extrabees:dustsmallzinc": "create:zinc_nugget",
    "extrabees:dustcertusquartz": "ae2:certus_quartz_dust",
    "extrabees:dustenderpearl": "ae2:ender_dust",
    "extrabees:dustsaltpeter": "immersiveengineering:dust_saltpeter",
    "extrabees:dustsawdust": "forestry:wood_pulp",
    "extrabees:sawdust": "forestry:wood_pulp",
    "extrabees:itemrubber": f"{MI}:rubber_sheet",
    "extrabees:itemharz": "createpropulsion:pine_resin",
    "extrabees:itemcofeepowder": "croptopia:coffee_beans",
    "extrabees:sapphire_shard": "projectred_core:sapphire",
    # BigReactors reactor materials (no dust form; use the ingot the dust would smelt to)
    "extrabees:yellorium_dust": "bigreactors:yellorium_ingot",
    "extrabees:cyanite_dust": "bigreactors:cyanite_ingot",
    "extrabees:blutonium_dust": "bigreactors:blutonium_ingot",
    # NOTE still unmapped (thin module, Extra-Bees-flavor with no clean equivalent):
    #   propolis_{oil,fuel,water,creosote}, drop_{milk,apple,seed,alcohol,acid,poison,ice,energy},
    #   dustobsidian
}

# Dye combs: each colored comb centrifuges directly to its matching vanilla dye. Binnie routed this
# through a colored honey-drop whose *remnant* was the dye; we skip that intermediary (and its 16 would-be
# drop items) and emit the dye straight from the comb. The Binnie dyeMetas array maps each comb to its own
# colour, so the mapping is just comb-name -> same-colour dye (with lime/light_blue/light_gray renames).
DYE_COMBS = {
    "RED": "red", "YELLOW": "yellow", "BLUE": "blue", "GREEN": "green", "BLACK": "black", "WHITE": "white",
    "BROWN": "brown", "ORANGE": "orange", "CYAN": "cyan", "PURPLE": "purple", "GRAY": "gray",
    "LIGHTBLUE": "light_blue", "PINK": "pink", "LIMEGREEN": "lime", "MAGENTA": "magenta", "LIGHTGRAY": "light_gray",
}

# --- AlleleEffects.<field> (base Forestry effects referenced directly by branches) -----------
BASE_EFFECTS = {
    "effectNone": "forestry:bee_effect_none",
    "effectMiasmic": "forestry:bee_effect_miasmic",
    "effectBeatific": "forestry:bee_effect_beatific",
}

# --- EnumTemperature / EnumHumidity -> TemperatureType/HumidityType (lowercased) -------------
TEMPERATURE = {"ICY": "icy", "COLD": "cold", "NORMAL": "normal", "WARM": "warm", "HOT": "hot", "HELLISH": "hellish"}
HUMIDITY = {"ARID": "arid", "NORMAL": "normal", "DAMP": "damp"}

# --- Base bee genus (branch scientific names live in taxa; the genus id is the branch name) --
BASE_FAMILY_TAXON = "forestry:apidae"   # ForestryTaxa.FAMILY_BEES parent for datapack genera

# --- Alloy bees (NOT from Binnie) -----------------------------------------------------------
# Binnie stubbed these combs as bare enum constants (no color / products / recipe) because alloys
# have no ore to mine. We resurrect them as mutation-bred alloy bees: each is a mutation of its two
# constituent metal bees (already in the pack), clones the first parent's genome, and produces the
# base "stone" comb + a specialty alloy comb that centrifuges into the alloy (dust where a mod ships
# one, else the ingot). Emitted by generate.py:emit_alloy_bees(), fully self-contained.
COMB_METAL_SECONDARY = "#363534"   # shared metal-comb base color (int 3552564), matches iron/gold/etc.
# id: (comb primary color, display name, centrifuge output, first parent, second parent, species epithet)
ALLOY_BEES = {
    "bronze":   ("#cd7f32", "Bronze Comb",   "forestry:ingot_bronze",                   "copper",   "tin",     "aeneus"),
    "brass":    ("#b5a642", "Brass Comb",     "create:brass_ingot",                     "copper",   "zinc",    "orichalcum"),
    "steel":    ("#8a8f99", "Steel Comb",     f"{MI}:steel_dust",                       "iron",     "coal",    "chalybs"),
    "invar":    ("#b8bcc4", "Invar Comb",     f"{MI}:invar_dust",                       "iron",     "nickel",  "invar"),
    "electrum": ("#f0e18a", "Electrum Comb",  f"{MI}:electrum_dust",                    "gold",     "silver",  "electri"),
    "iridium":  ("#dfe8ee", "Iridium Comb",   f"{MI}:iridium_dust",                     "platinum", "diamond", "iridis"),
}

# The default bee template (ExtraBeeBranchDefinition.getDefaultTemplate), pre-resolved.
DEFAULT_TEMPLATE = {
    "forestry:speed": SPEED["SLOWEST"],
    "forestry:lifespan": LIFESPAN["SHORTER"],
    "forestry:fertility": FERTILITY["NORMAL"],
    "forestry:temperature_tolerance": TOLERANCE["NONE"],
    "forestry:humidity_tolerance": TOLERANCE["NONE"],
    "forestry:tolerates_rain": BOOL[False],
    "forestry:cave_dwelling": BOOL[False],
    "forestry:flower_type": FLOWERS_BASE["VANILLA"],
    "forestry:pollination": FLOWERING["SLOWEST"],
    "forestry:territory": TERRITORY["AVERAGE"],
    "forestry:bee_effect": BASE_EFFECTS["effectNone"],
    # activity resolved separately from NEVER_SLEEPS(false) + nocturnal -> forestry:activity_diurnal
}

# Base *Forestry* branch templates (for the ~11 EB species on BeeBranchDefinition.<X> branches, which the
# generator can't parse from the EB source). Transcribed from this repo's BeeTaxonomy.java genus
# setDefaultChromosome() calls — the authoritative ForestryCE branch defaults. Flattened into those species
# ahead of their own setAlleles, exactly like the EB branch templates.
BASE_BRANCH_TEMPLATES = {
    "AGRARIAN": {   # GENUS_AGRARIAN (rustapis)
        "forestry:speed": SPEED["SLOWER"],
        "forestry:lifespan": LIFESPAN["SHORTER"],
        "forestry:flower_type": FLOWERS_BASE["WHEAT"],
        "forestry:pollination": FLOWERING["FASTER"],
    },
    "BOGGY": {   # GENUS_BOGGY (paludapis)
        "forestry:flower_type": FLOWERS_BASE["MUSHROOMS"],
        "forestry:pollination": FLOWERING["SLOWER"],
        "forestry:temperature_tolerance": TOLERANCE["BOTH_1"],
    },
    "FROZEN": {   # GENUS_FROZEN (coagapis)
        "forestry:temperature_tolerance": TOLERANCE["UP_1"],
        "forestry:humidity_tolerance": TOLERANCE["BOTH_1"],
        "forestry:flower_type": FLOWERS_BASE["SNOW"],
        "forestry:bee_effect": "forestry:bee_effect_glacial",
    },
    "FESTIVE": {   # GENUS_FESTIVE (festapis)
        "forestry:speed": SPEED["SLOWER"],
        "forestry:temperature_tolerance": TOLERANCE["BOTH_2"],
        "forestry:humidity_tolerance": TOLERANCE["BOTH_1"],
        "forestry:lifespan": LIFESPAN["NORMAL"],
    },
    "AUSTERE": {   # GENUS_AUSTERE (modapis)
        "forestry:temperature_tolerance": TOLERANCE["BOTH_1"],
        "forestry:humidity_tolerance": TOLERANCE["DOWN_1"],
        "forestry:flower_type": FLOWERS_BASE["CACTI"],
        "forestry:activity": "forestry:activity_nocturnal",
    },
}
