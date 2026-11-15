package ar.hxi.ez2rs.terminal;

import java.util.List;

import io.netty.buffer.ByteBuf;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * 通用终端可以承载的终端类型。
 *
 * <p>每一种都对应一个无线终端物品；解锁配方用该物品与通用终端合成。</p>
 */
public enum UniversalTerminalType implements StringRepresentable {
    GRID("grid"),
    CRAFTING_GRID("crafting_grid"),
    AUTOCRAFTING_MONITOR("autocrafting_monitor"),
    PATTERN_GRID("pattern_grid"),
    AUTOCRAFTER_MANAGER("autocrafter_manager");

    public static final Codec<UniversalTerminalType> CODEC =
        StringRepresentable.fromEnum(UniversalTerminalType::values);
    public static final StreamCodec<ByteBuf, UniversalTerminalType> STREAM_CODEC =
        ByteBufCodecs.STRING_UTF8.map(UniversalTerminalType::byName, UniversalTerminalType::getSerializedName);

    private final String name;

    UniversalTerminalType(final String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Component getDisplayName() {
        return Component.translatable("gui.ez2rs.universal_terminal.type." + name);
    }

    /** 左侧按钮与提示用的图标精灵。 */
    public String getSpriteName() {
        return "terminal/" + name;
    }

    public static UniversalTerminalType byName(final String name) {
        for (final UniversalTerminalType type : values()) {
            if (type.name.equals(name)) {
                return type;
            }
        }
        return GRID;
    }

    /** 同步用的固定枚举顺序，用于把列表编成下标。 */
    public static List<UniversalTerminalType> all() {
        return List.of(values());
    }
}
