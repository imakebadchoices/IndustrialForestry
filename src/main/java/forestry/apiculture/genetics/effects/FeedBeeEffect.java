package forestry.apiculture.genetics.effects;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/**
 * The {@code forestry:feed} primitive: pokes the food stats of players in range — restoring nutrition and
 * saturation and/or adding exhaustion. Covers the ExtraBees FOOD effect (nutrition/saturation) and the
 * hunger-drain half of the HUNGER effect (exhaustion) from JSON.
 */
public class FeedBeeEffect extends ThrottledBeeEffect {
	public static final MapCodec<FeedBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("dominant", true).forGetter(IBeeEffect::isDominant),
		Codec.INT.optionalFieldOf("nutrition", 0).forGetter(effect -> effect.nutrition),
		Codec.FLOAT.optionalFieldOf("saturation", 0f).forGetter(effect -> effect.saturation),
		Codec.floatRange(0f, Float.MAX_VALUE).optionalFieldOf("exhaustion", 0f).forGetter(effect -> effect.exhaustion),
		Codec.INT.optionalFieldOf("throttle", 100).forGetter(ThrottledBeeEffect::getThrottle)
	).apply(instance, FeedBeeEffect::new));

	private final int nutrition;
	private final float saturation;
	private final float exhaustion;

	public FeedBeeEffect(boolean dominant, int nutrition, float saturation, float exhaustion, int throttle) {
		super(dominant, throttle, false, true);
		this.nutrition = nutrition;
		this.saturation = saturation;
		this.exhaustion = exhaustion;
	}

	@Override
	public MapCodec<FeedBeeEffect> codec() {
		return MAP_CODEC;
	}

	@Override
	public IEffectData doEffectThrottled(IGenome genome, IEffectData storedData, IBeeHousing housing) {
		if (housing.getWorldObj().isClientSide) {
			return storedData;
		}
		List<Player> players = ThrottledBeeEffect.getEntitiesInRange(genome, housing, Player.class);
		for (Player player : players) {
			FoodData food = player.getFoodData();
			if (this.nutrition != 0 || this.saturation != 0f) {
				food.eat(this.nutrition, this.saturation);
			}
			if (this.exhaustion > 0f) {
				food.addExhaustion(this.exhaustion);
			}
		}
		return storedData;
	}
}
