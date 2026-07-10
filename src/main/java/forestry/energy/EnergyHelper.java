package forestry.energy;

import forestry.core.config.ForestryConfig;
import forestry.energy.tiles.EngineBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

public class EnergyHelper {
	public static int scaleForDifficulty(int energyValue) {
		return Math.round(energyValue * (float) ForestryConfig.energyDemandModifier());
	}

	/**
	 * Consumes one work cycle's worth of energy.
	 *
	 * @return true if the energy to do work was consumed
	 */
	public static boolean consumeEnergyToDoWork(ForestryEnergyStorage energyStorage, int ticksPerWorkCycle, int energyPerWorkCycle) {
		if (energyPerWorkCycle == 0) {
			return true;
		}
		int energyPerCycle = (int) Math.ceil(energyPerWorkCycle / (float) ticksPerWorkCycle);
		if (energyStorage.getEnergyStored() < energyPerCycle) {
			return false;
		}

		energyStorage.drainEnergy(energyPerCycle);

		return true;
	}

	public static int sendEnergy(ForestryEnergyStorage energyStorage, Direction orientation, @Nullable BlockEntity tile) {
		return sendEnergy(energyStorage, orientation, tile, Integer.MAX_VALUE, false);
	}

	public static int sendEnergy(ForestryEnergyStorage energyStorage, Direction face, @Nullable BlockEntity tile, int amount, boolean simulate) {
		int extractable = energyStorage.extractEnergy(amount, true);
		if (extractable > 0) {
			Direction side = face.getOpposite();
			final int sent = sendEnergyToTile(tile, side, extractable, simulate);
			energyStorage.extractEnergy(sent, simulate);
			return sent;
		}
		return 0;
	}

	private static int sendEnergyToTile(@Nullable BlockEntity tile, Direction side, int extractable, boolean simulate) {
		if (tile == null) {
			return 0;
		}

		if (tile instanceof EngineBlockEntity receptor) { // engine chaining
			return receptor.getEnergyManager().forceReceiveEnergy(extractable, simulate);
		}

		if (tile.getLevel() == null) {
			return 0;
		}

		IEnergyStorage storage = tile.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, tile.getBlockPos(), side);
		return storage != null ? storage.receiveEnergy(extractable, simulate) : 0;
	}

	public static boolean canSendEnergy(ForestryEnergyStorage energyStorage, Direction orientation, BlockEntity tile) {
		return sendEnergy(energyStorage, orientation, tile, Integer.MAX_VALUE, true) > 0;
	}

	public static boolean isEnergyReceiverOrEngine(Direction side, @Nullable BlockEntity tile) {
		if (tile == null) {
			return false;
		}
		if (tile instanceof EngineBlockEntity) { // engine chaining
			return true;
		}

		if (tile.getLevel() == null) {
			return false;
		}

		IEnergyStorage energyStorage = tile.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, tile.getBlockPos(), side);
		return energyStorage != null && energyStorage.canReceive();
	}

}
