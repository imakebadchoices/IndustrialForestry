package forestry.extrabees.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import io.netty.buffer.Unpooled;

import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.BeeChromosomes;
import forestry.apiculture.genetics.BeeEffectManager;
import forestry.extrabees.genetics.effects.BonemealBeeEffect;
import forestry.extrabees.genetics.effects.EntityForceBeeEffect;
import forestry.extrabees.genetics.effects.FillFluidBeeEffect;
import forestry.extrabees.genetics.effects.FireworkBeeEffect;
import forestry.extrabees.genetics.effects.InjectEnergyBeeEffect;
import forestry.extrabees.genetics.effects.LightningBeeEffect;
import forestry.extrabees.genetics.effects.PlaceBlockBeeEffect;
import forestry.apiculture.genetics.effects.PotionBeeEffect;
import forestry.extrabees.genetics.effects.ProjectileBeeEffect;
import forestry.extrabees.genetics.effects.SpawnMobBeeEffect;
import forestry.apiculture.genetics.effects.TransformBlockBeeEffect;
import forestry.core.utils.SpeciesUtil;
import forestry.extrabees.ExtraBees;

/**
 * Behavioral oracle for every Extra Bees bee effect. Unlike the base {@link forestry.gametest.BeeEffectSystemTest}
 * (which proves the effect <em>primitives</em> exist and their codec works), this pins down the 17 Extra Bees effect
 * <em>definitions</em> shipped as datapack JSON in {@code data/extrabees/bee_effect/}: each one loads into the live
 * effect map, decodes to the primitive type its {@code "type"} field names, survives a JSON + network codec round trip,
 * and is wired to the species that carry it (no dangling effect refs left by the datapack lift, no orphaned effect JSON).
 * All Extra Bees effects reuse Forestry's parameterized primitives — there are no Extra-Bees-specific effect classes —
 * so a regression here is almost always a bad JSON edit or a renamed primitive, which these tests catch cheaply.
 */
@GameTestHolder(ExtraBees.NAMESPACE)
@PrefixGameTestTemplate(false)
public class ExtraBeesEffectTest {
	/**
	 * The complete set of Extra Bees effects, keyed by effect path ({@code extrabees:<path>}), mapped to the primitive
	 * effect class the JSON's {@code "type"} field must decode to. Kept in lockstep with the JSON in
	 * {@code data/extrabees/bee_effect/}; adding an effect there without adding it here (or vice versa) fails
	 * {@link #allExtraBeesEffectsLoadedAndWired}.
	 */
	private static final Map<String, Class<? extends IBeeEffect>> EXPECTED = new LinkedHashMap<>();

	static {
		EXPECTED.put("effect_acid", TransformBlockBeeEffect.class);
		EXPECTED.put("effect_bonemeal_fruit", BonemealBeeEffect.class);
		EXPECTED.put("effect_bonemeal_mushroom", BonemealBeeEffect.class);
		EXPECTED.put("effect_bonemeal_sapling", BonemealBeeEffect.class);
		EXPECTED.put("effect_ectoplasm", PlaceBlockBeeEffect.class);
		EXPECTED.put("effect_fireworks", FireworkBeeEffect.class);
		EXPECTED.put("effect_gravity", EntityForceBeeEffect.class);
		EXPECTED.put("effect_hunger", PotionBeeEffect.class);
		EXPECTED.put("effect_lightning", LightningBeeEffect.class);
		EXPECTED.put("effect_meteor", ProjectileBeeEffect.class);
		EXPECTED.put("effect_power", InjectEnergyBeeEffect.class);
		EXPECTED.put("effect_slow", PotionBeeEffect.class);
		EXPECTED.put("effect_spawn_creeper", SpawnMobBeeEffect.class);
		EXPECTED.put("effect_spawn_skeleton", SpawnMobBeeEffect.class);
		EXPECTED.put("effect_spawn_zombie", SpawnMobBeeEffect.class);
		EXPECTED.put("effect_water", FillFluidBeeEffect.class);
		EXPECTED.put("effect_wither", PotionBeeEffect.class);
	}

	private static ResourceLocation effectId(String path) {
		return ResourceLocation.fromNamespaceAndPath(ExtraBees.NAMESPACE, path);
	}

