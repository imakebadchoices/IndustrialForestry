package forestry.beegistics.gametest;

import net.minecraft.core.BlockPos;

import appeng.api.networking.pathing.ChannelMode;
import appeng.me.service.PathingService;
import appeng.server.testworld.PlotBuilder;

/** Shared helpers for beegistics gametests that stand up a real AE2 grid. */
final class BeegisticsGridTests {
	/**
	 * Frees a test plot's grid from AE2's channel limits. The beegistics network blocks (Apiary Controller, Bee Requester,
	 * Bee Analyzer) each require a channel in play, but these tests exercise breeding/autocrafting - not channel routing -
	 * and some place far more channel-consuming blocks than an ad-hoc network's 8-channel ceiling (or the cabling of a
	 * single controller) would carry. Mirrors AE2's own dense test plots: force the grid rooted at {@code anchor} (any
	 * always-present grid-connected block, e.g. the creative energy cell) to {@link ChannelMode#INFINITE} once it forms.
	 */
	static void forceInfiniteChannels(PlotBuilder plot, BlockPos anchor) {
		plot.afterGridExistsAt(anchor, (grid, node) ->
				((PathingService) grid.getPathingService()).setForcedChannelMode(ChannelMode.INFINITE));
	}

	private BeegisticsGridTests() {
	}
}
