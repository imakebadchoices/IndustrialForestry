package forestry.extrabees.items;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

import forestry.api.core.IItemSubtype;

/**
 * Extra Bees comb types, ported from Binnie's {@code binnie.extrabees.items.types.EnumHoneyComb}.
 * Colors are Binnie-authoritative ({@code primary}, {@code secondary}); the six resurrected alloy
 * combs (bronze/brass/electrum/invar/steel/iridium) and quartz are our additions and use our
 * chosen colors. Products are registered in datagen (see ExtraBeesRecipeProvider), mirroring the
 * way base Forestry combs work.
 */
public enum ExtraBeesComb implements StringRepresentable, IItemSubtype {
	ACIDIC(0x14F73E, 0x348543),
	ALCOHOL(0xDED94E, 0x418521),
	BARREN(0xC2BEA7, 0x736C44),
	BAUXITE(0x9C6500, 0x363534),
	BLACK(0x575757, 0x191919),
	BLAZE(0xFFCC00, 0xFF6A00),
	BLUE(0x0022FF, 0x99B2F2),
	BLUTONIUM(0x1B00E6, 0x27204D),
	BONE(0xDEDEC1, 0xC4C4AF),
	BRASS(0xB5A642, 0x363534),
	BRONZE(0xCD7F32, 0x363534),
	BROWN(0x5C350F, 0x7F664C),
	CERTUS(0x394D63, 0xC6D0FF),
	CINNABAR(0x47320B, 0x363534),
	CLAY(0xB0C0D6, 0x6B563A),
	COAL(0x38311E, 0x9E9478),
	COFFEE(0xB37F4B, 0x54381D),
	COMPOST(0x6B5E3B, 0x423308),
	COPPER(0xD16308, 0x363534),
	CREOSOTE(0xBDAA57, 0x9C810C),
	CYAN(0x00FFE5, 0x4C99B2),
	CYANITE(0x0086ED, 0x27204D),
	DIAMOND(0x7FBDFA, 0x363534),
	ELECTRUM(0xF0E18A, 0x363534),
	EMERALD(0x1CFF03, 0x363534),
	ENDERPEARL(0x032620, 0x349786),
	FRUIT(0xDB4F62, 0x7D2934),
	FUEL(0xFFC400, 0x9C6F40),
	FUNGAL(0x2B9443, 0x6E654B),
	GLACIAL(0xCBF2F2, 0x4E8787),
	GLOWSTONE(0xE0C409, 0xA69C5E),
	GOLD(0xE6CC0B, 0x363534),
	GRAY(0xBABABA, 0x4C4C4C),
	GREEN(0x009900, 0x667F33),
	IC2ENERGY(0x20B3C9, 0xE9F50F),
	INVAR(0xB8BCC4, 0x363534),
	IRIDIUM(0xDFE8EE, 0x363534),
	IRON(0xA87058, 0x363534),
	LAPIS(0x3D2CDB, 0x363534),
	LATEX(0xA8A285, 0x595541),
	LEAD(0x9A809C, 0x363534),
	LIGHTBLUE(0x009DFF, 0x99B2F2),
	LIGHTGRAY(0xC9C9C9, 0x999999),
	LIMEGREEN(0x00FF08, 0x7FCC19),
	MAGENTA(0xFF00CC, 0xE57FD8),
	MILK(0xFFFFFF, 0xD7D9C7),
	NICKEL(0xFFDEFC, 0x363534),
	OIL(0x2C2B36, 0x060608),
	OLD(0xB39664, 0x453314),
	ORANGE(0xFF9D00, 0xF2B233),
	PINK(0xFF80DF, 0xF2B2CC),
	PLATINUM(0x9A809C, 0x363534),
	PURPLE(0xAE00FF, 0xB266E5),
	PYRITE(0xE3A739, 0x363534),
	QUARTZ(0xF0ECE2, 0xD9CFC0),
	RED(0xFF0000, 0xCC4C4C),
	REDSTONE(0xE61010, 0xFA9696),
	RESIN(0xC98A00, 0xFFC74F),
	ROTTEN(0xB1CC89, 0x3E5221),
	RUBY(0xD60000, 0x363534),
	SALTPETER(0xE0C409, 0xA69C5E),
	SAPPHIRE(0x0A47FF, 0x363534),
	SAWDUST(0xF2D37E, 0xBFAA71),
	SEED(0x71CC6E, 0x344F33),
	SHADOW(0x361835, 0x000000),
	SILVER(0xDBDBDB, 0x363534),
	SLIME(0x80D185, 0x3B473C),
	SODALITE(0x154FED, 0x363534),
	SPHALERITE(0xDBD51D, 0x363534),
	STEEL(0x8A8F99, 0x363534),
	STONE(0xC6C6CC, 0x8C8C91),
	TIN(0xBDB1BD, 0x363534),
	TITANIUM(0xB0AAE3, 0x363534),
	TUNGSTEN(0x131214, 0x363534),
	URANIUM(0x41AB33, 0x1EFF00),
	VENOMOUS(0xFF33FF, 0x7D187D),
	WATER(0x79A8C9, 0x2732CF),
	WHITE(0xFFFFFF, 0xD6D6D6),
	YELLORIUM(0xD5ED00, 0x27204D),
	YELLOW(0xFFDD00, 0xE5E533),
	ZINC(0xEDEBFF, 0x363534);

	public static final ExtraBeesComb[] VALUES = values();

	public final String combName;
	public final int primaryColor;
	public final int secondaryColor;

	ExtraBeesComb(int primaryColor, int secondaryColor) {
		this.combName = name().toLowerCase(Locale.ENGLISH);
		this.primaryColor = primaryColor;
		this.secondaryColor = secondaryColor;
	}

	@Override
	public String getSerializedName() {
		return this.combName;
	}
}
