package ar.hxi.ez2rs.client.widget;

import ar.hxi.ez2rs.Ez2rs;
import ar.hxi.ez2rs.net.CycleUniversalTerminalPacket;
import ar.hxi.ez2rs.terminal.UniversalTerminalType;

import java.util.List;

import com.refinedmods.refinedstorage.common.support.widget.AbstractSideButtonWidget;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * 通用终端的切换按钮：显示当前终端图标，点击切到下一个终端。
 *
 * <p>悬停提示沿用 RS2 侧边按钮的两段式结构：标题 + 副文本（当前类型 / 点击切换）。</p>
 */
public class UniversalTerminalSideButtonWidget extends AbstractSideButtonWidget {
    private static final MutableComponent TITLE =
        Component.translatable("gui.ez2rs.universal_terminal.button");

    private final UniversalTerminalType type;
    private final int page;
    private final int totalPages;
    private final int slot;

    public UniversalTerminalSideButtonWidget(final UniversalTerminalType type,
                                             final int page,
                                             final int totalPages,
                                             final int slot) {
        super(button -> ClientPlayNetworking.send(new CycleUniversalTerminalPacket(slot)));
        this.type = type;
        this.page = page;
        this.totalPages = totalPages;
        this.slot = slot;
    }

    @Override
    protected ResourceLocation getSprite() {
        return ResourceLocation.fromNamespaceAndPath(Ez2rs.MOD_ID, type.getSpriteName());
    }

    @Override
    protected MutableComponent getTitle() {
        return TITLE;
    }

    @Override
    protected List<MutableComponent> getSubText() {
        if (totalPages <= 1) {
            return List.of(Component.translatable("gui.ez2rs.universal_terminal.current",
                type.getDisplayName()));
        }
        return List.of(
            Component.translatable("gui.ez2rs.universal_terminal.current", type.getDisplayName()),
            Component.translatable("gui.ez2rs.universal_terminal.page", page + 1, totalPages),
            Component.translatable("gui.ez2rs.universal_terminal.click_to_cycle")
        );
    }
}
