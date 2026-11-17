package ar.hxi.ez2rs.net;

import ar.hxi.ez2rs.Ez2rs;
import ar.hxi.ez2rs.terminal.QuickMath;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.Ez2rsQuickMathBridge;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridContainerMenu;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 样板终端「快速数学」按钮的 C2S 包，只携带那一个因子（正=乘，负=除）。
 *
 * <p>刻意不做客户端预测：数量的真正归属在服务端的 {@code ResourceContainer} 里，
 * 乘法还会被该资源的处理样板上限夹住（物品=堆叠上限，流体=桶容量×64），
 * 客户端算一遍只会算出和服务端不一样的数。按钮点下去就当没发生，
 * 等 {@code handleResourceSlotUpdate} 把结果同步回来。</p>
 */
public record QuickMathPacket(int factor) implements CustomPacketPayload {
    public static final Type<QuickMathPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Ez2rs.MOD_ID, "quick_math")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, QuickMathPacket> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuickMathPacket::factor,
            QuickMathPacket::new
        );

    public static void register() {
        PayloadTypeRegistry.playC2S().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
            context.player().server.execute(() -> handle(context.player(), payload.factor())));
    }

    private static void handle(final ServerPlayer player, final int factor) {
        // 只认样板终端的菜单（方块样板终端与无线样板终端是同一个菜单类），
        // 且只认这 8 个因子，避免任意整数把数量算成天文数字。
        if (!QuickMath.isKnownFactor(factor)) {
            return;
        }
        if (player.containerMenu instanceof PatternGridContainerMenu menu) {
            Ez2rsQuickMathBridge.applyFactor(menu, factor);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
