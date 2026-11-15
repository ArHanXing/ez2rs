package ar.hxi.ez2rs.mixin;

import ar.hxi.ez2rs.client.UniversalTerminalButtonHelper;

import com.refinedmods.refinedstorage.common.autocrafting.autocraftermanager.AutocrafterManagerScreen;
import com.refinedmods.refinedstorage.common.support.AbstractBaseScreen;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自动合成仓管理界面：补上"切换终端"侧边按钮。
 *
 * <p>网格 / 样板界面能自动拿到按钮是因为它们继承 {@code AbstractGridScreen}
 * （被 {@link AbstractGridScreenMixin} 注入），而 {@code AutocrafterManagerScreen}
 * 继承的是 {@code AbstractStretchingScreen}，所以照监视器的先例单独注入。</p>
 *
 * <p>必须写完整描述符 {@code init(I)V}：{@code AbstractStretchingScreen} 同时有
 * {@code init()} 与 {@code init(int)}，只写方法名会产生歧义。</p>
 *
 * <p>{@code remap = false}：{@code init(int)} 是 RS2 自己的方法（不是覆写 Minecraft 的方法），
 * 混淆后名字不变；若交给 mixin 重映射，"init" 会被当成 Minecraft 的
 * {@code Screen#init} 映射成 intermediary 名而找不到目标，注入失败。</p>
 *
 * <p>用 {@code addIfUniversalTerminal} 而不是监视器那条 {@code addIfCarryingUniversalTerminal}：
 * 本菜单确实设置了 {@code disabledSlot}，因此能精确解析到正在使用的那件物品。
 * 独立物品打开时不会显示按钮（helper 只对 {@code WirelessUniversalGridItem} 继续）。</p>
 */
@Mixin(AutocrafterManagerScreen.class)
public abstract class AutocrafterManagerScreenMixin {
    @Inject(method = "init(I)V", at = @At("TAIL"), remap = false)
    private void ez2rs$addUniversalTerminalButton(final int rows, final CallbackInfo ci) {
        final Screen self = (Screen) (Object) this;
        UniversalTerminalButtonHelper.addIfUniversalTerminal((AbstractBaseScreen<?>) self);
    }
}
