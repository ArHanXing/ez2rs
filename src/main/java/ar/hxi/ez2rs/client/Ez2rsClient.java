package ar.hxi.ez2rs.client;

import ar.hxi.ez2rs.terminal.Ez2rsTerminalContent;

import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.autocrafting.PatternProviderItem;
import com.refinedmods.refinedstorage.common.util.ClientPlatformUtil;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.Optional;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口：注册「预览样板」按键（默认 P）与「打开通用终端」快捷键，并在任意容器界面里拦截 P。
 *
 * <p>样板预览的行为参考 ExtendedAE：鼠标悬停在样板物品上按 P，弹出输入 → 输出预览界面。
 * 通过 Fabric 的 {@code ScreenKeyboardEvents.allowKeyPress} 拦截，命中时返回 false 吞掉按键，
 * 不会让 P 继续传给界面（避免同时触发搜索框输入等副作用）。</p>
 *
 * <p>「打开通用终端」复用 RS2 自己的 {@code useSlotReferencedItem}：它在背包里找那件物品、
 * 生成槽位引用并发原生 C2S 包，服务端回调物品的 {@code use}。因此快捷键与右键走的是同一条路径，
 * 权限、网络绑定、当前页切换全部一致；"没找到"和"背包里有多件"的提示也是 RS2 原生文案。</p>
 */
public class Ez2rsClient implements ClientModInitializer {
    private static final String CATEGORY = "key.categories.ez2rs";

    public static final KeyMapping PATTERN_PREVIEW_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.ez2rs.pattern_preview",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_P,
        CATEGORY
    ));

    /**
     * 打开背包里的通用终端。
     *
     * <p>默认<b>不绑定</b>任何键（{@code InputConstants.UNKNOWN}），与 RS2 自己那三个无线终端
     * 快捷键的做法一致：这类键一按就顶掉整个界面，给默认值容易和其他模组撞车，
     * 交给玩家在「按键绑定」里自己设。</p>
     */
    public static final KeyMapping OPEN_UNIVERSAL_TERMINAL_KEY =
        KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.ez2rs.open_universal_terminal",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
        ));

    @Override
    public void onInitializeClient() {
        Ez2rsClientScreens.register();
        registerGuiPack();
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyCode, scanCode, modifiers) ->
                !tryOpenPreview(s, keyCode, scanCode)));
        ClientTickEvents.END_CLIENT_TICK.register(Ez2rsClient::handleInputEvents);
    }

    /**
     * 只在没有打开任何界面时响应：避免在箱子/聊天框里误触，把正在用的界面顶掉。
     *
     * <p>用 {@code while (consumeClick())} 是 RS2 的写法，保证一 tick 内多次按下也能排空队列。</p>
     */
    private static void handleInputEvents(final Minecraft client) {
        if (client.player == null) {
            return;
        }
        while (OPEN_UNIVERSAL_TERMINAL_KEY.consumeClick()) {
            if (client.screen != null) {
                continue;
            }
            RefinedStorageApi.INSTANCE.useSlotReferencedItem(
                client.player,
                Ez2rsTerminalContent.getItem()
            );
        }
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
