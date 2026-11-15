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
 * 独立的「无线样板终端」。
 *
 * <p>与本物品合成可解锁通用终端的样板页。两者使用同一套原生样板界面与草稿格式，
 * 草稿内容分别保存在当前终端物品中。</p>
 */
public class WirelessPatternGridItem extends AbstractNetworkEnergyItem {
    public WirelessPatternGridItem() {
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
        PatternGridOpener.open(player, slotReference.resolve(player).orElse(ItemStack.EMPTY), slotReference);
    }
}
