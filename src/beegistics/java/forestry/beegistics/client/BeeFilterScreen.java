package forestry.beegistics.client;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;

import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.beegistics.BeeFilter;
import forestry.beegistics.ItemBeeFilterCard;
import forestry.beegistics.network.SetBeeFilterPayload;
import forestry.core.utils.SpeciesUtil;

/**
 * A self-contained configuration screen for the {@link ItemBeeFilterCard}, opened by right-clicking the card. It edits a
 * local {@link BeeFilter} and pushes the result to the server on close via {@link SetBeeFilterPayload} - no container or
 * slot syncing, so it stays decoupled from both Forestry's GUI framework and AE2.
 *
 * <p>Two tabs:
 * <ul>
 *     <li><b>Filter</b> - stage/species/genus/analyzed/pristine constraints. Species and genus are captured from a bee
 *     held in the <em>off hand</em> (breed the bee you want to match - even a mutation whose exact genome you do not know
 *     - hold it, and click).</li>
 *     <li><b>Traits</b> - per-chromosome allele targets picked <em>directly</em> (no sample bee needed). Each trait can
 *     require its expressed value (<em>active</em>) or be bred true (<em>pure</em>/homozygous); the apiary controller
 *     uses the same target as its breeding objective, so this is how a player asks for a hand-bred strain. The offered
 *     values are those carried by some bee species (i.e. actually reachable by breeding).</li>
 * </ul>
 *
 * <p>This is client-only code; {@link ItemBeeFilterCard} references it exclusively behind a {@code level.isClientSide}
 * guard so it never loads on a dedicated server.
 */
public class BeeFilterScreen extends Screen implements JeiBeeDropScreen {
	private static final int PANEL_W = 210;
	private static final int PANEL_H = 232;
	private static final int PAD = 12;                   // panel edge -> widget
	private static final int GAP = 6;                    // between side-by-side widgets
	private static final int BTN_H = 18;
	private static final int CONTENT_W = PANEL_W - 2 * PAD;
	private static final int HALF_W = (CONTENT_W - GAP) / 2;
	private static final int ROW = BTN_H + 4;            // one button row (button + gap)
	private static final int TRAIT_ROWS_PER_PAGE = 5;
	private static final int FILTER_ROWS = 4;            // slot+match toggle, stage, analyzed, pristine

	private final InteractionHand hand;

	// Top-left of the AE2-style panel, computed in init() so widgets and the panel share one origin.
	private int panelLeft;
	private int panelTop;

	/** Which tab is shown: 0 = Filter, 1 = Traits. */
	private int tab = 0;

	// Editable model, held across rebuildWidgets() so the panel survives resizes, refreshes, and tab switches.
	private final EnumSet<BeeLifeStage> stages = EnumSet.noneOf(BeeLifeStage.class);
	private Optional<ResourceLocation> inactiveSpecies = Optional.empty();   // preserved; not editable here
	private Optional<Boolean> analyzed = Optional.empty();
	private Optional<Boolean> pristine = Optional.empty();

	/** The bee shown in the top-left slot; its species or genus (per {@link #matchGenus}) becomes the constraint. Empty = no constraint. */
	@Nullable
	private ItemStack sampleBee;
	/** false = constrain by the sampled bee's exact species; true = by its whole genus. Either-or, never both. */
	private boolean matchGenus = false;

	/** How a chosen trait must be present in a matching bee. */
	private enum Mode {ACTIVE, PURE}

	// Trait model. The chromosome list and per-chromosome value palette are derived once from the loaded species.
	private final List<IChromosome<?>> traitChromosomes = new ArrayList<>();
	private final Map<IChromosome<?>, List<Object>> candidates = new IdentityHashMap<>();
	private final Map<IChromosome<?>, Object> chosenValue = new IdentityHashMap<>();   // absent = "Any"
	private final Map<IChromosome<?>, Mode> chosenMode = new IdentityHashMap<>();
	private int traitPage = 0;

