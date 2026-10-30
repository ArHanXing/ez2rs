package ar.hxi.ez2rs.terminal;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 终端类型到对应无线终端物品的映射（用于合成配方判定）。 */
public final class TerminalTypes {
    private static final ResourceLocation WIRELESS_GRID =
        ResourceLocation.fromNamespaceAndPath("refinedstorage", "wireless_grid");
    private static final ResourceLocation WIRELESS_AUTOCRAFTING_MONITOR =
        ResourceLocation.fromNamespaceAndPath("refinedstorage", "wireless_autocrafting_monitor");
    private static final ResourceLocation WIRELESS_CRAFTING_GRID =
        ResourceLocation.fromNamespaceAndPath("refinedstorage_quartz_arsenal", "wireless_crafting_grid");
    // 通用终端的样板页现在由「无线样板终端」解锁（不再是方块版样板终端）：
    // 无线终端的整合应该发生在无线物品之间，玩家不必为了加一页再去拆一个方块终端。
    private static final ResourceLocation WIRELESS_PATTERN_GRID =
        ResourceLocation.fromNamespaceAndPath("ez2rs", "wireless_pattern_grid");

    private TerminalTypes() {
    }

    public static boolean isItemOf(final ItemStack stack, final UniversalTerminalType type) {
        return switch (type) {
            case GRID -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(WIRELESS_GRID);
            case AUTOCRAFTING_MONITOR ->
                BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(WIRELESS_AUTOCRAFTING_MONITOR);
            case CRAFTING_GRID ->
                BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(WIRELESS_CRAFTING_GRID);
            case PATTERN_GRID -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(WIRELESS_PATTERN_GRID);
        };
    }
}
