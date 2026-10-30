package ar.hxi.ez2rs.terminal;

import ar.hxi.ez2rs.terminal.patterngrid.GridAttachable;
import ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGrid;
import ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridContainerMenu;
import ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridData;

import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.grid.Grid;
import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemContext;
import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridData;
import com.refinedmods.refinedstorage.common.grid.Ez2rsGridBridge;

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
 * <p>关键点：方块版的样板终端把 Grid 挂在方块实体上，无线版必须自己造一个
 * 基于 {@link NetworkItemContext} 的 Grid（和 RS2 的无线网格同款），
 * 并且在服务端把它挂到菜单上，否则菜单没有 watcher、没有存取策略，
 * 界面会是一片黑、也编不了样板。</p>
 *
 * <p>不走 RS2 的 {@code MenuOpener}：它把 provider 当作 {@code ExtendedMenuProvider<?>} 处理，
 * 泛型擦除后会把我们的数据按 {@code PatternGridData} 编码，发包时直接 ClassCastException。</p>
 */
public final class PatternGridOpener {
    private PatternGridOpener() {
    }

    public static void open(final ServerPlayer player,
                            final ItemStack stack,
                            final SlotReference slotReference) {
        final NetworkItemContext context = RefinedStorageApi.INSTANCE.getNetworkItemHelper().createContext(
            stack,
            player,
            slotReference
        );
        final Grid grid = Ez2rsGridBridge.createWirelessGrid(context);
        final PatternGridData data = WirelessPatternGrid.initial(grid);

        player.openMenu(new ExtendedScreenHandlerFactory<WirelessPatternGridData>() {
            @Override
            public Component getDisplayName() {
                return stack.getHoverName();
            }

            @Override
            public AbstractContainerMenu createMenu(final int syncId,
                                                    final Inventory inventory,
                                                    final Player p) {
                final WirelessPatternGridData openingData = new WirelessPatternGridData(data, slotReference);
                final WirelessPatternGridContainerMenu menu =
                    new WirelessPatternGridContainerMenu(syncId, inventory, openingData);
                // 服务端把活的 Grid 挂上去：watcher + 插入/提取/滚动策略
                ((GridAttachable) menu).ez2rs$attachGrid(grid, player);
                return menu;
            }

            @Override
            public WirelessPatternGridData getScreenOpeningData(final ServerPlayer serverPlayer) {
                return new WirelessPatternGridData(data, slotReference);
            }
        });
    }
}
