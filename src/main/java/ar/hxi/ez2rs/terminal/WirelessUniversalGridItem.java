package ar.hxi.ez2rs.terminal;

import java.util.List;

import com.refinedmods.refinedstorage.api.network.energy.EnergyStorage;
import com.refinedmods.refinedstorage.api.network.impl.energy.EnergyStorageImpl;
import com.refinedmods.refinedstorage.common.Platform;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.energy.AbstractNetworkEnergyItem;
import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemContext;
import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;
import com.refinedmods.refinedstorage.common.content.Items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 通用终端：一个物品，按自身已解锁的"页"切换打开哪种无线终端。
 *
 * <p>刚合成出来是空终端（没有任何页），右键不打开任何界面；需要用对应的无线终端与它合成来解锁。
 * 打开时把动作委托给 RS2 / 石英兵工厂自己的无线物品，因此权限检查、网络绑定、菜单与界面
 * 全部复用它们的实现，行为与单独使用那些终端完全一致。</p>
 */
public class WirelessUniversalGridItem extends AbstractNetworkEnergyItem {
    public WirelessUniversalGridItem() {
        super(
            new Item.Properties().stacksTo(1),
            RefinedStorageApi.INSTANCE.getEnergyItemHelper(),
            RefinedStorageApi.INSTANCE.getNetworkItemHelper()
        );
    }

    public EnergyStorage createEnergyStorage(final ItemStack stack) {
        return RefinedStorageApi.INSTANCE.asItemEnergyStorage(
            new EnergyStorageImpl(Platform.INSTANCE.getConfig().getWirelessGrid().getEnergyCapacity()),
            stack
        );
    }

    @Override
    protected void use(final Component name,
                       final ServerPlayer player,
                       final SlotReference slotReference,
                       final NetworkItemContext context) {
        open(player, slotReference.resolve(player).orElse(ItemStack.EMPTY), slotReference);
    }

    /** 按当前页打开对应终端；空终端什么也不做。 */
    public static void open(final ServerPlayer player,
                            final ItemStack stack,
                            final SlotReference slotReference) {
        final UniversalTerminalType type = Ez2rsTerminalContent.getCurrentType(stack);
        if (type == null) {
            return;
        }
        useDelegate(type, player, stack, slotReference);
    }

    /**
     * 打开无线样板终端。
     *
     * <p>不走 RS2 的 {@code MenuOpener}：它把 provider 当作 {@code ExtendedMenuProvider<?>} 处理，
     * 泛型擦除后会把我们的数据按 {@code PatternGridData} 编码，发包时直接 ClassCastException。
     * 这里自己用 {@code ExtendedScreenHandlerFactory} 明确指定数据类型。</p>
     */
    private static void openPatternGrid(final ServerPlayer player,
                                        final ItemStack stack,
                                        final SlotReference slotReference) {
        final com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridData data =
            ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGrid.empty();
        player.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<
            ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridData>() {
            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return stack.getHoverName();
            }

            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                final int syncId,
                final net.minecraft.world.entity.player.Inventory inventory,
                final net.minecraft.world.entity.player.Player p) {
                return new ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridContainerMenu(
                    syncId,
                    inventory,
                    data,
                    slotReference,
                    true
                );
            }

            @Override
            public ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridData getScreenOpeningData(
                final ServerPlayer serverPlayer) {
                return new ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridData(data, slotReference);
            }
        });
    }

    private static void useDelegate(final UniversalTerminalType type,
                                    final ServerPlayer player,
                                    final ItemStack stack,
                                    final SlotReference slotReference) {
        switch (type) {
            case GRID -> Items.INSTANCE.getWirelessGrid().use(player, stack, slotReference);
            case CRAFTING_GRID -> com.refinedmods.refinedstorage.quartzarsenal.common.Items.INSTANCE
                .getWirelessCraftingGrid()
                .use(player, stack, slotReference);
            case AUTOCRAFTING_MONITOR -> Items.INSTANCE.getWirelessAutocraftingMonitor()
                .use(player, stack, slotReference);
            case PATTERN_GRID -> openPatternGrid(player, stack, slotReference);
        }
    }

    @Override
    public void appendHoverText(final ItemStack stack,
                                final TooltipContext context,
                                final List<Component> lines,
                                final TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        final TerminalPages pages = Ez2rsTerminalContent.getPages(stack);
        if (pages.isEmpty()) {
            lines.add(Component.translatable("tooltip.ez2rs.universal_terminal.empty")
                .withStyle(ChatFormatting.GRAY));
            return;
        }
        final int current = Ez2rsTerminalContent.getCurrentPage(stack);
        for (int i = 0; i < pages.pages().size(); i++) {
            final Component line = Component.literal((i + 1) + ". ")
                .append(pages.pages().get(i).getDisplayName());
            lines.add(i == current
                ? line.copy().withStyle(ChatFormatting.WHITE)
                : line.copy().withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
