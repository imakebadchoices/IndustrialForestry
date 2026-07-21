package forestry.beegistics.machine;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import appeng.block.AEBaseEntityBlock;

/**
 * The Bee Analyzer block. Behaviour lives entirely in {@link BeeAnalyzerBlockEntity}; the block is a plain
 * grid-connected machine (its block entity is wired up via {@code setBlockEntity} during common setup).
 */
public class BeeAnalyzerBlock extends AEBaseEntityBlock<BeeAnalyzerBlockEntity> {
	public BeeAnalyzerBlock() {
		super(BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.strength(1.5f, 6.0f)
				.sound(SoundType.METAL)
				.requiresCorrectToolForDrops());
	}
}
