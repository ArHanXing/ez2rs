package ar.hxi.ez2rs.terminal.autocraftermanager;

import ar.hxi.ez2rs.terminal.Ez2rsTerminalContent;

import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemContext;
import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;
import com.refinedmods.refinedstorage.common.autocrafting.autocraftermanager.AutocrafterManagerBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.autocraftermanager.AutocrafterManagerData;
import com.refinedmods.refinedstorage.common.autocrafting.autocraftermanager.Ez2rsAutocrafterManagerBridge;
import com.refinedmods.refinedstorage.common.content.Blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 无线自动合成仓管理终端的状态适配器。
 *
 * <p>构造期只做三件事：存字段、造一个<b>不入世界</b>的哑
 * {@link AutocrafterManagerBlockEntity}、以及用当次解析出的网络捕获一次分组快照。
 * 哑 BE 只被原生服务端菜单构造器用来 {@code addWatcher} + 注册红石模式
 * {@code ServerProperty}（{@code setChanged()} 在 {@code level == null} 时天然 no-op），
 * 因此它不需要子类，也不需要任何反射。</p>
 *
 * <p>与 {@code WirelessPatternGridState} 的<b>故意差异</b>（不要"补齐"这些）：</p>
 * <ul>
 *   <li>不 {@code setLevel}：本适配器不需要 recipe/registry 上下文（那是样板网格为了
 *       {@code PatternType} 才需要的）；</li>
 *   <li>不覆盖 {@code setChanged}、不写世界：样板写在真实 {@code PatternInventory} 里；</li>
 *   <li>没有草稿持久化：本特性没有草稿；</li>
 *   <li>不订阅任何 watcher：分组只在开屏时算一次，写入靠原版容器同步广播；</li>
 *   <li>不 {@code drainEnergy}：无线网格收费是因为它持有实时 watcher 并持续下发增量，
 *       这里没有任何持续订阅。</li>
 * </ul>
 *
 * <p>绝不调用 {@code setLevel}/{@code clearRemoved}/{@code setBlockState}/{@code doWork}/
 * {@code updateActiveness}/{@code getContainerProvider().initialize|update|remove}，
 * 也绝不把哑 BE 注册进任何 Level/chunk/registry。</p>
 */
public final class WirelessAutocrafterManagerState {
    private final NetworkItemContext context;
    private final ServerPlayer owner;
    /** 精确的原始 stack 引用，不是副本，也不是保存时按槽位重新查一次。 */
    private final ItemStack terminalIdentity;
    private final SlotReference slotReference;
    private final AutocrafterManagerBlockEntity blockEntity;
    private final Ez2rsAutocrafterManagerBridge.Groups groups;
    private boolean closed;

    public WirelessAutocrafterManagerState(final NetworkItemContext context,
                                           final ServerPlayer owner,
                                           final ItemStack terminalIdentity,
                                           final SlotReference slotReference) {
        this.context = context;
        this.owner = owner;
        this.terminalIdentity = terminalIdentity;
        this.slotReference = slotReference;
        // 哑 BE：只做记账。不入世界、不 setLevel、不 tick、不挂网络。
        // 默认色固定 LIGHT_BLUE（Blocks.COLOR），与方块版默认色一致。
        this.blockEntity = new AutocrafterManagerBlockEntity(
            BlockPos.ZERO,
            Blocks.INSTANCE.getAutocrafterManager().getDefault().defaultBlockState()
        );
        // 只查询一次网络：这份 Groups 同时喂给开场数据与服务端菜单，两条路径绝不重算。
        this.groups = Ez2rsAutocrafterManagerBridge.capture(context.resolveNetwork().orElse(null));
    }

    /** 物品仍在原槽位且仍是本特性的终端物品（引用相等，挡住"物品被换走/复制"）。 */
    private boolean hasTerminalIdentity() {
        return !terminalIdentity.isEmpty()
            && Ez2rsTerminalContent.isAutocrafterManagerCapable(terminalIdentity)
            && slotReference.resolve(owner).orElse(ItemStack.EMPTY) == terminalIdentity;
    }

    /**
     * 是否可写。{@code resolveNetwork()} 每次重新查（{@code NetworkItemContextImpl} 只缓存玩家坐标，
     * 不缓存网络），所以拆控制器 / 走远 / 换维度 / 断电都会被实时反映，网络恢复后自动回到可写，
     * 不需要重开界面。
     *
     * <p>独立物品的 {@code context.isActive()} 走原生"物品电池 > 0"；通用终端走我们的 mixin
     * （网络有电）。两者都不需要这里额外处理。</p>
     */
    public boolean isWritable() {
        return hasTerminalIdentity() && context.resolveNetwork().isPresent() && context.isActive();
    }

    /** 会话寿命只看"同一个人 + 物品仍在原处"，<b>不含</b>网络可达性：断网不关界面，只是不可写。 */
    public boolean canMenuStayOpen(final Player player) {
        return player == owner && hasTerminalIdentity();
    }

    public AutocrafterManagerBlockEntity blockEntity() {
        return blockEntity;
    }

    /** 服务端菜单与开场数据共用的那唯一一份分组快照。 */
    public Ez2rsAutocrafterManagerBridge.Groups groups() {
        return groups;
    }

    /** 仅供开场数据使用；{@code active} 恒 true（见 {@code Groups#data()}）。 */
    public AutocrafterManagerData menuData() {
        return groups.data();
    }

    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }
}
