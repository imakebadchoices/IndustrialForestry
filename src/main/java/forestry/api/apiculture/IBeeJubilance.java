package forestry.api.apiculture;

import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import forestry.api.ForestryRegistries;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.genetics.IGenome;

/**
 * Determines whether a bee species is jubilant in a certain environment.
 */
public interface IBeeJubilance {
	/**
	 * Dispatch codec for jubilance conditions in datapack species definitions. The {@code "type"} field
	 * is resolved against {@link ForestryRegistries#BEE_JUBILANCE_TYPE}.
	 */
	Codec<IBeeJubilance> CODEC = ForestryRegistries.BEE_JUBILANCE_TYPE.byNameCodec().dispatch("type", IBeeJubilance::codec, Function.identity());

	/**
	 * Returns true when conditions are right to make this species Jubilant.
	 * Jubilant bees can produce their Specialty products.
	 */
	boolean isJubilant(IBeeSpecies species, IGenome genome, IBeeHousing housing);

	/**
	 * @return The serializer used to (de)serialize this jubilance in a datapack species definition.
	 * Must be registered in {@link ForestryRegistries#BEE_JUBILANCE_TYPE}.
	 */
	MapCodec<? extends IBeeJubilance> codec();
}
