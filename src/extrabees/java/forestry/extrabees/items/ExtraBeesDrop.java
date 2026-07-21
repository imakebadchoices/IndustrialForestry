package forestry.extrabees.items;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

import forestry.api.core.IItemSubtype;

/**
 * Extra Bees comb "flavor" products: Binnie's {@code EnumPropolis} and {@code EnumHoneyDrop}, ported
 * as code items. These are the intermediate products of the fluid-bearing combs; a squeezer turns
 * the ones with a matching modern fluid into that fluid (see ExtraBeesRecipeProvider). Colors are
 * Binnie-authoritative (its {@code (primaryColor, secondaryColor)} constructor). Binnie's inactive
 * entries and the 16 dye honey-drops are intentionally omitted (dye combs yield vanilla dye directly).
 */
public enum ExtraBeesDrop implements StringRepresentable, IItemSubtype {
	// Propolis family
	WATER(Family.PROPOLIS, 2405321, 12762791),
	OIL(Family.PROPOLIS, 1519411, 12762791),
	FUEL(Family.PROPOLIS, 10718482, 12762791),
	CREOSOTE(Family.PROPOLIS, 8877313, 12428819),
	// Honey-drop family
	ENERGY(Family.HONEY_DROP, 10242418, 14905713),
	ACID(Family.HONEY_DROP, 4961601, 4841020),
	POISON(Family.HONEY_DROP, 13698745, 16712674),
	APPLE(Family.HONEY_DROP, 13062738, 13183530),
	ICE(Family.HONEY_DROP, 11462882, 9895925),
	MILK(Family.HONEY_DROP, 14737632, 16777215),
	SEED(Family.HONEY_DROP, 8176242, 12762791),
	ALCOHOL(Family.HONEY_DROP, 14411853, 10872909);

	public enum Family {
		PROPOLIS("propolis"),
		HONEY_DROP("honey_drop");

		public final String suffix;

		Family(String suffix) {
			this.suffix = suffix;
		}
	}

	public static final ExtraBeesDrop[] VALUES = values();

	public final String dropName;
	public final Family family;
	public final int primaryColor;
	public final int secondaryColor;

	ExtraBeesDrop(Family family, int primaryColor, int secondaryColor) {
		this.dropName = name().toLowerCase(Locale.ENGLISH);
		this.family = family;
		this.primaryColor = primaryColor;
		this.secondaryColor = secondaryColor;
	}

	/** The registered item path, e.g. {@code water_propolis} or {@code acid_honey_drop}. */
	public String itemName() {
		return this.dropName + "_" + this.family.suffix;
	}

	@Override
	public String getSerializedName() {
		return this.dropName;
	}
}
