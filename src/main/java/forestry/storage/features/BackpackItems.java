package forestry.storage.features;

import forestry.api.core.ItemGroups;
import forestry.api.genetics.ForestrySpeciesTypes;
import forestry.api.modules.ForestryModuleIds;
import forestry.api.storage.EnumBackpackType;
import forestry.modules.features.FeatureItem;
import forestry.modules.features.FeatureProvider;
import forestry.modules.features.IFeatureRegistry;
import forestry.modules.features.ModFeatureRegistry;
import forestry.storage.ModuleStorage;

@FeatureProvider
public class BackpackItems {
	private static final IFeatureRegistry REGISTRY = ModFeatureRegistry.get(ForestryModuleIds.STORAGE);

	public static final FeatureItem<?> APIARIST_BACKPACK = REGISTRY.naturalistBackpack(ModuleStorage.APIARIST, ForestrySpeciesTypes.BEE, ItemGroups.tabApiculture, "apiarist_bag");
	public static final FeatureItem<?> ARBORIST_BACKPACK = REGISTRY.naturalistBackpack(ModuleStorage.ARBORIST, ForestrySpeciesTypes.TREE, ItemGroups.tabArboriculture, "arborist_bag");
	public static final FeatureItem<?> LEPIDOPTERIST_BACKPACK = REGISTRY.naturalistBackpack(ModuleStorage.LEPIDOPTERIST, ForestrySpeciesTypes.BUTTERFLY, ItemGroups.tabLepidopterology, "lepidopterist_bag");

	public static final FeatureItem<?> MINER_BACKPACK = REGISTRY.backpack(ModuleStorage.MINER, EnumBackpackType.NORMAL, "miner_bag");
	public static final FeatureItem<?> MINER_BACKPACK_T_2 = REGISTRY.backpack(ModuleStorage.MINER, EnumBackpackType.WOVEN, "miner_bag_woven");
	public static final FeatureItem<?> MINER_BACKPACK_T_3 = REGISTRY.backpack(ModuleStorage.MINER, EnumBackpackType.ENDER, "miner_bag_ender");
	public static final FeatureItem<?> MINER_BACKPACK_T_4 = REGISTRY.backpack(ModuleStorage.MINER, EnumBackpackType.CHORUS, "miner_bag_chorus");
	public static final FeatureItem<?> DIGGER_BACKPACK = REGISTRY.backpack(ModuleStorage.DIGGER, EnumBackpackType.NORMAL, "digger_bag");
	public static final FeatureItem<?> DIGGER_BACKPACK_T_2 = REGISTRY.backpack(ModuleStorage.DIGGER, EnumBackpackType.WOVEN, "digger_bag_woven");
	public static final FeatureItem<?> DIGGER_BACKPACK_T_3 = REGISTRY.backpack(ModuleStorage.DIGGER, EnumBackpackType.ENDER, "digger_bag_ender");
	public static final FeatureItem<?> DIGGER_BACKPACK_T_4 = REGISTRY.backpack(ModuleStorage.DIGGER, EnumBackpackType.CHORUS, "digger_bag_chorus");
	public static final FeatureItem<?> FORESTER_BACKPACK = REGISTRY.backpack(ModuleStorage.FORESTER, EnumBackpackType.NORMAL, "forester_bag");
	public static final FeatureItem<?> FORESTER_BACKPACK_T_2 = REGISTRY.backpack(ModuleStorage.FORESTER, EnumBackpackType.WOVEN, "forester_bag_woven");
	public static final FeatureItem<?> FORESTER_BACKPACK_T_3 = REGISTRY.backpack(ModuleStorage.FORESTER, EnumBackpackType.ENDER, "forester_bag_ender");
	public static final FeatureItem<?> FORESTER_BACKPACK_T_4 = REGISTRY.backpack(ModuleStorage.FORESTER, EnumBackpackType.CHORUS, "forester_bag_chorus");
	public static final FeatureItem<?> HUNTER_BACKPACK = REGISTRY.backpack(ModuleStorage.HUNTER, EnumBackpackType.NORMAL, "hunter_bag");
	public static final FeatureItem<?> HUNTER_BACKPACK_T_2 = REGISTRY.backpack(ModuleStorage.HUNTER, EnumBackpackType.WOVEN, "hunter_bag_woven");
	public static final FeatureItem<?> HUNTER_BACKPACK_T_3 = REGISTRY.backpack(ModuleStorage.HUNTER, EnumBackpackType.ENDER, "hunter_bag_ender");
	public static final FeatureItem<?> HUNTER_BACKPACK_T_4 = REGISTRY.backpack(ModuleStorage.HUNTER, EnumBackpackType.CHORUS, "hunter_bag_chorus");
	public static final FeatureItem<?> ADVENTURER_BACKPACK = REGISTRY.backpack(ModuleStorage.ADVENTURER, EnumBackpackType.NORMAL, "adventurer_bag");
	public static final FeatureItem<?> ADVENTURER_BACKPACK_T_2 = REGISTRY.backpack(ModuleStorage.ADVENTURER, EnumBackpackType.WOVEN, "adventurer_bag_woven");
	public static final FeatureItem<?> ADVENTURER_BACKPACK_T_3 = REGISTRY.backpack(ModuleStorage.ADVENTURER, EnumBackpackType.ENDER, "adventurer_bag_ender");
	public static final FeatureItem<?> ADVENTURER_BACKPACK_T_4 = REGISTRY.backpack(ModuleStorage.ADVENTURER, EnumBackpackType.CHORUS, "adventurer_bag_chorus");
	public static final FeatureItem<?> BUILDER_BACKPACK = REGISTRY.backpack(ModuleStorage.BUILDER, EnumBackpackType.NORMAL, "builder_bag");
	public static final FeatureItem<?> BUILDER_BACKPACK_T_2 = REGISTRY.backpack(ModuleStorage.BUILDER, EnumBackpackType.WOVEN, "builder_bag_woven");
	public static final FeatureItem<?> BUILDER_BACKPACK_T_3 = REGISTRY.backpack(ModuleStorage.BUILDER, EnumBackpackType.ENDER, "builder_bag_ender");
	public static final FeatureItem<?> BUILDER_BACKPACK_T_4 = REGISTRY.backpack(ModuleStorage.BUILDER, EnumBackpackType.CHORUS, "builder_bag_chorus");
}
