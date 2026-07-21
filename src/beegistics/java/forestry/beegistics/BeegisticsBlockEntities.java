package forestry.beegistics;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import forestry.beegistics.machine.ApiaryControllerBlockEntity;
import forestry.beegistics.machine.BeeAnalyzerBlockEntity;

/**
 * Registers Beegistics block-entity types under the {@code beegistics} namespace through a plain NeoForge
 * {@link DeferredRegister}, mirroring {@link BeegisticsItems}.
 */
public class BeegisticsBlockEntities {
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Beegistics.NAMESPACE);

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeeAnalyzerBlockEntity>> BEE_ANALYZER = BLOCK_ENTITIES.register(
			"bee_analyzer",
			() -> BlockEntityType.Builder.of(BeeAnalyzerBlockEntity::new, BeegisticsBlocks.BEE_ANALYZER.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ApiaryControllerBlockEntity>> APIARY_CONTROLLER = BLOCK_ENTITIES.register(
			"apiary_controller",
			() -> BlockEntityType.Builder.of(ApiaryControllerBlockEntity::new, BeegisticsBlocks.APIARY_CONTROLLER.get()).build(null));

	public static void register(IEventBus modBus) {
		BLOCK_ENTITIES.register(modBus);
	}
}
