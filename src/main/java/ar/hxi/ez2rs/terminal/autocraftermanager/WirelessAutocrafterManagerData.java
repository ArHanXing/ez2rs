package ar.hxi.ez2rs.terminal.autocraftermanager;

import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;
import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReferenceFactory;
import com.refinedmods.refinedstorage.common.autocrafting.autocraftermanager.AutocrafterManagerData;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 无线自动合成仓管理终端打开菜单时下发的数据：原生分组快照 + 终端物品所在槽位。
 *
 * <p>与 {@code WirelessPatternGridData} 逐字同构：保留显式编解码以清楚展示两部分的线格式，
 * 并必须由我们自己的菜单 {@code getType()} 选中 —— 否则 Fabric 会用父类
 * {@code AutocrafterManagerContainerMenu} 的数据 codec（只解 {@link AutocrafterManagerData}）
 * 而对本 record 抛类型转换异常。</p>
 */
public record WirelessAutocrafterManagerData(AutocrafterManagerData data, SlotReference slotReference) {
    public static final StreamCodec<RegistryFriendlyByteBuf, WirelessAutocrafterManagerData> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public WirelessAutocrafterManagerData decode(final RegistryFriendlyByteBuf buffer) {
                return new WirelessAutocrafterManagerData(
                    AutocrafterManagerData.STREAM_CODEC.decode(buffer),
                    SlotReferenceFactory.STREAM_CODEC.decode(buffer)
                );
            }

            @Override
            public void encode(final RegistryFriendlyByteBuf buffer, final WirelessAutocrafterManagerData value) {
                AutocrafterManagerData.STREAM_CODEC.encode(buffer, value.data());
                SlotReferenceFactory.STREAM_CODEC.encode(buffer, value.slotReference());
            }
        };
}
