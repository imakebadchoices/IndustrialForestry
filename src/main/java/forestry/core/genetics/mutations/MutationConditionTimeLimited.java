package forestry.core.genetics.mutations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.climate.IClimateProvider;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.IMutationCondition;
import forestry.core.utils.DayMonth;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public class MutationConditionTimeLimited implements IMutationCondition {
	public static final MapCodec<MutationConditionTimeLimited> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.INT.fieldOf("start_month").forGetter(condition -> condition.start.month()),
		Codec.INT.fieldOf("start_day").forGetter(condition -> condition.start.day()),
		Codec.INT.fieldOf("end_month").forGetter(condition -> condition.end.month()),
		Codec.INT.fieldOf("end_day").forGetter(condition -> condition.end.day())
	).apply(instance, MutationConditionTimeLimited::new));

	private final DayMonth start;
	private final DayMonth end;

	public MutationConditionTimeLimited(int startMonth, int startDay, int endMonth, int endDay) {
		this.start = new DayMonth(startDay, startMonth);
		this.end = new DayMonth(endDay, endMonth);
	}

	@Override
	public MapCodec<MutationConditionTimeLimited> codec() {
		return MAP_CODEC;
	}

	@Override
	public float modifyChance(Level level, BlockPos pos, IMutation<?> mutation, IGenome genome0, IGenome genome1, IClimateProvider climate, float currentChance) {
		DayMonth now = DayMonth.now();

		if (now.between(this.start, this.end)) {
			return currentChance;
		}

		return 0;
	}

	@Override
	public Component getDescription() {
		return Component.translatable("for.mutation.condition.date", this.start.getDisplayName(), this.end.getDisplayName());
	}
}
