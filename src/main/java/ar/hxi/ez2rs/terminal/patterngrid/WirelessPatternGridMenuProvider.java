package ar.hxi.ez2rs.terminal.patterngrid;

import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridData;
import com.refinedmods.refinedstorage.common.support.containermenu.ExtendedMenuProvider;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** 无线样板终端的菜单提供器：服务端造菜单、下发草稿数据。 */
public class WirelessPatternGridMenuProvider implements ExtendedMenuProvider<WirelessPatternGridData> {
    private final Component name;
    private final PatternGridData patternGridData;
    private final SlotReference slotReference;

    public WirelessPatternGridMenuProvider(final Component name,
                                           final PatternGridData patternGridData,
                                           final SlotReference slotReference) {
        this.name = name;
        this.patternGridData = patternGridData;
        this.slotReference = slotReference;
    }

    @Override
    public WirelessPatternGridData getMenuData() {
        return new WirelessPatternGridData(patternGridData, slotReference);
    }

    @Override
    public StreamEncoder<RegistryFriendlyByteBuf, WirelessPatternGridData> getMenuCodec() {
        return WirelessPatternGridData.STREAM_CODEC;
    }

    @Override
    public Component getDisplayName() {
        return name;
    }

    @Override
    public AbstractContainerMenu createMenu(final int syncId, final Inventory inventory, final Player player) {
        return new WirelessPatternGridContainerMenu(syncId, inventory, patternGridData, slotReference, true);
    }
}
