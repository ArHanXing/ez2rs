package ar.hxi.ez2rs.mixin;

import ar.hxi.ez2rs.client.widget.QuickMathButton;
import ar.hxi.ez2rs.terminal.QuickMath;

import java.util.ArrayList;
import java.util.List;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.Ez2rsQuickMathBridge;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridScreen;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.support.AbstractBaseScreen;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 样板终端（方块版与无线版共用同一个界面类）右侧的「快速数学」按钮列。
 *
 * <p>为什么放面板<b>外面</b>的右侧：{@code PatternGridScreen} 内部已经没有 8 个按钮的位置了——
 * 底部 inset 占满 x+8..x+146，右列 x+172 从 y+76 往下被 4 个样板类型按钮占掉，再往下是玩家背包；
 * 左列已经被 RS2 自己的侧边按钮（视图 / 资源类型 / 排序 / 同步 / 尺寸…）占满。
 * 所以照 RS2 侧边按钮的样式在面板右边缘外加一列：与左列对称、不与任何原生控件重叠，
 * 而且两个终端共用这个界面类，方块版和无线版一次就能同时拥有。</p>
 *
 * <p>按钮只在处理样板模式下显示：合成 / 切石 / 锻造的原料数量在 RS2 里恒为 1
 * （{@code PatternResolver} 对它们硬编码 {@code Ingredient(1, ...)}），没有可缩放的东西。
 * 可见性在 {@code init} 与 {@code patternTypeChanged} 里对齐；样板类型本身是同步属性，
 * 界面打开时它的包可能比 {@code setListener} 先到，那一刻回调不会触发，
 * 所以 {@code containerTick} 每 tick 再对齐一次（只做一次布尔比较）。</p>
 *
 * <p>加控件走 Fabric 的 {@link Screens#getButtons}（公开 API，内部就是对 renderables /
 * narratables / children 三个列表的同一套增删），避免 {@code @Shadow} 私有列表；
 * 坐标用 {@code AbstractBaseScreen} 的两个公开 getter，不需要影子字段。</p>
 */
@Mixin(PatternGridScreen.class)
public abstract class PatternGridScreenQuickMathMixin {
    /** 面板宽 193（{@code PatternGridScreen} 构造函数里写死），按钮列贴着右边缘外 2px。 */
    @Unique
    private static final int EZ2RS_QUICK_MATH_X_OFFSET = 193 + 2;

    /** 与 {@code AbstractBaseScreen#getSideButtonY()} 同一个起始高度，左右两列对齐。 */
    @Unique
    private static final int EZ2RS_QUICK_MATH_Y_OFFSET = 6;

    @Unique
    private static final int EZ2RS_QUICK_MATH_STEP = 20;

    @Unique
    private List<QuickMathButton> ez2rs$quickMathButtons;

    /** 已经登记进 {@code exclusionZones} 的区域，用于按可见性整批增删。 */
    @Unique
    private List<Rect2i> ez2rs$quickMathZones;

    /** 上一次应用的可见性，避免每 tick 无谓地改排除区列表。 */
    @Unique
    private boolean ez2rs$quickMathVisible;

    @Inject(method = "init()V", at = @At("TAIL"))
    private void ez2rs$addQuickMathButtons(final CallbackInfo ci) {
        final Screen self = (Screen) (Object) this;
        if (!(self instanceof AbstractBaseScreen<?> baseScreen)) {
            return;
        }
        final List<QuickMathButton> buttons = new ArrayList<>(QuickMath.values().length);
        final List<Rect2i> zones = new ArrayList<>(QuickMath.values().length);
        final QuickMath[] all = QuickMath.values();
        for (int i = 0; i < all.length; ++i) {
            final QuickMathButton button = new QuickMathButton(all[i]);
            button.setX(baseScreen.getLeftPos() + EZ2RS_QUICK_MATH_X_OFFSET);
            button.setY(baseScreen.getTopPos() + EZ2RS_QUICK_MATH_Y_OFFSET + (i * EZ2RS_QUICK_MATH_STEP));
            button.visible = false;
            buttons.add(button);
            zones.add(new Rect2i(button.getX(), button.getY(), button.getWidth(), button.getHeight()));
            Screens.getButtons(self).add(button);
        }
        this.ez2rs$quickMathButtons = buttons;
        this.ez2rs$quickMathZones = zones;
        this.ez2rs$quickMathVisible = false;
        ez2rs$updateQuickMathVisibility();
    }

    @Inject(method = "patternTypeChanged", at = @At("TAIL"), remap = false)
    private void ez2rs$onPatternTypeChanged(final PatternType newPatternType, final CallbackInfo ci) {
        ez2rs$updateQuickMathVisibility();
    }

    @Inject(method = "containerTick()V", at = @At("TAIL"))
    private void ez2rs$tickQuickMathButtons(final CallbackInfo ci) {
        ez2rs$updateQuickMathVisibility();
    }

    @Unique
    private void ez2rs$updateQuickMathVisibility() {
        if (ez2rs$quickMathButtons == null) {
            return;
        }
        // getMenu() 在 PatternGridScreen 上的泛型已经是 PatternGridContainerMenu。
        final boolean processing = Ez2rsQuickMathBridge.isProcessing(
            ((PatternGridScreen) (Object) this).getMenu()
        );
        if (processing == ez2rs$quickMathVisible) {
            return;
        }
        ez2rs$quickMathVisible = processing;
        for (final QuickMathButton button : ez2rs$quickMathButtons) {
            button.visible = processing;
        }
        // EMI 与 RS2 自己的界面都靠这张表避开终端控件：显示时才占位，隐藏时让出来，
        // 免得没按钮的时候也把配方面板挤到更外面。
        final List<Rect2i> zones = ((AbstractBaseScreen<?>) (Object) this).getExclusionZones();
        zones.removeAll(ez2rs$quickMathZones);
        if (processing) {
            zones.addAll(ez2rs$quickMathZones);
        }
    }
}
