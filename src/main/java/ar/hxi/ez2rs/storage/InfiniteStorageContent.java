package ar.hxi.ez2rs.storage;

import ar.hxi.ez2rs.Ez2rs;

import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.content.BlockConstants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 按配置文件注册所有无限存储方块。
 *
 * <p>只能从 {@link Ez2rs#onApiAvailable} 调：注册 storage type 要碰
 * {@code RefinedStorageApi.INSTANCE.getStorageTypeRegistry()}，而那个 API 代理在 RS2 自己的
 * {@code ModInitializerImpl#onInitialize} 之前是空的（见 {@link Ez2rs} 的类注释）。</p>
 *
 * <p><b>注册顺序不能颠倒</b>，每一步都依赖上一步：</p>
 * <ol>
 *   <li>{@link InfiniteStorageType} —— 必须先进 RS2 的 storage type 注册表，否则方块放下去之后
 *       {@code StorageRepositoryImpl} 序列化存档时会因为没有 codec 而失败；</li>
 *   <li>{@link InfiniteStorageProvider} —— 只是持有 type，此时还没有方块实体类型；</li>
 *   <li>方块 —— {@code new InfiniteStorageBlock(...)} 之后立刻注册进 {@code BuiltInRegistries.BLOCK}，
 *       因为后面构造 {@code BlockEntityType} 需要方块实例；</li>
 *   <li>物品 —— 同样要进注册表，创造模式标签与客户端模型都靠它；</li>
 *   <li>{@code BlockEntityType} —— 需要方块集合，所以必须最后注册；注册完再回填给 provider。
 *       方块实体只有在世界加载后才会被创建，那时类型早就就位了。</li>
 * </ol>
 *
 * <p><b>注意整个流程都不查询资源注册表。</b>这一步发生在 {@code refinedstorage_plugin} 入口，
 * 比大多数模组的 {@code onInitialize} 都早（{@code "ez2rs" < "refinedstorage" < "techreborn"}），
 * 此刻 {@code BuiltInRegistries.ITEM} 里还没有别人模组的物品。资源 id 只被原样保存进
 * {@link InfiniteStorageResources}，等世界加载后才查表——这就是为什么配置里能写
 * {@code techreborn:cell} 这种"比我们晚注册"的模组的物品。</p>
 */
public final class InfiniteStorageContent {
    private static final List<Block> BLOCKS = new ArrayList<>();
    private static final List<Item> ITEMS = new ArrayList<>();
    private static final Set<ResourceLocation> ITEM_IDS = new HashSet<>();
    private static final List<InfiniteStorageResources> RESOURCES = new ArrayList<>();

    private static boolean registered;

    private InfiniteStorageContent() {
    }

    public static synchronized void register(final RefinedStorageApi api) {
        if (registered) {
            return;
        }
        // 先读配置再置标志位：配置抛异常时（游戏会崩）不要把状态留在"已完成"，
        // 否则万一上层捕获了异常，后面的调用会静默地什么都不做。
        final List<InfiniteStorageConfig.Entry> entries = InfiniteStorageConfig.load();
        registered = true;
        int succeeded = 0;
        for (final InfiniteStorageConfig.Entry entry : entries) {
            if (registerEntry(api, entry)) {
                succeeded++;
            }
        }
        Ez2rs.LOGGER.info("Registered {} infinite storage block(s), skipped {}",
            succeeded, entries.size() - succeeded);
    }

    /**
     * 在服务端 / 客户端启动完成后解析所有条目的资源 id。
     *
     * <p>严格来说不是必需的——{@link InfiniteStorageResources} 会在首次访问时自己解析。
     * 提前跑一次只是为了让"某个 id 写错了"的 warn 出现在固定的位置，而不是在一次游戏内操作之后。</p>
     */
    public static void resolveResources() {
        for (final InfiniteStorageResources resources : List.copyOf(RESOURCES)) {
            resources.resolveEagerly();
        }
    }

    /** @return 是否成功；单条失败只 warn 并跳过，不能让整批注册连带崩掉 */
    private static boolean registerEntry(final RefinedStorageApi api, final InfiniteStorageConfig.Entry entry) {
        try {
            // 0. 延迟解析器：只保存 id 字符串，不查注册表（见类注释）。
            final InfiniteStorageResources resources =
                new InfiniteStorageResources(entry.id(), entry.items(), entry.fluids());
            // 1. storage type：进 RS2 的注册表，否则存档序列化时找不到 codec。
            final InfiniteStorageType type = new InfiniteStorageType(entry.id(), resources);
            api.getStorageTypeRegistry().register(entry.id(), type);
            // 2. provider：只持有 type 与显示名，方块实体类型稍后回填。
            final InfiniteStorageProvider provider =
                new InfiniteStorageProvider(entry.kind(), entry.name(), type);
            // 3. 方块：必须先进 BLOCK 注册表，后面构造 BlockEntityType 要用它。
            final InfiniteStorageBlock block =
                new InfiniteStorageBlock(BlockConstants.PROPERTIES, provider, entry.name());
            Registry.register(BuiltInRegistries.BLOCK, entry.id(), block);
            // 4. 物品
            final InfiniteStorageBlockItem item =
                new InfiniteStorageBlockItem(block, new Item.Properties(), entry.name());
            Registry.register(BuiltInRegistries.ITEM, entry.id(), item);
            // 5. 方块实体类型：RS2 的 createStorageBlockEntity 会造出它自己的 StorageBlockBlockEntity。
            //    泛型参数写 BlockEntity：BlockEntitySupplier.create 的返回值必须能赋给它，
            //    而 RS2 的 StorageBlock<BlockEntity> 也按这个类型去 cast provider.getBlockEntityType()。
            final BlockEntityType<BlockEntity> blockEntityType = new BlockEntityType<>(
                (pos, state) -> RefinedStorageApi.INSTANCE.createStorageBlockEntity(pos, state, provider),
                Set.of(block),
                null
            );
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, entry.id(), blockEntityType);
            // 6. 回填：StorageBlock 的 ticker 是懒查询 provider.getBlockEntityType()，所以可以后设。
            provider.setBlockEntityType(blockEntityType);
            BLOCKS.add(block);
            ITEMS.add(item);
            ITEM_IDS.add(entry.id());
            RESOURCES.add(resources);
            Ez2rs.LOGGER.info("Registered infinite storage block {} ({})", entry.id(), entry.kind());
            return true;
        } catch (final RuntimeException e) {
            Ez2rs.LOGGER.warn("Failed to register infinite storage block {}, skipped", entry.id(), e);
            return false;
        }
    }

    /** 客户端模型插件用：每个方块都要注册一个 block state resolver。 */
    public static List<Block> getBlocks() {
        return List.copyOf(BLOCKS);
    }

    /** 创造模式标签用。 */
    public static List<Item> getItems() {
        return List.copyOf(ITEMS);
    }

    /**
     * 客户端模型插件用：判断某个 id 是不是我们动态注册的物品。
     *
     * <p>不能用「id 前缀匹配 {@code infinite_storage_}」来判定：目标物品模型
     * {@code ez2rs:item/infinite_storage_block_item} 本身也命中这个前缀，会自递归。</p>
     */
    public static boolean isRegisteredItemId(final ResourceLocation id) {
        return ITEM_IDS.contains(id);
    }
}
