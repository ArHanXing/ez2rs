package ar.hxi.ez2rs.client;

import ar.hxi.ez2rs.Ez2rs;
import ar.hxi.ez2rs.storage.InfiniteStorageContent;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 给动态注册的无限存储方块接上模型。
 *
 * <p>方块 id 由配置文件生成，所以不可能手写 {@code blockstates/*.json} 与 {@code models/item/*.json}。
 * 用 Fabric 的模型加载 API 在内存里补上：</p>
 *
 * <ul>
 *   <li>方块：{@code registerBlockStateResolver}，每个 {@link BlockState} 恰好 {@code setModel} 一次，
 *       全部指向同一个 {@code ez2rs:block/infinite_storage_block}（cube_all，贴图共用）。
 *       漏设会触发 Fabric 的 error 日志并把这个方块整体回退成 missing model。</li>
 *   <li>物品：{@code resolveModel()} 事件。物品模型最终由 {@code ModelBakery} 按
 *       {@code item/<path>} 的形式去加载，所以匹配的是 {@code ez2rs:item/<id>}；
 *       这里同时容忍不带 {@code item/} 前缀的写法，避免不同加载阶段取的 id 形态不一致时失效。</li>
 * </ul>
 *
 * <p>模型文件数量因此与配置条目数无关：不管配多少条，都只有这两个模型。</p>
 */
public final class InfiniteStorageModels {
    private static final ResourceLocation BLOCK_MODEL =
        ResourceLocation.fromNamespaceAndPath(Ez2rs.MOD_ID, "block/infinite_storage_block");
    private static final ResourceLocation ITEM_MODEL =
        ResourceLocation.fromNamespaceAndPath(Ez2rs.MOD_ID, "item/infinite_storage_block_item");
    private static final String ITEM_FOLDER = "item/";

    private InfiniteStorageModels() {
    }

    public static void register() {
        ModelLoadingPlugin.register(context -> {
            // 注意：这些集合在 lambda 里才读，不是注册时就读 —— 插件回调在资源重载时才跑，
            // 那时内容注册早已完成。
            for (final Block block : InfiniteStorageContent.getBlocks()) {
                context.registerBlockStateResolver(block, resolverContext -> {
                    final UnbakedModel model = resolverContext.getOrLoadModel(BLOCK_MODEL);
                    for (final BlockState state : block.getStateDefinition().getPossibleStates()) {
                        resolverContext.setModel(state, model);
                    }
                });
            }
            context.resolveModel().register(modelContext -> {
                final ResourceLocation id = modelContext.id();
                if (!id.getNamespace().equals(Ez2rs.MOD_ID)) {
                    return null;
                }
                // 目标模型自身必须放过：否则下面 getOrLoadModel(ITEM_MODEL) 会重新命中这里，
                // 无限递归直到 StackOverflow。
                if (id.equals(ITEM_MODEL)) {
                    return null;
                }
                if (!InfiniteStorageContent.isRegisteredItemId(stripItemFolder(id))) {
                    return null;
                }
                return modelContext.getOrLoadModel(ITEM_MODEL);
            });
        });
    }

    /** 去掉可能存在的 {@code item/} 前缀，得到物品本身在注册表里的 id。 */
    private static ResourceLocation stripItemFolder(final ResourceLocation id) {
        final String path = id.getPath();
        if (!path.startsWith(ITEM_FOLDER)) {
            return id;
        }
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path.substring(ITEM_FOLDER.length()));
    }
}
