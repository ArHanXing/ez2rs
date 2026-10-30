package ar.hxi.ez2rs.terminal.patterngrid;

import com.refinedmods.refinedstorage.common.api.grid.Grid;

import net.minecraft.server.level.ServerPlayer;

/**
 * 由 {@code AbstractGridContainerMenuMixin} 实现，用来在服务端给无线菜单挂上 Grid。
 *
 * <p>必须走接口：mixin 类不能在普通代码里直接引用（Mixin 会报
 * "Illegal classload request ... cannot be referenced directly"）。</p>
 */
public interface GridAttachable {
    void ez2rs$attachGrid(Grid grid, ServerPlayer player);
}
