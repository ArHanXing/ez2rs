package ar.hxi.ez2rs.terminal.autocraftermanager;

import ar.hxi.ez2rs.terminal.Ez2rsMenus;

import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;
import com.refinedmods.refinedstorage.common.autocrafting.autocraftermanager.Ez2rsNativeAutocrafterManagerMenu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 无线自动合成仓管理终端的薄菜单：复用原生界面与原生槽位，只加"离线即拒绝写入"一层闸门。
 *
 * <p>它<b>不是</b>只读终端：网络可达时行为 100% 等于方块版（拖入 / 取出 / 数字键交换全部走原生）。
 * 离线时只挡触碰样板槽的操作，玩家背包内部的移动照常。</p>
 *
 * <p>两条构造路径的槽位必须逐一对齐：</p>
 * <ul>
 *   <li>客户端：父类 public 构造器读 data 并自己调一次 {@code resized(0,0,0)}；我们再赋值
 *       {@code disabledSlot} 后<b>补调一次</b> {@code resized(0,0,0)}，让原生
 *       {@code addPlayerInventory} 造出 {@code DisabledSlot}。</li>
 *   <li>服务端：<b>绝不允许</b>调 {@code resized(...)}（它会 {@code resetSlots()} 并用父类那个
 *       恒为空的 {@code groups} 字段重建槽位，把样板槽全丢掉）；改用
 *       {@code ez2rs$disableInventorySlot} 做同位替换。</li>
 * </ul>
 */
public final class WirelessAutocrafterManagerContainerMenu extends Ez2rsNativeAutocrafterManagerMenu {
    /** 父类的同名字段是 private，闸门里 {@code slot.container == playerInventory} 用的是这一份。 */
    private final Inventory playerInventory;
    private final WirelessAutocrafterManagerState wirelessState;
    private boolean closed;

    /** 客户端路径：只用原生下发的同步数据，绝不在此读客户端物品。 */
    public WirelessAutocrafterManagerContainerMenu(final int id, final Inventory inventory,
                                                   final WirelessAutocrafterManagerData data) {
        super(id, inventory, data.data());
        this.playerInventory = inventory;
        this.wirelessState = null;
        disabledSlot = data.slotReference();
        // 父类构造器那次 resized 发生在 disabledSlot 赋值之前，补一次才能生效。
        resized(0, 0, 0);
    }

    /** 服务端路径：打开数据与服务端菜单共用同一个 state（因此也共用同一次 Groups 快照）。 */
    public WirelessAutocrafterManagerContainerMenu(final int id, final Inventory inventory,
                                                   final WirelessAutocrafterManagerState state,
                                                   final SlotReference reference) {
        super(id, inventory, state.blockEntity(), state.groups());
        this.playerInventory = inventory;
        this.wirelessState = state;
        disabledSlot = reference;
        // 服务端不调 resized（见类注释）：同位替换成 DisabledSlot，槽位编号/顺序不变。
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (reference.isDisabledSlot(i)) {
                ez2rs$disableInventorySlot(inventory, i);
                break;
            }
        }
    }

    @Override
    public MenuType<?> getType() {
        return Ez2rsMenus.wirelessAutocrafterManager();
    }

    /** 服务端为真实判定；客户端拿不到 state（null），恒放行，靠原版预测 + 服务端拒绝 + 广播纠偏。 */
    private boolean canEdit() {
        return !closed && (wirelessState == null || wirelessState.isWritable());
    }

    @Override
    public boolean stillValid(final Player player) {
        // 客户端恒 true；服务端不看可达性 —— 断网/换维度/离开区块都不关界面，只是不可写。
        return !closed && (wirelessState == null || wirelessState.canMenuStayOpen(player));
    }

    @Override
    public void removed(final Player player) {
        // 客户端：AbstractContainerScreen 在 EMI 临时替换界面时也会调 removed，但会保留这个 menu
        // 并从那里调用转移方法，所以客户端不能在这里置 closed，否则 canEdit 会变 false 卡住交互。
        if (player.level().isClientSide()) {
            super.removed(player);
            return;
        }
        if (closed) {
            return;
        }
        closed = true;
        super.removed(player); // 父类在这里 removeWatcher；本特性没有网络/持久化操作要做
        if (wirelessState != null) {
            wirelessState.close();
        }
    }

    @Override
    public void clicked(final int index, final int button, final ClickType type, final Player player) {
        if (index >= slots.size() || (index < 0 && index != -999 && index != -1)) {
            return;
        }
        if (!canEdit()) {
            // 拖拽分发（含拖拽提交）一律拒绝：起始拖拽可能发生在上线时。
            if (type == ClickType.QUICK_CRAFT) {
                rejectClick();
                return;
            }
            // 任何触及样板槽的点击都拒绝；背包内部的点击照常落到 super。
            if (index >= 0 && slots.get(index).container != playerInventory) {
                rejectClick();
                return;
            }
        }
        super.clicked(index, button, type, player);
    }

    /**
     * 拒绝一次写入后，强制把服务端的真实槽位内容重新下发一次。
     *
     * <p>为什么必须有这一步：客户端在 {@code MultiPlayerGameMode.handleInventoryMouseClick} 里
     * <b>先</b>本地执行 {@code menu.clicked(...)}（预测），<b>再</b>发包。我们拒掉之后，服务端的
     * {@code slots} 没变、{@code remoteSlots} 也没变，两者仍然相等，
     * 于是 {@code broadcastChanges()} 判定"没有变化"、不会补发 —— 客户端会一直停在预测出来的
     * 错误状态（幽灵物品）。这里主动全量重发，把客户端拉回真实内容。</p>
     *
     * <p>只在服务端可达：客户端 {@link #canEdit()} 恒为 true，永远走不到拒绝分支。</p>
     */
    private void rejectClick() {
        sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        // 原生本就没有登记任何 TransferDestination（QUICK_MOVE 是空操作），这里只是别让离线时走进去。
        if (!canEdit()) {
            rejectClick();
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, index);
    }

    @Override
    public boolean canDragTo(final Slot slot) {
        return (canEdit() || slot.container == playerInventory) && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(final ItemStack stack, final Slot slot) {
        return (canEdit() || slot.container == playerInventory) && super.canTakeItemForPickAll(stack, slot);
    }
}
