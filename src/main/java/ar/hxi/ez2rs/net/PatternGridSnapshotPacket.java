package ar.hxi.ez2rs.net;

import ar.hxi.ez2rs.mixin.ResourceRepositoryAccessor;
import ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridContainerMenu;
import com.refinedmods.refinedstorage.common.grid.GridData;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One targeted reconnect replay, using the native complete GridData wire format. */
public record PatternGridSnapshotPacket(int containerId, GridData data) implements CustomPacketPayload {
    public static final Type<PatternGridSnapshotPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("ez2rs", "pattern_grid_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PatternGridSnapshotPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PatternGridSnapshotPacket::containerId,
        GridData.STREAM_CODEC, PatternGridSnapshotPacket::data, PatternGridSnapshotPacket::new);

    public static void register() { PayloadTypeRegistry.playS2C().register(TYPE, CODEC); }

    public void apply(final WirelessPatternGridContainerMenu menu) {
        menu.onClear();
        final var sticky = ((ResourceRepositoryAccessor) menu.getRepository()).ez2rs$stickyResources();
        sticky.clear();
        sticky.addAll(data.autocraftableResources());
        for (final GridData.GridResource resource : data.resources()) {
            // Native onResourceUpdate/update is a DELTA API; clear above establishes the zero baseline.
            if (resource.resourceAmount().amount() != 0) {
                menu.onResourceUpdate(resource.resourceAmount().resource(), resource.resourceAmount().amount(),
                    resource.trackedResource().orElse(null));
            }
        }
        menu.onActiveChanged(data.active());
        menu.getRepository().sort(); // rebuild zero-quantity sticky entries and notify the screen listener
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
