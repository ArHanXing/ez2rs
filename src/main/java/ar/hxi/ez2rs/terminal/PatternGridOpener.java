package ar.hxi.ez2rs.terminal;

import ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGrid;
import ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridContainerMenu;
import ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridData;

import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridData;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 打开无线样板终端。
 *
 * <p>不走 RS2 的 {@code MenuOpener}：它把 provider 当作 {@code ExtendedMenuProvider<?>} 处理，
 * 泛型擦除后会把我们的数据按 {@code PatternGridData} 编码，发包时直接 ClassCastException。
 * 这里用 {@code ExtendedScreenHandlerFactory} 明确指定数据类型。</p>
 *
 * <p>通用终端的样板页和独立物品「无线样板终端」共用这一份实现。</p>
 */
public final class PatternGridOpener {
    private PatternGridOpener() {
    }

    public static void open(final ServerPlayer player,
                            final ItemStack stack,
                            final SlotReference slotReference) {
        final PatternGridData data = WirelessPatternGrid.empty();
        player.openMenu(new ExtendedScreenHandlerFactory<WirelessPatternGridData>() {
            @Override
            public Component getDisplayName() {
                return stack.getHoverName();
            }

            @Override
            public AbstractContainerMenu createMenu(final int syncId,
                                                    final Inventory inventory,
                                                    final Player p) {
                return new WirelessPatternGridContainerMenu(syncId, inventory, data, slotReference, true);
            }

            @Override
            public WirelessPatternGridData getScreenOpeningData(final ServerPlayer serverPlayer) {
                return new WirelessPatternGridData(data, slotReference);
            }
        });
    }
}
