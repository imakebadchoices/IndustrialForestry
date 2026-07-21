package forestry.beegistics.terminal;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.storage.ITerminalHost;
import appeng.menu.implementations.MenuTypeBuilder;

import forestry.beegistics.Beegistics;
import forestry.beegistics.machine.ApiaryControllerBlockEntity;
import forestry.beegistics.machine.ApiaryControllerMenu;
import forestry.beegistics.machine.BeeRequesterBlockEntity;
import forestry.beegistics.machine.BeeRequesterMenu;

/**
 * Registers the add-on's menu types. AE2's own menu types register through its internal {@code InitMenuTypes}; since we
 * are a separate mod we go through a plain NeoForge {@link DeferredRegister}, using AE2's {@link MenuTypeBuilder} only to
 * construct the type (via {@link MenuTypeBuilder#buildUnregistered}, which also installs the server-side menu opener)
 * and letting our register hand the resulting instance to the vanilla {@code MENU} registry.
 */
public final class BeegisticsMenus {
	public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Beegistics.NAMESPACE);

	public static final DeferredHolder<MenuType<?>, MenuType<ApiaristTerminalMenu>> APIARIST_TERMINAL = MENUS.register(
			"apiarist_terminal",
			() -> MenuTypeBuilder.<ApiaristTerminalMenu, ITerminalHost>create(ApiaristTerminalMenu::new, ITerminalHost.class)
					.buildUnregistered(ResourceLocation.fromNamespaceAndPath(Beegistics.NAMESPACE, "apiarist_terminal")));

	public static final DeferredHolder<MenuType<?>, MenuType<ApiaryControllerMenu>> APIARY_CONTROLLER = MENUS.register(
			"apiary_controller",
			() -> MenuTypeBuilder.<ApiaryControllerMenu, ApiaryControllerBlockEntity>create(ApiaryControllerMenu::new, ApiaryControllerBlockEntity.class)
					.buildUnregistered(ResourceLocation.fromNamespaceAndPath(Beegistics.NAMESPACE, "apiary_controller")));

	public static final DeferredHolder<MenuType<?>, MenuType<BeeRequesterMenu>> BEE_REQUESTER = MENUS.register(
			"bee_requester",
			() -> MenuTypeBuilder.<BeeRequesterMenu, BeeRequesterBlockEntity>create(BeeRequesterMenu::new, BeeRequesterBlockEntity.class)
					.buildUnregistered(ResourceLocation.fromNamespaceAndPath(Beegistics.NAMESPACE, "bee_requester")));

	private BeegisticsMenus() {
	}

	public static void register(IEventBus modBus) {
		MENUS.register(modBus);
	}
}
