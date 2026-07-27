package forestry.extrabees;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import forestry.core.FluidProductTypes;
import forestry.core.genetics.ProductTypes;
import forestry.core.tab.ForestryCreativeTabs;
import forestry.extrabees.client.ExtraBeesClientHandler;
import forestry.extrabees.features.ExtraBeesBlocks;
import forestry.extrabees.features.ExtraBeesItems;
import forestry.extrabees.genetics.TagFluidOutput;
import forestry.extrabees.genetics.TagProduct;
import forestry.extrabees.genetics.effects.ExtraBeesBeeEffectTypes;
import forestry.extrabees.items.ExtraBeesComb;
import forestry.extrabees.items.ExtraBeesDrop;

/**
 * Mod entry point for the standalone Extra Bees add-on. A hard {@code forestry} dependency (declared in
 * {@code neoforge.mods.toml}) guarantees Forestry's mod constructor - and thus its built-in product /
 * fluid-output types - run first, so the add-on can layer its {@code extrabees:tag} dispatch types onto
 * the shared registries. The content itself is code-authored items under the {@code extrabees} namespace
 * plus a datapack bundled in this jar; it reaches into Forestry's (public) internals the same way the
 * original Binnie Extra Bees always did.
 */
@Mod(ExtraBees.NAMESPACE)
public final class ExtraBees {
	public static final String NAMESPACE = "extrabees";

	public ExtraBees(IEventBus modBus, ModContainer container) {
		registerProductTypes();
		ExtraBeesItems.register(modBus);
		ExtraBeesBlocks.register(modBus);
		ExtraBeesBeeEffectTypes.register(modBus);
		modBus.addListener(ExtraBees::onBuildCreativeTab);

		if (FMLEnvironment.dist == Dist.CLIENT) {
			ExtraBeesClientHandler.init(modBus);
		}
	}

	/** Registers the {@code extrabees:tag} product + fluid-product types on Forestry's shared dispatches. */
	private static void registerProductTypes() {
		ProductTypes.registerBuiltins(); // ensure the base types exist before we add ours
		ProductTypes.register(TagProduct.ID, TagProduct.TYPE);
		FluidProductTypes.registerBuiltins();
		FluidProductTypes.register(TagFluidOutput.ID, TagFluidOutput.TYPE);
	}

	private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == ForestryCreativeTabs.APICULTURE.getKey()) {
			for (ExtraBeesComb comb : ExtraBeesComb.VALUES) {
				event.accept(ExtraBeesItems.comb(comb));
			}
			for (ExtraBeesDrop drop : ExtraBeesDrop.VALUES) {
				event.accept(ExtraBeesItems.drop(drop));
			}
			ExtraBeesBlocks.hiveItems().forEach(event::accept);
		}
	}
}
