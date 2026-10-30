package ar.hxi.ez2rs.terminal;

import ar.hxi.ez2rs.Ez2rs;

import java.util.function.Supplier;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 通用终端相关的内容句柄。
 *
 * <p>物品通过 Refined Storage 的插件回调注册（见 {@link Ez2rs}），
 * 数据组件在模组初始化时直接注册进原版注册表。</p>
 */
public final class Ez2rsTerminalContent {
    public static final ResourceLocation WIRELESS_UNIVERSAL_GRID_ID =
        ResourceLocation.fromNamespaceAndPath(Ez2rs.MOD_ID, "wireless_universal_grid");
    public static final ResourceLocation WIRELESS_PATTERN_GRID_ID =
        ResourceLocation.fromNamespaceAndPath(Ez2rs.MOD_ID, "wireless_pattern_grid");

    /** 已解锁的终端能力（页）。 */
    public static final DataComponentType<TerminalPages> PAGES =
        DataComponentType.<TerminalPages>builder()
            .persistent(TerminalPages.CODEC)
            .networkSynchronized(TerminalPages.STREAM_CODEC)
            .build();

    /** 当前停在第几页。 */
    public static final DataComponentType<Integer> CURRENT_PAGE =
        DataComponentType.<Integer>builder()
            .persistent(com.mojang.serialization.Codec.INT)
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
            .build();

    /** 无线样板终端的草稿快照。 */
    public static final DataComponentType<net.minecraft.nbt.CompoundTag> PATTERN_GRID_DRAFT =
        DataComponentType.<net.minecraft.nbt.CompoundTag>builder()
            .persistent(net.minecraft.nbt.CompoundTag.CODEC)
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.COMPOUND_TAG)
            .build();

    private static Supplier<WirelessUniversalGridItem> item;
    private static Supplier<WirelessPatternGridItem> patternGridItem;

    private Ez2rsTerminalContent() {
    }

    public static void registerDataComponents() {
        net.minecraft.core.Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(Ez2rs.MOD_ID, "terminal_pages"),
            PAGES
        );
        net.minecraft.core.Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(Ez2rs.MOD_ID, "current_page"),
            CURRENT_PAGE
        );
        net.minecraft.core.Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(Ez2rs.MOD_ID, "pattern_grid_draft"),
            PATTERN_GRID_DRAFT
        );
    }

    public static void setItem(final Supplier<WirelessUniversalGridItem> supplier) {
        item = supplier;
    }

    public static WirelessUniversalGridItem getItem() {
        if (item == null) {
            throw new IllegalStateException("Universal terminal item has not been registered yet");
        }
        return item.get();
    }

    public static void setPatternGridItem(final Supplier<WirelessPatternGridItem> supplier) {
        patternGridItem = supplier;
    }

    public static WirelessPatternGridItem getPatternGridItem() {
        if (patternGridItem == null) {
            throw new IllegalStateException("Wireless pattern grid item has not been registered yet");
        }
        return patternGridItem.get();
    }

    /** 草稿组件对所有携带它的终端物品通用，不区分是通用终端还是独立样板终端。 */
    public static boolean isPatternGridCapable(final ItemStack stack) {
        return stack.getItem() instanceof WirelessUniversalGridItem
            || stack.getItem() instanceof WirelessPatternGridItem;
    }

    public static ResourceKey<Item> itemKey() {
        return ResourceKey.create(Registries.ITEM, WIRELESS_UNIVERSAL_GRID_ID);
    }

    public static TerminalPages getPages(final ItemStack stack) {
        return stack.getOrDefault(PAGES, TerminalPages.EMPTY);
    }

    public static int getCurrentPage(final ItemStack stack) {
        final TerminalPages pages = getPages(stack);
        if (pages.isEmpty()) {
            return 0;
        }
        return Math.floorMod(stack.getOrDefault(CURRENT_PAGE, 0), pages.size());
    }

    public static UniversalTerminalType getCurrentType(final ItemStack stack) {
        final TerminalPages pages = getPages(stack);
        return pages.isEmpty() ? null : pages.pages().get(getCurrentPage(stack));
    }
}
