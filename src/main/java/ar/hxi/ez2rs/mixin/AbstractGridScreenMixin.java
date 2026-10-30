package ar.hxi.ez2rs.mixin;

import ar.hxi.ez2rs.client.UniversalTerminalButtonHelper;

import com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen;
import com.refinedmods.refinedstorage.common.support.AbstractBaseScreen;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 网格终端 / 合成终端界面：初始化末尾补上通用终端的切换按钮。 */
@Mixin(AbstractGridScreen.class)
public abstract class AbstractGridScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void ez2rs$addUniversalTerminalButton(final CallbackInfo ci) {
        final Screen self = (Screen) (Object) this;
        UniversalTerminalButtonHelper.addIfUniversalTerminal((AbstractBaseScreen<?>) self);
    }
}
