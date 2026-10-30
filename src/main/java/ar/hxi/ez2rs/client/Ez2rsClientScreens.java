package ar.hxi.ez2rs.client;

import ar.hxi.ez2rs.terminal.Ez2rsMenus;
import ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridContainerMenu;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridContainerMenu;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridScreen;

import net.minecraft.client.gui.screens.MenuScreens;

/** 客户端把本模组的菜单接到 RS2 现成的界面上。 */
public final class Ez2rsClientScreens {
    private Ez2rsClientScreens() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        // 用父类作为泛型参数：这样同一个界面可以同时服务 RS2 自己的菜单和我们的子类。
        MenuScreens.register(
            (net.minecraft.world.inventory.MenuType) Ez2rsMenus.wirelessPatternGrid(),
            (MenuScreens.ScreenConstructor<PatternGridContainerMenu, PatternGridScreen>)
                PatternGridScreen::new
        );
    }
}
