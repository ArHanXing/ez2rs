package ar.hxi.ez2rs.mixin;

import ar.hxi.ez2rs.client.UniversalTerminalButtonHelper;

import com.refinedmods.refinedstorage.common.autocrafting.monitor.AutocraftingMonitorScreen;
import com.refinedmods.refinedstorage.common.autocrafting.monitor.WirelessAutocraftingMonitorContainerMenu;
import com.refinedmods.refinedstorage.common.support.AbstractBaseScreen;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自动合成监视器界面：无线版本补上切换按钮。
 *
 * <p><b>必须按菜单类区分，不能只看"背包里有没有通用终端"。</b>监视器的方块版
 * （{@link com.refinedmods.refinedstorage.common.autocrafting.monitor.AutocraftingMonitorContainerMenu}）
 * 和无线版（{@link WirelessAutocraftingMonitorContainerMenu}）共用同一个界面类，但只有无线版
 * 可能是通用终端打开的。监视器菜单<b>没有玩家背包槽位</b>（{@code AbstractAutocraftingMonitorContainerMenu}
 * 从不调 {@code addPlayerInventory}），所以它也不会设置 {@code disabledSlot}，
 * 无法像网格那样靠槽位引用判定——只能用菜单类本身来分辨。</p>
 *
 * <p>方块版一定要排除：玩家背包里带着通用终端、走过去点方块上的监视器，那颗按钮没有任何意义，
 * 点下去还会把方块界面顶掉、换成通用终端的当前页。</p>
 */
@Mixin(AutocraftingMonitorScreen.class)
public abstract class AutocraftingMonitorScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void ez2rs$addUniversalTerminalButton(final CallbackInfo ci) {
        final Screen self = (Screen) (Object) this;
        final AbstractBaseScreen<?> screen = (AbstractBaseScreen<?>) self;
        if (!(screen.getMenu() instanceof WirelessAutocraftingMonitorContainerMenu)) {
            return;
        }
        UniversalTerminalButtonHelper.addIfCarryingUniversalTerminal(screen);
    }
}
