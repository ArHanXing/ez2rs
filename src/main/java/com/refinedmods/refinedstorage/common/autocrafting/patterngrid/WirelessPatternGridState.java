package com.refinedmods.refinedstorage.common.autocrafting.patterngrid;

import ar.hxi.ez2rs.terminal.Ez2rsTerminalContent;
import ar.hxi.ez2rs.terminal.patterngrid.PatternGridDraft;
import com.refinedmods.refinedstorage.common.grid.Ez2rsManagedWirelessGrid;
import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken;
import com.refinedmods.refinedstorage.api.autocrafting.preview.Preview;
import com.refinedmods.refinedstorage.api.autocrafting.preview.TreePreview;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.node.grid.GridOperations;
import com.refinedmods.refinedstorage.api.network.node.grid.GridWatcher;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.Storage;
import com.refinedmods.refinedstorage.api.storage.TrackedResourceAmount;
import com.refinedmods.refinedstorage.api.storage.root.RootStorage;
import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemContext;
import com.refinedmods.refinedstorage.common.api.support.resource.PlatformResourceKey;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceType;
import com.refinedmods.refinedstorage.common.content.Blocks;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/**
 * Unattached native state adapter with item-local persistence.
 * The native node/container is constructed but NEVER registered,
 * initialized, ticked, attached to a network, or put into a Level/chunk.
 *
 * <p>setLevel is only a recipe/registry context and retains native result updates.
 * Never call clearRemoved/setRemoved, setBlockState, doWork, updateActiveness,
 * updateDebugNetworkId, or getContainerProvider().initialize/update/remove.
 * getContainerProvider and initialize(ServerLevel) are inherited final traps;
 * the directional initialize hook below is explicitly inert.</p>
 */
public final class WirelessPatternGridState extends PatternGridBlockEntity {
    private final Ez2rsManagedWirelessGrid wirelessGrid;
    protected final NetworkItemContext context;
    protected final ServerPlayer owner;
    /** Exact original stack reference, NOT a copy or a lookup by slot on save. */
    protected final ItemStack terminalIdentity;
    private boolean draftDirty;
    private boolean restored;
    private boolean restoring;
    private final SlotReference slotReference;

    public WirelessPatternGridState(final NetworkItemContext context,
                                    final ServerPlayer owner, final ItemStack terminalIdentity,
                                    final SlotReference slotReference) {
        // Bookkeeping only. No world location is claimed or looked up here.
        super(BlockPos.ZERO, Blocks.INSTANCE.getPatternGrid().get(DyeColor.LIGHT_BLUE).defaultBlockState());
        this.context = Objects.requireNonNull(context);
        this.owner = Objects.requireNonNull(owner);
        this.terminalIdentity = Objects.requireNonNull(terminalIdentity);
        this.slotReference = Objects.requireNonNull(slotReference);
        this.wirelessGrid = new Ez2rsManagedWirelessGrid(context, this::hasTerminalIdentity);
        setLevel(owner.level()); // recipe/registry context ONLY, no registration lifecycle
    }

    private boolean hasTerminalIdentity() {
        return !terminalIdentity.isEmpty() && Ez2rsTerminalContent.isPatternGridCapable(terminalIdentity)
            && slotReference.resolve(owner).orElse(ItemStack.EMPTY) == terminalIdentity;
    }

    public void menuReady(final Runnable snapshot) { wirelessGrid.menuReady(snapshot); }

    @Override
    public final void setChanged() {
        // May be called before subclass fields exist. No callback, Level access,
        // super call, network mutation, or immediate partially-restored writeback.
        draftDirty = true;
    }

    @Override
    protected final void initialize(final ServerLevel level, final Direction direction) {
        // Intentionally inert: native setLevel still updates all recipe results.
    }

    public final boolean isDraftDirty() {
        return draftDirty;
    }

    protected final void markDraftSaved() {
        draftDirty = false;
    }

    /** retained-storage detach, pending reset, clear, strategy refresh, replay. */
    public void refreshNetworkState() { wirelessGrid.refresh(); }

    /** immediate non-forced native authorization/activity AND exact item identity. */
    public boolean isWritable() { return hasTerminalIdentity() && wirelessGrid.isGridActive(); }

    /** server-only writeback to still-identical pattern-capable terminal. */
    public void saveDraft() {
        if (!restored || restoring || !draftDirty || !hasTerminalIdentity()) return;
        terminalIdentity.set(Ez2rsTerminalContent.PATTERN_GRID_DRAFT, captureDraft());
        markDraftSaved();
    }

    /** existing PatternGridDraft schema + optional stonecutter_selected_recipe. */
    public CompoundTag captureDraft() {
        final CompoundTag draft = PatternGridDraft.capture(owner.level(), getPatternType(), isFuzzyMode(),
            craftingInput(), smithingInput(), stonecutterInput(), getPatternInput(), patternOutput(),
            processingInput(), processingOutput());
        PatternGridDraft.setStonecutterSelected(draft, getStonecutterSelectedRecipe());
        return draft;
    }

