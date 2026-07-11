package forestry.apiculture.genetics.effects;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;

/**
 * The {@code forestry:firework} primitive: launches a firework rocket from the housing under open sky.
 * Covers the ExtraBees FIREWORKS / FESTIVAL / BIRTHDAY effects from JSON (the birthday date check is dropped;
 * author the desired shape/colors directly).
 */
public class FireworkBeeEffect extends ThrottledBeeEffect {
	public static final MapCodec<FireworkBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("dominant", true).forGetter(IBeeEffect::isDominant),
		Codec.INT.listOf().optionalFieldOf("colors", List.of(0xff0000, 0xffffff, 0x0000ff)).forGetter(effect -> effect.colors),
		StringRepresentable.fromEnum(FireworkExplosion.Shape::values).optionalFieldOf("shape", FireworkExplosion.Shape.SMALL_BALL).forGetter(effect -> effect.shape),
		Codec.BOOL.optionalFieldOf("trail", false).forGetter(effect -> effect.trail),
		Codec.BOOL.optionalFieldOf("flicker", false).forGetter(effect -> effect.flicker),
		Codec.INT.optionalFieldOf("throttle", 100).forGetter(ThrottledBeeEffect::getThrottle),
		Codec.floatRange(0f, 1f).optionalFieldOf("chance", 0.1f).forGetter(effect -> effect.chance)
	).apply(instance, FireworkBeeEffect::new));

	private final List<Integer> colors;
	private final FireworkExplosion.Shape shape;
	private final boolean trail;
	private final boolean flicker;
	private final float chance;

	public FireworkBeeEffect(boolean dominant, List<Integer> colors, FireworkExplosion.Shape shape, boolean trail, boolean flicker, int throttle, float chance) {
		super(dominant, throttle, false, false);
		this.colors = colors;
		this.shape = shape;
		this.trail = trail;
		this.flicker = flicker;
		this.chance = chance;
	}

	@Override
	public MapCodec<FireworkBeeEffect> codec() {
		return MAP_CODEC;
	}

	@Override
	public IEffectData doEffectThrottled(IGenome genome, IEffectData storedData, IBeeHousing housing) {
		Level level = housing.getWorldObj();
		if (level.isClientSide) {
			return storedData;
		}
		RandomSource rand = level.random;
		if (rand.nextFloat() >= this.chance) {
			return storedData;
		}

		// Use a random territory position (like Extra Bees) — the housing's own block never sees the sky.
		BlockPos pos = ThrottledBeeEffect.getRandomPositionInRange(genome, housing);
		if (!level.canSeeSky(pos)) {
			return storedData;
		}

		IntList colorList = new IntArrayList(this.colors);
		FireworkExplosion explosion = new FireworkExplosion(this.shape, colorList, IntList.of(), this.trail, this.flicker);
		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
		rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));

		FireworkRocketEntity firework = new FireworkRocketEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, rocket);
		level.addFreshEntity(firework);
		return storedData;
	}
}
