package ar.hxi.ez2rs.mixin;

import ar.hxi.ez2rs.client.UniversalTerminalButtonHelper;

import com.refinedmods.refinedstorage.common.autocrafting.monitor.AutocraftingMonitorScreen;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 自动合成监视器界面：同样补上切换按钮。 */
@Mixin(AutocraftingMonitorScreen.class)
public abstract class AutocraftingMonitorScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void ez2rs$addUniversalTerminalButton(final CallbackInfo ci) {
        final Screen self = (Screen) (Object) this;
        UniversalTerminalButtonHelper.addIfCarryingUniversalTerminal(
            (com.refinedmods.refinedstorage.common.support.AbstractBaseScreen<?>) self
        );
    }
}
