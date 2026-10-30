package com.refinedmods.refinedstorage.common.grid;

import com.refinedmods.refinedstorage.common.api.grid.Grid;
import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemContext;

import net.minecraft.server.level.ServerPlayer;

/**
 * 桥接类：放在 RS2 的 grid 包里，用来访问该包私有的类型。
 *
 * <p>方块版的样板终端持有方块实体上的 Grid，无线版没有方块，必须像 RS2 的
 * 无线网格那样基于 {@link NetworkItemContext} 造一个 Grid，否则菜单拿不到
 * 网络上的资源、也不能存取。</p>
 */
public final class Ez2rsGridBridge {
    private Ez2rsGridBridge() {
    }

    public static Grid createWirelessGrid(final NetworkItemContext context) {
        return new WirelessGrid(context);
    }

    /** {@code PendingGridUpdates} 是包私有的，外部只能在这里构造。 */
    public static Object newPendingGridUpdates(final ServerPlayer serverPlayer) {
        return new PendingGridUpdates(serverPlayer);
    }
}
