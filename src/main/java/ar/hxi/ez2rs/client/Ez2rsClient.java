package ar.hxi.ez2rs.client;

import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.common.api.autocrafting.PatternProviderItem;
import com.refinedmods.refinedstorage.common.util.ClientPlatformUtil;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.Optional;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口：注册「预览样板」按键（默认 P），并在任意容器界面里拦截该按键。
 *
 * <p>行为参考 ExtendedAE 的样板预览：鼠标悬停在样板物品上按 P，弹出输入 → 输出预览界面。
 * 通过 Fabric 的 {@code ScreenKeyboardEvents.allowKeyPress} 拦截，命中时返回 false 吞掉按键，
 * 不会让 P 继续传给界面（避免同时触发搜索框输入等副作用）。</p>
 */
public class Ez2rsClient implements ClientModInitializer {
    private static final String CATEGORY = "key.categories.ez2rs";

    public static final KeyMapping PATTERN_PREVIEW_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.ez2rs.pattern_preview",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_P,
        CATEGORY
    ));

    @Override
    public void onInitializeClient() {
        registerGuiPack();
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyCode, scanCode, modifiers) ->
                !tryOpenPreview(s, keyCode, scanCode)));
    }

    /**
     * 内置资源包：把 RS2 的 GUI 换成 AE2 1.21 风格。
     * 默认不启用（NORMAL，玩家可在「选项 → 资源包」里打开），避免强行改掉别人的界面观感。
     */
    private static void registerGuiPack() {
        FabricLoader.getInstance().getModContainer("ez2rs").ifPresent(container ->
            ResourceManagerHelper.registerBuiltinResourcePack(
                ResourceLocation.fromNamespaceAndPath("ez2rs", "ae2_gui"),
                container,
                Component.translatable("resourcepack.ez2rs.ae2_gui"),
                Boolean.getBoolean("ez2rs.pack.enabled")
                    ? ResourcePackActivationType.ALWAYS_ENABLED
                    : ResourcePackActivationType.NORMAL
            ));
    }

    private static boolean tryOpenPreview(final Screen screen, final int keyCode, final int scanCode) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return false;
        }
        if (!PATTERN_PREVIEW_KEY.matches(keyCode, scanCode)) {
            return false;
        }
        // 有输入框正在聚焦时（例如网格的搜索框），P 应该照常输入。
        if (screen.getFocused() instanceof EditBox) {
            return false;
        }
        final Slot slot = containerScreen.hoveredSlot;
        if (slot == null || !slot.hasItem()) {
            return false;
        }
        final ItemStack stack = slot.getItem();
        if (!(stack.getItem() instanceof PatternProviderItem patternProviderItem)) {
            return false;
        }
        final Level level = ClientPlatformUtil.getClientLevel();
        if (level == null) {
            return false;
        }
        final Optional<Pattern> pattern = patternProviderItem.getPattern(stack, level);
        if (pattern.isEmpty()) {
            return false;
        }
        Minecraft.getInstance().setScreen(new PatternPreviewScreen(
            containerScreen,
            stack.getHoverName(),
            pattern.get()
        ));
        return true;
    }
}
