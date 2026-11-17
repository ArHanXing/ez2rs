package ar.hxi.ez2rs.terminal.patterngrid;

import ar.hxi.ez2rs.net.PatternGridSnapshotPacket;
import ar.hxi.ez2rs.terminal.Ez2rsMenus;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.api.support.resource.PlatformResourceKey;
import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.Ez2rsNativePatternGridMenu;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.WirelessPatternGridState;
import com.refinedmods.refinedstorage.common.grid.Ez2rsGridBridge;
import com.refinedmods.refinedstorage.common.grid.GridData;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Native pattern menu, with item ownership, draft persistence and wireless read-only policy. */
public final class WirelessPatternGridContainerMenu extends Ez2rsNativePatternGridMenu {
    private boolean closed;

    /** Client path: native synchronized containers only; never restore from a client item. */
    public WirelessPatternGridContainerMenu(final int id, final Inventory inventory,
                                           final WirelessPatternGridData data) {
        super(id, inventory, data.patternGridData());
        disabledSlot = data.slotReference();
        resized(0, 0, 0);
    }

    /** Server path: uses the SAME restored state used for opening data. */
    public WirelessPatternGridContainerMenu(final int id, final Inventory inventory,
                                           final WirelessPatternGridState state,
                                           final SlotReference reference) {
        super(id, inventory, state);
        disabledSlot = reference;
        resized(0, 0, 0); // first native build preceded disabledSlot assignment
        state.menuReady(() -> ServerPlayNetworking.send((ServerPlayer) inventory.player,
            new PatternGridSnapshotPacket(containerId, GridData.of(state))));
    }

    @Override
    public MenuType<?> getType() { return Ez2rsMenus.wirelessPatternGrid(); }

    private boolean canEdit() {
        final WirelessPatternGridState state = wirelessState();
        return !closed && (state == null ? isActive() : state.isWritable());
    }

    /**
     * Quick-math writes arrive through a dedicated C2S packet, not through the native
     * amount-change packet, so they must re-apply the same offline / closed gate here.
     */
    @Override
    public boolean ez2rs$acceptsQuickMath() {
        return canEdit();
    }

    public void flushDraft() {
        if (wirelessState() != null) wirelessState().saveDraft();
    }

    @Override
    public void broadcastChanges() {
        if (wirelessState() != null && !closed) wirelessState().refreshNetworkState();
        super.broadcastChanges();
        flushDraft();
    }

    @Override
    public void invalidate() {
        if (wirelessState() != null && playerInventory.player instanceof ServerPlayer serverPlayer) {
            Ez2rsGridBridge.resetPendingUpdates(this, serverPlayer);
        }
        super.invalidate();
    }

    @Override
    public void removed(final Player player) {
        // AbstractContainerScreen calls this on the client when EMI temporarily
        // replaces the terminal with its recipe screen. EMI retains this menu
        // and calls its transfer methods from there, so it must remain usable.
        if (player.level().isClientSide()) {
            super.removed(player);
            return;
        }
        if (closed) return;
        flushDraft();
        closed = true;
        super.removed(player); // server owns draft persistence and watcher removal
    }

    @Override
    public boolean stillValid(final Player player) {
        return wirelessState() == null || (!closed && wirelessState().canMenuStayOpen(player));
    }

