package forestry.api.genetics;

import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import forestry.api.ForestryRegistries;
import forestry.api.climate.IClimateProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public interface IMutationCondition {
	/**
	 * Dispatch codec for mutation conditions in datapack mutation definitions. The {@code "type"} field is
	 * resolved against {@link ForestryRegistries#BEE_MUTATION_CONDITION_TYPE}. Conditions are always
	 * type-keyed, so there is no plain fallback (unlike {@link forestry.api.core.IProduct#CODEC}).
	 */
	Codec<IMutationCondition> CODEC = ForestryRegistries.BEE_MUTATION_CONDITION_TYPE.byNameCodec().dispatch("type", IMutationCondition::codec, Function.identity());


	/**
	 * Used to modify the chance of a mutation based on certain conditions being met.
	 * Most conditions will either return the current chance or {@code 0.0f} if the condition is not met.
	 *
	 * @param level         The world.
	 * @param pos           The position where this mutation is taking place.
	 * @param mutation      The mutation.
	 * @param firstGenome   The genome of one parent in the mutation. Order of genomes does not necessarily match {@code mutation}.
	 * @param secondGenome  The genome of the other parent in the mutation. Order of genomes does not necessarily match {@code mutation}.
	 * @param climate       The climate in which this mutation is taking place.
	 * @param currentChance The current chance. Starts out as the base chance of the mutation, but may be modified by other {@link IMutationCondition}.
	 * @return The new mutation chance. Usually {@code currentChance} if the condition is met, {@code 0.0f} if it is not.
	 */
	float modifyChance(Level level, BlockPos pos, IMutation<?> mutation, IGenome firstGenome, IGenome secondGenome, IClimateProvider climate, float currentChance);

	/**
	 * A localized description of the mutation condition. (i.e. "A temperature of HOT is required.")
	 */
	Component getDescription();

	/**
	 * @return The serializer used to (de)serialize this condition in a datapack mutation definition.
	 * Must be registered in {@link ForestryRegistries#BEE_MUTATION_CONDITION_TYPE}.
	 */
	MapCodec<? extends IMutationCondition> codec();
}
