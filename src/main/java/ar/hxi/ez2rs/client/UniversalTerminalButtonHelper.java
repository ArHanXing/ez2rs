package ar.hxi.ez2rs.client;

import ar.hxi.ez2rs.client.widget.UniversalTerminalSideButtonWidget;
import ar.hxi.ez2rs.mixin.AbstractBaseContainerMenuAccessor;
import ar.hxi.ez2rs.terminal.Ez2rsTerminalContent;
import ar.hxi.ez2rs.terminal.TerminalPages;
import ar.hxi.ez2rs.terminal.WirelessUniversalGridItem;

import java.util.Optional;

import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;
import com.refinedmods.refinedstorage.common.support.AbstractBaseScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 在无线终端界面左侧按钮栏加上"切换终端"按钮。
 *
 * <p>网格 / 合成终端：菜单的 disabled slot 就指向正在使用的物品，判定精确。
 * 无线监视器：RS2 的菜单不记录槽位，退化成"背包里带通用终端就显示按钮"。</p>
 */
public final class UniversalTerminalButtonHelper {
    private UniversalTerminalButtonHelper() {
    }

    public static void addIfUniversalTerminal(final AbstractBaseScreen<?> screen) {
        final Player player = Minecraft.getInstance().player;
        if (player == null || !(screen.getMenu() instanceof AbstractBaseContainerMenuAccessor accessor)) {
            return;
        }
        final SlotReference slotReference = accessor.ez2rs$getDisabledSlot();
        if (slotReference == null) {
            return;
        }
        final ItemStack stack = slotReference.resolve(player).orElse(ItemStack.EMPTY);
        addButtonFor(screen, stack);
    }

    public static void addIfCarryingUniversalTerminal(final AbstractBaseScreen<?> screen) {
        final Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        findUniversalTerminal(player).ifPresent(slot ->
            addButtonFor(screen, player.getInventory().getItem(slot)));
    }

    private static void addButtonFor(final AbstractBaseScreen<?> screen, final ItemStack stack) {
        if (!(stack.getItem() instanceof WirelessUniversalGridItem)) {
            return;
        }
        final TerminalPages pages = Ez2rsTerminalContent.getPages(stack);
        if (pages.isEmpty()) {
            return;
        }
        findUniversalTerminalSlot(screen).ifPresent(slot -> screen.addSideButton(
            new UniversalTerminalSideButtonWidget(
                pages.pages().get(Ez2rsTerminalContent.getCurrentPage(stack)),
                Ez2rsTerminalContent.getCurrentPage(stack),
                pages.pages().size(),
                slot
            )
        ));
    }

    /** 找通用终端所在的背包槽位：先看菜单槽位引用，再退回扫描背包。 */
    public static Optional<Integer> findUniversalTerminalSlot(final AbstractBaseScreen<?> screen) {
        final Player player = Minecraft.getInstance().player;
        if (player == null) {
            return Optional.empty();
        }
        if (screen.getMenu() instanceof AbstractBaseContainerMenuAccessor accessor) {
            final SlotReference slotReference = accessor.ez2rs$getDisabledSlot();
            if (slotReference != null) {
                final Optional<ItemStack> stack = slotReference.resolve(player);
                if (stack.isPresent() && stack.get().getItem() instanceof WirelessUniversalGridItem) {
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        if (slotReference.isDisabledSlot(i)) {
                            return Optional.of(i);
                        }
                    }
                }
            }
        }
        return findUniversalTerminal(player);
    }

    private static Optional<Integer> findUniversalTerminal(final Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof WirelessUniversalGridItem) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }
}
