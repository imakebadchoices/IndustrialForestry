package forestry.beegistics.machine;

/**
 * The state of a {@link BeeRequesterBlockEntity}'s single maintain loop, mirroring the AE2 requester lifecycle
 * (IDLE → SCHEDULED → CRAFTING) plus two non-fatal blocked states the loop retries out of. Surfaced to the GUI by
 * ordinal through {@link BeeRequesterMenu}.
 */
public enum RequesterStatus {
	/** No card loaded, or the network already holds the maintain threshold - nothing to do. */
	IDLE,
	/** A crafting calculation is in flight (the plan is being computed). */
	SCHEDULED,
	/** A crafting job is running; the requester holds the {@code ICraftingLink} until it finishes. */
	CRAFTING,
	/** The last plan reported missing base ingredients (no breeding path is stocked/craftable); retried slowly. */
	MISSING,
	/** No crafting CPU was free to accept the job; retried slowly. */
	NO_CPU;

	private static final RequesterStatus[] VALUES = values();

	/** @return the status with the given ordinal, or {@link #IDLE} if out of range (for GuiSync round-tripping). */
	public static RequesterStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
	}
}
