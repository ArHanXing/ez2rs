package com.refinedmods.refinedstorage.common.autocrafting.patterngrid;

import com.refinedmods.refinedstorage.common.support.FilteredContainer;
import com.refinedmods.refinedstorage.common.support.containermenu.ValidatedSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Typed access to the RS2 2.0.9 package-private server constructor.
 * Neither constructor registers extra properties: native order/count is preserved.
 *
 * <p>Server reads use the native adapter directly: ServerProperty is not a ClientProperty.</p>
 */
public abstract class Ez2rsNativePatternGridMenu extends PatternGridContainerMenu {
    private WirelessPatternGridState wirelessState;

    protected Ez2rsNativePatternGridMenu(final int syncId, final Inventory inventory,
                                         final PatternGridData data) {
        super(syncId, inventory, data);
    }

    protected Ez2rsNativePatternGridMenu(final int syncId, final Inventory inventory,
                                         final WirelessPatternGridState state) {
        super(syncId, inventory, state);
        this.wirelessState = state;
    }

    protected final WirelessPatternGridState wirelessState() {
        return wirelessState;
    }

    /** Native real pattern slots on both sides; matrix/resource/result slots are excluded. */
    protected final boolean isRealPatternSlot(final Slot slot) {
        if (wirelessState != null) {
            return slot.container == wirelessState.getPatternInput()
                || slot.container == wirelessState.patternOutput();
        }
        // In the native client layout only the two real pattern slots are
        // ValidatedSlots backed by a one-slot FilteredContainer (including PatternOutputContainer).
        return slot instanceof ValidatedSlot && slot.container instanceof FilteredContainer
            && slot.container.getContainerSize() == 1;
    }

    /**
     * Whether a server-side write may touch this menu's processing amounts.
     *
     * <p>Default allows it: a block pattern grid is authorized by its own network state, and RS2's
     * amount-change packet has no extra policy either. The wireless terminal overrides this to
     * enforce its offline / closed rule.</p>
     */
    public boolean ez2rs$acceptsQuickMath() {
        return true;
    }

    // Native slot callbacks read these methods AFTER construction. Native resized
    // only captures the callbacks; it does not invoke them during super(). Keep
    // subclass resized overrides equally safe before wirelessState is assigned.
    // ServerProperty.getValue() throws, so server reads must use the BE directly.
    @Override
    public PatternType getPatternType() {
        return wirelessState == null ? super.getPatternType() : wirelessState.getPatternType();
    }

    @Override
    public boolean isFuzzyMode() {
        return wirelessState == null ? super.isFuzzyMode() : wirelessState.isFuzzyMode();
    }

    @Override
    public int getStonecutterSelectedRecipe() {
        return wirelessState == null
            ? super.getStonecutterSelectedRecipe()
            : wirelessState.getStonecutterSelectedRecipe();
    }
}
