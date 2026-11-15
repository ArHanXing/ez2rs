package ar.hxi.ez2rs.terminal;

import ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridContainerMenu;
import ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridData;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemContext;
import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.WirelessPatternGridState;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Prepares one unattached native state, shared by the server menu and its opening snapshot. */
public final class PatternGridOpener {
    private PatternGridOpener() { }

    public static void open(final ServerPlayer player, final ItemStack stack, final SlotReference reference) {
        // openMenu removes the old menu AFTER preparation. Persist before reading a new draft.
        if (player.containerMenu instanceof WirelessPatternGridContainerMenu oldMenu) oldMenu.flushDraft();
        if (reference.resolve(player).orElse(ItemStack.EMPTY) != stack
            || !Ez2rsTerminalContent.isPatternGridCapable(stack)) return;
        final NetworkItemContext context = RefinedStorageApi.INSTANCE.getNetworkItemHelper()
            .createContext(stack, player, reference);
        final WirelessPatternGridState state = new WirelessPatternGridState(context, player, stack, reference);
        state.restoreDraft(stack.get(Ez2rsTerminalContent.PATTERN_GRID_DRAFT));
        player.openMenu(new ExtendedScreenHandlerFactory<WirelessPatternGridData>() {
            @Override
            public Component getDisplayName() { return stack.getHoverName(); }
            @Override
            public AbstractContainerMenu createMenu(final int id, final Inventory inventory, final Player p) {
                return new WirelessPatternGridContainerMenu(id, inventory, state, reference);
            }
            @Override
            public WirelessPatternGridData getScreenOpeningData(final ServerPlayer p) {
                return new WirelessPatternGridData(state.getMenuData(), reference);
            }
        });
    }
}
