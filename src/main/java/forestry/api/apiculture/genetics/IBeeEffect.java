package forestry.api.apiculture.genetics;

import java.util.List;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import forestry.api.ForestryConstants;
import forestry.api.ForestryRegistries;
import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.IBeekeepingLogic;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.IRegistryAlleleValue;
import forestry.core.render.ParticleRender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

public interface IBeeEffect extends IEffect, IRegistryAlleleValue {
	/**
	 * The datapack registry that defines bee effect alleles from JSON. Each entry at
	 * {@code data/<namespace>/forestry/bee_effect/<name>.json} becomes an effect allele whose ID is the
	 * entry key; a species genome references it through the {@code forestry:effect} chromosome. The
	 * {@linkplain forestry.apiculture.genetics.DatapackBeePlugin rebuild} feeds these into
	 * {@link forestry.api.plugin.IApicultureRegistration#registerBeeEffect} before species are built.
	 */
	ResourceKey<Registry<IBeeEffect>> REGISTRY_KEY = ResourceKey.createRegistryKey(ForestryConstants.forestry("bee_effect"));

	/**
	 * Dispatch codec for datapack effect definitions. The {@code "type"} field is resolved against
	 * {@link ForestryRegistries#BEE_EFFECT_TYPE} to a parameterized primitive (apply_potion, spawn_mob, …).
	 * Effects are always type-keyed, so there is no plain fallback (mirrors {@link forestry.api.genetics.IMutationCondition#CODEC}).
	 */
	Codec<IBeeEffect> CODEC = ForestryRegistries.BEE_EFFECT_TYPE.byNameCodec().dispatch("type", IBeeEffect::codec, Function.identity());

	/**
	 * @return The serializer used to (de)serialize this effect in a datapack effect definition. Only
	 * primitives that are meant to be datapack-configurable override this; code-only base effects never
	 * pass through the datapack registry, so they inherit the throwing default.
	 */
	default MapCodec<? extends IBeeEffect> codec() {
		throw new UnsupportedOperationException(getClass().getName() + " is not a datapack-serializable bee effect (no codec())");
	}

	@Override
	default IEffectData validateStorage(IEffectData storedData) {
		return storedData;
	}

	@Override
	default boolean isCombinable() {
		return false;
	}

	/**
	 * Called by apiaries to cause an effect in the world. (server)
	 *
	 * @param genome     Genome of the bee queen causing this effect
	 * @param storedData Object containing the stored effect data for the apiary/hive the bee is in.
	 * @param housing    {@link IBeeHousing} the bee currently resides in.
	 * @return storedData, may have been manipulated.
	 */
	default IEffectData doEffect(IGenome genome, IEffectData storedData, IBeeHousing housing) {
		return storedData;
	}

	/**
	 * Called on the client side to produce visual bee effects.
	 *
	 * @param genome     Genome of the bee queen causing this effect
	 * @param storedData Object containing the stored effect data for the apiary/hive the bee is in.
	 * @param housing    {@link IBeeHousing} the bee currently resides in.
	 * @return storedData, may have been manipulated.
	 */
	default IEffectData doFX(IGenome genome, IEffectData storedData, IBeeHousing housing) {
		IBeekeepingLogic beekeepingLogic = housing.getBeekeepingLogic();
		List<BlockPos> flowerPositions = beekeepingLogic.getFlowerPositions();

		ParticleRender.addBeeHiveFX(housing, genome, flowerPositions);
		return storedData;
	}
}
