package forestry.api.storage;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum EnumBackpackType implements StringRepresentable {
	NORMAL, WOVEN, ENDER, CHORUS, NATURALIST;

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ENGLISH);
	}
}
