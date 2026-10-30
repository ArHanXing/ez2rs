package ar.hxi.ez2rs.mixin;

import ar.hxi.ez2rs.terminal.WirelessUniversalGridItem;

import java.util.Optional;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.energy.EnergyNetworkComponent;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemContext;
import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 通用终端：网络可达时从网络取电，不再消耗物品内部电池；网络不可达时回退到原有的物品电池逻辑。
 *
 * <p>只对 {@link WirelessUniversalGridItem} 生效，RS2 自己的无线物品行为不变。
 * 注入点是 RS2 的 {@code NetworkItemContextImpl}（包私有类，用 targets 字符串指定）。</p>
 */
@Mixin(targets = "com.refinedmods.refinedstorage.common.support.network.item.NetworkItemContextImpl",
    remap = false)
public abstract class NetworkItemContextImplMixin {
    @Shadow(remap = false)
    @Final
    private Player player;

    @Shadow(remap = false)
    @Final
    private SlotReference slotReference;

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true, remap = false)
    private void ez2rs$networkFirstActive(final CallbackInfoReturnable<Boolean> cir) {
        final Optional<Network> network = ez2rs$resolveNetworkForOurItem();
        if (network.isEmpty()) {
            return;
        }
        if (!RefinedStorageApi.INSTANCE.isEnergyRequired()) {
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(
            network.get().getComponent(EnergyNetworkComponent.class).getStored() > 0
        );
    }

    @Inject(method = "drainEnergy", at = @At("HEAD"), cancellable = true, remap = false)
    private void ez2rs$drainFromNetwork(final long amount, final CallbackInfo ci) {
        final Optional<Network> network = ez2rs$resolveNetworkForOurItem();
        if (network.isEmpty()) {
            return;
        }
        network.get().getComponent(EnergyNetworkComponent.class).extract(amount);
        ci.cancel();
    }

    private Optional<Network> ez2rs$resolveNetworkForOurItem() {
        final boolean isOurItem = this.slotReference.resolve(this.player)
            .map(stack -> stack.getItem() instanceof WirelessUniversalGridItem)
            .orElse(false);
        if (!isOurItem) {
            return Optional.empty();
        }
        return ((NetworkItemContext) (Object) this).resolveNetwork();
    }
}
