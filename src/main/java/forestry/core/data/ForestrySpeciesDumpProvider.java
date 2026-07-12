package forestry.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import forestry.api.IForestryApi;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.core.IClimateSensitive;
import forestry.api.core.IProduct;
import forestry.api.core.IProductProducer;
import forestry.api.core.ISpecialtyProducer;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.ISpecies;
import forestry.api.genetics.ISpeciesType;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.IAllele;
import forestry.api.genetics.alleles.IChromosome;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Datagen provider that serializes every registered species (bees, trees, butterflies) and their mutations
 * from the live Forestry registry into a single {@code species-dump.json}. This is the machine-readable feed
 * the {@code wiki/} generator consumes, replacing the old regex parsing of {@code Default*Species.java}.
 * <p>
 * At datagen time {@link forestry.apiimpl.plugin.PluginManager#registerGenetics()} has already run (via
 * {@code ModuleCore.ensureApiInitialized}), so the code-registered breeds (base bees, all trees and
 * butterflies) are all present. Datapack species (e.g. Extra Bees) are <em>not</em> loaded into the live
 * registry during {@code runData} — those are only merged at server datapack-load, and the datagen lookup
 * provider does not expose the {@code forestry:bee_species} datapack registry either. So this provider dumps
 * only the code-registered content; the {@code wiki} generator folds the on-disk datapack JSON
 * ({@code data/<ns>/forestry/bee_species}, resolved against that pack's lang) in downstream. As trees and
 * butterflies migrate to datapacks, they will simply move from this dump to that downstream fold-in with no
 * change to the wiki pipeline.
 * <p>
 * The dump is written to {@code build/wiki-data/species-dump.json}, deliberately outside
 * {@code src/generated/resources} so it is neither bundled into the jar nor committed.
 */
public class ForestrySpeciesDumpProvider implements DataProvider {
	private final PackOutput output;
	private final CompletableFuture<HolderLookup.Provider> lookupProvider;

	public ForestrySpeciesDumpProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
		this.output = output;
		this.lookupProvider = lookupProvider;
	}

	@Override
	public String getName() {
		return "Forestry Species Dump (wiki)";
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cache) {
		return this.lookupProvider.thenCompose(registries -> {
			JsonObject root = new JsonObject();
			root.addProperty("generated", java.time.Instant.now().toString());

			JsonObject speciesTypes = new JsonObject();
			for (ISpeciesType<?, ?> type : IForestryApi.INSTANCE.getGeneticManager().getSpeciesTypes()) {
				speciesTypes.add(type.id().toString(), dumpSpeciesType(type));
			}
			root.add("speciesTypes", speciesTypes);

			// build/wiki-data/species-dump.json — outputFolder is <root>/src/generated/resources
			Path target = this.output.getOutputFolder()
				.resolve("../../../build/wiki-data/species-dump.json")
				.normalize();
			return DataProvider.saveStable(cache, root, target);
		});
	}

	private static JsonObject dumpSpeciesType(ISpeciesType<?, ?> type) {
		JsonObject out = new JsonObject();

		JsonArray species = new JsonArray();
		for (ISpecies<?> s : type.getAllSpecies()) {
			species.add(dumpSpecies(s));
		}
		out.add("species", species);

		JsonArray mutations = new JsonArray();
		for (IMutation<?> mutation : type.getMutations().getAllMutations()) {
			mutations.add(dumpMutation(mutation));
		}
		out.add("mutations", mutations);

		return out;
	}

	private static JsonObject dumpSpecies(ISpecies<?> s) {
		JsonObject o = new JsonObject();
		o.addProperty("id", s.id().toString());
		o.addProperty("translationKey", s.getTranslationKey());
		o.addProperty("name", s.getSpeciesName());
		o.addProperty("binomial", s.getBinomial());
		o.addProperty("genus", s.getGenusName());
		o.addProperty("dominant", s.isDominant());
		o.addProperty("secret", s.isSecret());
		o.addProperty("complexity", s.getComplexity());
		o.addProperty("authority", s.getAuthority());

		if (s instanceof IClimateSensitive climate) {
			o.addProperty("temperature", climate.getTemperature().name());
			o.addProperty("humidity", climate.getHumidity().name());
		}
		if (s instanceof IBeeSpecies bee) {
			JsonObject colors = new JsonObject();
			colors.addProperty("outline", bee.getOutline());
			colors.addProperty("body", bee.getBody());
			colors.addProperty("stripes", bee.getStripes());
			o.add("colors", colors);
		}
		if (s instanceof IProductProducer producer) {
			o.add("products", dumpProducts(producer.getProducts()));
		}
		if (s instanceof ISpecialtyProducer producer) {
			JsonArray specialties = dumpProducts(producer.getSpecialties());
			if (!specialties.isEmpty()) {
				o.add("specialties", specialties);
			}
		}

		o.add("genome", dumpGenome(s.getDefaultGenome()));
		return o;
	}

	private static JsonArray dumpProducts(List<IProduct> products) {
		JsonArray arr = new JsonArray();
		for (IProduct product : products) {
			JsonObject p = new JsonObject();
			Item item = product.item();
			p.addProperty("item", BuiltInRegistries.ITEM.getKey(item).toString());
			p.addProperty("chance", product.chance());
			arr.add(p);
		}
		return arr;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static JsonArray dumpGenome(IGenome genome) {
		JsonArray arr = new JsonArray();
		for (Map.Entry<IChromosome<?>, AllelePair<?>> entry : genome.getChromosomes().entrySet()) {
			IChromosome chromosome = entry.getKey();
			IAllele active = entry.getValue().active();
			JsonObject g = new JsonObject();
			g.addProperty("chromosome", chromosome.id().toString());
			g.addProperty("allele", active.alleleId().toString());
			g.addProperty("key", chromosome.getTranslationKey(active));
			arr.add(g);
		}
		return arr;
	}

	private static JsonObject dumpMutation(IMutation<?> mutation) {
		JsonObject o = new JsonObject();
		o.addProperty("first", mutation.getFirstParent().id().toString());
		o.addProperty("second", mutation.getSecondParent().id().toString());
		o.addProperty("result", mutation.getResult().id().toString());
		o.addProperty("chance", mutation.getChance());
		o.addProperty("secret", mutation.isSecret());
		List<Component> conditions = mutation.getSpecialConditions();
		if (!conditions.isEmpty()) {
			JsonArray arr = new JsonArray();
			for (Component c : conditions) {
				arr.add(c.getString());
			}
			o.add("conditions", arr);
		}
		return o;
	}
}