	/**
	 * Every Extra Bees effect JSON loads into the live bee effect map and decodes to the primitive type its
	 * {@code "type"} field names. Catches a missing/renamed effect file and a wrong or mistyped {@code "type"} field.
	 */
	@GameTest(template = "empty")
	public static void allEffectsDecodeToExpectedType(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		for (Map.Entry<String, Class<? extends IBeeEffect>> entry : EXPECTED.entrySet()) {
			ResourceLocation id = effectId(entry.getKey());
			IBeeEffect effect = beeType.getBeeEffect(id);
			if (effect == null) {
				helper.fail("Extra Bees effect did not load: " + id);
				return;
			}
			if (!entry.getValue().isInstance(effect)) {
				helper.fail("Effect " + id + " decoded to " + effect.getClass().getSimpleName()
					+ ", expected " + entry.getValue().getSimpleName());
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * Each loaded Extra Bees effect round-trips through {@link IBeeEffect#CODEC} (JSON) and the network stream codec the
	 * {@code BeeEffectSyncPacket} uses, preserving its primitive type. This is what proves the definitions survive being
	 * synced to a client (which never reads the datapack, only the packet).
	 */
	@GameTest(template = "empty")
	public static void allEffectsRoundTripThroughCodec(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
		StreamCodec<RegistryFriendlyByteBuf, IBeeEffect> streamCodec = ByteBufCodecs.fromCodecWithRegistries(IBeeEffect.CODEC);

		for (String path : EXPECTED.keySet()) {
			ResourceLocation id = effectId(path);
			IBeeEffect effect = beeType.getBeeEffect(id);

			JsonElement json = IBeeEffect.CODEC.encodeStart(ops, effect).getOrThrow();
			IBeeEffect fromJson = IBeeEffect.CODEC.parse(ops, json).getOrThrow();
			if (!effect.getClass().isInstance(fromJson)) {
				helper.fail("Effect " + id + " did not survive the JSON codec round trip (got "
					+ fromJson.getClass().getSimpleName() + ")");
				return;
			}

			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			streamCodec.encode(buf, effect);
			IBeeEffect fromBuf = streamCodec.decode(buf);
			if (!effect.getClass().isInstance(fromBuf)) {
				helper.fail("Effect " + id + " did not survive the network stream codec round trip (got "
					+ fromBuf.getClass().getSimpleName() + ")");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * The effect map and the species genomes agree exactly: every Extra Bees effect JSON is carried by at least one
	 * species, every {@code extrabees:} effect a species references actually loaded, and the two sets are the 17
	 * {@link #EXPECTED} effects. Catches both a dangling effect reference (species points at a deleted effect) and an
	 * orphaned effect file (effect JSON no species uses), the two failure modes of the datapack lift.
	 * <p>
	 * The species-genome sweep also resolves the effects we handed back to base ({@code forestry:bee_effect_radioactive}
	 * on the nuclear line, {@code forestry:bee_effect_darkness} on the shadow line), so a typo in either reference fails
	 * here even though neither is an {@code extrabees:} effect any more.
	 */
	@GameTest(template = "empty")
	public static void allExtraBeesEffectsLoadedAndWired(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();

		// The Extra Bees effects actually loaded into the live map must be exactly the ones we expect.
		TreeSet<String> loaded = new TreeSet<>();
		for (ResourceLocation id : BeeEffectManager.INSTANCE.getEffects().keySet()) {
			if (id.getNamespace().equals(ExtraBees.NAMESPACE)) {
				loaded.add(id.getPath());
			}
		}
		if (!loaded.equals(new TreeSet<>(EXPECTED.keySet()))) {
			helper.fail("Loaded Extra Bees effects " + loaded + " do not match the expected set " + new TreeSet<>(EXPECTED.keySet()));
			return;
		}

		// Sweep every Extra Bees species: its genome effect must resolve, and if it names an extrabees effect that
		// effect must be loaded. Collect the referenced set to compare against the loaded set below.
		TreeSet<String> referenced = new TreeSet<>();
		for (IBeeSpecies species : beeType.getAllSpecies()) {
			if (!species.id().getNamespace().equals(ExtraBees.NAMESPACE)) {
				continue;
			}
			IGenome genome = species.getDefaultGenome();
			ResourceLocation effectId = genome.getActiveValue(BeeChromosomes.EFFECT);
			if (genome.resolveActive(BeeChromosomes.EFFECT) == null) {
				helper.fail("Species " + species.id() + " has an unresolvable effect " + effectId);
				return;
			}
			if (effectId.getNamespace().equals(ExtraBees.NAMESPACE)) {
				if (beeType.getBeeEffect(effectId) == null) {
					helper.fail("Species " + species.id() + " references a missing Extra Bees effect " + effectId);
					return;
				}
				referenced.add(effectId.getPath());
			}
		}

		// Every shipped effect is used by at least one species (no orphaned effect JSON).
		if (!referenced.equals(loaded)) {
			TreeSet<String> orphaned = new TreeSet<>(loaded);
			orphaned.removeAll(referenced);
			helper.fail("Extra Bees effects not referenced by any species: " + orphaned);
			return;
		}
		helper.succeed();
	}
}
