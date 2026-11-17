package ar.hxi.ez2rs.storage;

import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.api.storage.SerializableStorage;
import com.refinedmods.refinedstorage.common.api.storage.StorageType;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 每个配置条目一个 {@code StorageType}。
 *
 * <p><b>为什么必须注册。</b>RS2 的 {@code StorageRepositoryImpl} 用一个 dispatch codec 序列化存档里
 * 所有 storage：{@code getStorageTypeRegistry().codec().dispatch(SerializableStorage::getType, ...)}。
 * 我们的 storage 会进同一个 repository（由 RS2 的 {@code StorageBlockBlockEntity} 放进去），
 * 未注册的 {@code StorageType} 在存档时就没有 codec 可写。</p>
 *
 * <p><b>为什么 codec 是 unit。</b>这类 storage 不持久化任何状态：内容完全来自配置，方块本身也不存
 * 每个方块的 UUID。{@code MapCodec.unit} 序列化时什么都不写，反序列化时直接造一个新的
 * {@link InfiniteStorage}——正是我们想要的语义。</p>
 *
 * <p><b>为什么构造时不持有物品/流体列表。</b>注册时机早于其它模组注册物品（见
 * {@link InfiniteStorageResources} 的说明），这里只持有那个延迟解析器，查表推迟到真正使用时。</p>
 */
public final class InfiniteStorageType implements StorageType {
    /** 仅用于日志与报错；注册表里的 id 由 {@code InfiniteStorageContent} 另外传。 */
    private final ResourceLocation id;
    private final InfiniteStorageResources resources;

    public InfiniteStorageType(final ResourceLocation id, final InfiniteStorageResources resources) {
        this.id = id;
        this.resources = resources;
    }

    /** {@code capacity} 被忽略：无限存储没有容量概念，也不实现 {@code LimitedStorage}。 */
    @Override
    public SerializableStorage create(final Long capacity, final Runnable listener) {
        return new InfiniteStorage(this, this.resources, listener);
    }

    @Override
    public MapCodec<SerializableStorage> getMapCodec(final Runnable listener) {
        return MapCodec.<SerializableStorage>unit(() -> this.create(null, listener));
    }

    @Override
    public boolean isAllowed(final ResourceKey resource) {
        return this.resources.get().accepts(resource);
    }

    /** 无限方块不参与磁盘接口搬运：没有"搬多少"的概念，给 0 让接口直接跳过它。 */
    @Override
    public long getDiskInterfaceTransferQuota(final boolean stackUpgrade) {
        return 0;
    }

    @Override
    public String toString() {
        return "InfiniteStorageType[" + this.id + "]";
    }
}
