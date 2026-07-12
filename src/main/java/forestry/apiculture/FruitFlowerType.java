package forestry.apiculture;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import forestry.api.apiculture.IFlowerType;
import forestry.api.genetics.IFruitBearer;

/**
 * A bespoke {@link IFlowerType} for Extra Bees' FRUIT flower: a queen accepts any block whose block entity
 * bears fruit (Forestry arboriculture leaves / fruit pods, i.e. {@link IFruitBearer}). This can't be a
 * block/tag predicate like {@link DataFlowerType} because it tests a block-entity capability, not a block id,
 * so it needs code. Non-planting — Extra Bees' FRUIT flower never planted anything.
 */
public class FruitFlowerType implements IFlowerType {
	private final boolean dominant;

	public FruitFlowerType(boolean dominant) {
		this.dominant = dominant;
	}

	@Override
	public boolean isAcceptableFlower(Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof IFruitBearer;
	}

	@Override
	public boolean plantRandomFlower(Level level, BlockPos pos, List<BlockState> nearbyFlowers) {
		return false;
	}

	@Override
	public boolean isDominant() {
		return this.dominant;
	}
}
