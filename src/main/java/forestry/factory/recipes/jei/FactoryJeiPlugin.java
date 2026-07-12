package forestry.factory.recipes.jei;

import forestry.api.ForestryConstants;
import forestry.api.core.Product;
import forestry.api.fuels.FuelManager;
import forestry.api.fuels.RainSubstrate;
import forestry.api.modules.ForestryModuleIds;
import forestry.api.recipes.ICentrifugeRecipe;
import forestry.api.recipes.ISqueezerRecipe;
import forestry.apiculture.CombExtract;
import forestry.core.features.CoreDataComponents;
import forestry.factory.recipes.SqueezerRecipe;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import forestry.core.ClientsideCode;
import forestry.core.features.FluidsItems;
import forestry.core.gui.GuiForestry;
import forestry.core.gui.widgets.TankWidget;
import forestry.core.recipes.jei.ForestryRecipeType;
import forestry.core.utils.JeiUtil;
import forestry.core.utils.ModUtil;
import forestry.core.utils.RecipeUtils;
import forestry.factory.blocks.BlockFactoryPlain;
import forestry.factory.blocks.BlockTypeFactoryPlain;
import forestry.factory.blocks.BlockTypeFactoryTesr;
import forestry.factory.features.FactoryBlocks;
import forestry.factory.features.FactoryRecipeTypes;
import forestry.factory.gui.*;
import forestry.factory.recipes.jei.carpenter.CarpenterRecipeCategory;
import forestry.factory.recipes.jei.carpenter.CarpenterRecipeTransferHandler;
import forestry.factory.recipes.jei.centrifuge.CentrifugeRecipeCategory;
import forestry.factory.recipes.jei.fabricator.FabricatorRecipeCategory;
import forestry.factory.recipes.jei.fabricator.FabricatorRecipeTransferHandler;
import forestry.factory.recipes.jei.fermenter.FermenterRecipeCategory;
import forestry.factory.recipes.jei.moistener.MoistenerRecipeCategory;
import forestry.factory.recipes.jei.rainmaker.RainmakerRecipeCategory;
import forestry.factory.recipes.jei.squeezer.SqueezerRecipeCategory;
import forestry.factory.recipes.jei.still.StillRecipeCategory;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.fluids.FluidUtil;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@JeiPlugin
public class FactoryJeiPlugin implements IModPlugin {
	@Override
	public ResourceLocation getPluginUid() {
		return ForestryModuleIds.FACTORY;
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registry) {
		IJeiHelpers jeiHelpers = registry.getJeiHelpers();
		IGuiHelper guiHelper = jeiHelpers.getGuiHelper();

		registry.addRecipeCategories(
			new CarpenterRecipeCategory(guiHelper),
			new CentrifugeRecipeCategory(guiHelper),
			new FabricatorRecipeCategory(guiHelper),
			new FermenterRecipeCategory(guiHelper),
			new MoistenerRecipeCategory(guiHelper),
			new RainmakerRecipeCategory(guiHelper),
			new SqueezerRecipeCategory(guiHelper),
			new StillRecipeCategory(guiHelper)
		);
	}

	@Override
	public void registerRecipes(IRecipeRegistration registry) {
		RecipeManager manager = ClientsideCode.getRecipeManager();

		registry.addRecipes(ForestryRecipeType.CARPENTER, RecipeUtils.getRecipes(manager, FactoryRecipeTypes.CARPENTER).toList());
		registry.addRecipes(ForestryRecipeType.CENTRIFUGE, RecipeUtils.getRecipes(manager, FactoryRecipeTypes.CENTRIFUGE).toList());
		registry.addRecipes(ForestryRecipeType.FABRICATOR, RecipeUtils.getRecipes(manager, FactoryRecipeTypes.FABRICATOR).toList());
		registry.addRecipes(ForestryRecipeType.FERMENTER, RecipeUtils.getRecipes(manager, FactoryRecipeTypes.FERMENTER).toList());
		registry.addRecipes(ForestryRecipeType.MOISTENER, RecipeUtils.getRecipes(manager, FactoryRecipeTypes.MOISTENER).toList());
		registry.addRecipes(ForestryRecipeType.RAINMAKER, FuelManager.rainSubstrate.values().stream()
			.sorted(Comparator.comparing(RainSubstrate::duration))
			.toList());
		registry.addRecipes(ForestryRecipeType.SQUEEZER, collectSqueezerRecipes(manager));
		registry.addRecipes(ForestryRecipeType.STILL, RecipeUtils.getRecipes(manager, FactoryRecipeTypes.STILL).toList());

		BlockFactoryPlain rainTank = FactoryBlocks.PLAIN.get(BlockTypeFactoryPlain.RAINTANK).block();
		JeiUtil.addDescription(registry, rainTank);
		JeiUtil.addDescription(registry, FactoryBlocks.TESR.get(BlockTypeFactoryTesr.BOTTLER).block());
	}

