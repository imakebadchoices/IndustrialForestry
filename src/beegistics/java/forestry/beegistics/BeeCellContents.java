package forestry.beegistics;

import java.util.ArrayList;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;

import io.netty.buffer.Unpooled;

import appeng.api.stacks.AEItemKey;

import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.core.features.CoreDataComponents;
import forestry.core.utils.SpeciesUtil;

/**
 * The compact, self-limiting on-disk/on-wire form of a {@link ItemBeeCell}'s contents.
 *
 * <p><b>Why this exists.</b> A bee is an ordinary {@code AEItemKey} whose {@code GENOME} (and, for mated queens,
 * {@code MATE_GENOME}) components each serialize to well over a kilobyte. Storing a cell's contents as AE2's own
 * {@code STORAGE_CELL_INV} (a {@code List<GenericStack>}) therefore made a full cell serialize to tens of mebibytes -
 * larger than the network packet limit, disconnecting any client the cell item was sent to (the "book ban"). This class
 * replaces that with a delta encoding: a genome is stored as its species id plus <em>only the chromosomes that differ
 * from that species' default genome</em>, so a pure-species bee costs a handful of bytes and even a heavily mutated
 * mated queen costs a few dozen. Round-trip fidelity is exact - {@link #decodeEntry} reconstructs a byte-identical
 * {@link AEItemKey}, so grid stacking and extraction are unchanged.
 *
 * <p>The realized size of a single stored bee ({@link #entryByteSize}) is charged against the cell's byte budget by
 * {@link BeeCellInventory}, which makes each cell hard-bounded in size regardless of how genetically diverse its
 * contents are.
 *
 * @param entries The stored bees, each a distinct {@link AEItemKey} and its stacked amount.
 */
public record BeeCellContents(List<Entry> entries) {
	public record Entry(AEItemKey key, long amount) {}

	public static final BeeCellContents EMPTY = new BeeCellContents(List.of());

	private static final DataComponentType<IGenome> GENOME = CoreDataComponents.GENOME.get();
	private static final DataComponentType<IGenome> MATE_GENOME = CoreDataComponents.MATE_GENOME.get();

	/** The compact binary form used to sync the cell item to clients (and, via {@link #CODEC}, to persist it). */
	public static final StreamCodec<RegistryFriendlyByteBuf, BeeCellContents> STREAM_CODEC = StreamCodec.of(
		BeeCellContents::encode, BeeCellContents::decode);

	/**
	 * Persistent form: the same compact binary blob as {@link #STREAM_CODEC}, base64-encoded into a string. The stream
	 * codec is registry-access independent (species ids, chromosome values and the known bee components all serialize
	 * without touching the dynamic registries), so encoding it against {@link RegistryAccess#EMPTY} is safe.
	 */
	public static final Codec<BeeCellContents> CODEC = Codec.STRING.xmap(BeeCellContents::fromBase64, BeeCellContents::toBase64);

	// --- Whole-cell (de)serialization ---

	private static void encode(RegistryFriendlyByteBuf buf, BeeCellContents contents) {
		buf.writeVarInt(contents.entries.size());
		for (Entry entry : contents.entries) {
			encodeEntry(buf, entry);
		}
	}