	private BeeFilterScreen(InteractionHand hand, BeeFilter filter) {
		super(Component.translatable("gui.beegistics.bee_filter.title"));
		this.hand = hand;
		// The stage filter is a single choice (Any / Princess / Drone / Queen); a card carrying multiple stages collapses to Any.
		if (filter.stages().size() == 1) {
			this.stages.add(filter.stages().iterator().next());
		}
		this.inactiveSpecies = filter.inactiveSpecies();
		this.analyzed = filter.analyzed();
		this.pristine = filter.pristine();
		loadSample(filter);
		buildTraitPalette();
		loadTraitSelection(filter);
	}

	/** Rebuilds the slot's sample bee from an already-configured filter's species or genus constraint (for the icon + toggle). */
	private void loadSample(BeeFilter filter) {
		if (filter.species().isPresent()) {
			IBeeSpecies species = SpeciesUtil.getBeeSpecies(filter.species().get());
			if (species != null) {
				this.sampleBee = species.createStack(BeeLifeStage.DRONE);
				this.matchGenus = false;
			}
		} else if (filter.genus().isPresent()) {
			String genus = filter.genus().get();
			for (IBeeSpecies species : SpeciesUtil.getAllBeeSpecies()) {
				if (species.getGenus().name().equalsIgnoreCase(genus)) {
					this.sampleBee = species.createStack(BeeLifeStage.DRONE);
					this.matchGenus = true;
					break;
				}
			}
		}
	}

	/** Entry point called from {@link ItemBeeFilterCard#use} on the client. */
	public static void open(InteractionHand hand, ItemStack card) {
		Minecraft.getInstance().setScreen(new BeeFilterScreen(hand, ItemBeeFilterCard.getFilter(card)));
	}

	// --- trait palette ------------------------------------------------------------------------------------------------

	/**
	 * Collects, for every non-species chromosome, the distinct allele values that appear as some bee species' default -
	 * i.e. the values actually reachable by breeding (a donor species carries them). Numeric/comparable values are sorted
	 * ascending for a predictable pick order.
	 */
	private void buildTraitPalette() {
		IKaryotype karyotype = SpeciesUtil.BEE_TYPE.get().getKaryotype();
		IChromosome<?> speciesChromosome = karyotype.getSpeciesChromosome();
		Map<IChromosome<?>, LinkedHashSet<Object>> gathered = new IdentityHashMap<>();
		for (IChromosome<?> chromosome : karyotype.getChromosomes()) {
			if (chromosome != speciesChromosome) {
				this.traitChromosomes.add(chromosome);
				gathered.put(chromosome, new LinkedHashSet<>());
			}
		}
		for (var beeSpecies : SpeciesUtil.getAllBeeSpecies()) {
			IGenome genome = beeSpecies.getDefaultGenome();
			for (IChromosome<?> chromosome : this.traitChromosomes) {
				gathered.get(chromosome).add(genome.getActiveValue(cast(chromosome)));
			}
		}
		for (IChromosome<?> chromosome : this.traitChromosomes) {
			List<Object> values = new ArrayList<>(gathered.get(chromosome));
			values.sort(BeeFilterScreen::compareValues);
			this.candidates.put(chromosome, values);
		}
	}

	@SuppressWarnings("unchecked")
	private static int compareValues(Object a, Object b) {
		if (a instanceof Comparable<?> && a.getClass() == b.getClass()) {
			return ((Comparable<Object>) a).compareTo(b);
		}
		return a.toString().compareTo(b.toString());
	}

	/** Restores the per-trait value/mode selection from an already-configured filter. */
	private void loadTraitSelection(BeeFilter filter) {
		if (filter.genomeTemplate().isEmpty()) {
			return;
		}
		IGenome template = filter.genomeTemplate().get();
		for (IChromosome<?> chromosome : this.traitChromosomes) {
			boolean pure = filter.requiredHomozygous().contains(chromosome.id());
			boolean active = filter.requiredActive().contains(chromosome.id());
			if (!pure && !active) {
				continue;
			}
			Object want = template.getActiveValue(cast(chromosome));
			if (this.candidates.get(chromosome).contains(want)) {
				this.chosenValue.put(chromosome, want);
				this.chosenMode.put(chromosome, pure ? Mode.PURE : Mode.ACTIVE);
			}
		}
	}

