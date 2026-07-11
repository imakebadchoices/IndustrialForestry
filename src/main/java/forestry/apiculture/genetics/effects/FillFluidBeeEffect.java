package forestry.apiculture.genetics.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The {@code forestry:fill_fluid} primitive: fills a fluid handler adjacent to the housing with a configured
 * fluid. Covers the ExtraBees WATER effect from JSON. Guarded by capability presence, so it no-ops unless a
 * tank/pipe sits next to the apiary.
 */
public class FillFluidBeeEffect extends ThrottledBeeEffect {
	public static final MapCodec<FillFluidBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("dominant", true).forGetter(IBeeEffect::isDominant),
		BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(effect -> effect.fluid),
		Codec.INT.optionalFieldOf("amount", 100).forGetter(effect -> effect.amount),
		Codec.INT.optionalFieldOf("throttle", 120).forGetter(ThrottledBeeEffect::getThrottle)
	).apply(instance, FillFluidBeeEffect::new));

	private final Fluid fluid;
	private final int amount;

	public FillFluidBeeEffect(boolean dominant, Fluid fluid, int amount, int throttle) {
		super(dominant, throttle, false, false);
		this.fluid = fluid;
		this.amount = amount;
	}

	@Override
	public MapCodec<FillFluidBeeEffect> codec() {
		return MAP_CODEC;
	}

	@Override
	public IEffectData doEffectThrottled(IGenome genome, IEffectData storedData, IBeeHousing housing) {
		Level level = housing.getWorldObj();
		if (level.isClientSide) {
			return storedData;
		}

		BlockPos hive = housing.getCoordinates();
		for (Direction direction : Direction.values()) {
			BlockPos adjacent = hive.relative(direction);
			IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, adjacent, direction.getOpposite());
			if (handler != null) {
				handler.fill(new FluidStack(this.fluid, this.amount), IFluidHandler.FluidAction.EXECUTE);
				return storedData; // one tank per activation
			}
		}
		return storedData;
	}
}
