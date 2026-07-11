package forestry.apiimpl.client;

import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.client.apiculture.IBeeClientManager;
import forestry.api.genetics.ILifeStage;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;

public class BeeClientManager implements IBeeClientManager {
	private final IdentityHashMap<ILifeStage, Map<IBeeSpecies, ResourceLocation>> beeModels;
	private final Map<ILifeStage, ResourceLocation> defaultModels;

	public BeeClientManager(IdentityHashMap<ILifeStage, Map<IBeeSpecies, ResourceLocation>> beeModels, Map<ILifeStage, ResourceLocation> defaultModels) {
		this.beeModels = beeModels;
		this.defaultModels = defaultModels;
	}

	@Override
	public Map<IBeeSpecies, ResourceLocation> getBeeModels(ILifeStage stage) {
		return this.beeModels.get(stage);
	}

	@Nullable
	@Override
	public ResourceLocation getDefaultBeeModel(ILifeStage stage) {
		return this.defaultModels.get(stage);
	}
}