	// --- layout -------------------------------------------------------------------------------------------------------

	@Override
	protected void init() {
		this.panelLeft = (this.width - PANEL_W) / 2;
		this.panelTop = (this.height - PANEL_H) / 2;
		int left = this.panelLeft + PAD;
		int col1 = left + HALF_W + GAP;

		int tabY = this.panelTop + 6;
		addRenderableWidget(new HiveButton(left, tabY, HALF_W, BTN_H, tabLabel("gui.beegistics.bee_filter.tab.filter", this.tab == 0), b -> switchTab(0)));
		addRenderableWidget(new HiveButton(col1, tabY, HALF_W, BTN_H, tabLabel("gui.beegistics.bee_filter.tab.traits", this.tab == 1), b -> switchTab(1)));

		int contentTop = tabY + BTN_H + GAP;
		if (this.tab == 0) {
			buildFilterTab(left, contentTop);
		} else {
			buildTraitsTab(left, contentTop);
		}

		// Shared bottom row: clear everything / close (both tabs).
		int bottomY = this.panelTop + PANEL_H - PAD - BTN_H;
		addRenderableWidget(new HiveButton(left, bottomY, HALF_W, BTN_H, Component.translatable("gui.beegistics.bee_filter.clear"), b -> clearAll()));
		addRenderableWidget(new HiveButton(col1, bottomY, HALF_W, BTN_H, Component.translatable("gui.done"), b -> onClose()));
	}

	/** A {@link HiveButton} whose action runs and then {@link #rebuildWidgets() rebuilds} the panel - the common edit pattern. */
	private HiveButton rebuildingButton(int x, int y, int w, int h, Component label, Runnable action) {
		return new HiveButton(x, y, w, h, label, b -> {
			action.run();
			rebuildWidgets();
		});
	}

	private void buildFilterTab(int left, int contentTop) {
		// Top-left bee slot (drawn in render(), clicked in mouseClicked()) with a Species/Genus match toggle beside it. The
		// slot fills from the off-hand bee on click, or by dragging a bee from JEI onto it (see the beegistics JEI plugin).
		int toggleX = left + BeegisticsGuiStyle.SLOT_SIZE + GAP;
		addRenderableWidget(rebuildingButton(toggleX, contentTop, left + CONTENT_W - toggleX, BTN_H, matchToggleLabel(), () -> this.matchGenus = !this.matchGenus));
		int y = contentTop + ROW;

		// A single life-stage choice: Any / Princess / Drone / Queen. (Larva is a vestigial bee stage no apiary produces, so
		// it is omitted; the controller's parent slots force Princess/Drone anyway, so this mainly scopes the target count.)
		addRenderableWidget(rebuildingButton(left, y, CONTENT_W, BTN_H, stageToggleLabel(), this::cycleStage));
		y += ROW;

		// Analyzed / pristine tri-state cycles, full width each ("Analyzed: Excluded" needs the room).
		addRenderableWidget(rebuildingButton(left, y, CONTENT_W, BTN_H, triLabel("gui.beegistics.bee_filter.analyzed", this.analyzed), () -> this.analyzed = cycle(this.analyzed)));
		y += ROW;
		addRenderableWidget(rebuildingButton(left, y, CONTENT_W, BTN_H, triLabel("gui.beegistics.bee_filter.pristine", this.pristine), () -> this.pristine = cycle(this.pristine)));
	}