	private static BeeCellContents decode(RegistryFriendlyByteBuf buf) {
		int size = buf.readVarInt();
		List<Entry> entries = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			entries.add(decodeEntry(buf));
		}
		return new BeeCellContents(entries);
	}

	private static BeeCellContents fromBase64(String encoded) {
		byte[] bytes = Base64.getDecoder().decode(encoded);
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), RegistryAccess.EMPTY);
		return decode(buf);
	}

	private static String toBase64(BeeCellContents contents) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		encode(buf, contents);
		byte[] bytes = new byte[buf.readableBytes()];
		buf.readBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}

	// --- Per-entry (de)serialization ---

	private static void encodeEntry(RegistryFriendlyByteBuf buf, Entry entry) {
		ItemStack stack = entry.key.getReadOnlyStack();

		buf.writeVarInt(BuiltInRegistries.ITEM.getId(stack.getItem()));

		encodeGenome(buf, stack.get(GENOME));

		IGenome mate = stack.get(MATE_GENOME);
		buf.writeBoolean(mate != null);
		if (mate != null) {
			encodeGenome(buf, mate);
		}

		// Everything that is not the (large) genome components, preserved verbatim so the reconstructed key is identical.
		encodeResidual(buf, stack.getComponentsPatch());

		buf.writeVarLong(entry.amount);
	}

	private static Entry decodeEntry(RegistryFriendlyByteBuf buf) {
		Item item = BuiltInRegistries.ITEM.byId(buf.readVarInt());
		ItemStack stack = new ItemStack(item);

		IGenome genome = decodeGenome(buf);
		IGenome mate = buf.readBoolean() ? decodeGenome(buf) : null;

		applyResidual(buf, stack);

		stack.set(GENOME, genome);
		if (mate != null) {
			stack.set(MATE_GENOME, mate);
		}

		long amount = buf.readVarLong();
		return new Entry(AEItemKey.of(stack), amount);
	}

	// --- Genome delta encoding (against the active species' default genome) ---

	private static void encodeGenome(RegistryFriendlyByteBuf buf, IGenome genome) {
		IKaryotype karyotype = genome.getKaryotype();
		ResourceLocation speciesId = genome.getActiveValue(karyotype.getSpeciesChromosome());
		buf.writeResourceLocation(speciesId);

		IGenome base = baseGenome(speciesId);
		List<IChromosome<?>> chromosomes = karyotype.getChromosomes();

		// Which chromosomes differ from the species default - only those are written.
		List<Integer> changed = new ArrayList<>();
		for (int i = 0; i < chromosomes.size(); i++) {
			IChromosome<?> chromosome = chromosomes.get(i);
			if (!genome.getAllelePair(chromosome).equals(base.getAllelePair(chromosome))) {
				changed.add(i);
			}
		}

		buf.writeVarInt(changed.size());
		for (int index : changed) {
			buf.writeVarInt(index);
			writePair(buf, chromosomes.get(index), genome);
		}
	}

	private static IGenome decodeGenome(RegistryFriendlyByteBuf buf) {
		ResourceLocation speciesId = buf.readResourceLocation();
		IGenome base = baseGenome(speciesId);
		List<IChromosome<?>> chromosomes = base.getKaryotype().getChromosomes();

		int count = buf.readVarInt();
		Map<IChromosome<?>, AllelePair<?>> overrides = new IdentityHashMap<>(count);
		for (int i = 0; i < count; i++) {
			IChromosome<?> chromosome = chromosomes.get(buf.readVarInt());
			overrides.put(chromosome, readPair(buf, chromosome));
		}
		return base.copyWithPairs(overrides);
	}

	private static IGenome baseGenome(ResourceLocation speciesId) {
		return SpeciesUtil.BEE_TYPE.get().getSpecies(speciesId).getDefaultGenome();
	}

	private static <V> void writePair(RegistryFriendlyByteBuf buf, IChromosome<V> chromosome, IGenome genome) {
		StreamCodec<RegistryFriendlyByteBuf, Allele<V>> alleleCodec = Allele.streamCodec(chromosome.valueStreamCodec());
		AllelePair<V> pair = genome.getAllelePair(chromosome);
		alleleCodec.encode(buf, pair.active());
		alleleCodec.encode(buf, pair.inactive());
	}

	private static <V> AllelePair<V> readPair(RegistryFriendlyByteBuf buf, IChromosome<V> chromosome) {
		StreamCodec<RegistryFriendlyByteBuf, Allele<V>> alleleCodec = Allele.streamCodec(chromosome.valueStreamCodec());
		return new AllelePair<>(alleleCodec.decode(buf), alleleCodec.decode(buf));
	}

	// --- Residual component patch (everything except the two genome components) ---

	private static void encodeResidual(RegistryFriendlyByteBuf buf, DataComponentPatch patch) {
		DataComponentPatch residual = patch.forget(type -> type == GENOME || type == MATE_GENOME);
		var entries = residual.entrySet();
		buf.writeVarInt(entries.size());
		for (Map.Entry<DataComponentType<?>, Optional<?>> entry : entries) {
			DataComponentType<?> type = entry.getKey();
			buf.writeVarInt(BuiltInRegistries.DATA_COMPONENT_TYPE.getId(type));
			Optional<?> value = entry.getValue();
			buf.writeBoolean(value.isPresent());
			value.ifPresent(v -> encodeComponent(buf, type, v));
		}
	}

	private static void applyResidual(RegistryFriendlyByteBuf buf, ItemStack stack) {
		int count = buf.readVarInt();
		for (int i = 0; i < count; i++) {
			DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.byId(buf.readVarInt());
			if (buf.readBoolean()) {
				setComponent(buf, stack, type);
			} else {
				stack.remove(type);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> void encodeComponent(RegistryFriendlyByteBuf buf, DataComponentType<T> type, Object value) {
		type.streamCodec().encode(buf, (T) value);
	}

	private static <T> void setComponent(RegistryFriendlyByteBuf buf, ItemStack stack, DataComponentType<T> type) {
		stack.set(type, type.streamCodec().decode(buf));
	}

	// --- Accounting / display helpers ---

	/**
	 * @return The realized serialized size, in bytes, of a single stored bee. This is what {@link BeeCellInventory}
	 * charges against the cell's byte budget, so a distinct bee's cost equals what it actually contributes to the
	 * synced/persisted item.
	 */
	public static int entryByteSize(AEItemKey key) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			encodeEntry(buf, new Entry(key, 1));
			return buf.readableBytes();
		} finally {
			buf.release();
		}
	}

	/** @return The total bytes used by these contents (sum of every stored bee's {@link #entryByteSize}). */
	public long usedBytes() {
		long total = 0;
		for (Entry entry : this.entries) {
			total += entryByteSize(entry.key);
		}
		return total;
	}

	/** @return The total number of bees stored (summed across stacks). */
	public long totalCount() {
		long total = 0;
		for (Entry entry : this.entries) {
			total += entry.amount;
		}
		return total;
	}

	/** @return The number of distinct bees (unique keys) stored. */
	public int typeCount() {
		return this.entries.size();
	}
}
