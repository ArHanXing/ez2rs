package ar.hxi.ez2rs.mixin;

import ar.hxi.ez2rs.terminal.patterngrid.GridAttachable;

import com.refinedmods.refinedstorage.common.api.grid.Grid;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 让无线样板终端能把 Grid 挂到已经构造好的菜单上。
 *
 * <p>RS2 的 {@code PatternGridContainerMenu} 只暴露"用 GridData 构造"的路子，
 * 那条路是纯客户端快照：没有 watcher、没有插入/提取策略，于是无线终端既看不到
 * 网络资源也不能存取。真正需要的是父类那个 protected 的"用 Grid 构造"，
 * 但样板网格菜单没有它。</p>
 *
 * <p>通过 {@link GridAttachable} 接口对外暴露，业务代码不直接引用本类。</p>
 */
@Mixin(AbstractGridContainerMenu.class)
public abstract class AbstractGridContainerMenuMixin implements GridAttachable {
    @Shadow(remap = false)
    private Grid grid;

    /** 父类的策略初始化（私有方法），挂完 Grid 后需要重跑一次。 */
    @Invoker(value = "initStrategies", remap = false)
    public abstract void ez2rs$initStrategies(ServerPlayer player);

    @Override
    public void ez2rs$attachGrid(final Grid newGrid, final ServerPlayer player) {
        this.grid = newGrid;
        newGrid.addWatcher((AbstractGridContainerMenu) (Object) this, PlayerActor.class);
        ez2rs$initStrategies(player);
    }
}
