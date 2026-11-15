package ar.hxi.ez2rs.terminal;

import com.refinedmods.refinedstorage.api.network.energy.EnergyStorage;
import com.refinedmods.refinedstorage.api.network.impl.energy.EnergyStorageImpl;
import com.refinedmods.refinedstorage.common.Platform;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.energy.AbstractNetworkEnergyItem;
import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemContext;
import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 独立的「无线自动合成仓管理终端」。
 *
 * <p>与本物品合成可解锁通用终端的自动合成仓管理页。右键打开的是 RS2 原生
 * 自动合成仓管理界面（复用原生菜单/界面/槽位），只是把"分组"改为从物品绑定的网络捕获。</p>
 *
 * <p>能量行为与 {@code WirelessPatternGridItem} 一致：独立物品用自己内部的电池
 * （本项目现有的网络取电 mixin 只认通用终端，不要为此扩展 mixin）。</p>
 */
public class WirelessAutocrafterManagerItem extends AbstractNetworkEnergyItem {
    public WirelessAutocrafterManagerItem() {
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
        AutocrafterManagerOpener.open(player, slotReference.resolve(player).orElse(ItemStack.EMPTY), slotReference);
    }
}
