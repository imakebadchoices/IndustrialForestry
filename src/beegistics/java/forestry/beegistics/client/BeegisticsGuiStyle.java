package forestry.beegistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

/**
 * Shared "hive" GUI palette and primitives for the Beegistics screens. Both the Apiarist's Terminal
 * ({@link ApiaristTerminalScreen}) and the Apiary Controller ({@link ApiaryControllerScreen}) render through these so
 * they read as one set - dark warm-brown panels, honey-brown borders and honey text - matching the block textures
 * (charcoal frame + honey comb face).
 */
final class BeegisticsGuiStyle {
	private BeegisticsGuiStyle() {
	}

	/** Dark, slightly warm, mostly-opaque panel fill. */
	static final int PANEL_BG = 0xE0140D08;
	/** Honey-brown panel outline. */
	static final int PANEL_BORDER = 0xFF5B3A1A;
	/** Lit panel fill, for hovered/active panels. */
	static final int PANEL_HOVER = 0xFF2A1E12;

	// Recessed slot well (vanilla-style bevel, kept dark so honey panels stay coherent).
	static final int SLOT_BG = 0xFF373737;
	static final int SLOT_LIGHT = 0xFFFFFFFF;
	static final int SLOT_DARK = 0xFF8B8B8B;

	/** Bright honey, matching the comb face of the blocks; use for titles. */
	static final int TEXT_TITLE = 0xF6C94A;
	/** Header honey. */
	static final int TEXT_HEADER = 0xE0C080;
	/** Body-label honey. */
	static final int TEXT_LABEL = 0xD0C0A0;
	/** Dim honey, for secondary marks (arrows, hints). */
	static final int TEXT_MUTED = 0xC0A070;

	static final int SLOT_SIZE = 18;

	/** Fills a warm panel and outlines it in honey-brown. */
	static void panel(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, PANEL_BG);
		g.renderOutline(x, y, w, h, PANEL_BORDER);
	}

	/** Draws an 18x18 recessed slot well with its top-left at (x, y). */
	static void slot(GuiGraphics g, int x, int y) {
		g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_DARK);
		g.fill(x + 1, y + 1, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_LIGHT);
		g.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, SLOT_BG);
	}

	/** @return whether the point (x, y) lies inside the rectangle (shared hit-test for the Beegistics screens). */
	static boolean contains(Rect2i rect, double x, double y) {
		return x >= rect.getX() && x < rect.getX() + rect.getWidth() && y >= rect.getY() && y < rect.getY() + rect.getHeight();
	}
}