	/**
	 * Real squeezer recipes plus synthetic entries for the dynamic comb_extract → fluid squeeze (see
	 * {@code TileSqueezer}). The extracts aren't in the recipe manager, so we derive them from every
	 * centrifuge output that is a {@code comb_extract} — keeping JEI in sync with the data with no registry.
	 */
	private static List<ISqueezerRecipe> collectSqueezerRecipes(RecipeManager manager) {
		List<ISqueezerRecipe> recipes = new java.util.ArrayList<>(RecipeUtils.getRecipes(manager, FactoryRecipeTypes.SQUEEZER).toList());
		java.util.Set<String> seen = new java.util.HashSet<>();
		int i = 0;
		for (ICentrifugeRecipe centrifuge : RecipeUtils.getRecipes(manager, FactoryRecipeTypes.CENTRIFUGE).toList()) {
			for (Product product : centrifuge.getAllProducts()) {
				ItemStack stack = product.createStack();
				CombExtract extract = stack.get(CoreDataComponents.COMB_EXTRACT);
				if (extract != null && !extract.fluid().isEmpty() && seen.add(extract.subtypeKey())) {
					recipes.add(new SqueezerRecipe(
						ForestryConstants.forestry("jei/comb_extract_squeeze_" + (i++)),
						extract.squeezeTime(),
						List.of(DataComponentIngredient.of(true, stack)),
						extract.fluid(), ItemStack.EMPTY, 0f));
				}
			}
		}
		return recipes;
	}

	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registry) {
		registry.addRecipeTransferHandler(new CarpenterRecipeTransferHandler(), ForestryRecipeType.CARPENTER);
		registry.addRecipeTransferHandler(new FabricatorRecipeTransferHandler(), ForestryRecipeType.FABRICATOR);
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registry) {
		registry.addRecipeCatalyst(new ItemStack(FactoryBlocks.TESR.get(BlockTypeFactoryTesr.CARPENTER).block()), ForestryRecipeType.CARPENTER);
		registry.addRecipeCatalyst(new ItemStack(FactoryBlocks.TESR.get(BlockTypeFactoryTesr.CENTRIFUGE).block()), ForestryRecipeType.CENTRIFUGE);
		registry.addRecipeCatalyst(new ItemStack(FactoryBlocks.PLAIN.get(BlockTypeFactoryPlain.FABRICATOR).block()), ForestryRecipeType.FABRICATOR);
		registry.addRecipeCatalyst(new ItemStack(FactoryBlocks.TESR.get(BlockTypeFactoryTesr.FERMENTER).block()), ForestryRecipeType.FERMENTER);
		registry.addRecipeCatalyst(new ItemStack(FactoryBlocks.TESR.get(BlockTypeFactoryTesr.MOISTENER).block()), ForestryRecipeType.MOISTENER);
		registry.addRecipeCatalyst(new ItemStack(FactoryBlocks.TESR.get(BlockTypeFactoryTesr.RAINMAKER).block()), ForestryRecipeType.RAINMAKER);
		registry.addRecipeCatalyst(new ItemStack(FactoryBlocks.TESR.get(BlockTypeFactoryTesr.SQUEEZER).block()), ForestryRecipeType.SQUEEZER);
		registry.addRecipeCatalyst(new ItemStack(FactoryBlocks.TESR.get(BlockTypeFactoryTesr.STILL).block()), ForestryRecipeType.STILL);
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registry) {
		registry.addGenericGuiContainerHandler(GuiForestry.class, new ForestryAdvancedGuiHandler(registry.getJeiHelpers().getIngredientManager()));

		registry.addRecipeClickArea(GuiCarpenter.class, 98, 48, 21, 26, ForestryRecipeType.CARPENTER);

		registry.addRecipeClickArea(GuiCentrifuge.class, 38, 22, 38, 14, ForestryRecipeType.CENTRIFUGE);
		registry.addRecipeClickArea(GuiCentrifuge.class, 38, 54, 38, 14, ForestryRecipeType.CENTRIFUGE);

		registry.addRecipeClickArea(GuiFabricator.class, 121, 53, 18, 18, ForestryRecipeType.FABRICATOR);

		registry.addRecipeClickArea(GuiFermenter.class, 72, 40, 32, 18, ForestryRecipeType.FERMENTER);

		registry.addRecipeClickArea(GuiMoistener.class, 123, 35, 19, 21, ForestryRecipeType.MOISTENER);

		registry.addRecipeClickArea(GuiSqueezer.class, 76, 41, 43, 16, ForestryRecipeType.SQUEEZER);

		registry.addRecipeClickArea(GuiStill.class, 73, 17, 33, 57, ForestryRecipeType.STILL);
	}

	@Override
	public void registerItemSubtypes(ISubtypeRegistration subtypeRegistry) {
		ISubtypeInterpreter<ItemStack> subtypeInterpreter = new ISubtypeInterpreter<>() {
			private Optional<ResourceLocation> currentFluidId(ItemStack itemStack) {
				return FluidUtil.getFluidHandler(itemStack)
					.map(handler -> handler.getFluidInTank(0))
					.map(fluid -> ModUtil.getRegistryName(fluid.getFluid()));
			}

			@Override
			public Object getSubtypeData(ItemStack itemStack, UidContext context) {
				return currentFluidId(itemStack).orElse(null);
			}

			@Override
			public String getLegacyStringSubtypeInfo(ItemStack itemStack, UidContext context) {
				return currentFluidId(itemStack).map(ResourceLocation::toString).orElse("");
			}
		};

		for (Item container : FluidsItems.CONTAINERS.itemArray()) {
			subtypeRegistry.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, container, subtypeInterpreter);
		}
	}


	static class ForestryAdvancedGuiHandler implements IGuiContainerHandler<GuiForestry<?>> {
		private final IIngredientManager manager;

		ForestryAdvancedGuiHandler(IIngredientManager manager) {
			this.manager = manager;
		}

		@Override
		public List<Rect2i> getGuiExtraAreas(GuiForestry<?> guiContainer) {
			return guiContainer.getExtraGuiAreas();
		}

		@Override
		public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(IClickableIngredientFactory factory, GuiForestry<?> guiContainer, double mouseX, double mouseY) {
			TankWidget widget = guiContainer.getTankAtPosition(mouseX, mouseY);

			if (widget == null || widget.getTank() == null) {
				return Optional.empty();
			}

			return this.manager.createTypedIngredient(widget.getTank().getFluid(), false)
				.flatMap(typed -> factory.createBuilder(typed)
					.buildWithArea(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
		}
	}
}
