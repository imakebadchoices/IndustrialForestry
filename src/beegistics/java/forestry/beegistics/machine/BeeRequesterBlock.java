package forestry.beegistics.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;

import forestry.beegistics.terminal.BeegisticsMenus;

/**
 * The Bee Requester block. All behaviour lives in {@link BeeRequesterBlockEntity}: it maintains its target by submitting
 * AE2 crafting jobs, so the block itself is a plain grid-connected machine with a menu.
 */
public class BeeRequesterBlock extends AEBaseEntityBlock<BeeRequesterBlockEntity> {
	public BeeRequesterBlock() {
		super(BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.strength(1.5f, 6.0f)
				.sound(SoundType.METAL)
				.requiresCorrectToolForDrops());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BeeRequesterBlockEntity be) {
			MenuOpener.open(BeegisticsMenus.BEE_REQUESTER.get(), player, MenuLocators.forBlockEntity(be));
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
