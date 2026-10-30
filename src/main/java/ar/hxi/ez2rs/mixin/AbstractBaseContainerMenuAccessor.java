package ar.hxi.ez2rs.mixin;

import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;
import com.refinedmods.refinedstorage.common.support.AbstractBaseContainerMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取菜单里指向"正在使用的终端物品"的槽位引用（RS2 里是 protected 字段）。 */
@Mixin(AbstractBaseContainerMenu.class)
public interface AbstractBaseContainerMenuAccessor {
    @Accessor("disabledSlot")
    SlotReference ez2rs$getDisabledSlot();
}
