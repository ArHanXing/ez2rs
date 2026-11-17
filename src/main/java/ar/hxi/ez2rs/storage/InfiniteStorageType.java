package ar.hxi.ez2rs.storage;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.api.storage.SerializableStorage;
import com.refinedmods.refinedstorage.common.api.storage.StorageType;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

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
 */
public final class InfiniteStorageType implements StorageType {
    /** 仅用于日志与报错；注册表里的 id 由 {@code InfiniteStorageContent} 另外传。 */
    private final ResourceLocation id;
    private final List<ResourceAmount> contents;
    private final Set<Item> items;
    private final Set<Fluid> fluids;

    public InfiniteStorageType(final ResourceLocation id,
                               final List<Item> items,
                               final List<Fluid> fluids) {
        this.id = id;
        this.items = Set.copyOf(items);
        this.fluids = Set.copyOf(fluids);
        // 内容在这里算一次、所有同类型 storage 共享：它永远不会变，没必要每个方块各建一份。
        final List<ResourceAmount> amounts = new ArrayList<>(items.size() + fluids.size());
        items.forEach(item -> amounts.add(new ResourceAmount(new ItemResource(item), InfiniteStorage.INFINITE)));
        fluids.forEach(fluid -> amounts.add(new ResourceAmount(new FluidResource(fluid), InfiniteStorage.INFINITE)));
        this.contents = List.copyOf(amounts);
    }

    /** {@code capacity} 被忽略：无限存储没有容量概念，也不实现 {@code LimitedStorage}。 */
    @Override
    public SerializableStorage create(final Long capacity, final Runnable listener) {
        return new InfiniteStorage(this, this.contents, this.items, this.fluids, listener);
    }

    @Override
    public MapCodec<SerializableStorage> getMapCodec(final Runnable listener) {
        return MapCodec.<SerializableStorage>unit(() -> this.create(null, listener));
    }

    @Override
    public boolean isAllowed(final ResourceKey resource) {
        return InfiniteStorage.accepts(resource, this.items, this.fluids);
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
