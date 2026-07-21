package forestry.beegistics;

import forestry.api.core.IItemSubtype;

/**
 * Capacity tiers for the {@link ItemBeeCell}, sized in <em>bytes</em> like AE2's own storage cells (and crafted from the
 * matching AE2 storage components). Unlike a stock cell there is no 63-<em>type</em> cap: a bee cell is limited only by
 * its byte budget, against which each distinct bee is charged its realized compact size (see {@link BeeCellContents}).
 * The effective budget is capped at {@link ItemBeeCell#MAX_SAFE_BYTES} to keep the cell item under the packet limit.
 */
public enum BeeCellTier implements IItemSubtype {
	T1K(1024, "1k"),
	T4K(4096, "4k"),
	T16K(16384, "16k"),
	T32K(32768, "32k");

	private final int bytes;
	private final String name;

	BeeCellTier(int bytes, String name) {
		this.bytes = bytes;
		this.name = name;
	}

	/**
	 * @return The total byte budget of this tier.
	 */
	public int getBytes() {
		return this.bytes;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
