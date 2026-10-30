package ar.hxi.ez2rs.terminal.patterngrid;

import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;
import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReferenceFactory;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridData;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 无线样板终端打开菜单时下发的数据：样板网格数据 + 终端物品所在槽位。
 *
 * <p>手写编解码而不是用 {@code StreamCodec.composite}：composite 在泛型擦除后
 * 会把组合中的 {@link PatternGridData} 当作最终类型去强转，导致发包时
 * "WirelessPatternGridData cannot be cast to PatternGridData"。</p>
 */
public record WirelessPatternGridData(PatternGridData patternGridData, SlotReference slotReference) {
    public static final StreamCodec<RegistryFriendlyByteBuf, WirelessPatternGridData> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public WirelessPatternGridData decode(final RegistryFriendlyByteBuf buffer) {
                return new WirelessPatternGridData(
                    PatternGridData.STREAM_CODEC.decode(buffer),
                    SlotReferenceFactory.STREAM_CODEC.decode(buffer)
                );
            }

            @Override
            public void encode(final RegistryFriendlyByteBuf buffer, final WirelessPatternGridData value) {
                PatternGridData.STREAM_CODEC.encode(buffer, value.patternGridData());
                SlotReferenceFactory.STREAM_CODEC.encode(buffer, value.slotReference());
            }
        };
}
