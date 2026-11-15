package com.refinedmods.refinedstorage.common.autocrafting.autocraftermanager;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.node.GraphNetworkComponent;
import com.refinedmods.refinedstorage.common.api.autocrafting.Autocrafter;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.world.Container;

/**
 * 同包普通类（不是 mixin）：复刻 RS2 2.0.9 的
 * {@link AutocrafterManagerBlockEntity#getGroups()}，把一次网络查询的结果交给无线菜单复用。
 *
 * <p>为什么必须放在这个包：{@code AutocrafterManagerBlockEntity.Group} /
 * {@code SubGroup} 是包私有 record（{@code AutocrafterManagerBlockEntity.java:114-132}），
 * 只有同包类型能 new 它们的 canonical 构造器。这里没有任何反射。</p>
 *
 * <p>{@code getGroups()} 本身是 private（同包也拿不到），所以排序规则必须在此逐字复刻，
 * 与 {@code AutocrafterManagerBlockEntity.java:86-112} 对应：
 * 组内按 {@code getLocalPosition} → 按名称文本分组 → 组间按名称文本排序。
 * RS 升级后如果那条链变了，这里要同步（风险已登记在 PLAN §9.4）。</p>
 */
public final class Ez2rsAutocrafterManagerBridge {
    private Ez2rsAutocrafterManagerBridge() {
    }

    /**
     * 捕获一次分组快照。{@code network == null}（未绑定 / 不可达）时返回空分组，
     * 界面仍会正常打开、只显示玩家背包 —— 与方块版"这个网络上没有自动合成仓"表现一致。
     *
     * <p>{@code Groups} 一旦返回就不可变，且必须由调用方同时喂给"开场数据"与"服务端菜单"，
     * 两条路径绝不许各自重算（否则槽位数量/顺序可能漂移）。</p>
     */
    public static Groups capture(final Network network) {
        if (network == null) {
            return Groups.EMPTY;
        }
        return new Groups(collect(network));
    }

    /** 与原生 {@code getAutocrafters()} + {@code getGroups()} 等价的一次完整查询。 */
    private static List<AutocrafterManagerBlockEntity.Group> collect(final Network network) {
        return network.getComponent(GraphNetworkComponent.class)
            .getContainers(Autocrafter.class)
            .stream()
            .sorted(Comparator.comparing(Autocrafter::getLocalPosition))
            .collect(Collectors.groupingBy(autocrafter -> autocrafter.getAutocrafterName().getString()))
            .entrySet()
            .stream()
            .map(entry -> new AutocrafterManagerBlockEntity.Group(
                entry.getKey(),
                entry.getValue()
                    .stream()
                    .sorted(Comparator.comparing(Autocrafter::getLocalPosition))
                    .map(Ez2rsAutocrafterManagerBridge::subGroupOf)
                    .toList()
            ))
            .sorted(Comparator.comparing(AutocrafterManagerBlockEntity.Group::name))
            .toList();
    }

    /**
     * {@code SubGroup.of} 是包私有静态辅助（且内部 {@code isFull} 也是 private），
     * 这里内联等价逻辑，不反射调用。
     */
    private static AutocrafterManagerBlockEntity.SubGroup subGroupOf(final Autocrafter autocrafter) {
        final Container container = autocrafter.getPatternContainer();
        return new AutocrafterManagerBlockEntity.SubGroup(
            container,
            autocrafter.isVisibleToTheAutocrafterManager(),
            isFull(container)
        );
    }

    /** 与 {@code AutocrafterManagerBlockEntity.SubGroup.isFull} 逐字一致。 */
    private static boolean isFull(final Container container) {
        for (int i = 0; i < container.getContainerSize(); ++i) {
            if (container.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 一次捕获的分组快照。
     *
     * <p>{@link #data()} 供开场数据使用；{@link #list()} 是包私有的，因为它的元素类型
     * （{@code AutocrafterManagerBlockEntity.Group}）是包私有 record，只有同包的原生子类
     * 菜单能读取它。</p>
     */
    public static final class Groups {
        public static final Groups EMPTY = new Groups(List.of());

        private final List<AutocrafterManagerBlockEntity.Group> groups;

        private Groups(final List<AutocrafterManagerBlockEntity.Group> groups) {
            this.groups = groups;
        }

        /**
         * 客户端需要的视图。{@code active} 恒为 {@code true}：原生
         * {@code AutocrafterManagerScreen.renderRows()} 在 {@code !isActive()} 时只画灰底并 return，
         * 传 false 会把布局整块藏掉，直接违反"离线也要看到布局"的需求。
         * <b>不要</b>"修正"成 {@code mainNetworkNode.isActive()}（PLAN §3(g) / §9.8）。
         */
        public AutocrafterManagerData data() {
            return new AutocrafterManagerData(
                groups.stream().map(Ez2rsAutocrafterManagerBridge::dataGroup).toList(),
                true
            );
        }

        List<AutocrafterManagerBlockEntity.Group> list() {
            return groups;
        }
    }

    /** {@code AutocrafterManagerData.Group.of} 可用，但它的 {@code SubGroup.of} 是 private，故整体内联。 */
    private static AutocrafterManagerData.Group dataGroup(final AutocrafterManagerBlockEntity.Group group) {
        return new AutocrafterManagerData.Group(
            group.name(),
            group.subGroups().stream()
                .map(subGroup -> new AutocrafterManagerData.SubGroup(
                    subGroup.container().getContainerSize(),
                    subGroup.visibleToTheAutocrafterManager(),
                    subGroup.full()
                ))
                .toList()
        );
    }
}