	private void buildTraitsTab(int left, int contentTop) {
		int pages = traitPageCount();
		this.traitPage = Math.max(0, Math.min(this.traitPage, pages - 1));
		int start = this.traitPage * TRAIT_ROWS_PER_PAGE;
		int end = Math.min(start + TRAIT_ROWS_PER_PAGE, this.traitChromosomes.size());
		int modeW = 44;
		int valueW = CONTENT_W - modeW - GAP;

		for (int i = start; i < end; i++) {
			IChromosome<?> chromosome = this.traitChromosomes.get(i);
			int y = contentTop + (i - start) * ROW;
			// Left button: cycle this trait's target value (Any -> each reachable value -> Any); shift-click resets to Any.
			addRenderableWidget(rebuildingButton(left, y, valueW, BTN_H, traitValueLabel(chromosome, valueW), () -> {
				if (hasShiftDown()) {
					clearTrait(chromosome);
				} else {
					cycleTraitValue(chromosome);
				}
			}));
			// Right button: toggle Active (expressed) vs Pure (bred true). Inert until a value is chosen.
			HiveButton modeButton = rebuildingButton(left + valueW + GAP, y, modeW, BTN_H, traitModeLabel(chromosome), () -> toggleTraitMode(chromosome));
			modeButton.active = this.chosenValue.containsKey(chromosome);
			addRenderableWidget(modeButton);
		}

		// Paging controls, only when there is more than one page.
		if (pages > 1) {
			int y = contentTop + TRAIT_ROWS_PER_PAGE * ROW;
			int navW = 40;
			addRenderableWidget(rebuildingButton(left, y, navW, BTN_H, Component.literal("<"), () -> this.traitPage = (this.traitPage - 1 + pages) % pages));
			addRenderableWidget(rebuildingButton(left + CONTENT_W - navW, y, navW, BTN_H, Component.literal(">"), () -> this.traitPage = (this.traitPage + 1) % pages));
		}
	}

	private int traitPageCount() {
		return Math.max(1, (this.traitChromosomes.size() + TRAIT_ROWS_PER_PAGE - 1) / TRAIT_ROWS_PER_PAGE);
	}

	// --- trait editing ------------------------------------------------------------------------------------------------

	private void cycleTraitValue(IChromosome<?> chromosome) {
		List<Object> values = this.candidates.get(chromosome);
		if (values.isEmpty()) {
			return;
		}
		Object current = this.chosenValue.get(chromosome);
		int next = current == null ? 0 : values.indexOf(current) + 1;
		if (next >= values.size()) {
			clearTrait(chromosome);   // wrap past the last value back to "Any"
		} else {
			this.chosenValue.put(chromosome, values.get(next));
			this.chosenMode.putIfAbsent(chromosome, Mode.PURE);   // true-breeding is the useful default for a target
		}
	}

	private void toggleTraitMode(IChromosome<?> chromosome) {
		if (this.chosenValue.containsKey(chromosome)) {
			this.chosenMode.put(chromosome, this.chosenMode.get(chromosome) == Mode.PURE ? Mode.ACTIVE : Mode.PURE);
		}
	}

	private void clearTrait(IChromosome<?> chromosome) {
		this.chosenValue.remove(chromosome);
		this.chosenMode.remove(chromosome);
	}

	/** @return the single selected stage, or {@code null} for "Any" (no stage constraint). */
	@Nullable
	private BeeLifeStage currentStage() {
		return this.stages.size() == 1 ? this.stages.iterator().next() : null;
	}

	/** Cycles the single stage choice: Any -> Princess -> Drone -> Any. Queen and larva are omitted - a filter never has a
	 * use for them (an apiary never yields a larva, and the controller's parent slots force princess/drone). */
	private void cycleStage() {
		BeeLifeStage current = currentStage();
		BeeLifeStage next;
		if (current == null) {
			next = BeeLifeStage.PRINCESS;
		} else if (current == BeeLifeStage.PRINCESS) {
			next = BeeLifeStage.DRONE;
		} else {
			next = null;   // Drone (or any other value) -> Any
		}
		this.stages.clear();
		if (next != null) {
			this.stages.add(next);
		}
	}

	private void switchTab(int which) {
		this.tab = which;
		rebuildWidgets();
	}

