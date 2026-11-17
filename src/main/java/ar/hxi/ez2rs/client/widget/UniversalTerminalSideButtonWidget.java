package ar.hxi.ez2rs.client.widget;

import ar.hxi.ez2rs.Ez2rs;
import ar.hxi.ez2rs.net.CycleUniversalTerminalPacket;
import ar.hxi.ez2rs.terminal.Ez2rsTerminalContent;
import ar.hxi.ez2rs.terminal.TerminalPages;
import ar.hxi.ez2rs.terminal.UniversalTerminalType;
import ar.hxi.ez2rs.terminal.WirelessUniversalGridItem;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.refinedmods.refinedstorage.common.support.widget.AbstractSideButtonWidget;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 通用终端的切换按钮：显示当前终端图标，点击切到下一个终端。
 *
 * <p><b>为什么图标与文字必须每次重新解析，不能在构造时快照。</b>点击按钮后服务端改的是
 * <b>玩家背包里那件物品</b>的 {@code CURRENT_PAGE} 组件，改完才 {@code openMenu}。新界面在客户端的
 * {@code init()} 里构造这个按钮，而此刻背包内容（{@code ClientboundContainerSetContentPacket}）
 * 还没到 —— 构造时读到的是<b>旧页码</b>。快照下来就永远停在旧值：图标与「当前：…」都在说谎，
 * 界面看上去"没切过去"。合成仓管理终端最明显：它的界面初始化比其它终端晚，
 * 但构造按钮那一刻背包仍可能没同步完。</p>
 *
 * <p>所以这里只保存<b>槽位号</b>，{@link #getSprite()} 与 {@link #getSubText()} 每次被调用时都
 * 从玩家背包重新解析。RS2 的 {@code AbstractSideButtonWidget} 恰好每帧调一次 {@code getSprite()}、
 * 悬停时调一次 {@code getSubText()}，所以内容包一到、下一帧就自动正确，不需要重建按钮。</p>
 *
 * <p>构造时传进来的 {@code type} / {@code page} / {@code totalPages} 退化成<b>兜底值</b>：
 * 玩家中途丢掉了物品、或组件暂时读不到时，按钮显示最后一次已知状态，而不是崩掉或变空白。</p>
 */
public class UniversalTerminalSideButtonWidget extends AbstractSideButtonWidget {
    private static final MutableComponent TITLE =
        Component.translatable("gui.ez2rs.universal_terminal.button");

    /** 精灵 id 只跟枚举有关，预建一次，避免每帧渲染都新建 {@code ResourceLocation}。 */
    private static final Map<UniversalTerminalType, ResourceLocation> SPRITES =
        buildSprites();

    private final int slot;
    private final UniversalTerminalType fallbackType;
    private final int fallbackPage;
    private final int fallbackTotalPages;

    public UniversalTerminalSideButtonWidget(final UniversalTerminalType type,
                                             final int page,
                                             final int totalPages,
                                             final int slot) {
        super(button -> ClientPlayNetworking.send(new CycleUniversalTerminalPacket(slot)));
        this.fallbackType = type;
        this.fallbackPage = page;
        this.fallbackTotalPages = totalPages;
        this.slot = slot;
    }

    private static Map<UniversalTerminalType, ResourceLocation> buildSprites() {
        final Map<UniversalTerminalType, ResourceLocation> sprites =
            new EnumMap<>(UniversalTerminalType.class);
        for (final UniversalTerminalType type : UniversalTerminalType.values()) {
            sprites.put(type, ResourceLocation.fromNamespaceAndPath(Ez2rs.MOD_ID, type.getSpriteName()));
        }
        return sprites;
    }

    /**
     * 每次调用都从玩家背包重新读那件通用终端。
     *
     * @return 仍是通用终端就返回它；槽位越界、物品已换掉、玩家不在世界里都返回 {@code null}
     */
    private ItemStack resolveStack() {
        final Player player = Minecraft.getInstance().player;
        if (player == null || this.slot < 0 || this.slot >= player.getInventory().getContainerSize()) {
            return null;
        }
        final ItemStack stack = player.getInventory().getItem(this.slot);
        return stack.getItem() instanceof WirelessUniversalGridItem ? stack : null;
    }

    @Override
    protected ResourceLocation getSprite() {
        final ItemStack stack = this.resolveStack();
        final UniversalTerminalType type = stack == null
            ? this.fallbackType
            : Ez2rsTerminalContent.getCurrentType(stack);
        return SPRITES.get(type == null ? this.fallbackType : type);
    }

    @Override
    protected MutableComponent getTitle() {
        return TITLE;
    }

    @Override
    protected List<MutableComponent> getSubText() {
        final ItemStack stack = this.resolveStack();
        // 一次调用只解析一次，避免「当前页」与「第 N/M 页」在两次读之间漂移。
        final UniversalTerminalType type;
        final int page;
        final int totalPages;
        if (stack == null) {
            type = this.fallbackType;
            page = this.fallbackPage;
            totalPages = this.fallbackTotalPages;
        } else {
            final TerminalPages pages = Ez2rsTerminalContent.getPages(stack);
            final UniversalTerminalType resolved = Ez2rsTerminalContent.getCurrentType(stack);
            type = resolved == null ? this.fallbackType : resolved;
            page = pages.isEmpty() ? this.fallbackPage : Ez2rsTerminalContent.getCurrentPage(stack);
            totalPages = pages.isEmpty() ? this.fallbackTotalPages : pages.size();
        }
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
