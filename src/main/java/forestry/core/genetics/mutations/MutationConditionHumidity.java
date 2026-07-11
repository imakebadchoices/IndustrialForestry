package forestry.core.genetics.mutations;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.climate.IClimateProvider;
import forestry.api.core.HumidityType;
import forestry.api.genetics.ClimateHelper;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.IMutationCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public class MutationConditionHumidity implements IMutationCondition {
	public static final MapCodec<MutationConditionHumidity> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		HumidityType.CODEC.fieldOf("min").forGetter(condition -> condition.minHumidity),
		HumidityType.CODEC.fieldOf("max").forGetter(condition -> condition.maxHumidity)
	).apply(instance, MutationConditionHumidity::new));

	private final HumidityType minHumidity;
	private final HumidityType maxHumidity;

	public MutationConditionHumidity(HumidityType minHumidity, HumidityType maxHumidity) {
		this.minHumidity = minHumidity;
		this.maxHumidity = maxHumidity;
	}

	@Override
	public MapCodec<MutationConditionHumidity> codec() {
		return MAP_CODEC;
	}

	@Override
	public float modifyChance(Level level, BlockPos pos, IMutation<?> mutation, IGenome genome0, IGenome genome1, IClimateProvider climate, float currentChance) {
		HumidityType biomeHumidity = climate.humidity();

		if (biomeHumidity.ordinal() < this.minHumidity.ordinal() || biomeHumidity.ordinal() > this.maxHumidity.ordinal()) {
			return 0f;
		}
		return currentChance;
	}

	@Override
	public Component getDescription() {
		Component minHumidityString = ClimateHelper.toDisplay(this.minHumidity);

		if (this.minHumidity != this.maxHumidity) {
			Component maxHumidityString = ClimateHelper.toDisplay(this.maxHumidity);
			return Component.translatable("for.mutation.condition.humidity.range", minHumidityString, maxHumidityString);
		} else {
			return Component.translatable("for.mutation.condition.humidity.single", minHumidityString);
		}
	}
}
