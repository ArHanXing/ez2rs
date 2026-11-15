package ar.hxi.ez2rs.terminal;

import ar.hxi.ez2rs.Ez2rs;
import ar.hxi.ez2rs.terminal.autocraftermanager.WirelessAutocrafterManagerContainerMenu;
import ar.hxi.ez2rs.terminal.autocraftermanager.WirelessAutocrafterManagerData;
import ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridContainerMenu;
import ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridData;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

/** 本模组自己的菜单类型。 */
public final class Ez2rsMenus {
    private static MenuType<WirelessPatternGridContainerMenu> wirelessPatternGrid;
    private static MenuType<WirelessAutocrafterManagerContainerMenu> wirelessAutocrafterManager;

    private Ez2rsMenus() {
    }

    public static void register() {
        ar.hxi.ez2rs.net.PatternGridSnapshotPacket.register();
        // 显式声明成 StreamCodec<RegistryFriendlyByteBuf, WirelessPatternGridData>，
        // 否则 ExtendedScreenHandlerType 会把数据按擦除后的类型编码，
        // 发包时抛 WirelessPatternGridData cannot be cast to PatternGridData。
        final StreamCodec<RegistryFriendlyByteBuf, WirelessPatternGridData> codec =
            WirelessPatternGridData.STREAM_CODEC;
        final ExtendedScreenHandlerType<WirelessPatternGridContainerMenu, WirelessPatternGridData> type =
            new ExtendedScreenHandlerType<>(
                (syncId, inventory, data) -> new WirelessPatternGridContainerMenu(syncId, inventory, data),
                codec
            );
        wirelessPatternGrid = Registry.register(
            BuiltInRegistries.MENU,
            ResourceLocation.fromNamespaceAndPath(Ez2rs.MOD_ID, "wireless_pattern_grid"),
            type
        );

        // 同理：显式声明 StreamCodec 的泛型参数，否则按擦除后的 AutocrafterManagerData 编码会崩。
        final StreamCodec<RegistryFriendlyByteBuf, WirelessAutocrafterManagerData> autocrafterManagerCodec =
            WirelessAutocrafterManagerData.STREAM_CODEC;
        final ExtendedScreenHandlerType<WirelessAutocrafterManagerContainerMenu, WirelessAutocrafterManagerData>
            autocrafterManagerType = new ExtendedScreenHandlerType<>(
                (syncId, inventory, data) ->
                    new WirelessAutocrafterManagerContainerMenu(syncId, inventory, data),
                autocrafterManagerCodec
            );
        wirelessAutocrafterManager = Registry.register(
            BuiltInRegistries.MENU,
            ResourceLocation.fromNamespaceAndPath(Ez2rs.MOD_ID, "wireless_autocrafter_manager"),
            autocrafterManagerType
        );
    }

    public static MenuType<WirelessPatternGridContainerMenu> wirelessPatternGrid() {
        if (wirelessPatternGrid == null) {
            throw new IllegalStateException("Menus are not registered yet");
        }
        return wirelessPatternGrid;
    }

    public static MenuType<WirelessAutocrafterManagerContainerMenu> wirelessAutocrafterManager() {
        if (wirelessAutocrafterManager == null) {
            throw new IllegalStateException("Menus are not registered yet");
        }
        return wirelessAutocrafterManager;
    }
}
