package ar.hxi.ez2rs;

import ar.hxi.ez2rs.net.CycleUniversalTerminalPacket;
import ar.hxi.ez2rs.terminal.Ez2rsRecipes;
import ar.hxi.ez2rs.terminal.Ez2rsTerminalContent;
import ar.hxi.ez2rs.terminal.WirelessUniversalGridItem;

import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.content.DirectRegistryCallback;
import com.refinedmods.refinedstorage.fabric.api.RefinedStoragePlugin;
import com.refinedmods.refinedstorage.fabric.support.energy.EnergyStorageAdapter;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.reborn.energy.api.EnergyStorage;

/**
 * EZ2RS：Refined Storage 2 的附属模组。
 *
 * <p>功能：自动合成预览把缺失原料置顶、按 P 预览样板输入输出、通用终端（可切换网格 / 合成 /
 * 自动合成监视器）、以及 AE2 风格 GUI 资源包。</p>
 *
 * <p>内容注册走 RS2 的 {@code refinedstorage_plugin} 入口（{@link #onApiAvailable}），
 * 因为物品必须注册进 RS2 已经准备好的注册表；数据组件与网络包在 {@link #bootstrap()} 里做，
 * 两处入口都会调用它，用标志位保证只执行一次。</p>
 */
public class Ez2rs implements ModInitializer, RefinedStoragePlugin {
    public static final String MOD_ID = "ez2rs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static boolean bootstrapped;

    @Override
    public void onInitialize() {
        bootstrap();
    }

    @Override
    public void onApiAvailable(final RefinedStorageApi refinedStorageApi) {
        bootstrap();
        registerContent();
        registerEnergyProvider();
        registerCreativeModeTabListener(refinedStorageApi);
    }

    private static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        Ez2rsTerminalContent.registerDataComponents();
        Ez2rsRecipes.register();
        CycleUniversalTerminalPacket.register();
        LOGGER.info("EZ2RS enabled");
    }

    private static void registerContent() {
        Ez2rsTerminalContent.setItem(
            new DirectRegistryCallback<>(BuiltInRegistries.ITEM).register(
                Ez2rsTerminalContent.WIRELESS_UNIVERSAL_GRID_ID,
                WirelessUniversalGridItem::new
            )
        );
    }

    /** 让通用终端拥有真正的物品电池（Team Reborn Energy），外部充电与能量条都靠它。 */
    private static void registerEnergyProvider() {
        final WirelessUniversalGridItem item = Ez2rsTerminalContent.getItem();
        EnergyStorage.ITEM.registerForItems(
            (stack, context) -> new EnergyStorageAdapter(item.createEnergyStorage(stack), context),
            item
        );
    }

    private static void registerCreativeModeTabListener(final RefinedStorageApi refinedStorageApi) {
        final ResourceKey<CreativeModeTab> tab = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            refinedStorageApi.getCreativeModeTabId()
        );
        ItemGroupEvents.modifyEntriesEvent(tab).register(entries ->
            entries.accept(Ez2rsTerminalContent.getItem().createAtEnergyCapacity()));
    }
}
