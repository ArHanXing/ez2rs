package com.refinedmods.refinedstorage.common.autocrafting.autocraftermanager;

import com.refinedmods.refinedstorage.common.support.containermenu.DisabledSlot;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 同包普通类（不是 mixin）：给 {@link AutocrafterManagerContainerMenu} 的两个构造器
 * 一个稳定的、对外可用的入口。
 *
 * <p>为什么必须放在这个包：服务端构造器
 * {@code AutocrafterManagerContainerMenu(int, Inventory, AutocrafterManagerBlockEntity, List<Group>)}
 * 是包私有的（{@code AutocrafterManagerContainerMenu.java:59-74}），且它的参数类型
 * {@code AutocrafterManagerBlockEntity.Group} 也是包私有的 —— 只有同包类既能调用它、
 * 又能持有那份 {@code List<Group>}。本类不做任何额外注册：槽位顺序/数量与原生完全一致。</p>
 */
public abstract class Ez2rsNativeAutocrafterManagerMenu extends AutocrafterManagerContainerMenu {
    /** 客户端路径：原生 synchronized 数据；父类构造器自己会调一次 {@code resized(0,0,0)}。 */
    protected Ez2rsNativeAutocrafterManagerMenu(final int syncId,
                                                final Inventory inventory,
                                                final AutocrafterManagerData data) {
        super(syncId, inventory, data);
    }

    /**
     * 服务端路径：哑 BE + 捕获好的分组。{@code groups.list()} 只能在这里读 —— 元素类型是包私有 record。
     */
    protected Ez2rsNativeAutocrafterManagerMenu(final int syncId,
                                                final Inventory inventory,
                                                final AutocrafterManagerBlockEntity autocrafterManager,
                                                final Ez2rsAutocrafterManagerBridge.Groups groups) {
        super(syncId, inventory, autocrafterManager, groups.list());
    }

    /**
     * 服务端把"手持终端"占用的背包槽位替换成 {@link DisabledSlot}。
     *
     * <p>服务端不能走 {@code resized(...)}（那会用原生恒空的 {@code groups} 字段重建槽位、
     * 把样板槽全丢掉），所以这里做同位替换：{@code DisabledSlot} 的构造器只把
     * {@code container/index/x/y} 原样传给 {@code Slot}，因此槽位编号、数量、顺序与坐标全部不变，
     * 原版按位置比对的 {@code lastSlots/remoteSlots} 同步不会错位。</p>
     *
     * <p>匹配与重建都用 {@link Slot#getContainerSlot()}（容器内编号，语义明确），
     * 并把原槽位的 {@code index} 字段原样抄回，使替换结果与原来的 {@code PlayerInventorySlot}
     * 在每个公开字段上都完全一致。1.21.1 mojmap 已确认 {@code AbstractContainerMenu.slots}
     * 是 public {@code List<Slot>}，{@code Slot#container/getContainerSlot/x/y} 均 public。</p>
     */
    public final void ez2rs$disableInventorySlot(final Inventory inventory, final int playerSlotIndex) {
        for (int i = 0; i < slots.size(); i++) {
            final Slot slot = slots.get(i);
            if (slot.container != inventory || slot.getContainerSlot() != playerSlotIndex) {
                continue;
            }
            final DisabledSlot replacement = new DisabledSlot(inventory, slot.getContainerSlot(), slot.x, slot.y);
            // 保持与原生 addSlot 之后的字段状态一致（不同版本 index 语义不同，照抄最安全）。
            replacement.index = slot.index;
            slots.set(i, replacement);
            return;
        }
    }
}
