package com.refinedmods.refinedstorage.common.grid;

import com.refinedmods.refinedstorage.common.api.grid.Grid;
import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemContext;

import java.lang.reflect.Field;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

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

    /**
     * 给菜单补上资源变化的下发队列（{@code pendingUpdates}）。
     *
     * <p>那个字段是 private 且类型包私有：mixin 的 {@code @Shadow} 用 Object 声明匹配不到，
     * 同包类也读不到 private 字段。所以用反射按需填一次，仅在为空时执行。</p>
     */
    public static void ensurePendingUpdates(final AbstractGridContainerMenu menu, final Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        try {
            final Field field = AbstractGridContainerMenu.class.getDeclaredField("pendingUpdates");
            field.setAccessible(true);
            if (field.get(menu) != null) {
                return;
            }
            field.set(menu, new PendingGridUpdates(serverPlayer));
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to attach pending grid updates", e);
        }
    }
}
