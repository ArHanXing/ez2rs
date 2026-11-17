package ar.hxi.ez2rs;

import ar.hxi.ez2rs.net.CycleUniversalTerminalPacket;
import ar.hxi.ez2rs.net.QuickMathPacket;
import ar.hxi.ez2rs.terminal.Ez2rsMenus;
import ar.hxi.ez2rs.terminal.Ez2rsRecipes;
import ar.hxi.ez2rs.terminal.Ez2rsTerminalContent;
import ar.hxi.ez2rs.terminal.WirelessAutocrafterManagerItem;
import ar.hxi.ez2rs.terminal.WirelessPatternGridItem;
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
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.reborn.energy.api.EnergyStorage;

/**
 * EZ2RS：Refined Storage 2 的附属模组。
 *
 * <p>功能：自动合成预览把缺失原料置顶、按 P 预览样板输入输出、通用终端（可切换网格 / 合成 /
 * 自动合成监视器）、以及 AE2 风格 GUI 资源包。</p>
 *
 * <p><b>初始化分成两个阶段，阶段边界不能挪。</b></p>
 *
 * <ul>
 *   <li>{@link #onInitialize()}（Fabric 的 {@code main} 入口）只做<b>完全不碰 RS2 类</b>的事情：
 *       数据组件注册。</li>
 *   <li>{@link #onApiAvailable}（RS2 的 {@code refinedstorage_plugin} 入口）才做需要 RS2 的事情：
 *       菜单、配方序列化器、网络包、物品。</li>
 * </ul>
 *
 * <p>原因：RS2 的 {@code RefinedStorageApi.INSTANCE} 是一个代理，代理的 delegate 直到它自己的
 * {@code ModInitializerImpl#onInitialize} 里调用 {@code initializePlatformApi()} 才被装上；
 * 在那之前访问任何 RS2 的编解码器都会抛 {@code IllegalStateException: API not loaded yet}。
 * 而 Fabric 的 {@code main} 入口是按<b>模组顺序</b>逐个调用的：开发环境里
 * {@code FabricLoaderImpl} 会 {@code Collections.shuffle(modCandidates)}（见
 * {@code isDevelopmentEnvironment()} 那段），所以谁先跑每次都不一样——这就是偶发失败；
 * 正式环境按 mod id 升序排序，{@code "ez2rs" < "refinedstorage"}，我们<b>永远</b>先跑。</p>
 *
 * <p>历史坑：之前 {@link #onInitialize()} 里直接调用了 {@code Ez2rsMenus.register()}，
 * 它会触发 {@code PatternGridSnapshotPacket} 的静态初始化 → {@code GridData.STREAM_CODEC}
 * → {@code ResourceCodecs} → 代理。一旦抢跑，{@code ResourceCodecs} 的类初始化被永久标记为失败，
 * RS2 随后在 {@code StorageTypes.<clinit>} 抛出 {@code NoClassDefFoundError}，
 * 整条启动链彻底崩掉（且无法重试）。</p>
 */
public class Ez2rs implements ModInitializer, RefinedStoragePlugin {
    public static final String MOD_ID = "ez2rs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static boolean contentBootstrapped;
    private static boolean refinedStorageHooksInitialized;

    /** Fabric {@code main} 入口：只能放不依赖 RS2 初始化状态的东西。 */
    @Override
    public void onInitialize() {
        bootstrap();
    }

    /** RS2 {@code refinedstorage_plugin} 入口：此时 RS2 的 API 代理已经装好。 */
    @Override
    public void onApiAvailable(final RefinedStorageApi refinedStorageApi) {
        bootstrap();
        initializeRefinedStorageHooks();
        registerContent();
        registerEnergyProvider();
        registerCreativeModeTabListener(refinedStorageApi);
    }

    /** 与 RS2 无关的部分：两处入口谁先到都可以安全执行。 */
    private static synchronized void bootstrap() {
        if (contentBootstrapped) {
            return;
        }
        contentBootstrapped = true;
        Ez2rsTerminalContent.registerDataComponents();
        LOGGER.info("EZ2RS enabled");
    }

    /**
     * 与 RS2 有关的部分：菜单的打开数据 codec、配方序列化器、网络包都会碰 RS2 的静态编解码器，
     * 必须等 {@link #onApiAvailable} 才能执行（见类注释）。
     */
    private static synchronized void initializeRefinedStorageHooks() {
        if (refinedStorageHooksInitialized) {
            return;
        }
        refinedStorageHooksInitialized = true;
        Ez2rsMenus.register();
        Ez2rsRecipes.register();
        CycleUniversalTerminalPacket.register();
        QuickMathPacket.register();
    }

    private static void registerContent() {
        final DirectRegistryCallback<Item> registry = new DirectRegistryCallback<>(BuiltInRegistries.ITEM);
        Ez2rsTerminalContent.setItem(registry.register(
            Ez2rsTerminalContent.WIRELESS_UNIVERSAL_GRID_ID,
            WirelessUniversalGridItem::new
        ));
        Ez2rsTerminalContent.setPatternGridItem(registry.register(
            Ez2rsTerminalContent.WIRELESS_PATTERN_GRID_ID,
            WirelessPatternGridItem::new
        ));
        Ez2rsTerminalContent.setAutocrafterManagerItem(registry.register(
            Ez2rsTerminalContent.WIRELESS_AUTOCRAFTER_MANAGER_ID,
            WirelessAutocrafterManagerItem::new
        ));
    }

    /** 让通用终端拥有真正的物品电池（Team Reborn Energy），外部充电与能量条都靠它。 */
    private static void registerEnergyProvider() {
        final WirelessUniversalGridItem item = Ez2rsTerminalContent.getItem();
        EnergyStorage.ITEM.registerForItems(
            (stack, context) -> new EnergyStorageAdapter(item.createEnergyStorage(stack), context),
            item
        );
        final WirelessPatternGridItem patternGrid = Ez2rsTerminalContent.getPatternGridItem();
        EnergyStorage.ITEM.registerForItems(
            (stack, context) -> new EnergyStorageAdapter(patternGrid.createEnergyStorage(stack), context),
            patternGrid
        );
        final WirelessAutocrafterManagerItem autocrafterManager = Ez2rsTerminalContent.getAutocrafterManagerItem();
        EnergyStorage.ITEM.registerForItems(
            (stack, context) -> new EnergyStorageAdapter(autocrafterManager.createEnergyStorage(stack), context),
            autocrafterManager
        );
    }

    private static void registerCreativeModeTabListener(final RefinedStorageApi refinedStorageApi) {
        final ResourceKey<CreativeModeTab> tab = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            refinedStorageApi.getCreativeModeTabId()
        );
        ItemGroupEvents.modifyEntriesEvent(tab).register(entries -> {
            entries.accept(Ez2rsTerminalContent.getItem().createAtEnergyCapacity());
            entries.accept(Ez2rsTerminalContent.getPatternGridItem().createAtEnergyCapacity());
            entries.accept(Ez2rsTerminalContent.getAutocrafterManagerItem().createAtEnergyCapacity());
        });
    }
}