	private void clearAll() {
		this.stages.clear();
		this.sampleBee = null;
		this.matchGenus = false;
		this.analyzed = Optional.empty();
		this.pristine = Optional.empty();
		this.chosenValue.clear();
		this.chosenMode.clear();
		rebuildWidgets();
	}

	/** @return The bee individual held in the hand opposite the card (so capture works whichever hand holds the card). */
	private Optional<IIndividual> capturedIndividual() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return Optional.empty();
		}
		InteractionHand sampleHand = this.hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		return Optional.ofNullable(IIndividualHandlerItem.getIndividual(mc.player.getItemInHand(sampleHand)));
	}

	// --- labels -------------------------------------------------------------------------------------------------------

	/** @return the sampled individual, or {@code null} if the slot is empty / not a bee. */
	@Nullable
	private IIndividual sampleIndividual() {
		return this.sampleBee == null ? null : IIndividualHandlerItem.getIndividual(this.sampleBee);
	}

	/** "Match: Species" / "Match: Genus" - which of the sampled bee's ranks the filter constrains. */
	private Component matchToggleLabel() {
		String key = this.matchGenus ? "gui.beegistics.bee_filter.match.genus" : "gui.beegistics.bee_filter.match.species";
		return Component.translatable(key).withStyle(this.sampleBee == null ? ChatFormatting.GRAY : ChatFormatting.GREEN);
	}

	/** The species or genus name the current sample+toggle resolves to, for the slot tooltip (or "Any" when empty). */
	private Component sampleTargetName() {
		IIndividual individual = sampleIndividual();
		if (individual == null) {
			return Component.translatable("gui.beegistics.bee_filter.any").withStyle(ChatFormatting.GRAY);
		}
		Component name = this.matchGenus
			? Component.literal(individual.getSpecies().getGenus().name())
			: individual.getSpecies().getDisplayName();
		return name.copy().withStyle(ChatFormatting.GREEN);
	}

	private Component tabLabel(String key, boolean selected) {
		return Component.translatable(key).withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.GRAY);
	}

	private Component stageToggleLabel() {
		BeeLifeStage stage = currentStage();
		Component value = stage == null
			? Component.translatable("gui.beegistics.bee_filter.stage.any").withStyle(ChatFormatting.GRAY)
			: Component.translatable("gui.beegistics.bee_filter.stage." + stage.getSerializedName()).withStyle(ChatFormatting.GREEN);
		return Component.translatable("gui.beegistics.bee_filter.stage_label").append(": ").append(value);
	}

	private static Component triLabel(String key, Optional<Boolean> value) {
		String stateKey = value.map(v -> v ? "require" : "exclude").orElse("any");
		return Component.translatable(key).append(": ").append(Component.translatable("gui.beegistics.bee_filter.tri." + stateKey)
			.withStyle(value.isEmpty() ? ChatFormatting.GRAY : (value.get() ? ChatFormatting.GREEN : ChatFormatting.RED)));
	}

	/** "TraitName: Value", truncated to the button width; grey when unset. Colour matches the mode button's meaning. */
	private Component traitValueLabel(IChromosome<?> chromosome, int width) {
		String name = Component.translatable(chromosome.chromosomeTranslationKey()).getString();
		Object value = this.chosenValue.get(chromosome);
		if (value == null) {
			return truncate(name + ": " + Component.translatable("gui.beegistics.bee_filter.any").getString(), width, ChatFormatting.GRAY);
		}
		String valueName = Component.translatable(valueKey(chromosome, value)).getString();
		ChatFormatting colour = this.chosenMode.get(chromosome) == Mode.PURE ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
		return truncate(name + ": " + valueName, width, colour);
	}

	private Component traitModeLabel(IChromosome<?> chromosome) {
		if (!this.chosenValue.containsKey(chromosome)) {
			return Component.literal("-").withStyle(ChatFormatting.DARK_GRAY);
		}
		boolean pure = this.chosenMode.get(chromosome) == Mode.PURE;
		return Component.translatable(pure ? "gui.beegistics.bee_filter.mode.pure" : "gui.beegistics.bee_filter.mode.active")
			.withStyle(pure ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
	}

	/** Cuts a plain label to fit {@code width} (minus padding), appending an ellipsis, so no label ever overruns its button. */
	private Component truncate(String text, int width, ChatFormatting colour) {
		int max = width - 8;
		if (this.font.width(text) <= max) {
			return Component.literal(text).withStyle(colour);
		}
		String ellipsis = "…";
		String cut = this.font.plainSubstrByWidth(text, max - this.font.width(ellipsis));
		return Component.literal(cut + ellipsis).withStyle(colour);
	}

	@SuppressWarnings("unchecked")
	private static String valueKey(IChromosome<?> chromosome, Object value) {
		return ((IChromosome<Object>) chromosome).translationKey(value);
	}

	@SuppressWarnings("unchecked")
	private static IChromosome<Object> cast(IChromosome<?> chromosome) {
		return (IChromosome<Object>) chromosome;
	}

	private static Optional<Boolean> cycle(Optional<Boolean> value) {
		if (value.isEmpty()) {
			return Optional.of(Boolean.TRUE);
		}
		return value.get() ? Optional.of(Boolean.FALSE) : Optional.empty();
	}

	// --- persistence --------------------------------------------------------------------------------------------------

	private BeeFilter toFilter() {
		Optional<IGenome> template = Optional.empty();
		Set<ResourceLocation> requiredActive = new LinkedHashSet<>();
		Set<ResourceLocation> requiredHomozygous = new LinkedHashSet<>();
		Map<IChromosome<?>, Allele<?>> overrides = new IdentityHashMap<>();
		for (IChromosome<?> chromosome : this.traitChromosomes) {
			Object value = this.chosenValue.get(chromosome);
			if (value == null) {
				continue;
			}
			overrides.put(chromosome, Allele.of(value, true));   // dominance is irrelevant for a both-equal (homozygous) template
			(this.chosenMode.get(chromosome) == Mode.PURE ? requiredHomozygous : requiredActive).add(chromosome.id());
		}
		if (!overrides.isEmpty()) {
			IGenome base = SpeciesUtil.BEE_TYPE.get().getDefaultSpecies().getDefaultGenome();
			template = Optional.of(base.copyWith(overrides));
		}

		// The sampled bee constrains either species or genus (never both), per the toggle.
		Optional<ResourceLocation> species = Optional.empty();
		Optional<String> genus = Optional.empty();
		IIndividual sample = sampleIndividual();
		if (sample != null) {
			if (this.matchGenus) {
				genus = Optional.of(sample.getSpecies().getGenus().name());
			} else {
				species = Optional.of(sample.getSpecies().id());
			}
		}
		return new BeeFilter(EnumSet.copyOf(this.stages), species, genus, this.inactiveSpecies, this.analyzed, this.pristine,
			template, requiredActive, requiredHomozygous);
	}

	@Override
	public void onClose() {
		PacketDistributor.sendToServer(new SetBeeFilterPayload(this.hand == InteractionHand.MAIN_HAND, toFilter()));
		super.onClose();
	}

	// --- rendering ----------------------------------------------------------------------------------------------------

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
		BeegisticsGuiStyle.panel(graphics, this.panelLeft, this.panelTop, PANEL_W, PANEL_H);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);

		// The top-left bee sample slot (filter tab only; the toggle beside it is a normal widget drawn by super.render).
		if (this.tab == 0) {
			Rect2i slot = sampleSlotRect();
			BeegisticsGuiStyle.slot(graphics, slot.getX(), slot.getY());
			if (this.sampleBee != null) {
				graphics.renderItem(this.sampleBee, slot.getX() + 1, slot.getY() + 1);
			}
		}

		int cx = this.panelLeft + PANEL_W / 2;
		String hintKey = this.tab == 0 ? "gui.beegistics.bee_filter.hint" : "gui.beegistics.bee_filter.traits_hint";
		List<FormattedCharSequence> lines = this.font.split(Component.translatable(hintKey), CONTENT_W);

		// Vertically center the hint block in the gap between the last option row and the bottom Clear/Done row.
		int contentTop = this.panelTop + 6 + BTN_H + GAP;
		int rows = this.tab == 0 ? FILTER_ROWS : TRAIT_ROWS_PER_PAGE + (traitPageCount() > 1 ? 1 : 0);
		int widgetsBottom = contentTop + (rows - 1) * ROW + BTN_H;
		int bottomY = this.panelTop + PANEL_H - PAD - BTN_H;
		int lineH = this.font.lineHeight + 1;
		int blockH = lines.size() * lineH - 1;
		int y = widgetsBottom + (bottomY - widgetsBottom - blockH) / 2;
		for (FormattedCharSequence line : lines) {
			graphics.drawCenteredString(this.font, line, cx, y, BeegisticsGuiStyle.TEXT_MUTED);
			y += lineH;
		}

		// Slot tooltip, drawn last so it sits above everything.
		if (this.tab == 0 && BeegisticsGuiStyle.contains(sampleSlotRect(), mouseX, mouseY)) {
			List<Component> tip = new ArrayList<>();
			tip.add(this.sampleBee != null ? this.sampleBee.getHoverName() : Component.translatable("gui.beegistics.bee_filter.slot_empty"));
			tip.add(Component.translatable(this.matchGenus ? "gui.beegistics.bee_filter.match.genus" : "gui.beegistics.bee_filter.match.species")
				.append(": ").append(sampleTargetName()));
			tip.add(Component.translatable("gui.beegistics.bee_filter.slot_hint").withStyle(ChatFormatting.DARK_GRAY));
			graphics.renderComponentTooltip(this.font, tip, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Left-click the slot to fill it from the bee held in the opposite hand; shift-click to clear it.
		if (this.tab == 0 && button == 0 && BeegisticsGuiStyle.contains(sampleSlotRect(), mouseX, mouseY)) {
			if (hasShiftDown()) {
				this.sampleBee = null;
			} else {
				capturedIndividual().ifPresent(individual -> this.sampleBee = individual.createStack(BeeLifeStage.DRONE));
			}
			rebuildWidgets();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	// --- JEI integration hooks (called from the beegistics JEI plugin; no JEI types leak into this client screen) -------

	@Override
	public boolean acceptsBeeDrop() {
		// The Filter tab owns the sample slot; the Traits tab has no drop target.
		return this.tab == 0;
	}

	@Override
	public Rect2i beeDropRect() {
		return sampleSlotRect();
	}

	@Override
	public void acceptBeeDrop(ItemStack stack) {
		setSampleFromStack(stack);
	}

	/** @return whether the stack is a Forestry bee (reusing the empty filter, which matches any bee and nothing else). */
	@Override
	public boolean isBeeStack(ItemStack stack) {
		return BeeFilter.EMPTY.matches(stack);
	}

	/** @return the screen-space rectangle of the top-left bee sample slot (a JEI ghost-ingredient drop target). */
	public Rect2i sampleSlotRect() {
		int contentTop = this.panelTop + 6 + BTN_H + GAP;
		return new Rect2i(this.panelLeft + PAD, contentTop, BeegisticsGuiStyle.SLOT_SIZE, BeegisticsGuiStyle.SLOT_SIZE);
	}

	/** Fills the slot from a bee stack (e.g. one dragged from JEI). Ignores non-bees. */
	public void setSampleFromStack(ItemStack stack) {
		if (isBeeStack(stack)) {
			this.sampleBee = stack.copyWithCount(1);
			rebuildWidgets();
		}
	}

	@Override
	public int panelLeft() {
		return this.panelLeft;
	}

	@Override
	public int panelTop() {
		return this.panelTop;
	}

	@Override
	public int panelWidth() {
		return PANEL_W;
	}

	@Override
	public int panelHeight() {
		return PANEL_H;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
