package ar.hxi.ez2rs.terminal;

import ar.hxi.ez2rs.Ez2rs;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * 通用终端解锁配方的注册与查询。
 *
 * <p>这里只注册 serializer：自定义合成配方在 json 里的 {@code type} 指向 serializer id，
 * RecipeType 仍然是原版的 {@code minecraft:crafting}。</p>
 */
public final class Ez2rsRecipes {
    private static final Map<UniversalTerminalType, TerminalUpgradeRecipe.Serializer> SERIALIZERS =
        new EnumMap<>(UniversalTerminalType.class);

    private Ez2rsRecipes() {
    }

    public static void register() {
        for (final UniversalTerminalType type : UniversalTerminalType.values()) {
            final TerminalUpgradeRecipe.Serializer serializer = new TerminalUpgradeRecipe.Serializer(type);
            SERIALIZERS.put(type, serializer);
            net.minecraft.core.Registry.register(
                BuiltInRegistries.RECIPE_SERIALIZER,
                serializerId(type),
                serializer
            );
        }
    }

    public static ResourceLocation serializerId(final UniversalTerminalType type) {
        return ResourceLocation.fromNamespaceAndPath(
            Ez2rs.MOD_ID,
            "terminal_upgrade_" + type.getSerializedName()
        );
    }

    public static RecipeSerializer<TerminalUpgradeRecipe> terminalUpgrade(final UniversalTerminalType type) {
        final TerminalUpgradeRecipe.Serializer serializer = SERIALIZERS.get(type);
        if (serializer == null) {
            throw new IllegalStateException("Recipe serializer for " + type + " is not registered yet");
        }
        return serializer;
    }
}
