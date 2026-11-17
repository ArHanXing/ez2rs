package ar.hxi.ez2rs.storage;

import com.refinedmods.refinedstorage.common.api.storage.StorageBlockProvider;
import com.refinedmods.refinedstorage.common.storage.storageblock.StorageBlock;

import java.util.List;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

/**
 * 无限存储方块本体。
 *
 * <p>除了掉落物和名字，其它全部交给 RS2 的 {@link StorageBlock}：它的 {@code newBlockEntity} 会调
 * {@code RefinedStorageApi.INSTANCE.createStorageBlockEntity(pos, state, provider)} 造出 RS2 的
 * {@code StorageBlockBlockEntity}，菜单、界面、数据同步、ticker 都不需要我们写一行。</p>
 *
 * <p><b>为什么必须自己给掉落物。</b>方块是动态注册的（id 由配置文件生成），不可能为每个 id 手写
 * {@code data/ez2rs/loot_table/blocks/infinite_storage_*.json}。而原版 {@code BlockBehaviour#getDrops}
 * 在找不到 loot table 时返回空表，结果就是方块被挖掉后什么都不掉。这里直接返回自身。</p>
 */
public class InfiniteStorageBlock extends StorageBlock<BlockEntity> {
    private final MutableComponent displayName;

    public InfiniteStorageBlock(final Properties properties,
                                final StorageBlockProvider provider,
                                final MutableComponent displayName) {
        super(properties, provider);
        this.displayName = displayName;
    }

    /**
     * 方块名也覆盖掉：物品名由 {@link InfiniteStorageBlockItem} 负责，但方块名会出现在
     * 拾取提示、部分信息类模组和 RS2 自己的权限提示里，不覆盖就是
     * {@code block.ez2rs.infinite_storage_water} 这种原始键（我们没有 lang 文件）。
     *
     * <p>返回 {@link MutableComponent}：{@code Block#getName()} 的返回类型就是它，covariant return 合法。</p>
     */
    @Override
    public MutableComponent getName() {
        return this.displayName;
    }

    /**
     * 参数不再使用，但签名必须原样保留：它是 {@code BlockBehaviour} 上的覆盖点。
     *
     * <p>这里写成 {@code public} 而不是 {@code protected}：Java 允许放宽访问权限，所以父类里
     * 无论是 {@code protected} 还是 {@code public}，这个覆盖都能编译；反过来写 {@code protected}
     * 一旦父类是 {@code public} 就编译失败。</p>
     */
    @Override
    public List<ItemStack> getDrops(final BlockState state, final LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }
}
