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
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * The {@code forestry:inject_energy} primitive: pushes Forge Energy into an energy storage adjacent to the
 * housing. Covers the ExtraBees POWER effect from JSON. Guarded by capability presence, so it no-ops unless a
 * machine/battery sits next to the apiary.
 */
public class InjectEnergyBeeEffect extends ThrottledBeeEffect {
	public static final MapCodec<InjectEnergyBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("dominant", true).forGetter(IBeeEffect::isDominant),
		Codec.INT.optionalFieldOf("amount", 10).forGetter(effect -> effect.amount),
		Codec.INT.optionalFieldOf("throttle", 5).forGetter(ThrottledBeeEffect::getThrottle)
	).apply(instance, InjectEnergyBeeEffect::new));

	private final int amount;

	public InjectEnergyBeeEffect(boolean dominant, int amount, int throttle) {
		super(dominant, throttle, false, false);
		this.amount = amount;
	}

	@Override
	public MapCodec<InjectEnergyBeeEffect> codec() {
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
			IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, adjacent, direction.getOpposite());
			if (storage != null && storage.canReceive()) {
				storage.receiveEnergy(this.amount, false);
				return storedData; // one machine per activation
			}
		}
		return storedData;
	}
}