    /** restore containers, then selection; suppress writes until restore completes. */
    public void restoreDraft(final CompoundTag draft) {
        if (restored) throw new IllegalStateException("Draft already restored");
        restoring = true;
        try {
            if (draft != null) {
                PatternGridDraft.restore(draft, owner.level(), craftingInput(), smithingInput(), stonecutterInput(),
                    getPatternInput(), patternOutput(), processingInput(), processingOutput());
                super.setPatternType(PatternGridDraft.patternType(draft));
                super.setFuzzyMode(PatternGridDraft.fuzzy(draft));
                final int selected = PatternGridDraft.stonecutterSelected(draft);
                super.setStonecutterSelectedRecipe(selected >= 0 && selected < getStonecutterInput().getRecipes().size()
                    ? selected : -1);
            } else {
                super.setStonecutterSelectedRecipe(-1);
            }
        } finally {
            restoring = false;
            restored = true;
            markDraftSaved();
        }
    }

    @Override
    void copyPattern(final ItemStack stack) {
        if (isWritable()) super.copyPattern(stack);
    }

    @Override
    void setPatternType(final PatternType type) {
        if (restoring || isWritable()) super.setPatternType(type);
    }
    @Override
    void setFuzzyMode(final boolean fuzzy) {
        if (restoring || isWritable()) super.setFuzzyMode(fuzzy);
    }
    @Override
    void setStonecutterSelectedRecipe(final int selected) {
        if (restoring || isWritable()) {
            super.setStonecutterSelectedRecipe(selected >= 0 && selected < getStonecutterInput().getRecipes().size()
                ? selected : -1);
        }
    }
    @Override
    public void setRedstoneMode(final com.refinedmods.refinedstorage.common.support.RedstoneMode mode) {
        if (isWritable()) super.setRedstoneMode(mode);
    }

    /**
     * freshly resolve through context.resolveNetwork(), never force=true.
     * Return empty storage/patterns when unauthorized/inactive, not the inert node.
     */
    public SortingContext sortingContext() {
        return wirelessGrid.authorizedNetwork().map(network -> new SortingContext(
            network.getComponent(StorageNetworkComponent.class),
            network.getComponent(AutocraftingNetworkComponent.class).getPatterns())).orElseGet(SortingContext::empty);
    }

    public record SortingContext(RootStorage storage, Set<Pattern> patterns) {
        public SortingContext {
            Objects.requireNonNull(patterns);
        }

        public static SortingContext empty() {
            return new SortingContext(null, Set.of());
        }
    }

    // Public facades retain native containers/getters, not menu field reflection.
    public final Container craftingInput() { return getCraftingMatrix(); }
    public final Container smithingInput() { return getSmithingTableMatrix(); }
    public final Container stonecutterInput() { return getStonecutterInput(); }
    public final Container patternOutput() { return getPatternOutput(); }
    public final ResourceContainer processingInput() { return getProcessingInput(); }
    public final ResourceContainer processingOutput() { return getProcessingOutput(); }

    @Override
    public PatternType getPatternType() { return super.getPatternType(); }
    @Override
    public boolean isFuzzyMode() { return super.isFuzzyMode(); }
    @Override
    public int getStonecutterSelectedRecipe() { return super.getStonecutterSelectedRecipe(); }

    // Native transfer, clear, encoding and output Slot.set behavior remains native.
    // Persistence reads adapter getters, never menu ClientProperty/ServerProperty values.

    @Override
    public Network getNetworkForItem() { return wirelessGrid.authorizedNetwork().orElse(null); }
    @Override
    public void addWatcher(final GridWatcher watcher, final Class<? extends Actor> actorType) {
        wirelessGrid.addWatcher(watcher, actorType);
    }
    @Override
    public void removeWatcher(final GridWatcher watcher) { wirelessGrid.removeWatcher(watcher); }
    @Override
    public Storage getItemStorage() { return wirelessGrid.getItemStorage(); }
    @Override
    public boolean isGridActive() { return wirelessGrid.isGridActive(); }
    @Override
    public List<TrackedResourceAmount> getResources(final Class<? extends Actor> actorType) {
        return wirelessGrid.getResources(actorType);
    }
    @Override
    public Set<PlatformResourceKey> getAutocraftableResources() {
        return wirelessGrid.getAutocraftableResources();
    }
    @Override
    public GridOperations createOperations(final ResourceType type, final ServerPlayer player) {
        return wirelessGrid.createOperations(type, player);
    }
    /** Retain terminal identity instead of block-distance validation. */
    @Override
    public boolean canMenuStayOpen(final Player player) { return player == owner && hasTerminalIdentity(); }
    @Override
    public CompletableFuture<Optional<Preview>> getPreview(final ResourceKey resource, final long amount,
                                                          final CancellationToken token) {
        return isWritable() ? wirelessGrid.getPreview(resource, amount, token)
            : CompletableFuture.completedFuture(Optional.empty());
    }
    @Override
    public CompletableFuture<Optional<TreePreview>> getTreePreview(final ResourceKey resource, final long amount,
                                                                  final CancellationToken token) {
        return isWritable() ? wirelessGrid.getTreePreview(resource, amount, token)
            : CompletableFuture.completedFuture(Optional.empty());
    }
    @Override
    public CompletableFuture<Long> getMaxAmount(final ResourceKey resource, final CancellationToken token) {
        return isWritable() ? wirelessGrid.getMaxAmount(resource, token) : CompletableFuture.completedFuture(0L);
    }
    @Override
    public Optional<TaskId> startTask(final ResourceKey resource, final long amount, final Actor actor,
                                      final boolean notify, final CancellationToken token) {
        return isWritable() ? wirelessGrid.startTask(resource, amount, actor, notify, token) : Optional.empty();
    }
}
