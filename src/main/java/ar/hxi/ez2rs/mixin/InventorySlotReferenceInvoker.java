package ar.hxi.ez2rs.mixin;

import com.refinedmods.refinedstorage.common.support.slotreference.InventorySlotReference;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * RS2 的 {@link InventorySlotReference} 构造器是包私有的，而菜单数据序列化时会把槽位引用
 * 强转成这个具体类型（用自定义实现会 ClassCastException）。
 * 这里把构造器暴露出来，用背包槽位号造一个合法的引用。
 */
@Mixin(InventorySlotReference.class)
public interface InventorySlotReferenceInvoker {
    @Invoker("<init>")
    static InventorySlotReference ez2rs$create(final int slotIndex) {
        throw new AssertionError();
    }
}
