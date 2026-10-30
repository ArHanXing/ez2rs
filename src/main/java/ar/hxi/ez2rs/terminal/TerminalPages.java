package ar.hxi.ez2rs.terminal;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 通用终端已解锁的终端能力，按解锁先后排列（就是"页"）。
 *
 * <p>用不可变列表包装成 record，方便直接当数据组件的值；
 * 空列表表示还没合成过任何终端，右键不打开任何界面。</p>
 */
public record TerminalPages(List<UniversalTerminalType> pages) {
    public static final TerminalPages EMPTY = new TerminalPages(List.of());

    public static final Codec<TerminalPages> CODEC =
        UniversalTerminalType.CODEC.listOf().xmap(TerminalPages::new, TerminalPages::pages);
    public static final StreamCodec<ByteBuf, TerminalPages> STREAM_CODEC =
        UniversalTerminalType.STREAM_CODEC.apply(ByteBufCodecs.list())
            .map(TerminalPages::new, TerminalPages::pages);

    public TerminalPages {
        pages = List.copyOf(pages);
    }

    public boolean isEmpty() {
        return pages.isEmpty();
    }

    public int size() {
        return pages.size();
    }

    /** 追加一个能力；已经有了就原样返回（不重复加页）。 */
    public TerminalPages with(final UniversalTerminalType type) {
        if (pages.contains(type)) {
            return this;
        }
        final List<UniversalTerminalType> result = new ArrayList<>(pages);
        result.add(type);
        return new TerminalPages(result);
    }

    public UniversalTerminalType first() {
        return pages.getFirst();
    }

    /** 按当前页的下标取下一个下标，用于按钮循环。 */
    public int nextIndex(final int current) {
        if (pages.isEmpty()) {
            return 0;
        }
        return (current + 1) % pages.size();
    }
}
