package ar.hxi.ez2rs.mixin;

import ar.hxi.ez2rs.terminal.patterngrid.GridAttachable;

import com.refinedmods.refinedstorage.common.api.grid.Grid;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import com.refinedmods.refinedstorage.common.grid.Ez2rsGridBridge;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让无线样板终端能把 Grid 挂到已经构造好的菜单上。
 *
 * <p>RS2 的 {@code PatternGridContainerMenu} 只暴露"用 GridData 构造"的路子，
 * 那条路是纯客户端快照：没有 watcher、没有插入/提取策略、也没有资源变化的批量下发队列，
 * 于是无线终端既看不到网络资源也不能存取、取完还会留虚影。</p>
 *
 * <p>{@code pendingUpdates} 的类型是包私有的，{@code @Shadow} 只能用 Object 承接、
 * 这会匹配不到字段（remap 报 "does not exist in any of the targets"）。
 * 所以改成在 {@code broadcastChanges} 里按需补建：那时字段已存在，用反射写不优雅，
 * 改用桥接类提供的构造入口，仅在为空时填充。</p>
 */
@Mixin(AbstractGridContainerMenu.class)
public abstract class AbstractGridContainerMenuMixin implements GridAttachable {
    @Shadow(remap = false)
    private Grid grid;

    @Shadow(remap = false)
    protected net.minecraft.world.entity.player.Inventory playerInventory;

    /** 父类的策略初始化（私有方法），挂完 Grid 后需要重跑一次。 */
    @Invoker(value = "initStrategies", remap = false)
    public abstract void ez2rs$initStrategies(ServerPlayer player);

    @Override
    public void ez2rs$attachGrid(final Grid newGrid, final ServerPlayer player) {
        this.grid = newGrid;
        newGrid.addWatcher((AbstractGridContainerMenu) (Object) this, PlayerActor.class);
        ez2rs$initStrategies(player);
    }

    /**
     * 资源变化的下发队列为空时补一个，否则服务端提取物品后不会通知客户端，
     * 界面上的物品会变成"虚影"一直留着。
     *
     * <p>注入点选在 {@code broadcastChanges} 的 HEAD：那里 {@code playerInventory}
     * 一定已经赋值，且每 tick 都会跑。</p>
     */
    @Inject(method = "broadcastChanges", at = @At("HEAD"), remap = false)
    private void ez2rs$ensurePendingUpdates(final CallbackInfo ci) {
        if (this.grid == null) {
            return;
        }
        Ez2rsGridBridge.ensurePendingUpdates(
            (AbstractGridContainerMenu) (Object) this,
            this.playerInventory.player
        );
    }
}
