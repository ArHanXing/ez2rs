package ar.hxi.ez2rs.storage;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.storage.SerializableStorage;
import com.refinedmods.refinedstorage.common.api.storage.StorageType;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

/**
 * "无限"存储：对配置里的每个资源永久提供 {@link #INFINITE} 的数量，不消耗、不接受插入。
 *
 * <p><b>为什么数量是 1e12。</b>不能用 {@code Long.MAX_VALUE}：RS2 的
 * {@code MutableResourceListImpl.Entry.increment} 没有溢出检查，同网络上另一个存储插入同一资源时
 * {@code MAX + n} 会溢出成负数，把整个网络该资源的数量搞成负值。也不能用 1e15 以上：
 * 每次 {@code addSource}（区块重载 / 网络重建 / 节点重新激活）都会把 {@code getAll()} 的数量
 * 加进网络缓存 list，1e15 几千次就会溢出，1e12 要上百万次，实际安全。1e12 显示成 {@code 1.0T}，
 * 视觉上就是"用不完"。</p>
 *
 * <p><b>为什么 {@link #getStored()} 返回 0 而不是 {@link #INFINITE}。</b>RS2 的
 * {@code StorageRepositoryImpl#removeIfEmpty} 的判据是 {@code storage.getStored() == 0}：
 * {@code StorageBlockBlockEntity#setLevel} 会先给方块随机生成一个 UUID 并把我们的 storage 放进去，
 * 随后读档时 {@code loadAdditional} 读到 {@code sid} 又会 {@code setStorageId(old)} →
 * {@code tryRemoveCurrentStorage} → {@code removeIfEmpty(临时 UUID)}。如果这里返回 {@link #INFINITE}，
 * 那个临时 storage 永远删不掉：每次进世界、每个这种方块都会在 {@code refinedstorage_storages} 里
 * 留一个孤儿条目，并且 RS2 每次都打一条 {@code Storage ... could not be removed} WARN。
 * 返回 0 则清理正常。代价是方块自己打开界面时显示 0，这与 RS2 原版创造存储方块一致
 * （那个方块的"创造"指的是无限<b>容量</b>，内容也从 0 开始）。</p>
 *
 * <p><b>为什么不实现 {@code LimitedStorage}。</b>给出了 capacity 就会被 RS2 当成有限存储，
 * {@code StateTrackedStorage} 会据此算出 {@code FULL} 状态，干扰网络的容量与状态判断。
 * 也不实现 {@code CompositeAwareChild}：这条链路上网络缓存外面包的是 {@code StateTrackedStorage}，
 * 它不是 {@code CompositeAwareChild}，所以 {@code CompositeStorageImpl.extract} 走的是会扣减
 * 父缓存 list 的普通分支——我们阻止不了，也不需要阻止（见 {@link #INFINITE} 的余量说明）。</p>
 */
public final class InfiniteStorage implements SerializableStorage {
    /**
     * 对外的"无限"数量。取 1e12 的理由见类注释：既远大于任何现实用量，又不会在
     * {@code addSource} 的累加里很快溢出。
     */
    public static final long INFINITE = 1_000_000_000_000L;

    private final StorageType type;
    private final Collection<ResourceAmount> contents;
    private final Set<Item> items;
    private final Set<Fluid> fluids;
    private final Runnable markChanged;

    /**
     * @param contents 构造时就算好的资源列表（数量统一是 {@link #INFINITE}），构造后不可变
     * @param items    配置里的物品；{@code isAllowed} 按物品身份判断，因此带任意数据组件的
     *                 同种物品（附魔书、带损伤的工具、装着内容的桶）也算命中
     * @param fluids   配置里的流体，判断方式同上
     * @param markChanged RS2 的"存储内容变了"回调；我们其实永不变化，仅在抽取时按约定通知一次
     */
    public InfiniteStorage(final StorageType type,
                           final Collection<ResourceAmount> contents,
                           final Set<Item> items,
                           final Set<Fluid> fluids,
                           final Runnable markChanged) {
        this.type = type;
        this.contents = List.copyOf(contents);
        this.items = Set.copyOf(items);
        this.fluids = Set.copyOf(fluids);
        this.markChanged = markChanged;
    }

    @Override
    public Collection<ResourceAmount> getAll() {
        return this.contents;
    }

    /** 永远返回 0：见类注释里 {@code removeIfEmpty} 的说明，这是刻意的，不是 bug。 */
    @Override
    public long getStored() {
        return 0;
    }

    /** 永不接收：否则网络会往"无限"方块里塞东西，而这些东西会随方块消失。 */
    @Override
    public long insert(final ResourceKey resource, final long amount, final Action action, final Actor actor) {
        return 0;
    }

    /**
     * 配置内资源恒返回请求量。调用方（RS2 的网络缓存）保证 {@code amount > 0} 并且已经用缓存值截断过，
     * 所以这里不需要再和 {@link #INFINITE} 取小。
     */
    @Override
    public long extract(final ResourceKey resource,
                        final long amount,
                        final Action action,
                        final Actor actor) {
        if (!this.accepts(resource)) {
            return 0;
        }
        if (action == Action.EXECUTE) {
            this.markChanged.run();
        }
        return amount;
    }

    @Override
    public StorageType getType() {
        return this.type;
    }

    /**
     * 按"物品/流体身份"而不是 {@code ResourceKey.equals} 判断：配置里给的是
     * {@code new ItemResource(item)}（数据组件为空），而网络上流动的资源可能带组件，
     * 严格相等会把同一件物品的变体当成未知资源。
     */
    private boolean accepts(final ResourceKey resource) {
        return accepts(resource, this.items, this.fluids);
    }

    /** {@link InfiniteStorageType#isAllowed} 与抽取判定共用同一套规则，避免两处判据漂移。 */
    static boolean accepts(final ResourceKey resource, final Set<Item> items, final Set<Fluid> fluids) {
        if (resource instanceof ItemResource itemResource) {
            return items.contains(itemResource.item());
        }
        if (resource instanceof FluidResource fluidResource) {
            return fluids.contains(fluidResource.fluid());
        }
        return false;
    }
}
