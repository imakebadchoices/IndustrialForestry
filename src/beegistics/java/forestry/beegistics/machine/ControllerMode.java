package forestry.beegistics.machine;

/**
 * The two modes an {@link ApiaryControllerBlockEntity} runs in. They differ only in <em>who decides what gets bred</em>;
 * the actual breeding is always done by a real adjacent apiary the controller feeds and drains. (Maintaining a target
 * <em>count</em> in the network is the job of the separate {@link BeeRequesterBlockEntity Bee Requester} block, which
 * submits AE2 crafting jobs against a controller's Autocraft patterns.)
 *
 * <ul>
 *     <li>{@link #STANDALONE}: a dumb perpetual breeder. Each driven apiary is kept stocked with a princess and a drone
 *     matching a loaded card (same species), and every product is harvested back to the network - self-sustaining as
 *     long as replacements exist. No target count, no genome hill-climb, no autocraft.</li>
 *     <li>{@link #AUTOCRAFT}: exposes each loaded card to AE2 as a crafting pattern, so the network's autocrafting
 *     schedules the whole breeding tree on demand (creating the base species and donor as needed).</li>
 * </ul>
 */
public enum ControllerMode {
	STANDALONE,
	AUTOCRAFT;

	/** The mode a freshly placed controller starts in - the self-sustaining perpetual breeder, which needs no ME crafting setup to do something useful. */
	public static final ControllerMode DEFAULT = STANDALONE;

	private static final ControllerMode[] VALUES = values();

	/** @return the next mode in the cycle (wrapping), used by the GUI's single cycle button. */
	public ControllerMode next() {
		return VALUES[(ordinal() + 1) % VALUES.length];
	}

	/** @return the mode with the given {@code name()}, or {@link #DEFAULT} if none matches (forward/backward compatible). */
	public static ControllerMode byName(String name) {
		for (ControllerMode mode : VALUES) {
			if (mode.name().equals(name)) {
				return mode;
			}
		}
		return DEFAULT;
	}

	/** @return the mode with the given ordinal, or {@link #DEFAULT} if out of range (used for GuiSync round-tripping). */
	public static ControllerMode byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : DEFAULT;
	}
}
