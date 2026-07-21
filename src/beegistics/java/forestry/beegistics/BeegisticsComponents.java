package forestry.beegistics;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import forestry.beegistics.crafting.BeeMutation;

/**
 * Registers Beegistics {@link DataComponentType}s under the {@code beegistics} namespace, through a plain NeoForge
 * {@link DeferredRegister} - mirroring {@link BeegisticsItems}.
 */
public class BeegisticsComponents {
	public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Beegistics.NAMESPACE);

	/** The bee predicate carried by an {@link ItemBeeFilterCard}. */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<BeeFilter>> BEE_FILTER = COMPONENTS.register(
		"bee_filter",
		() -> DataComponentType.<BeeFilter>builder()
			.persistent(BeeFilter.CODEC)
			.networkSynchronized(BeeFilter.STREAM_CODEC)
			.build());

	/**
	 * The compact contents of an {@link ItemBeeCell}. Replaces AE2's {@code STORAGE_CELL_INV} on bee cells so the item
	 * stays small enough to sync safely - see {@link BeeCellContents}.
	 */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<BeeCellContents>> BEE_CELL_CONTENTS = COMPONENTS.register(
		"bee_cell_contents",
		() -> DataComponentType.<BeeCellContents>builder()
			.persistent(BeeCellContents.CODEC)
			.networkSynchronized(BeeCellContents.STREAM_CODEC)
			.cacheEncoding()
			.build());

	/** The mutation encoded on an {@link ItemBeeMutationPattern} - see {@link BeeMutation}. */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<BeeMutation>> BEE_MUTATION = COMPONENTS.register(
		"bee_mutation",
		() -> DataComponentType.<BeeMutation>builder()
			.persistent(BeeMutation.CODEC)
			.networkSynchronized(BeeMutation.STREAM_CODEC)
			.build());

	/**
	 * Internal marker distinguishing the <em>drone-primary</em> variant of a mutation pattern from the default
	 * princess-primary one. The controller reports both variants (with/without this component) so AE2 can autocraft an
	 * intermediate species as either gender; it is never present on a player-held pattern item.
	 */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> PATTERN_DRONE_PRIMARY = COMPONENTS.register(
		"pattern_drone_primary",
		() -> DataComponentType.<Unit>builder()
			.persistent(Unit.CODEC)
			.networkSynchronized(StreamCodec.unit(Unit.INSTANCE))
			.build());

	public static void register(IEventBus modBus) {
		COMPONENTS.register(modBus);
	}
}
