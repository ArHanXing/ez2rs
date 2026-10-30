package ar.hxi.ez2rs.net;

import ar.hxi.ez2rs.Ez2rs;
import ar.hxi.ez2rs.mixin.InventorySlotReferenceInvoker;
import ar.hxi.ez2rs.terminal.Ez2rsTerminalContent;
import ar.hxi.ez2rs.terminal.TerminalPages;
import ar.hxi.ez2rs.terminal.WirelessUniversalGridItem;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 客户端点左侧按钮时发的包：切到下一页，并用新页的终端重新打开界面。
 *
 * <p>携带玩家背包里的槽位号：网格/合成终端菜单能给出物品位置，无线监视器菜单不行，
 * 所以由客户端统一算出槽位号再发过来。</p>
 */
public record CycleUniversalTerminalPacket(int slot) implements CustomPacketPayload {
    public static final Type<CycleUniversalTerminalPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Ez2rs.MOD_ID, "cycle_universal_terminal")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CycleUniversalTerminalPacket> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CycleUniversalTerminalPacket::slot,
            CycleUniversalTerminalPacket::new
        );

    public static void register() {
        PayloadTypeRegistry.playC2S().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
            context.player().server.execute(() -> handle(context.player(), payload.slot())));
    }

    private static void handle(final ServerPlayer player, final int slot) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return;
        }
        final ItemStack stack = player.getInventory().getItem(slot);
        if (!(stack.getItem() instanceof WirelessUniversalGridItem)) {
            return;
        }
        final TerminalPages pages = Ez2rsTerminalContent.getPages(stack);
        if (pages.isEmpty()) {
            return;
        }
        final int next = pages.nextIndex(Ez2rsTerminalContent.getCurrentPage(stack));
        stack.set(Ez2rsTerminalContent.CURRENT_PAGE, next);
        WirelessUniversalGridItem.open(player, stack, InventorySlotReferenceInvoker.ez2rs$create(slot));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
