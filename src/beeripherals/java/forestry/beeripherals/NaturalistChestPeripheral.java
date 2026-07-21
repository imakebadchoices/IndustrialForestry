package forestry.beeripherals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

import forestry.api.apiculture.genetics.IBee;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.core.IProduct;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.IIndividualLiving;
import forestry.api.genetics.ILifeStage;
import forestry.api.genetics.ISpecies;
import forestry.api.genetics.alleles.BeeChromosomes;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.core.tiles.TileNaturalistChest;

import org.jetbrains.annotations.Nullable;

/**
 * ComputerCraft peripheral exposing the contents of a naturalist chest (apiarist / arborist / lepidopterist)
 * to a computer. Each occupied slot can be read as a Lua table containing the same genetic information a player
 * would see in the Beealyzer / analyzer GUI: active and inactive alleles for every chromosome, plus species-level
 * data such as produce, specialty combs and preferred climate.
 * <p>
 * Genome details are only revealed for specimens that have been {@linkplain IIndividual#isAnalyzed() analyzed},
 * mirroring the in-game requirement that a bee be run through an analyzer before its traits are visible.
 * <p>
 * Registered by {@link Beeripherals}, a standalone add-on with a hard dependency on ComputerCraft, so the
 * {@code dan200.computercraft} classes this file links against are always present.
 */
public class NaturalistChestPeripheral implements IPeripheral {
	private final TileNaturalistChest chest;
	private final String type;

	public NaturalistChestPeripheral(TileNaturalistChest chest) {
		this.chest = chest;
		// e.g. "bee_chest", "tree_chest", "butterfly_chest" - lets programs peripheral.find() a specific chest kind.
		// The species type ids are "bee_species" / "tree_species" / "butterfly_species"; drop the suffix for a tidy name.
		String kind = chest.getSpeciesType().id().getPath().replace("_species", "");
		this.type = kind + "_chest";
	}

	@Override
	public String getType() {
		return this.type;
	}

	/**
	 * @return The number of inventory slots in this chest.
	 */
	@LuaFunction(mainThread = true)
	public final int size() {
		return this.chest.getInternalInventory().getContainerSize();
	}

	/**
	 * Reads a single specimen from the chest.
	 *
	 * @param slot The 1-based slot to read, following the ComputerCraft inventory convention.
	 * @return A table describing the specimen in that slot, or {@code nil} if the slot is empty.
	 */
	@LuaFunction(mainThread = true)
	@Nullable
	public final Map<String, Object> getSpecimen(int slot) throws LuaException {
		Container inventory = this.chest.getInternalInventory();
		int index = slot - 1;
		if (index < 0 || index >= inventory.getContainerSize()) {
			throw new LuaException("Slot out of range (1.." + inventory.getContainerSize() + ")");
		}
		ItemStack stack = inventory.getItem(index);
		if (stack.isEmpty()) {
			return null;
		}
		return describe(slot, stack);
	}

