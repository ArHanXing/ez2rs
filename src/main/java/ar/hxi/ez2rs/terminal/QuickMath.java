package ar.hxi.ez2rs.terminal;

import ar.hxi.ez2rs.Ez2rs;

import net.minecraft.resources.ResourceLocation;

/**
 * 样板终端的「快速数学」因子：×2 / ×3 / ×5 / ×7 与 ÷2 / ÷3 / ÷5 / ÷7。
 *
 * <p>只作用于 RS2 里<b>真的有数量</b>的那一部分内容。RS2 的样板类型里，合成 / 切石 / 锻造
 * 的原料数量在样板里恒为 1（{@code PatternResolver} 对它们硬编码 {@code new Ingredient(1, ...)}），
 * 缩放矩阵里的物品数量不会改变生成的样板，所以那三种类型没有可缩放的东西；
 * 只有处理样板（PROCESSING）的输入 / 输出是 {@code ResourceContainer} 里的真实数量。
 * 因此本功能的按钮只会在处理样板模式下出现。</p>
 *
 * <p>与参考模组（Pattern-Encoding-Terminal-Quick-Math）的语义差别是刻意的：
 * 参考模组在「除不尽」时直接放弃整次操作，这里改成<b>四舍五入</b>，并且至少保留 1，
 * 避免玩家一路除下去把整行原料除没了。乘法结果由 {@code ResourceContainerImpl.setAmount}
 * 夹到该资源的处理样板上限（物品=堆叠上限，流体=桶容量×64），不做溢出放弃。</p>
 */
public enum QuickMath {
    MULTIPLY_2(2),
    MULTIPLY_3(3),
    MULTIPLY_5(5),
    MULTIPLY_7(7),
    DIVIDE_2(-2),
    DIVIDE_3(-3),
    DIVIDE_5(-5),
    DIVIDE_7(-7);

    private final int factor;

    QuickMath(final int factor) {
        this.factor = factor;
    }

    /** 正数=乘，负数=除（除数的相反数）。也就是网络上传输的那一个整数。 */
    public int factor() {
        return factor;
    }

    public boolean multiply() {
        return factor > 0;
    }

    /** 2 / 3 / 5 / 7。 */
    public int value() {
        return Math.abs(factor);
    }

    /** {@code multiply_2} / {@code divide_2}：贴图文件名与语言键共用这一个字符串。 */
    public String key() {
        return (multiply() ? "multiply_" : "divide_") + value();
    }

    /** {@code ez2rs:quick_math/multiply_2} 这类 GUI sprite。 */
    public ResourceLocation sprite() {
        return ResourceLocation.fromNamespaceAndPath(Ez2rs.MOD_ID, "quick_math/" + key());
    }

    /** 只接受这 8 个因子，避免客户端塞任意整数把数量算爆。 */
    public static boolean isKnownFactor(final int factor) {
        for (final QuickMath math : values()) {
            if (math.factor == factor) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按因子缩放一个数量。
     *
     * <p>乘法做饱和处理（真正的上限由容器自己夹）；除法四舍五入且下限为 1，
     * 这样 ÷7 对数量 1 就是「除不动」而不是把这一格抹掉。</p>
     */
    public static long scale(final long amount, final int factor) {
        if (amount <= 0 || factor == 0) {
            return amount;
        }
        if (factor > 0) {
            return amount > Long.MAX_VALUE / factor ? Long.MAX_VALUE : amount * factor;
        }
        final long divisor = -factor;
        return Math.max(1L, (amount + divisor / 2L) / divisor);
    }
}
