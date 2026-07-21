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
 * The Apiary Controller block. All behaviour lives in {@link ApiaryControllerBlockEntity}: the breeding loop halts
 * itself by ceasing to pull parents from the network once the target mutation reaches the configured count, so the
 * block needs no redstone output of its own.
 */
public class ApiaryControllerBlock extends AEBaseEntityBlock<ApiaryControllerBlockEntity> {
	public ApiaryControllerBlock() {
		super(BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.strength(1.5f, 6.0f)
				.sound(SoundType.METAL)
				.requiresCorrectToolForDrops());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ApiaryControllerBlockEntity be) {
			MenuOpener.open(BeegisticsMenus.APIARY_CONTROLLER.get(), player, MenuLocators.forBlockEntity(be));
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
