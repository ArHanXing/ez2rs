package ar.hxi.ez2rs.client.emi;

import ar.hxi.ez2rs.terminal.Ez2rsMenus;
import ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridContainerMenu;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import net.minecraft.world.inventory.MenuType;

/**
 * 把无线样板终端注册进 EMI。
 *
 * <p>RS2 官方的 EMI 集成只按 RS2 自己的菜单类型注册了样板网格处理器，
 * 我们的 {@code ez2rs:wireless_pattern_grid} 不在其中，所以拖拽没有反应。
 * 这里补一个 {@link WirelessPatternGridEmiHandler}。</p>
 *
 * <p>EMI 通过 {@code fabric.mod.json} 的 {@code emi} 入口点发现本类。</p>
 */
@EmiEntrypoint
public final class Ez2rsEmiPlugin implements EmiPlugin {
    @Override
    public void register(final EmiRegistry registry) {
        // 泛型擦除：Ez2rsMenus 返回的是 MenuType<?>，这里断言成处理器要求的菜单类型。
        @SuppressWarnings("unchecked")
        final MenuType<WirelessPatternGridContainerMenu> type =
            (MenuType<WirelessPatternGridContainerMenu>) (MenuType<?>) Ez2rsMenus.wirelessPatternGrid();
        registry.addRecipeHandler(type, new WirelessPatternGridEmiHandler());
    }
}