	/**
	 * @return A list of tables describing every occupied slot in the chest.
	 */
	@LuaFunction(mainThread = true)
	public final List<Map<String, Object>> getSpecimens() {
		Container inventory = this.chest.getInternalInventory();
		List<Map<String, Object>> specimens = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				specimens.add(describe(i + 1, stack));
			}
		}
		return specimens;
	}

	/**
	 * Searches the chest for specimens matching every criterion in the given table, casting a deliberately wide net
	 * for automation. Each key names a searchable attribute (e.g. {@code species}, {@code speed}, {@code effect},
	 * {@code caveDwelling}); string criteria match case-insensitively as substrings, numbers match by equality and
	 * booleans match exactly. A criterion referencing genome data only matches analyzed specimens.
	 *
	 * @param criteria A table of attribute-name to desired-value pairs. An empty table matches every specimen.
	 * @return A list of specimen tables (each carrying its {@code slot}) that matched, ready to feed into
	 * {@link #pushItems}.
	 */
	@LuaFunction(mainThread = true)
	public final List<Map<String, Object>> searchSpecimen(Map<?, ?> criteria) {
		Container inventory = this.chest.getInternalInventory();
		List<Map<String, Object>> results = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (matches(searchableAttributes(stack), criteria)) {
				results.add(describe(i + 1, stack));
			}
		}
		return results;
	}

	/**
	 * Pushes items out of this chest into another inventory peripheral, mirroring the standard ComputerCraft
	 * inventory API.
	 *
	 * @param computer The computer invoking this method (supplied automatically).
	 * @param toName   The network name of the target inventory peripheral.
	 * @param fromSlot The 1-based slot in this chest to pull from.
	 * @param limit    The maximum number of items to move. Defaults to a full stack.
	 * @param toSlot   The 1-based slot in the target to move into. Defaults to the first available slot.
	 * @return The number of items actually moved.
	 */
	@LuaFunction(mainThread = true)
	public final int pushItems(IComputerAccess computer, String toName, int fromSlot, Optional<Integer> limit, Optional<Integer> toSlot) throws LuaException {
		IItemHandler to = remoteHandler(computer, toName);
		IItemHandler from = localHandler();

		int actualLimit = limit.orElse(Integer.MAX_VALUE);
		assertSlot(fromSlot, from.getSlots(), "From slot");
		if (toSlot.isPresent()) {
			assertSlot(toSlot.get(), to.getSlots(), "To slot");
		}
		if (actualLimit <= 0) {
			return 0;
		}
		return moveItem(from, fromSlot - 1, to, toSlot.orElse(0) - 1, actualLimit);
	}

	/**
	 * Pulls items into this chest from another inventory peripheral, mirroring the standard ComputerCraft
	 * inventory API.
	 *
	 * @param computer The computer invoking this method (supplied automatically).
	 * @param fromName The network name of the source inventory peripheral.
	 * @param fromSlot The 1-based slot in the source to pull from.
	 * @param limit    The maximum number of items to move. Defaults to a full stack.
	 * @param toSlot   The 1-based slot in this chest to move into. Defaults to the first available slot.
	 * @return The number of items actually moved.
	 */
	@LuaFunction(mainThread = true)
	public final int pullItems(IComputerAccess computer, String fromName, int fromSlot, Optional<Integer> limit, Optional<Integer> toSlot) throws LuaException {
		IItemHandler from = remoteHandler(computer, fromName);
		IItemHandler to = localHandler();

		int actualLimit = limit.orElse(Integer.MAX_VALUE);
		assertSlot(fromSlot, from.getSlots(), "From slot");
		if (toSlot.isPresent()) {
			assertSlot(toSlot.get(), to.getSlots(), "To slot");
		}
		if (actualLimit <= 0) {
			return 0;
		}
		return moveItem(from, fromSlot - 1, to, toSlot.orElse(0) - 1, actualLimit);
	}

	private IItemHandler localHandler() {
		IItemHandler handler = this.chest.getItemHandler(null);
		return handler != null ? handler : new InvWrapper(this.chest.getInternalInventory());
	}

	private static IItemHandler remoteHandler(IComputerAccess computer, String name) throws LuaException {
		IPeripheral location = computer.getAvailablePeripheral(name);
		if (location == null) {
			throw new LuaException("Inventory '" + name + "' does not exist");
		}
		IItemHandler handler = extractHandler(location);
		if (handler == null) {
			throw new LuaException("Inventory '" + name + "' is not an inventory");
		}
		return handler;
	}

	@Nullable
	private static IItemHandler extractHandler(IPeripheral peripheral) {
		Object target = peripheral.getTarget();
		if (target instanceof BlockEntity blockEntity && !blockEntity.isRemoved()) {
			Level level = blockEntity.getLevel();
			if (level != null) {
				IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, blockEntity.getBlockPos(), null);
				if (handler != null) {
					return handler;
				}
				for (Direction side : Direction.values()) {
					handler = level.getCapability(Capabilities.ItemHandler.BLOCK, blockEntity.getBlockPos(), side);
					if (handler != null) {
						return handler;
					}
				}
			}
		}
		if (target instanceof Container container) {
			return new InvWrapper(container);
		}
		return null;
	}

	private static void assertSlot(int slot, int size, String label) throws LuaException {
		if (slot < 1 || slot > size) {
			throw new LuaException(label + " out of range (1.." + size + ")");
		}
	}

	// Move up to limit items from one handler+slot into another, honouring the destination's insertion rules.
	// A negative toSlot means "any available slot". Returns the number of items actually moved.
	private static int moveItem(IItemHandler from, int fromSlot, IItemHandler to, int toSlot, int limit) {
		ItemStack simulated = from.extractItem(fromSlot, limit, true);
		if (simulated.isEmpty()) {
			return 0;
		}
		ItemStack leftover = toSlot < 0 ? ItemHandlerHelper.insertItemStacked(to, simulated, true) : to.insertItem(toSlot, simulated, true);
		int movable = simulated.getCount() - leftover.getCount();
		if (movable <= 0) {
			return 0;
		}
		ItemStack extracted = from.extractItem(fromSlot, movable, false);
		ItemStack remainder = toSlot < 0 ? ItemHandlerHelper.insertItemStacked(to, extracted, false) : to.insertItem(toSlot, extracted, false);
		// Anything the destination refused after the simulation succeeded is returned to the source to avoid voiding.
		if (!remainder.isEmpty()) {
			ItemHandlerHelper.insertItemStacked(from, remainder, false);
		}
		return extracted.getCount() - remainder.getCount();
	}

	private static Map<String, Object> describe(int slot, ItemStack stack) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("slot", slot);
		map.put("count", stack.getCount());
		map.put("displayName", stack.getHoverName().getString());

		ILifeStage stage = IIndividualHandlerItem.getLifeStage(stack);
		if (stage != null) {
			map.put("stage", stage.getSerializedName());
		}

		IIndividual individual = IIndividualHandlerItem.getIndividual(stack);
		if (individual == null) {
			return map;
		}

		map.put("species", speciesInfo(individual.getSpecies()));
		map.put("inactiveSpecies", speciesInfo(individual.getInactiveSpecies()));
		boolean analyzed = individual.isAnalyzed();
		map.put("analyzed", analyzed);

		if (individual instanceof IIndividualLiving living) {
			map.put("health", living.getHealth());
			map.put("maxHealth", living.getMaxHealth());
		}

		// Only expose the full genome once the specimen has been analyzed, matching the analyzer GUI.
		if (analyzed) {
			if (individual instanceof IBee bee) {
				addBeeGenome(map, bee);
			} else {
				map.put("genome", genericGenome(individual.getGenome()));
			}
		}

		return map;
	}

	// A flat map of matchable attributes for searchSpecimen. Kept flat (unlike the nested describe() table) so
	// callers can search by a single attribute name. Genome traits are only populated for analyzed specimens.
	private static Map<String, Object> searchableAttributes(ItemStack stack) {
		Map<String, Object> attrs = new HashMap<>();
		attrs.put("displayName", stack.getHoverName().getString());

		ILifeStage stage = IIndividualHandlerItem.getLifeStage(stack);
		if (stage != null) {
			attrs.put("stage", stage.getSerializedName());
		}

		IIndividual individual = IIndividualHandlerItem.getIndividual(stack);
		if (individual == null) {
			return attrs;
		}

		ISpecies<?> species = individual.getSpecies();
		attrs.put("species", species.getDisplayName().getString());
		attrs.put("speciesId", species.id().toString());
		attrs.put("genus", species.getGenusName());
		boolean analyzed = individual.isAnalyzed();
		attrs.put("analyzed", analyzed);

		if (individual instanceof IIndividualLiving living) {
			attrs.put("health", living.getHealth());
			attrs.put("maxHealth", living.getMaxHealth());
		}

		if (analyzed && individual instanceof IBee bee) {
			IGenome genome = bee.getGenome();
			attrs.put("lifespan", genome.getActiveValue(BeeChromosomes.LIFESPAN));
			attrs.put("speed", genome.getActiveValue(BeeChromosomes.SPEED));
			attrs.put("pollination", genome.getActiveValue(BeeChromosomes.POLLINATION));
			attrs.put("fertility", genome.getActiveValue(BeeChromosomes.FERTILITY));
			attrs.put("flowerType", referenceName(BeeChromosomes.FLOWER_TYPE, genome.getActiveValue(BeeChromosomes.FLOWER_TYPE)));
			attrs.put("flowerTypeId", genome.getActiveValue(BeeChromosomes.FLOWER_TYPE).toString());
			attrs.put("effect", referenceName(BeeChromosomes.EFFECT, genome.getActiveValue(BeeChromosomes.EFFECT)));
			attrs.put("effectId", genome.getActiveValue(BeeChromosomes.EFFECT).toString());
			attrs.put("activity", referenceName(BeeChromosomes.ACTIVITY, genome.getActiveValue(BeeChromosomes.ACTIVITY)));
			attrs.put("activityId", genome.getActiveValue(BeeChromosomes.ACTIVITY).toString());
			attrs.put("temperatureTolerance", genome.getActiveValue(BeeChromosomes.TEMPERATURE_TOLERANCE).name());
			attrs.put("humidityTolerance", genome.getActiveValue(BeeChromosomes.HUMIDITY_TOLERANCE).name());
			attrs.put("caveDwelling", genome.getActiveValue(BeeChromosomes.CAVE_DWELLING));
			attrs.put("toleratesRain", genome.getActiveValue(BeeChromosomes.TOLERATES_RAIN));
			attrs.put("temperature", bee.getSpecies().getTemperature().name());
			attrs.put("humidity", bee.getSpecies().getHumidity().name());
			attrs.put("pristine", bee.isPristine());
			attrs.put("generation", bee.getGeneration());
		}

		return attrs;
	}

	private static boolean matches(Map<String, Object> attrs, Map<?, ?> criteria) {
		for (Map.Entry<?, ?> entry : criteria.entrySet()) {
			Object have = attrs.get(String.valueOf(entry.getKey()));
			if (have == null || !valueMatches(have, entry.getValue())) {
				return false;
			}
		}
		return true;
	}

	private static boolean valueMatches(Object have, Object want) {
		if (want instanceof String s) {
			// Fuzzy: case-insensitive substring, so {species = "forest"} matches "Forest" and "Marbled Forest".
			return have.toString().toLowerCase(Locale.ROOT).contains(s.toLowerCase(Locale.ROOT));
		} else if (want instanceof Boolean b) {
			return have instanceof Boolean hb && hb.equals(b);
		} else if (want instanceof Number wn) {
			// Lua numbers arrive as doubles; compare against the stored int/float within a small epsilon.
			return have instanceof Number hn && Math.abs(wn.doubleValue() - hn.doubleValue()) < 1.0e-6;
		}
		return want.equals(have);
	}

	private static String referenceName(IChromosome<ResourceLocation> chromosome, ResourceLocation id) {
		return Component.translatable(chromosome.translationKey(id)).getString();
	}

	private static Map<String, Object> speciesInfo(ISpecies<?> species) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", species.id().toString());
		map.put("name", species.getDisplayName().getString());
		map.put("binomial", species.getBinomial());
		map.put("genus", species.getGenusName());
		return map;
	}

	// Full Beealyzer readout for a bee: every chromosome as active/inactive plus species-level produce and climate.
	private static void addBeeGenome(Map<String, Object> map, IBee bee) {
		IGenome genome = bee.getGenome();

		Map<String, Object> active = new LinkedHashMap<>();
		Map<String, Object> inactive = new LinkedHashMap<>();

		putTrait(active, inactive, "lifespan", genome, BeeChromosomes.LIFESPAN);
		putTrait(active, inactive, "speed", genome, BeeChromosomes.SPEED);
		putTrait(active, inactive, "pollination", genome, BeeChromosomes.POLLINATION);
		putTrait(active, inactive, "fertility", genome, BeeChromosomes.FERTILITY);
		putReference(active, inactive, "flowerType", genome, BeeChromosomes.FLOWER_TYPE);
		putReference(active, inactive, "effect", genome, BeeChromosomes.EFFECT);
		putReference(active, inactive, "activity", genome, BeeChromosomes.ACTIVITY);
		putTrait(active, inactive, "temperatureTolerance", genome, BeeChromosomes.TEMPERATURE_TOLERANCE);
		putTrait(active, inactive, "humidityTolerance", genome, BeeChromosomes.HUMIDITY_TOLERANCE);
		putTrait(active, inactive, "caveDwelling", genome, BeeChromosomes.CAVE_DWELLING);
		putTrait(active, inactive, "toleratesRain", genome, BeeChromosomes.TOLERATES_RAIN);
		putTerritory(active, inactive, genome);

		map.put("active", active);
		map.put("inactive", inactive);

		map.put("pristine", bee.isPristine());
		map.put("generation", bee.getGeneration());

		IBeeSpecies species = bee.getSpecies();
		map.put("temperature", species.getTemperature().name());
		map.put("humidity", species.getHumidity().name());
		map.put("products", products(species.getProducts()));
		map.put("specialties", products(species.getSpecialties()));
	}

	// Best-effort readout for non-bee individuals (trees, butterflies): dumps every chromosome generically.
	private static Map<String, Object> genericGenome(IGenome genome) {
		Map<String, Object> chromosomes = new LinkedHashMap<>();
		genome.getChromosomes().forEach((chromosome, pair) -> {
			Map<String, Object> trait = new LinkedHashMap<>();
			trait.put("active", luaValue(pair.active().value()));
			trait.put("inactive", luaValue(pair.inactive().value()));
			chromosomes.put(chromosome.id().getPath(), trait);
		});
		return chromosomes;
	}

	private static <V> void putTrait(Map<String, Object> active, Map<String, Object> inactive, String key, IGenome genome, IChromosome<V> chromosome) {
		active.put(key, luaValue(genome.getActiveValue(chromosome)));
		inactive.put(key, luaValue(genome.getInactiveValue(chromosome)));
	}

	// Reference chromosomes (flower type, effect, activity) store an id; expose the id plus a localised name.
	private static void putReference(Map<String, Object> active, Map<String, Object> inactive, String key, IGenome genome, IChromosome<ResourceLocation> chromosome) {
		active.put(key, referenceInfo(chromosome, genome.getActiveValue(chromosome)));
		inactive.put(key, referenceInfo(chromosome, genome.getInactiveValue(chromosome)));
	}

	private static Map<String, Object> referenceInfo(IChromosome<ResourceLocation> chromosome, ResourceLocation id) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", id.toString());
		map.put("name", referenceName(chromosome, id));
		return map;
	}

	private static void putTerritory(Map<String, Object> active, Map<String, Object> inactive, IGenome genome) {
		active.put("territory", vec3i(genome.getActiveValue(BeeChromosomes.TERRITORY)));
		inactive.put("territory", vec3i(genome.getInactiveValue(BeeChromosomes.TERRITORY)));
	}

	private static Map<String, Object> vec3i(Vec3i vec) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("x", vec.getX());
		map.put("y", vec.getY());
		map.put("z", vec.getZ());
		return map;
	}

	private static List<Map<String, Object>> products(List<IProduct> products) {
		List<Map<String, Object>> list = new ArrayList<>(products.size());
		for (IProduct product : products) {
			Map<String, Object> map = new LinkedHashMap<>();
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(product.item());
			map.put("id", id.toString());
			map.put("name", new ItemStack(product.item()).getHoverName().getString());
			map.put("chance", product.chance());
			list.add(map);
		}
		return list;
	}

	// Enums and Vec3i are not valid Lua values, so coerce them; primitives/strings pass straight through.
	private static Object luaValue(Object value) {
		if (value instanceof Vec3i vec) {
			return vec3i(vec);
		} else if (value instanceof Enum<?> e) {
			return e.name();
		} else if (value instanceof ResourceLocation id) {
			return id.toString();
		}
		return value;
	}

	@Override
	public boolean equals(@Nullable IPeripheral other) {
		return other instanceof NaturalistChestPeripheral peripheral && peripheral.chest == this.chest;
	}

	@Nullable
	@Override
	public Object getTarget() {
		return this.chest;
	}
}
