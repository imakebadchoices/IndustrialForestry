package forestry.extrabees.genetics;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.util.RandomSource;

/**
 * Tie-break for {@code c:} tags that several loaded mods fill. Extra Bees combs squeeze/centrifuge into
 * mod-agnostic tags (e.g. {@code c:crude_oil}, {@code c:diesel}, {@code c:dusts/iron}) so they work with
 * whatever industrial mod is present. When more than one mod contributes a member, tag order is effectively
 * mod/pack load order, so the "wrong" provider (e.g. Immersive Petroleum's oil over Modern Industrialization's)
 * can win the {@link Optional#findFirst()} / random roll.
 *
 * <p>The Modern Bees add-on makes Modern Industrialization the intended industrial integration for this suite, so
 * its items/fluids are preferred here whenever they are a member. Anything outside {@link #PREFERRED} falls back to
 * plain tag order, keeping the tags fully usable with any other mod when MI is absent - this is only a tie-break,
 * not a hard dependency, so an MI-less install still resolves the first available member.
 */
public final class PreferredMember {
	/** Namespaces preferred when a shared {@code c:} tag has several members, highest priority first. */
	private static final List<String> PREFERRED = List.of("modern_industrialization");

	/** The preferred member of the tag, else the first member in tag order. */
	public static <T> Optional<Holder<T>> first(HolderSet<T> set) {
		return preferred(set).or(() -> set.stream().findFirst());
	}

	/** The preferred member of the tag, else a random member in tag order. */
	public static <T> Optional<Holder<T>> random(HolderSet<T> set, RandomSource random) {
		return preferred(set).or(() -> set.getRandomElement(random));
	}

	private static <T> Optional<Holder<T>> preferred(HolderSet<T> set) {
		for (String namespace : PREFERRED) {
			Optional<Holder<T>> match = set.stream()
				.filter(holder -> holder.unwrapKey()
					.map(key -> key.location().getNamespace().equals(namespace))
					.orElse(false))
				.findFirst();
			if (match.isPresent()) {
				return match;
			}
		}
		return Optional.empty();
	}

	private PreferredMember() {
	}
}
