package ar.hxi.ez2rs.storage;

import ar.hxi.ez2rs.Ez2rs;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * 配置里那串资源 id 的<b>延迟解析结果</b>。
 *
 * <p><b>为什么必须延迟到运行时才查注册表。</b>无限存储方块的注册发生在
 * {@code refinedstorage_plugin} 入口（RS2 自己 {@code onInitialize} 的末尾），而这个时刻
 * <b>早于别的模组注册物品</b>：正式环境里 Fabric 按 mod id 升序调用各模组的 {@code onInitialize}，
 * {@code "ez2rs" < "refinedstorage" < "techreborn"}，所以等到我们跑的时候
 * {@code BuiltInRegistries.ITEM} 里还没有 {@code techreborn:cell}；开发环境更糟，
 * {@code FabricLoaderImpl} 会 {@code Collections.shuffle(modCandidates)}，谁先谁后每次都不一样。
 *
 * <p>如果注册阶段就去解析，唯一的结果是「别人模组的方块在配置里永远加载不出来」，而且日志里写的是
 * {@code unknown item id}——看起来像配置写错了，其实只是查早了。所以这里只保存原始 id 字符串，
 * 真正查注册表推迟到首次访问（世界加载后、网络开始查询资源时），那时所有模组的
 * {@code onInitialize} 早就跑完了。</p>
 *
 * <p><b>为什么只解析一次。</b>资源列表在游戏运行期间不会变（改配置要重启），所以解析结果用
 * {@code volatile} 字段缓存起来；{@code getAll()} 在网络每 tick 的查询路径上，不能每次都查注册表。</p>
 *
 * <p><b>为什么注册阶段仍然不需要资源。</b>方块是物品型还是流体型只取决于配置里 {@code item} /
 * {@code fluid} 哪个数组非空——这是纯字符串判断，与注册表无关。菜单类型、
 * {@code ResourceFactory} 也只看这个，所以整个注册流程都可以在解析之前完成。</p>
 */
public final class InfiniteStorageResources {
    private final ResourceLocation blockId;
    private final List<String> itemIds;
    private final List<String> fluidIds;

    /** 解析结果的缓存；{@code null} 表示还没解析过。用 volatile + 双检锁，见类注释。 */
    private volatile Resolved resolved;

    public InfiniteStorageResources(final ResourceLocation blockId,
                                    final List<String> itemIds,
                                    final List<String> fluidIds) {
        this.blockId = blockId;
        this.itemIds = List.copyOf(itemIds);
        this.fluidIds = List.copyOf(fluidIds);
    }

    /** 首次调用时查注册表并缓存；之后是纯粹的字段读。 */
    public Resolved get() {
        Resolved result = this.resolved;
        if (result != null) {
            return result;
        }
        synchronized (this) {
            result = this.resolved;
            if (result == null) {
                result = this.resolve();
                this.resolved = result;
            }
            return result;
        }
    }

    /** 提前解析（服务端/客户端启动完成时调一次），让日志在可预期的位置出现，不必等首次使用。 */
    public void resolveEagerly() {
        this.get();
    }

    private Resolved resolve() {
        final Set<Item> items = new LinkedHashSet<>();
        final Set<Fluid> fluids = new LinkedHashSet<>();
        for (final String id : this.itemIds) {
            final Item item = findItem(id);
            if (item == null) {
                Ez2rs.LOGGER.warn("Infinite storage block {}: unknown item id \"{}\", skipped", this.blockId, id);
                continue;
            }
            items.add(item);
        }
        for (final String id : this.fluidIds) {
            final Fluid fluid = findFluid(id);
            if (fluid == null) {
                Ez2rs.LOGGER.warn("Infinite storage block {}: unknown fluid id \"{}\", skipped", this.blockId, id);
                continue;
            }
            fluids.add(fluid);
        }
        final List<ResourceAmount> contents = new ArrayList<>(items.size() + fluids.size());
        items.forEach(item -> contents.add(
            new ResourceAmount(new ItemResource(item), InfiniteStorage.INFINITE)
        ));
        fluids.forEach(fluid -> contents.add(
            new ResourceAmount(new FluidResource(fluid), InfiniteStorage.INFINITE)
        ));
        Ez2rs.LOGGER.info("Infinite storage block {} resolved {} item(s) and {} fluid(s)",
            this.blockId, items.size(), fluids.size());
        return new Resolved(List.copyOf(contents), Set.copyOf(items), Set.copyOf(fluids));
    }

    /**
     * {@code BuiltInRegistries.ITEM} 是 {@code DefaultedRegistry}：查不到的 id 会回落成 {@code air}
     * 而不是 {@code null}，所以必须拿默认值判未知——只判 null 会把 {@code minecraft:not_a_thing}
     * 静默变成一团空气。
     */
    private static Item findItem(final String id) {
        final ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        final Item item = BuiltInRegistries.ITEM.get(location);
        return item == null || item == Items.AIR ? null : item;
    }

    /** 同上：{@code BuiltInRegistries.FLUID} 的默认值是 {@code minecraft:empty}。 */
    private static Fluid findFluid(final String id) {
        final ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        final Fluid fluid = BuiltInRegistries.FLUID.get(location);
        return fluid == Fluids.EMPTY ? null : fluid;
    }

    /**
     * 一次解析的全部产物。
     *
     * @param contents {@code Storage#getAll()} 的返回值，数量统一是 {@link InfiniteStorage#INFINITE}
     * @param items    配置里的物品；按物品<b>身份</b>判断，所以带任意数据组件的同种物品
     *                 （附魔书、带损伤的工具、装着内容的桶）也算命中
     * @param fluids   配置里的流体，判断方式同上
     */
    public record Resolved(List<ResourceAmount> contents, Set<Item> items, Set<Fluid> fluids) {
        /**
         * 按"物品/流体身份"而不是 {@code ResourceKey.equals} 判断：配置里给的是
         * {@code new ItemResource(item)}（数据组件为空），而网络上流动的资源可能带组件，
         * 严格相等会把同一件物品的变体当成未知资源。
         */
        boolean accepts(final ResourceKey resource) {
            if (resource instanceof ItemResource itemResource) {
                return this.items.contains(itemResource.item());
            }
            if (resource instanceof FluidResource fluidResource) {
                return this.fluids.contains(fluidResource.fluid());
            }
            return false;
        }
    }
}
