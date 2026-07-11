package forestry.apiculture.genetics;

import com.mojang.serialization.MapCodec;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.IBeeJubilance;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.genetics.IGenome;

public enum DefaultBeeJubilance implements IBeeJubilance {
	INSTANCE;

	public static final MapCodec<DefaultBeeJubilance> MAP_CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<DefaultBeeJubilance> codec() {
		return MAP_CODEC;
	}

	@Override
	public boolean isJubilant(IBeeSpecies species, IGenome genome, IBeeHousing housing) {
		return housing.temperature() == species.getTemperature() && housing.humidity() == species.getHumidity();
	}
}
