package ar.hxi.ez2rs.client;

import ar.hxi.ez2rs.terminal.Ez2rsMenus;
import ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridContainerMenu;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridContainerMenu;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridScreen;

import com.refinedmods.refinedstorage.common.autocrafting.autocraftermanager.AutocrafterManagerContainerMenu;
import com.refinedmods.refinedstorage.common.autocrafting.autocraftermanager.AutocrafterManagerScreen;

import net.minecraft.client.gui.screens.MenuScreens;

/** 客户端把本模组的菜单接到 RS2 现成的界面上。 */
public final class Ez2rsClientScreens {
    private Ez2rsClientScreens() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            ar.hxi.ez2rs.net.PatternGridSnapshotPacket.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    final var client = context.client();
                    // The player's current menu is authoritative even while the pattern preview overlays its screen.
                    if (client.player != null && client.screen != null
                        && client.player.containerMenu instanceof WirelessPatternGridContainerMenu menu
                        && menu.containerId == payload.containerId()) {
                        payload.apply(menu);
                    }
                }));
        // 用父类作为泛型参数：这样同一个界面可以同时服务 RS2 自己的菜单和我们的子类。
        MenuScreens.register(
            (net.minecraft.world.inventory.MenuType) Ez2rsMenus.wirelessPatternGrid(),
            (MenuScreens.ScreenConstructor<PatternGridContainerMenu, PatternGridScreen>)
                PatternGridScreen::new
        );
        // 同理：泛型参数写父类 AutocrafterManagerContainerMenu，界面复用原生 AutocrafterManagerScreen。
        MenuScreens.register(
            (net.minecraft.world.inventory.MenuType) Ez2rsMenus.wirelessAutocrafterManager(),
            (MenuScreens.ScreenConstructor<AutocrafterManagerContainerMenu, AutocrafterManagerScreen>)
                AutocrafterManagerScreen::new
        );
    }
}
