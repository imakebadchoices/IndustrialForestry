package forestry.core.items.definitions;

import forestry.api.core.IItemSubtype;

import java.util.Locale;

public enum EnumCraftingMaterial implements IItemSubtype {
	PULSATING_DUST,
	PULSATING_MESH,
	WOOD_PULP,
	SILK_WISP,
	WOVEN_SILK,
	WOVEN_ENDER_SILK,
	WOVEN_CHORUS_SILK,
	ICE_SHARD,
	PHOSPHOR,
	IMPREGNATED_STICK,
	SCENTED_PANELING;

	private final String name;

	EnumCraftingMaterial() {
		this.name = toString().toLowerCase(Locale.ENGLISH);
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
