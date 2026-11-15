package ar.hxi.ez2rs.terminal;

import ar.hxi.ez2rs.terminal.autocraftermanager.WirelessAutocrafterManagerContainerMenu;
import ar.hxi.ez2rs.terminal.autocraftermanager.WirelessAutocrafterManagerData;
import ar.hxi.ez2rs.terminal.autocraftermanager.WirelessAutocrafterManagerState;

import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemContext;
import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 打开无线自动合成仓管理终端：解析一次 context、捕获一次 {@code Groups}，
 * 然后同一条 state 同时提供开场数据与服务端菜单。
 *
 * <p><b>只捕获一次</b>：{@code getScreenOpeningData()} 与 {@code createMenu()} 读的是同一个
 * {@code state.groups()}，绝不各自重算 —— Fabric 两个回调的调用顺序不作保证，
 * 重算会让两边的槽位数量/顺序在中间发生变动时漂移。</p>
 *
 * <p>进场<b>不</b>要求网络可达（否则离线就拿不到布局，违反需求 3）：网络不可达时
 * {@code Groups} 为空，界面照常打开、只显示玩家背包，与方块版"网络上没有自动合成仓"一致。</p>
 */
public final class AutocrafterManagerOpener {
    private AutocrafterManagerOpener() {
    }

    public static void open(final ServerPlayer player, final ItemStack stack, final SlotReference reference) {
        if (reference.resolve(player).orElse(ItemStack.EMPTY) != stack
            || !Ez2rsTerminalContent.isAutocrafterManagerCapable(stack)) return;
        final NetworkItemContext context = RefinedStorageApi.INSTANCE.getNetworkItemHelper()
            .createContext(stack, player, reference);
        // state 构造期只查询一次网络并捕获 Groups；不订阅 watcher、不 drainEnergy。
        final WirelessAutocrafterManagerState state =
            new WirelessAutocrafterManagerState(context, player, stack, reference);
        player.openMenu(new ExtendedScreenHandlerFactory<WirelessAutocrafterManagerData>() {
            @Override
            public Component getDisplayName() { return stack.getHoverName(); }
            @Override
            public AbstractContainerMenu createMenu(final int id, final Inventory inventory, final Player p) {
                return new WirelessAutocrafterManagerContainerMenu(id, inventory, state, reference);
            }
            @Override
            public WirelessAutocrafterManagerData getScreenOpeningData(final ServerPlayer p) {
                // active 恒为 true（见 Groups#data() 的注释），绝不传 mainNetworkNode.isActive()。
                return new WirelessAutocrafterManagerData(state.menuData(), reference);
            }
        });
    }
}