    @Override
    public void receivePropertyChangeFromClient(final ResourceLocation id, final int value) {
        if (wirelessState() != null && canEdit()) super.receivePropertyChangeFromClient(id, value);
    }
    @Override
    public void clear() { if (canEdit()) super.clear(); }
    @Override
    public void createPattern() { if (canEdit()) super.createPattern(); }
    @Override
    public void transferCraftingRecipe(final List<List<ItemResource>> recipe) {
        if (canEdit()) super.transferCraftingRecipe(recipe);
    }
    @Override
    public void transferProcessingRecipe(final List<List<ResourceAmount>> inputs,
                                         final List<List<ResourceAmount>> outputs) {
        if (canEdit()) super.transferProcessingRecipe(inputs, outputs);
    }
    @Override
    public void transferStonecutterRecipe(final ItemResource input, final ItemResource output) {
        if (canEdit()) super.transferStonecutterRecipe(input, output);
    }
    @Override
    public void transferSmithingTableRecipe(final List<ItemResource> templates, final List<ItemResource> bases,
                                            final List<ItemResource> additions) {
        if (canEdit()) super.transferSmithingTableRecipe(templates, bases, additions);
    }
    @Override
    public void handleAllowedAlternativesUpdate(final int index, final Set<ResourceLocation> ids) {
        if (canEdit()) super.handleAllowedAlternativesUpdate(index, ids);
    }
    @Override
    public void handleFilterSlotChange(final int index, final ItemStack stack) {
        if (canEdit()) super.handleFilterSlotChange(index, stack);
    }
    @Override
    public void handleResourceFilterSlotUpdate(final int index, final PlatformResourceKey resource) {
        if (canEdit()) super.handleResourceFilterSlotUpdate(index, resource);
    }
    @Override
    public void handleResourceSlotChange(final int index, final boolean alternatives) {
        if (canEdit()) super.handleResourceSlotChange(index, alternatives);
    }
    @Override
    public void handleResourceSlotAmountChange(final int index, final long amount) {
        if (canEdit()) super.handleResourceSlotAmountChange(index, amount);
    }
    // handleResourceSlotUpdate is the native S2C synchronization path, and remains enabled on client.
    @Override
    public void handleResourceSlotUpdate(final int index, final ResourceAmount amount) {
        if (wirelessState() == null || canEdit()) super.handleResourceSlotUpdate(index, amount);
    }
    @Override
    public void addToResourceSlotIfNotExisting(final ItemStack stack) {
        if (canEdit()) super.addToResourceSlotIfNotExisting(stack);
    }

    @Override
    public boolean onInsert(final com.refinedmods.refinedstorage.api.network.node.grid.GridInsertMode mode,
                            final boolean alternatives) {
        if (wirelessState() != null) wirelessState().refreshNetworkState();
        return canEdit() && super.onInsert(mode, alternatives);
    }

    @Override
    public boolean onExtract(final PlatformResourceKey resource,
                             final com.refinedmods.refinedstorage.api.network.node.grid.GridExtractMode mode,
                             final boolean cursor) {
        // Refresh BEFORE native strategy entry: fluid extraction also retains its bucket itemStorage.
        if (wirelessState() != null) wirelessState().refreshNetworkState();
        return canEdit() && super.onExtract(resource, mode, cursor);
    }

    @Override
    public boolean onScroll(final PlatformResourceKey resource,
                            final com.refinedmods.refinedstorage.common.api.grid.GridScrollMode mode,
                            final int index) {
        if (wirelessState() != null) wirelessState().refreshNetworkState();
        return canEdit() && super.onScroll(resource, mode, index);
    }

    @Override
    public void clicked(final int index, final int button, final ClickType type, final Player player) {
        if (index >= slots.size() || (index < 0 && index != -999 && index != -1)) return;
        if (!canEdit()) {
            // Reject drag completion too: its saved slot set could have been started while online.
            if (type == ClickType.QUICK_CRAFT) return;
            if (index >= 0) {
                final Slot slot = slots.get(index);
                if (slot.container != playerInventory) {
                    final boolean removal = isRealPatternSlot(slot)
                        && ((type == ClickType.PICKUP && getCarried().isEmpty())
                            || type == ClickType.THROW || type == ClickType.QUICK_MOVE);
                    if (!removal) return;
                }
            }
        }
        super.clicked(index, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        // Native fallback enters insertionStrategy directly, bypassing onInsert().
        if (wirelessState() != null) wirelessState().refreshNetworkState();
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        final Slot slot = slots.get(index);
        if (slot instanceof com.refinedmods.refinedstorage.common.support.containermenu.DisabledSlot) {
            return ItemStack.EMPTY;
        }
        if (!canEdit() && !isRealPatternSlot(slot)) return ItemStack.EMPTY;
        return super.quickMoveStack(player, index);
    }

    @Override
    public boolean canDragTo(final Slot slot) {
        return (canEdit() || slot.container == playerInventory) && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(final ItemStack stack, final Slot slot) {
        return (canEdit() || slot.container == playerInventory) && super.canTakeItemForPickAll(stack, slot);
    }
}
