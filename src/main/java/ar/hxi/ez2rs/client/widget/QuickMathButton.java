package ar.hxi.ez2rs.client.widget;

import ar.hxi.ez2rs.net.QuickMathPacket;
import ar.hxi.ez2rs.terminal.QuickMath;

import java.util.List;

import com.refinedmods.refinedstorage.common.support.widget.AbstractSideButtonWidget;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * 样板终端的「快速数学」按钮，外观完全沿用 RS2 的侧边按钮（18×18 底图 + 16×16 图标）。
 *
 * <p>图标是自己画的 16×16 单色字形（{@code ×2} / {@code ÷7} 这类），配色对齐 RS2 的图标墨色
 * {@code (58,58,58)}，这样和左边那一列原生侧边按钮看不出材质差异。
 * 参考模组的贴图是 GPLv3，这里没有复用。</p>
 *
 * <p>点击只发一个因子给服务端，本地不改任何数量：真正的上限夹取（物品=堆叠上限、
 * 流体=桶容量×64）只发生在服务端的 {@code ResourceContainerImpl#setAmount} 里，
 * 客户端抢先算一遍就会显示和服务端不一致的数字。</p>
 */
public class QuickMathButton extends AbstractSideButtonWidget {
    private static final MutableComponent HELP = Component.translatable("gui.ez2rs.quick_math.help");

    private final QuickMath math;

    public QuickMathButton(final QuickMath math) {
        super(button -> ClientPlayNetworking.send(new QuickMathPacket(math.factor())));
        this.math = math;
    }

    @Override
    protected ResourceLocation getSprite() {
        return math.sprite();
    }

    @Override
    protected MutableComponent getTitle() {
        return Component.translatable("gui.ez2rs.quick_math." + math.key());
    }

    @Override
    protected List<MutableComponent> getSubText() {
        return List.of(Component.translatable("gui.ez2rs.quick_math.desc"));
    }

    @Override
    protected Component getHelpText() {
        return HELP;
    }
}
