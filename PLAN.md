# PLAN — 无线自动合成仓管理终端（ez2rs:wireless_autocrafter_manager）

> Stage A of A→B→C→D. This file is the contract for B (skeleton) / C (content) / D (review+build).
> All API facts below were read from the vendored RS2 2.0.9 source tree in `.rs2src/` and the shipped
> ez2rs sources in `src/main/java`. Verification date: **2026-11-15**. Nothing here relies on external
> maven coordinates — no new third-party dependency is introduced.

---

## TL;DR（B/C 直接照这个动手，细节看后面各节）

**新建 8 个 Java 文件 + 3 个资源 json；改 10 个已有文件。零新依赖，1 个 client-only mixin。**

```
com/refinedmods/refinedstorage/common/autocrafting/autocraftermanager/
  Ez2rsAutocrafterManagerBridge.java        # 同包普通类：复刻 getGroups()，能 new 包私有 record
  Ez2rsNativeAutocrafterManagerMenu.java    # 同包 normal class extends AutocrafterManagerContainerMenu
ar/hxi/ez2rs/terminal/autocraftermanager/
  WirelessAutocrafterManagerData.java       # record(AutocrafterManagerData, SlotReference) + STREAM_CODEC
  WirelessAutocrafterManagerState.java      # 哑 BE + Groups + isWritable()
  WirelessAutocrafterManagerContainerMenu.java  # 薄菜单：getType/stillValid/离线闸门
ar/hxi/ez2rs/terminal/
  AutocrafterManagerOpener.java             # 捕获 Groups 一次，喂给 createMenu 与 opening data
  WirelessAutocrafterManagerItem.java
ar/hxi/ez2rs/mixin/AutocrafterManagerScreenMixin.java   # 仅补"切换终端"侧边按钮
data/ez2rs/recipe/{wireless_autocrafter_manager,terminal_upgrade_autocrafter_manager}.json
assets/ez2rs/models/item/wireless_autocrafter_manager.json
```

**三条铁律（违反必崩，D 必须 grep 校验）**
1. 服务端**不许**调 `resized(...)`；客户端**必须**在 `disabledSlot` 赋值后调一次 `resized(0,0,0)`。
2. 开场数据与服务端菜单用**同一条 `Groups`**（同一次网络查询、同一次排序）。
3. `active` 恒为 `true`（客户端 data 与服务端都别用 `mainNetworkNode.isActive()`）。

**其余**：可写；纯原生槽位交互；离线只挡样板槽、放行背包内移动；不新增网络包；不改 `fabric.mod.json`。

---

## 0. 需求修正（已与用户确认，覆盖早期"只读"设定）

1. **可写**：本终端可以像普通终端一样把样板放进/取出自动合成仓的样板槽位。删除"只读"设计。
2. 上传交互 = **纯原生槽位交互**：拖入、数字键交换、取出；**不新增按钮、不新增 C2S 包、不做批量填充辅助**。
3. 离线（物品绑定的网络不可达 / 未授权 / 物品能量为 0）时**禁止写入样板槽位**，但仍显示打开时收到的分组/槽位布局（界面不能变空白）。玩家背包内部的交互必须照常可用。
4. 依然：复用 RS2 原生 `AutocrafterManagerBlockEntity` / `AutocrafterManagerContainerMenu` / `AutocrafterManagerScreen` / `AutocrafterManagerData`；同包桥接；不注册方块、不挂世界。

### 0.1 必须向用户/后续阶段说明的原生行为事实

- RS2 2.0.9 的自动合成仓管理菜单**没有接 TransferManager**：
  `AutocrafterManagerContainerMenu` 全文没有 `transferManager.addTransfer(...)`（已验证），
  而 `AbstractBaseContainerMenu.quickMoveStack()` 只调 `transferManager.transfer(index)`，
  目标表为空 → **原生 shift-click（QUICK_MOVE）本来就是空操作**。
  ⇒ 本特性"支持 shift-click"= 与方块版完全一致（无操作）。**不要**为了实现 shift-click 而给
  `TransferManager` 加 `addBiTransfer(playerInventory, 每个 SubGroup 容器)`：目标表按 Container 身份建键，
  需要为每个 SubGroup 逐一注册，且 `TransferDestination` 走 Fabric `InventoryStorage` 插入（不走 `mayPlace`），
  会把非样板物品塞进 `PatternInventory` 的风险留给平台层 —— 违背"尽量薄"的要求。
  此点在 PLAN 中登记为**已知 UX 差距（与原生一致）**；若用户日后要真 shift-click，另开一个特性做，
  并在菜单里手写一次"按组顺序找同组空槽"的搬运（不要动 TransferManager）。
- 原生的"灰屏"由 `menu.isActive()` 驱动：`AutocrafterManagerScreen.renderRows()` 在 `!isActive()` 时
  **只画灰底并 return**（连分组名都不画）。因此**不能**在离线时把 `active` 翻成 false，否则违反需求 3
  （布局被藏起来）。⇒ 见 (g) 的"开场前置条件 + active 恒 true"决议。**绝不订阅/转发
  `AutocrafterManagerActivePacket`。**

---

## 1. 目标与验收标准

**目标**：玩家手持 ez2rs 的无线物品，在任意维度打开 RS2 原生"自动合成仓管理"界面，看到绑定网络上
所有 `Autocrafter` 的分组与样板槽位，并能像方块版一样拖入/取出/数字键交换样板；物品无网络可达时仍能看到
布局但不能改样板。

**验收（D 交付时逐条核对）**
1. `./gradlew build` 通过，产出 jar。
2. 独立物品 `ez2rs:wireless_autocrafter_manager` 可由配方合成，右键打开原生界面，标题为物品名。
3. 通用终端可解锁第 5 页（`autocrafter_manager`），左侧按钮切页后进入同一界面。
4. 跨维度（Nether/Overworld 之间）可见同一网络的全部自动合成仓；分组名 = 自动合成仓自定义名，按名排序。
5. 把样板从背包拖进任一空样板槽 → 关闭界面后该自动合成仓（方块版界面）里确实出现了该样板；
   取出同理。（写穿验证）
6. **会话中途**失去网络（控制器被拆 / 走远 / 换维度 / 能量耗尽）时：界面保持最近一次收到的布局不变灰、
   槽位仍可见，但拖入/取出/数字键交换/拖拽分发被**服务端**拒绝；玩家背包内部的移动照常。
   （**首次**在离线状态打开时没有"上次布局"可显示，只有玩家背包 —— 这与方块版"网络上没有自动合成仓"
   表现一致，属预期；需求 3 的"显示上次布局"指会话中途断网的情形，本 PLAN 按此实现。）
7. 玩家背包槽位里那件终端物品自身被 DisabledSlot 占位（与原生无线终端一致）。
8. **恰好新增一个** client-only mixin（`AutocrafterManagerScreenMixin`，只为补"切换终端"侧边按钮），
   位于 `ar.hxi.ez2rs.mixin` 包；无客户端类被 common 侧加载；无 REGISTER 到世界的 BlockEntity。

---

## 2. 平台 / 版本 / Era 判定

- 项目既有事实：`build.gradle` = `net.fabricmc.fabric-loom-remap 1.17.19`；`gradle.properties` =
  MC **1.21.1**、Loader 0.19.3、Fabric API 0.116.17、RS2 **2.0.9**；`fabric.mod.json` 存在
  ⇒ **Fabric / 1.21.1 / mojmap / Java 21（toolchain）**，属 Modern 1.20.5–1.21.x 线。
- 本特性**不改变**平台、不新增依赖、不改 wrapper（9.5.1）、不改 `fabric.mod.json`。
- 参照已落地的样板网格特性（`ar/hxi/ez2rs/terminal/patterngrid` + `…/autocrafting/patterngrid`）保持同构。

---

## 3. 设计决议（对应提问 a–g，逐条给证据）

### (a) 分离式 BE 适配器：**用哑 BE，但绝不把网络塞进它**
- 原生**服务端构造器** `AutocrafterManagerContainerMenu(int, Inventory, AutocrafterManagerBlockEntity, List<Group>)`
  是包私有且**必须**有 BE 实例（`.rs2src/.../autocraftermanager/AutocrafterManagerContainerMenu.java:59-74`）。
  它只对 BE 做两件事：`addWatcher(this)`（包私有）、`new ServerProperty<>(REDSTONE_MODE, be::getRedstoneMode, be::setRedstoneMode)`。
- 结论：构造一个**普通** `new AutocrafterManagerBlockEntity(BlockPos.ZERO, Blocks.INSTANCE.getAutocrafterManager().getDefault().defaultBlockState())`
  （public 构造器：`AutocrafterManagerBlockEntity.java:36`；默认色 `Blocks.COLOR = DyeColor.LIGHT_BLUE`，`Blocks.java:55`）。
  **不**调用 `setLevel` / `clearRemoved` / `setBlockState` / `doWork` / `getContainerProvider().initialize`。
- `setChanged()` 不需要子类：`AbstractBaseNetworkNodeContainerBlockEntity.setChanged()` 是
  `if (level != null) level.blockEntityChanged(...)`（`…/support/network/AbstractBaseNetworkNodeContainerBlockEntity.java:250-257`），
  level == null ⇒ 天然惰性。**所以本特性连 BE 子类都不需要，也不需要反射。**
- **不**给 `mainNetworkNode.setNetwork(network)`：这样不存在"指向旧网络的过期引用"，
  也不需要 `activenessChanged` 语义。分组改由同包桥直接从**当次解析出的 Network** 计算（见 (b)）。
- 哑 BE 上会被原生代码碰到的三个成员都安全（已逐个核对）：
  - `getRedstoneMode()` → `verifyHasRedstoneMode()` 只在 `!hasRedstoneMode()` 时抛；基类默认 `true`
    （`AbstractBaseNetworkNodeContainerBlockEntity.java:228-241`），**不会抛**。
  - `setRedstoneMode()` → `setChanged()` → `if (level != null)`（同上 `:243-257`），level 为 null ⇒ no-op。
  - `getName()` → 常量 `ContentNames.AUTOCRAFTER_MANAGER`，无世界访问。
  ⇒ 服务端 `ServerProperty(REDSTONE_MODE, be::getRedstoneMode, be::setRedstoneMode)` 可以安全使用。
- 构造期安全性（已核对）：`AbstractNetworkNodeContainerBlockEntity` 构造器只做
  `createContainerProvider()` + `createMainContainer()`（`…/common-api/.../AbstractNetworkNodeContainerBlockEntity.java:17-25`），
  后者经 `RefinedStorageApi.INSTANCE.createNetworkNodeContainer(this, node)` 造 `InWorldNetworkNodeContainerImpl`
  + `ColoredConnectionStrategy(this::getBlockState, getBlockPos())`
  （`InWorldNetworkNodeContainerBuilder.java:22-26`）。**构造期不读 `level`、不查 chunk、不碰 registry**；
  `getPosition()` 里的 `requireNonNull(blockEntity.getLevel())` 只有在我们主动调用时才会抛，而本特性从不调用。
- `activenessChanged` 只在 `updateActiveness`（由 `NetworkNodeBlockEntityTicker` 驱动）里被调用；
  哑 BE 不入世界、不被 tick ⇒ **永不触发**，`watchers` 集合永远不会被通知（只增删，无回调）。
- `getMenuData()` 会读 `mainNetworkNode.isActive()`（哑 BE 恒 false，`AbstractNetworkNode.java:33`）
  ⇒ **禁止调用 `state.getMenuData()`**；开场数据由桥构造。
- `stillValid`：原生返回 `Container.stillValidBlockEntity(be, player)`，`be.getLevel() == null` ⇒ **false**
  ⇒ 必须覆盖（见 §4.7）。

### (b) 无线网络获取：`NetworkItemContext`，直接读 `GraphNetworkComponent`
- `RefinedStorageApi.INSTANCE.getNetworkItemHelper().createContext(stack, player, slotReference)`
  （既有先例：`ar/hxi/ez2rs/terminal/PatternGridOpener.java:26-27`）。
- `context.resolveNetwork()` → `Network`；`network.getComponent(GraphNetworkComponent.class).getContainers(Autocrafter.class)`
  返回 `Set<Autocrafter>`（`GraphNetworkComponent.java:29`；`Autocrafter` 是 STABLE API，`Autocrafter.java:8-17`，
  提供 `getAutocrafterName()/getPatternContainer()/isVisibleToTheAutocrafterManager()/getLocalPosition()`）。
- **不**复用 `Ez2rsManagedWirelessGrid`（它绑定 GridWatcher / RootStorage 语义，本特性只需要容器集合）；
  本特性**不订阅任何 watcher**，因此也**不调用 `context.drainEnergy(openEnergyUsage)`** —— 与无线网格的差异
  要在代码注释里写明（无线网格收费是因为它持有实时 watcher 并持续下发资源增量）。
- `NetworkItemContextImpl` 在构造时**拷贝玩家坐标与维度**（`NetworkItemContextImpl.java:31-45`），
  所以"边走边开"不会像注释里担心的那样中途失效；`resolveNetwork()` 每次重新查方块实体，天然反映实时可达性。

### (c) 可写 + 离线闸门：只做一层"离线即拒绝"，不做槽级同步
- 删掉只读层；`PatternSlot`（`PatternSlot.java:9-13`）继续用 `PatternProviderItem.isValid(stack, level)` 做
  `mayPlace`，非样板物品由原版点击流程挡下（`AbstractContainerMenu.doClick` 会问 `slot.mayPlace`），无需我们处理。
- 唯一新增闸门：`isWritable()`（判定：`hasTerminalIdentity() && context.resolveNetwork().isPresent() && context.isActive()`）。
- 客户端无法知道可达性，因此**客户端恒放行**（`canEdit()` 在客户端为 true），由服务端拒绝并靠原版
  `broadcastChanges` 纠偏 —— 与 `WirelessPatternGridContainerMenu` 的 `canEdit() = state == null ? isActive() : state.isWritable()`
  完全同构（`WirelessPatternGridContainerMenu.java:51-54`）。

### (d) 活动状态推送 / 布局刷新
- **不推送** `AutocrafterManagerActivePacket`：见 (g)（会让界面变灰并藏掉布局，违反需求 3）。
  哑 BE 不 tick ⇒ 原生 `activeChanged()` 永不被调用，天然"零同步"，不需要新包。
- **不需要刷新分组/槽位内容**：分组结构与 `SubGroup` 的槽位数在会话期内不会因写入而改变（容器身份与
  `getContainerSize()` 不变）。写入后的样板内容由**原版容器同步**机制广播（见 §4.4）：服务端每 tick
  `broadcastChanges()` 比对 `lastSlots`，变化即发 `ClientboundContainerSetSlot`；同一自动合成仓的方块版界面
  也在同一条 `ItemStack` 上，各自同步。**不需要我们发任何包。**
- 已知且接受的陈旧点（与方块版一致）：`SubGroup.full` 与 `visibleToTheAutocrafterManager`、
  以及搜索索引都是打开时快照；`ViewType.NOT_FULL` 的过滤在会话中途可能不准（刚塞满的仓仍显示在 NOT_FULL 里）。

### (e) 数据 codec：**原样复用 + 外层包 SlotReference**
```java
public record WirelessAutocrafterManagerData(AutocrafterManagerData data, SlotReference slotReference)
```
- `AutocrafterManagerData` 是 public record，`STREAM_CODEC` 是 public（`AutocrafterManagerData.java:22-27`），
  解码只带 slotCount/visible/full 与 active，**不带内容**，正合适。
- 写法与 `WirelessPatternGridData`（`…/terminal/patterngrid/WirelessPatternGridData.java`）逐字同构：
  显式 `StreamCodec`（不用 `composite`），`SlotReferenceFactory.STREAM_CODEC` 解/编槽位引用。
- 菜单**必须**覆盖 `getType()` 返回我们自己的 MenuType，否则 Fabric 用父类的数据 codec（既有先例已证明）。
  细节（已核对原生构造器）：父类两个 ctor 都在 `super(...)` 里传 `Menus.INSTANCE.getAutocrafterManager()`，
  所以我们这个子类**构造期**的 `type` 字段是父类型 —— 这没影响，因为 Fabric 只在
  `initMenu`/`openMenu` 时读 `menu.getType()`，此时子类覆盖已生效。**不要**试图"修"这一点
  （`AbstractContainerMenu` 的 type 字段只能经由 super 设一次，也无法在 RS2 的 ctor 之后再改）。

### (f) 通用终端整合 + 配方（无环）
- `UniversalTerminalType` 增加常量 `AUTOCRAFTER_MANAGER("autocrafter_manager")`；
  `getDisplayName()` 走 `gui.ez2rs.universal_terminal.type.autocrafter_manager`；`getSpriteName()` 见 §7 占位说明。
- `TerminalTypes.isItemOf(..., AUTOCRAFTER_MANAGER)` → `ez2rs:wireless_autocrafter_manager`
  （**跟随样板网格先例**：页解锁用无线物品，不用方块版，`TerminalTypes.java:15-18` 的注释已写明理由）。
- `Ez2rsRecipes.register()` 遍历 `UniversalTerminalType.values()` 自动注册 serializer
  （`Ez2rsRecipes.java:25-35`）⇒ 只需加一条 json：`ez2rs:terminal_upgrade_autocrafter_manager`。
- `WirelessUniversalGridItem.useDelegate` 增加 `case AUTOCRAFTER_MANAGER -> AutocrafterManagerOpener.open(...)`。
- **切换按钮**：`PatternGridScreen` 能自动获得"切换终端"按钮，是因为它 `extends AbstractGridScreen`
  而 `AbstractGridScreenMixin` 注入了基类；`AutocrafterManagerScreen extends AbstractStretchingScreen`
  **拿不到**这个按钮，从通用终端进入本页后会被困住（只能关界面再右键）。
  对监视器，本项目已有的解法是专用 mixin（`AutocraftingMonitorScreenMixin`）。本特性照抄该先例：
  新增 `ar/hxi/ez2rs/mixin/AutocrafterManagerScreenMixin.java`，注入 `AutocrafterManagerScreen#init(I)V`
  的 `TAIL`，调 `UniversalTerminalButtonHelper.addIfUniversalTerminal((AbstractBaseScreen<?>) self)`。
  - 为什么用 `addIfUniversalTerminal` 而不是监视器用的 `addIfCarryingUniversalTerminal`：
    本菜单**确实**设置了 `disabledSlot`（§4.3），因此 `AbstractBaseContainerMenuAccessor` 能精确解析到
    手持的终端物品，不会误判背包里躺着的另一件。
  - 独立物品打开时不显示按钮：`addButtonFor` 只在 `stack.getItem() instanceof WirelessUniversalGridItem` 时继续。
- 配方无环：
  - 独立物品 = `refinedstorage:…autocrafter_manager`（方块/物品标签）+ 石英富铁 + 末影珍珠 + 高级处理器；
    **不含**通用终端。
  - 页解锁 = 通用终端 + **无线**自动合成仓管理终端（消耗后者）；不使用方块版，故与独立配方不构成环。

**精确 JSON（B 直接抄，不要自创字段名）**

`src/main/resources/data/ez2rs/recipe/wireless_autocrafter_manager.json`
```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": ["EPE", "AGA", "EPE"],
  "key": {
    "E": { "item": "refinedstorage:quartz_enriched_iron" },
    "P": { "tag": "c:ender_pearls" },
    "A": { "item": "refinedstorage:advanced_processor" },
    "G": { "item": "refinedstorage:autocrafter_manager" }
  },
  "result": { "id": "ez2rs:wireless_autocrafter_manager" }
}
```
（`G` 若想覆盖所有颜色，改成 `"tag": "refinedstorage:autocrafter_managers"` —— 见 §6 该行的 UNVERIFIED 说明。）

`src/main/resources/data/ez2rs/recipe/terminal_upgrade_autocrafter_manager.json`
```json
{ "type": "ez2rs:terminal_upgrade_autocrafter_manager", "category": "misc" }
```

`src/main/resources/assets/ez2rs/models/item/wireless_autocrafter_manager.json`
```json
{
  "parent": "minecraft:item/generated",
  "textures": { "layer0": "ez2rs:item/wireless_pattern_grid" }
}
```
（占位贴图，见 §7。）

`lang` 两条 key（中/英都要）：
- `item.ez2rs.wireless_autocrafter_manager` → `无线自动合成仓管理终端` / `Wireless Autocrafter Manager`
- `gui.ez2rs.universal_terminal.type.autocrafter_manager` → `自动合成仓管理器` / `Autocrafter Manager`

### (g) 网络在会话中途断开时（可写菜单）——决议
这是"哑 BE + 不订阅 watcher"方案必须正面回答的问题。

**打开前置条件（`AutocrafterManagerOpener.open`）**：与 `PatternGridOpener` 同构 ——
`reference.resolve(player).orElse(EMPTY) != stack` 或 `!isManagerCapable(stack)` ⇒ 直接 return（不打开）。
除此之外**不**要求网络可达（否则离线就拿不到布局，违反需求 3）。进场时：
- 只解析**一次** `NetworkItemContext`，用它算一次 `Groups`。
- `network == null`（未绑定 / 网络不存在）⇒ `Groups` 为空，界面正常打开、只有玩家背包
  （与方块版"这台网络上没有任何自动合成仓"表现一致；首次离线打开本来就没有"上次布局"可显示）。
- `network != null` ⇒ 正常捕获分组。
- **`active` 恒传 `true`**（无论 `network` 是否为 null），不传 `mainNetworkNode.isActive()`、不传"网络有电"。
  这是与方块版的**有意分歧**，理由：`AutocrafterManagerScreen.renderRows()` 在 `!isActive()` 时
  **只画灰底并 return**（连分组名都不画，`AutocrafterManagerScreen.java:158-168`），
  一旦传 false 就等于把布局藏掉，直接违反需求 3。而"能不能写"由菜单闸门（§4.5）独立表达，
  不需要靠灰屏。代价：无法用灰屏提示"网络没电"；可接受（不为此另写屏幕，违反"复用原生界面"）。
  代码里必须写注释说明这条分歧，避免后人"修正"回 `isActive()`。

**会话中途网络消失（拆控制器 / 走远 / 换维度丢传送 / 能量耗尽）**：
1. `watchers` 集合：哑 BE 的 `addWatcher` 只被服务端菜单构造器调用一次，`removed()` 里 `removeWatcher`
   一次；集合里最多 1 个元素，**永不触发回调**（无 ticker）。网络断开与它无关，**没有泄漏**。
   因为 `removeWatcher` 只做 `Set.remove`，`removed()` 在 BE 存活/已消失的任何状态下都安全 ⇒ 不会出现
   `WirelessGrid` 里"watch 已加、网络不可达、remove 时拿不到 storage"那类问题（我们根本不订阅）。
2. `AutocrafterManagerActivePacket`：**不发**。理由见 §0.1 —— 客户端 `setActive(false)` 会让
   `renderRows` 直接灰屏 return，把布局藏掉，直接违反需求 3。副作用：服务端 `active` 也保持 true，
   但 `active` 只影响**可见性/渲染**与 `initializeGroup` 里的 `visible` 布尔，**不影响槽位是否入列**
   （`AutocrafterManagerContainerMenu.java:158-181`：`slotVisible` 只传进 `AutocrafterManagerSlot`
   的 `active` 参数，槽位对象照样 `addSlot`）。所以 `active` 陈旧**不会**引起槽位错位。
3. `stillValid`：**留在界面里**（§4.7），只是 `isWritable()` 翻 false。不主动 `player.closeContainer()`。
4. `SubGroup.container`：**不置 null、不重建**。容器仍指向真实方块实体；读取安全，写入被菜单闸门挡住。
   这也保证 `broadcastChanges()` 每 tick 的 `getItem()` 不会 NPE。
5. 网络**恢复**后：`resolveNetwork()` 每次重新查，`isWritable()` 自动回到 true，无需重开界面。
   （已知限制：此时 `Groups` 仍是旧快照，若网络结构变了不会刷新 —— 见风险清单第 7 条。）

**结论**：本特性对"网络断开"的响应是**热切换可写性**，不是关界面、不是灰屏、不是重开。

---

## 4. 关键技术契约（B/C 必须逐字遵守）

### 4.1 槽位顺序/数量对等（否则物品同步错位，**最高优先级**）
服务端：`addServerSideSlots(groups)` 先按组加**全部**样板槽，再 `addPlayerInventory(inventory, 0, 0)`；
客户端：父类 public 构造器读完 data 后调 `resized(0,0,0)`，`initializeGroups` 里 `resetSlots()` 后
**对每个 SubGroup 的每个 index 都 `addSlot`**（`AutocrafterManagerContainerMenu.java:159-181`），
可见性只影响 x/y/`isActive`，不影响是否入列。

**必须遵守的排序规则（与原生逐字一致，`AutocrafterManagerBlockEntity.java:86-112`）**：
1. 每个组内先按 `Comparator.comparing(Autocrafter::getLocalPosition)` 排序 —— **逐字照抄这句表达式**，
   不要手写 x/y/z 比较器。（`getLocalPosition()` 返回 `BlockPos`，其 `compareTo` 来自 `Vec3i`；
   具体轴序不重要，重要的是与原生用同一个比较器。）
2. 再 `Collectors.groupingBy(a -> a.getAutocrafterName().getString())` 分组（key = 纯文本，不是 Component）。
3. 组之间 `.sorted(Comparator.comparing(group -> group.name))`（Java `String.compareTo`，非本地化排序）。
4. 组内 SubGroup 的槽位 index 取 `0 .. container.getContainerSize()-1`（自动合成仓恒为 9，但用实参，不硬编码）。
5. 之后才是玩家背包：主背包 containerId 9..35（3 行），再快捷栏 0..8（`AbstractBaseContainerMenu.addPlayerInventory`）。
6. 两边槽位数必须逐个 SubGroup 相等：客户端用 `data` 里的 `slotCount`（由服务端
   `subGroup.container().getContainerSize()` 在**捕获时**算出，`AutocrafterManagerData.java:36-42`），
   服务端用同一 `Groups` 里的容器在**建菜单时**读 `getContainerSize()`。`PatternInventory` 容量在
   构造时固定（`AutocrafterBlockEntity.java:83`，`PATTERNS = 9`），所以这两次读取必然一致；
   **但前提是同一条 `Groups`**（否则理论上可能跨了一次重组）。

**`level` 用的是玩家所在的 Level，不是自动合成仓所在的 Level**：服务端
`addServerSideSlots(group, playerInventory.player.level())`（`AutocrafterManagerContainerMenu.java:107-122`），
客户端 `ViewGroup.from(playerInventory.player.level(), …)`（同上 `:54`）。跨维度时就是"玩家维度"。
这是**原生的有意行为**（`PatternSlot` 只用 level 做 `PatternProviderItem.isValid` 的上下文），
我们不要"修正"成本地维度 —— 那会与服务端 `isValid` 结果不一致，导致放置被拒。

**`removed(Player)` 必须调 `super.removed(player)`**（父类在里面 `removeWatcher`），再置 `closed = true`；
**不要**在 `removed` 里做任何网络/持久化操作（本特性没有 draft，也没有 watcher 需要手动解绑）。

**`active` 有两个互不相干的副本**：
- **客户端** `active` 只来自 `data.active()`（`…:55`），只有它影响渲染/可见性（`renderRows`、`initializeGroup`）。
  **必须为 `true`**。若为 false：`visible=false` → `autocrafterSlots` 为空、`renderRows` 灰屏 return，
  但槽位对象**仍在 `slots` 里**（`addSlot` 无条件执行），同步不错位、只是"看不见"；
  更糟的是 `initializeGroup` 的 `rowY` 只在 `slotsWithinGroup > 0` 时推进（`…:182-185`），
  active=false 时所有组的槽位会**叠在同一坐标**上、且照样可点 —— 这是必须避免的伪影。
- **服务端** `active` 字段在服务端 ctor 里**根本没被赋值**（默认 false，`…:46`、`:59-74`），
  而唯一读它的 `initializeGroup` 服务端从不调用（§4.2）⇒ 服务端 `active` 死值，无需关心。
⇒ 桥的 `data()` 固定传 `true`（见 (g)）；服务端构造器不需要额外的 active 参数。

**同一个 `List<Group>` 实例必须同时用于「开场数据」与「服务端菜单」**：开场器只捕获一次，
`getScreenOpeningData()` 与 `createMenu()` 都读同一份；**不许**在 `getScreenOpeningData()` 里重算
（Fabric 这两个回调的调用顺序不作保证，重算会在两次查询之间因样板/名称变动而错位）。

**桥能直接构造这两个包私有 record（关键可行性证据）**：`Group`/`SubGroup` 是
`AutocrafterManagerBlockEntity` 的嵌套包私有 record（`AutocrafterManagerBlockEntity.java:114-132`），
record 的 canonical 构造器与访问器对其同包类型可见 ⇒ 同包普通类可以
`new AutocrafterManagerBlockEntity.Group(name, subs)`、`new AutocrafterManagerBlockEntity.SubGroup(container, visible, full)`，
并直接 `new AutocrafterManagerData.Group(name, dataSubs)`。**不需要反射**。
`SubGroup.of/isFull` 与 `AutocrafterManagerData.Group.of/SubGroup.of` 是 `private`/包私有静态辅助，
桥内自行内联等价逻辑（3 行 `isFull` + 一次 `getContainerSize()`），不要反射调用。
`AutocrafterManagerBlockEntity#getGroups()` 本身是 `private`（同包也拿不到）⇒ 必须复刻其查询/排序，见上。

### 4.2 服务端**绝对禁止**调用 `resized(...)`
`resized` → `resetSlots()`（清空 `slots/lastSlots/remoteSlots`，`fabric/PlatformImpl.java:496-500`）→
用**服务端字段 `groups`（恒 `Collections.emptyList()`）**重建 ⇒ 全部样板槽丢失、只剩玩家背包 ⇒ 必然错位。
- 客户端：父类构造器已调一次；我们**再**调一次 `resized(0,0,0)` 是为了让 `disabledSlot` 生效（同 `WirelessPatternGridContainerMenu:34/43`）。
- 服务端：**零次**。`disabledSlot` 的服务端落地见 §4.3。

### 4.3 禁用"手持终端"槽位：客户端靠字段，服务端靠同位替换
- `disabledSlot` 是 `AbstractBaseContainerMenu` 的 `protected` 字段，子类可直接赋值。
- 客户端：先赋值、再 `resized(0,0,0)` ⇒ `addPlayerInventory` 自己造出 `DisabledSlot`（原生机制，
  `AbstractBaseContainerMenu.java:82-103`）。
- 服务端：父类服务端构造器已用普通 `PlayerInventorySlot` 建好背包，且我们**不能**调 `resized`
  （§4.2）。改为**同位替换**：
  ```java
  // 放在 Ez2rsNativeAutocrafterManagerMenu（AbstractContainerMenu 的子类）里。
  public final void ez2rs$disableInventorySlot(final Inventory inventory, final int playerSlotIndex) {
      for (int i = 0; i < slots.size(); i++) {
          final Slot s = slots.get(i);
          if (s.container == inventory && s.index == playerSlotIndex) {
              // DisabledSlot 的 ctor 会把 Slot#index 设成同一个 playerSlotIndex，
              // 所以这里不会改变任何槽位编号或顺序。
              slots.set(i, new DisabledSlot(inventory, playerSlotIndex, s.x, s.y));
              return;
          }
      }
  }
  ```
  `DisabledSlot` 是 public（`…/support/containermenu/DisabledSlot.java:8`），跨包可 import。
  用到的成员：`AbstractContainerMenu.slots`（public `List<Slot>`）、`Slot.container`（public final）、
  `Slot.index`、`Slot.x`、`Slot.y`。**UNVERIFIED（本机无 MC 反编译源码，未逐字核对）**：这五个是 1.21.1
  mojmap 的公开成员（RS2 自己就跨包用 `slot.container`/`slot.index`，且用 `Platform.INSTANCE.setSlotY(slot, …)`
  改 `y`，见 `AutocrafterManagerScreen.java:124`、`AbstractBaseContainerMenu.java:119-127`）。
  若 `Slot.index` 读写失败的**等价退路**：改用 `s.getContainerSlot()` 取编号；
  坐标用 `playerInventoryY + row*18` 自算（服务端 ctor 里 `playerInventoryY` 恒 0），即
  主背包三行 `(9..35)` → `y = 0,18,36`，快捷栏 `(0..8)` → `y = 58`。
  **槽位数量与顺序完全不变** ⇒ `remoteSlots/lastSlots` 的按位对齐不受影响。
  生效点：`AbstractBaseContainerMenu.clicked()` 的 `slot instanceof DisabledSlot → return`
  与 `isSwappingDisabledSlotWithNumberKeys`（`:114-146`）。
- 两个边角（必须知道，不必处理）：
  - 副手（`Inventory.SLOT_OFFHAND = 40`）：`addPlayerInventory` 只加 0..35，找不到匹配 ⇒ 不替换。
    与原生行为一致（`disabledSlot.isDisabledSlot(40)` 永远匹配不到任何已加槽位）。
  - 被替换掉的 `PlayerInventorySlot` 的 `PlayerInventoryListener` 在这里恒为 null
    （服务端构造器走 3 参 `addPlayerInventory`，`AbstractBaseContainerMenu.java:68-72,80-104`），
    所以替换不丢任何回调。
- 若 D 实测发现替换引发异常（极不可能），**退路**：不赋值 `disabledSlot`、不做替换，
  只依赖 `stillValid()` 的"物品仍在原槽位"收尾；在交付说明里登记。

### 4.4 写穿路径（证明容器不是副本）
- 服务端 `SubGroup.container` 来自 `Autocrafter.getPatternContainer()`；实现为
  `AutocrafterNetworkNodeContainer.getPatternContainer()` → `AutocrafterBlockEntity.getPatternContainer()` →
  **那个真实方块实体自己的 `PatternInventory` 字段**（`AutocrafterBlockEntity.java:83,138-140`；
  `AutocrafterNetworkNodeContainer.java:27-30`）。
- 槽位是 `new PatternSlot(realContainer, i, 0, 0, level)`（`AutocrafterManagerContainerMenu.java:114-122`
  → `PatternSlot` → `ValidatedSlot` → `net.minecraft.world.inventory.Slot`）。原版点击 → `Slot.setByPlayer/set`
  → `PatternInventory.setItem`（`PatternInventory.java:41-47`）→ 该 BE 构造期注册的 listener
  （`AutocrafterBlockEntity.java:113-120`）：重算能耗 + `setChanged()`（真实 level 非 null ⇒ 方块实体标脏）
  + `setListener(this)` 的 `PatternProviderListener` 使任务缓存失效。
- 结论：**没有任何副本或桩**。客户端侧的 `PatternInventory(slotCount, () -> level)`（`SubViewGroup.from`，
  `AutocrafterManagerContainerMenu.java:260-267`）才是桩，且只存在于客户端、只用于 `mayPlace` 校验与渲染。
- 我们的适配层**不改写容器**：`capture()` 只是把 `autocrafter.getPatternContainer()` 的**引用**包进
  `SubGroup`；`addServerSideSlots` 用同一引用造 `PatternSlot`。所以"离线时把容器置 null"是错的（见 §4.7）。
- 顺带的正确副作用（无需我们写代码）：放入/取出样板后 `AutocrafterBlockEntity` 的
  `patternContainer` listener 会重算能耗并 `setChanged()`，方块实体正常持久化
  （写：`ContainerUtil.write(patternContainer, provider)` → `TAG_PATTERNS`；读：`ContainerUtil.read(...)`，
  见 `AutocrafterBlockEntity.java:244` 与 `:272`）；
  `PatternInventory.Listener` → `PatternProviderListener` 使自动合成任务缓存失效。
  自动合成仓的样板**不存放在 `itemStack` 组件里**，所以不存在"改了内存但没存盘"的窗口。
- 一条**必须避免**的写法：不要在 `SubGroup` 里包一层 `SimpleContainer`/`FilteredContainer` 快照
  去"缓存开屏内容"—— 那会静默变成假写穿（用户以为上传成功、实际只在菜单里）。

### 4.5 离线闸门的最小实现（菜单四个覆盖）
```java
// 客户端无法得知可达性：一律放行（依赖原版预测 + 服务端拒绝 + broadcastChanges 纠偏）。
// 服务端：真实判定，见 (g)。
private boolean canEdit() {
    final WirelessAutocrafterManagerState s = state();   // 客户端为 null
    return !closed && (s == null || s.isWritable());
}
```
- `clicked(int index, int button, ClickType type, Player player)`：
  ```java
  if (index >= slots.size() || (index < 0 && index != -999 && index != -1)) return;   // 沿用样板网格的守卫
  if (!canEdit()) {
      if (type == ClickType.QUICK_CRAFT) return;                 // 拖拽分发（含拖拽提交）
      if (index >= 0 && slots.get(index).container != playerInventory) return;  // 任何触及样板槽的点击
  }
  super.clicked(index, button, type, player);   // 必须走 super：DisabledSlot / FilterSlot 的短路在里面
  ```
- `canDragTo(Slot slot)`：`return (canEdit() || slot.container == playerInventory) && super.canDragTo(slot);`
- `canTakeItemForPickAll(ItemStack s, Slot slot)`：同上形态。
- `quickMoveStack(Player, int)`：`return canEdit() ? super.quickMoveStack(player, index) : ItemStack.EMPTY;`
  （原生本就是空操作，这里只是别让离线时有机会走进 TransferManager；玩家背包→背包也不受影响，因为原生
  什么也不做）。
- 以上四条**只**为离线闸门存在。**没有**任何一条是"只读"限制：网络可达时行为 100% 等于方块版。
- **不要**动 `receivePropertyChangeFromClient`：红石模式属性会被原样落到哑 BE 的字段上（无世界副作用，
  与方块版观感一致），保持原生。

### 4.6 状态适配器契约（`WirelessAutocrafterManagerState`）
构造参数（与 `WirelessPatternGridState` 同构，但**没有** BE 子类、没有 draft、没有 watcher）：
```java
public WirelessAutocrafterManagerState(NetworkItemContext context, ServerPlayer owner,
                                       ItemStack terminalIdentity, SlotReference slotReference)
```
- 构造期**只**做三件事：存字段、`new AutocrafterManagerBlockEntity(BlockPos.ZERO, <default state>)`、
  `this.groups = Ez2rsAutocrafterManagerBridge.capture(context.resolveNetwork().orElse(null))`。
  **不** `setLevel`（本适配器不需要 recipe/registry 上下文 —— 那是样板网格为了 `PatternType` 才需要的）、
  不 `setNetwork`、不 `drainEnergy`、不碰 `Level`。
- `hasTerminalIdentity()`：`!terminalIdentity.isEmpty() && Ez2rsTerminalContent.isAutocrafterManagerCapable(terminalIdentity)
  && slotReference.resolve(owner).orElse(ItemStack.EMPTY) == terminalIdentity`
  （**引用相等**，与 `WirelessPatternGridState.hasTerminalIdentity` 一致；用来挡住"物品被换走/复制"）。
- `isWritable()`：`hasTerminalIdentity() && context.resolveNetwork().isPresent() && context.isActive()`。
  注意顺序：`resolveNetwork()` 每次重新查（`NetworkItemContextImpl` 只缓存玩家坐标，不缓存网络），
  所以断网/重建会被实时反映；`context.isActive()` 对**独立物品**走原生"物品电池 > 0"
  （`NetworkItemContextImpl.java:80-89`），对**通用终端**走我们的 mixin（网络有电）——
  两者都不需要我们额外处理。
- `canMenuStayOpen(Player p)`：`p == owner && hasTerminalIdentity()`。**不含**可达性。
- `blockEntity()` / `groups()` / `menuData()`：供服务端菜单与 opening data 取同一份 `Groups`。
- `close()`/`closed`：`removed(Player)` 里置位，之后 `stillValid` 返回 false，防"关掉又被人 reopen 同 menu"。
- 与 `WirelessPatternGridState` 的**故意差异**（写进类注释）：无 `setChanged` 覆写（不写世界）、
  无 draft 持久化（样板写在真实容器里）、无 watcher（不订阅）、无 `setLevel`。

### 4.7 `stillValid` / 会话寿命
```java
@Override public boolean stillValid(Player player) {
    final WirelessAutocrafterManagerState s = state();
    return s == null ? !closed : (!closed && s.canMenuStayOpen(player));   // 客户端恒 true
}
```
- 服务端：`player == owner && hasTerminalIdentity()`（同 `WirelessPatternGridState.canMenuStayOpen`，
  `WirelessPatternGridState.java:239`）。**不含**可达性判断 ⇒ 断电/换维度/离开区块**不关界面**，只是不可写。
- 原生父类 `stillValid` 必须被覆盖：它走 `Container.stillValidBlockEntity(be, player)`，
  哑 BE 的 `level == null` ⇒ **恒 false** ⇒ 不覆盖会秒关界面（`AutocrafterManagerContainerMenu.java:84-90`）。
- **不需要**把 `SubGroup.container` 置 null：容器指向真实方块实体，读只是读内存里的 `ItemStack`；
  写入已被 `isWritable()` 挡住。置 null 反而会让 `PatternSlot.getItem()` NPE（原版同步每 tick 都会 `getItem`）。

---

## 5. 文件清单与归属

### 5.1 新增（B 建骨架 → C 填内容）
| 文件（相对项目根，`src/main/java/` 省略） | 包 | 职责 | B | C |
|---|---|---|---|---|
| `com/refinedmods/refinedstorage/common/autocrafting/autocraftermanager/Ez2rsAutocrafterManagerBridge.java` | RS2 包（**普通类，非 mixin**） | `public static Groups capture(@Nullable Network)`：复刻 `getGroups()`（§4.1 排序）+ `isFull`；`public static final class Groups { public AutocrafterManagerData data(); /* 包私有 */ List<AutocrafterManagerBlockEntity.Group> list(); }` | `Groups` 包装类 + `capture` 签名 + `data()` | `capture` 的查询/排序/`isFull`；`data()` 的 `Group/SubGroup` 映射 |
| `…/autocraftermanager/Ez2rsNativeAutocrafterManagerMenu.java` | RS2 包（**普通类**） | `public abstract class … extends AutocrafterManagerContainerMenu`：<br>· 客户端 ctor `(int, Inventory, AutocrafterManagerData)` → public `super`<br>· 服务端 ctor `(int, Inventory, AutocrafterManagerBlockEntity, Ez2rsAutocrafterManagerBridge.Groups)` → 包私有 `super(id, inv, be, groups.list())`（`Group` 是包私有类型，**只有本类能 `.list()`**）<br>· `public final void ez2rs$disableInventorySlot(Inventory, int)` | 两个 ctor + 该方法签名 | `ez2rs$disableInventorySlot` 实现 |
| `ar/hxi/ez2rs/terminal/autocraftermanager/WirelessAutocrafterManagerData.java` | 我们的 | `record(AutocrafterManagerData data, SlotReference slotReference)` + 显式 `STREAM_CODEC` | 全文件（照抄 `WirelessPatternGridData` 结构） | 无 |
| `…/WirelessAutocrafterManagerState.java` | 我们的 | 持有 `NetworkItemContext`/owner/identity/`SlotReference`/哑 BE/`Groups`；`isWritable()`、`canMenuStayOpen()`、`groups()`、`menuData()` | 字段 + 方法签名 | 身份判定、可达性/电量判定、`closed` 语义 |
| `…/WirelessAutocrafterManagerContainerMenu.java` | 我们的 | 薄菜单：**客户端 ctor 收 `WirelessAutocrafterManagerData`**（→ `super(id, inv, data.data())`，再赋值 `disabledSlot`、`resized(0,0,0)`）；服务端 ctor 收 `(state, SlotReference)`（→ `super(id, inv, state.blockEntity(), state.groups())`，再 `ez2rs$disableInventorySlot`）；`getType()`、`stillValid`、`canEdit`、4 个槽位覆盖 | ctor 两个 + `getType` 桩 | §4.5 全部门禁 + 自有 `playerInventory` 字段 |
| `ar/hxi/ez2rs/terminal/AutocrafterManagerOpener.java` | 我们的（与 `PatternGridOpener` 同包） | 打开流程：解析 context → 捕获 `Groups` 一次 → `ExtendedScreenHandlerFactory<WirelessAutocrafterManagerData>` | 骨架 + factory 匿名类 | 身份/能力校验、`Groups` 捕获与复用 |
| `ar/hxi/ez2rs/terminal/WirelessAutocrafterManagerItem.java` | 我们的 | `extends AbstractNetworkEnergyItem`，`use()` → opener | 全文件（照抄 `WirelessPatternGridItem`） | 无 |
| `src/main/resources/data/ez2rs/recipe/wireless_autocrafter_manager.json` | — | 独立配方（见 (f)） | 全文件 | 核对 ingredient id |
| `src/main/resources/data/ez2rs/recipe/terminal_upgrade_autocrafter_manager.json` | — | `{"type":"ez2rs:terminal_upgrade_autocrafter_manager","category":"misc"}` | 全文件 | 无 |
| `src/main/resources/assets/ez2rs/models/item/wireless_autocrafter_manager.json` | — | 物品模型（占位贴图见 §7） | 全文件 | 无 |
| `ar/hxi/ez2rs/mixin/AutocrafterManagerScreenMixin.java` | `ar.hxi.ez2rs.mixin`（**唯一允许的 mixin 包**） | `@Mixin(AutocrafterManagerScreen.class)` + `@Inject(method = "init(I)V", at = @At("TAIL"))` 调 `UniversalTerminalButtonHelper.addIfUniversalTerminal((AbstractBaseScreen<?>) self)`。**必须写描述符**：`AbstractStretchingScreen` 同时有 `init()` 与 `init(int)`，只写 `"init"` 会歧义（`AutocrafterManagerScreen.java:62` 覆盖的是 `init(int)`） | 全文件 | 无 |

### 5.2 修改
| 文件 | 改动 | B | C |
|---|---|---|---|
| `…/terminal/Ez2rsMenus.java` | 新 MenuType 字段 + `register()` 里 `ExtendedScreenHandlerType<>`（**显式**声明 `StreamCodec<RegistryFriendlyByteBuf, WirelessAutocrafterManagerData>`，否则擦除类型崩）+ getter | 全部 | 无 |
| `…/client/Ez2rsClientScreens.java` | `MenuScreens.register(Ez2rsMenus.wirelessAutocrafterManager(), (MenuScreens.ScreenConstructor<AutocrafterManagerContainerMenu, AutocrafterManagerScreen>) AutocrafterManagerScreen::new)`。泛型参数写**父类** `AutocrafterManagerContainerMenu`（与样板网格先例同理：`PatternGridScreen extends AbstractGridScreen<PatternGridContainerMenu>`，注册时也用父类）；`MenuType` 需要原始类型强转 + `@SuppressWarnings` 时照抄现有写法 | 全部 | 无 |
| `…/terminal/UniversalTerminalType.java` | 新枚举常量 + 占位 sprite 分支（§7） | 全部 | 无 |
| `…/terminal/TerminalTypes.java` | 新 case → `ez2rs:wireless_autocrafter_manager` | 全部 | 无 |
| `…/terminal/WirelessUniversalGridItem.java` | `useDelegate` 新 case | 全部 | 无 |
| `…/terminal/Ez2rsTerminalContent.java` | `WIRELESS_AUTOCRAFTER_MANAGER_ID`、`setManagerItem/getManagerItem`、`isAutocrafterManagerCapable(ItemStack)` | 字段与方法签名 | 能力判定实现 |
| `…/Ez2rs.java` | `registerContent()` 注册物品、`registerEnergyProvider()` 注册 Team Reborn 电池、`registerCreativeModeTabListener` 加条目 | 全部 | 无 |
| `src/main/resources/assets/ez2rs/lang/{en_us,zh_cn}.json` | `item.ez2rs.wireless_autocrafter_manager`、`gui.ez2rs.universal_terminal.type.autocrafter_manager` | 全部（两条 key 都要有） | 文案润色 |
| `src/main/resources/ez2rs.mixins.json` | **仅**在 `"client"` 数组追加 `"AutocrafterManagerScreenMixin"`（照 `AutocraftingMonitorScreenMixin` 的位置）；`"package"` 保持 `ar.hxi.ez2rs.mixin` 不变 | 全部 | 无 |

> 注：上表已包含新增的 `AutocrafterManagerScreenMixin.java`（它是"新增"而非"修改"，放在 5.1 表末行，
> 此处保留 mixins.json 的修改以便 B 不漏注册）。

### 5.3 B 的骨架顺序（每步都要能 `compileJava` 通过）
1. 桥 `Ez2rsAutocrafterManagerBridge`（`Groups` 空实现 + `capture` 返回空）+ 原生子类菜单（两个 ctor 转发）。
   —— 这一步就能验证"同包能否 new 包私有 record / 调包私有 ctor"，是本方案最大的技术风险点，**先做**。
2. `WirelessAutocrafterManagerData` + `Ez2rsMenus` 新 MenuType + `Ez2rsClientScreens` 注册
   （此时界面能开、只有玩家背包）。
3. `WirelessAutocrafterManagerState`（字段/方法签名 + 哑 BE 构造）+ `AutocrafterManagerOpener` 骨架
   + `WirelessAutocrafterManagerItem` + `Ez2rs` 注册。
4. `UniversalTerminalType`/`TerminalTypes`/`WirelessUniversalGridItem`/`Ez2rsTerminalContent` 的枚举与分支
   + 两个 recipe json + 模型 json + lang 两条 key。
5. `AutocrafterManagerScreenMixin` + `mixins.json` 的 `client` 数组。

### 5.4 C 的功能切片（按可独立自测的顺序）
- **S1 分组与渲染**：`capture()` 真正实现（查询 → 排序 ×3 → `SubGroup` 含 `isFull`）→ 开屏能看到全部组。
- **S2 写穿**：确认服务端走 `addServerSideSlots`（不碰 `resized`），把样板拖进去 → 到方块版确认已落地。
- **S3 离线闸门**：`isWritable()` + 菜单四个覆盖；断网后拖入被拒、背包内移动正常、界面不灰。
- **S4 禁用槽位**：`ez2rs$disableInventorySlot` + 客户端 `disabledSlot` 赋值顺序。
- **S5 通用终端页 + 切换按钮 + 配方**：第 5 页可解锁、按钮可切、配方可合成、无环。
- **S6 文案与占位**：lang 两条、贴图 TODO 注释。

### 5.5 D（审查 + 构建）
- 逐条核对 §4.1/§4.2（源码级：服务端不得出现 `resized(` 调用；客户端必须有）。
- 核对 mixin：新增的 `AutocrafterManagerScreenMixin` 在 `"client"` 数组里、`"package"` 仍是 `ar.hxi.ez2rs.mixin`；
  **不存在**任何位于 `com.refinedmods.*` 的 mixin 包（MixinProcessor 会对该前缀下的普通类抛 IllegalClassLoadError）。
- 核对 common 侧没有 import `net.minecraft.client.*` / `AutocrafterManagerScreen`。
- 核对 JSON 语法、lang 两条 key、recipe ingredient 是否存在。
- 记录实测结果（用户手测，不要 runClient）。

---

## 6. API 要点与来源

| 事实 | 来源（本地） | 状态 |
|---|---|---|
| BE 构造器 `(BlockPos, BlockState)` public；`addWatcher/removeWatcher` 包私有；`getMenuData()` public；`createMenu` public | `.rs2src/refinedstorage-common/.../autocraftermanager/AutocrafterManagerBlockEntity.java:36,42,46,68,82` | 已核对 |
| 服务端菜单 ctor 包私有，签名 `(int, Inventory, BE, List<Group>)`；客户端 ctor public 且自带 `resized(0,0,0)` | `…/AutocrafterManagerContainerMenu.java:48-74` | 已核对 |
| `resized` 会 `resetSlots()` 且服务端 `groups` 恒空 | 同上 `:72,134-148`；`fabric/PlatformImpl.java:496-500` | 已核对 |
| `isActive()`/`setQuery`/`getGroups`/`getAutocrafterSlots` 包私有；`setActive`/`containsPattern`/`resized` public | 同上 `:204-237,124` | 已核对 |
| `AutocrafterManagerData` public + public `STREAM_CODEC` + `Group.of/SubGroup.of` 包私有 | `…/AutocrafterManagerData.java:10,22,30,36` | 已核对 |
| `AutocrafterManagerScreen` public，ctor `(menu, Inventory, Component)` | `…/AutocrafterManagerScreen.java:52-59` | 已核对 |
| `AutocrafterManagerActivePacket.handle` 按 `instanceof AutocrafterManagerContainerMenu` 派发 | `…/support/packet/s2c/AutocrafterManagerActivePacket.java:23-28` | 已核对（本特性不依赖它） |
| `Autocrafter` API：`getAutocrafterName/getPatternContainer/isVisibleToTheAutocrafterManager/getLocalPosition` | `.rs2src/refinedstorage-common-api/.../autocrafting/Autocrafter.java:9-17` | 已核对 |
| `GraphNetworkComponent.getContainers(Class)` | `.rs2src/refinedstorage-network-api/.../node/GraphNetworkComponent.java:29` | 已核对 |
| `PatternSlot` 公开、校验 `PatternProviderItem.isValid(stack, level)`；`PatternInventory` 容量 9 且 `getMaxStackSize==1` | `…/autocrafting/PatternSlot.java:9-13`、`PatternInventory.java:20,61-68` | 已核对 |
| `AbstractNetworkNode.setNetwork/setActive/isActive` public；但本特性**不用** setNetwork | `.rs2src/refinedstorage-network/.../impl/node/AbstractNetworkNode.java:23-35` | 已核对 |
| 原生菜单**没有** `transferManager.add*Transfer` ⇒ QUICK_MOVE 无操作 | 全仓 grep `addTransfer` 在 `autocraftermanager` 包 0 命中 | 已核对 |
| `NetworkItemContext` 仅 `resolveNetwork()/isActive()/drainEnergy()` | `…/common/api/support/network/item/NetworkItemContext.java` | 已核对 |
| `Platform.INSTANCE.getWirelessGrid().getOpenEnergyUsage()`、`getAutocrafterManager().getEnergyUsage()` 可读 | `common/Config.java:101,243` | 已核对 |
| 侧边按钮 sprite 路径 = `ez2rs:terminal/<name>` | `ar/hxi/ez2rs/terminal/UniversalTerminalType.java:46` | 已核对 |
| `#refinedstorage:autocrafter_managers` 物品标签存在（16 色） | `.rs2src/refinedstorage-common/src/generated/resources/data/refinedstorage/tags/item/autocrafter_managers.json` | **UNVERIFIED**：该 datagen 产物是否被打进发布 jar 未确认 → 配方优先用 `"tag": "refinedstorage:autocrafter_managers"`，若加载失败退回 `"item": "refinedstorage:autocrafter_manager"` |

---

## 7. 已知占位（必须写进代码注释）

本阶段**只能写文本文件**，无法生成 PNG。为不阻塞构建：
- 物品模型 `assets/ez2rs/models/item/wireless_autocrafter_manager.json` 的 `layer0` 暂时指向
  `ez2rs:item/wireless_pattern_grid`，文件内注释无法写 JSON 注释，故在 `WirelessAutocrafterManagerItem`
  类注释里写 `// TODO(art): 独立贴图`。
- `UniversalTerminalType.getSpriteName()` 目前是 `"terminal/" + name`，对 `AUTOCRAFTER_MANAGER` 会算成
  `ez2rs:terminal/autocrafter_manager` —— **该贴图不存在**，会渲染成紫黑格。必须在方法里加一个分支：
  ```java
  case AUTOCRAFTER_MANAGER -> "terminal/pattern_grid"; // TODO(art): 独立侧边按钮图标
  ```
  （或写 `return type == AUTOCRAFTER_MANAGER ? "terminal/pattern_grid" : "terminal/" + name;`。）
  **不要**指向不存在的贴图。本阶段只能写文本文件，无法生成 PNG。
- 语言文件两条 key 必须齐（缺 key 会显示 raw key）。

---

## 8. D 的构建与验证步骤

环境：JDK 21 由 foojay toolchain 自动 provision（`build.gradle:89-94`），不要手动设 JAVA_HOME。
wrapper 9.5.1 / Loom 1.17.19；`org.gradle.jvmargs=-Xmx2G`。

D 通过 `mc_gradle(projectDir="/home/arhanxing/IdeaProjects/ez2rs", task=...)` 调用项目自带 wrapper：

| 顺序 | `task` | `args` | `timeoutMs` | 说明 |
|---|---|---|---|---|
| 1 | `compileJava` | `["--stacktrace"]` | `1800000` | 首次下载依赖 + 重映射，最慢；先用它把编译错误收敛 |
| 2 | `build` | `["--stacktrace"]` | `1800000` | 含 `remapJar`，产出 `build/libs/ez2rs-0.1.0.jar` |
| 3（按需） | `clean` → `build` | `["--stacktrace"]` | `1800000` | OOM / 增量异常时 |

- 若出现 `java.lang.OutOfMemoryError`：**先 clean 重试**；仍复现则需用户同意后把
  `gradle.properties` 的 `org.gradle.jvmargs=-Xmx2G` 提到 `-Xmx3G`（改配置属 B/C 范围，D 不要自行改）。
- **不要** `runClient`/`runServer`：项目规则要求游戏由用户手测。
- 常见失败定位：`cannot find symbol: class AutocrafterManagerContainerMenu` → 忘了 `modImplementation` 已有
  RS2 依赖（本特性无新依赖，检查 import 是否写错包）；`IncompatibleClassChangeError`/`AbstractMethodError`
  多为混用 yarn 名（本工程是 mojmap：`BlockPos`/`ItemStack`/`MenuType` 等）。
- 用户手测清单（D 输出给用户）：见 §1 的 2–8 条 + "跨维度"+"断电"+"把网络拆掉一半（重建网络）再点槽位"。

---

## 9. 风险清单

1. **【高】服务端误调 `resized()`** → 样板槽全丢、槽位数与服务端不一致、物品同步错位。缓解：§4.2 写死为硬规则，D 用 grep 校验。
2. **【高】开场数据与服务端菜单不同源**（两次查询网络/两次分组）→ 槽位数量/顺序漂移。缓解：`Groups` 只捕获一次并复用到两条路径（§4.1「同一个 Groups 实例」段）。
3. **【中】服务端 `DisabledSlot` 同位替换**是新做法（原生无先例）。缓解：只替换、不改顺序、用
   `DisabledSlot(inventory, 同一个 playerSlotIndex, s.x, s.y)` 保证 `Slot#index` 不变；退路见 §4.3。
4. **【中】分组逻辑是 RS 私有 `getGroups()` 的复刻**，未来 RS 升级可能漂移。缓解：桥内注释写明"与 RS2 2.0.9 `AutocrafterManagerBlockEntity#getGroups` 逐字对应"，并把 `2.0.9` 写进方法注释；D 校验三条排序键（组内位置 → 分组 key → 组名）。
5. **【中】shift-click 是原生空操作**，可能与用户预期不符。缓解：§0.1 明确登记；不改 TransferManager。
6. **【中】离线时客户端仍会预测移动**，服务端拒绝后靠原版 `broadcastChanges` 纠偏，可能出现一瞬"幽灵物品"。属原版同步语义，接受；不新增包。
7. **【中】网络在会话中途被拆分/重建**：槽位仍指向旧的（可能仍在世界里的）`PatternInventory`，此时"可写"判定只看到可达性 → 可能写进已不属于该网络的自动合成仓。与方块版"分组只在打开时算一次"同级陈旧，接受；如需更强保证，后续可加"当前网络容器集合包含该 SubGroup 容器"的校验（不在本次范围）。
8. **【低】`active` 恒 true 是有意分歧**：一旦有人"修正"成 `mainNetworkNode.isActive()` 或接上
   `activeChanged`，界面会在离线/断电时灰屏并把布局藏掉（违反需求 3）。缓解：桥与 opener 两处都写注释。
9. **【低】配方标签 / UNVERIFIED**：`#refinedstorage:autocrafter_managers` 若未打进 jar → 配方静默失效。缓解：备用写法已给出，D 向用户要一份 `latest.log` 里 recipe 加载报错确认。
10. **【低】贴图占位**：两个功能共用图标；纯美术问题，不影响功能。
11. **【低】`ez2rs:wireless_autocrafter_manager` 没有 NetworkItemContextImpl 的网络取电 mixin 特化**（现有 mixin 只认 `WirelessUniversalGridItem`），所以独立物品用自己的电池；与 `WirelessPatternGridItem` 行为一致。不要为此扩展 mixin。
12. **【低】哑 BE 所在包带 `@ParametersAreNonnullByDefault`**（`package-info.java`），桥方法不要返回 null；
   `capture(@Nullable Network)` 用 `@Nullable` 明确标注（`javax.annotation.Nullable` 已在编译路径上）。
13. **【低】`getType()` 覆盖**依赖父类 `getType()` 非 final —— 已由已上线的样板网格菜单证明（`WirelessPatternGridContainerMenu.java:49`）；锁定 RS2 2.0.9 即可。
14. **【低】父类 `AutocrafterManagerContainerMenu.playerInventory` 是 private** ⇒ 我们的菜单必须**自己**存一份
    `PlayerInventory` 引用（构造器参数直接留用），不能指望读父类字段；`canEdit`/`clicked` 的
    `slot.container == playerInventory` 判定用的是这一份。
15. **【低】新增客户端的 `AutocrafterManagerScreenMixin`** 是本特性唯一的 mixin。风险点：
    ①必须写 `method = "init(I)V"`（`AbstractStretchingScreen` 有 `init()`/`init(int)` 两个重载，只写名字有歧义）；
    ②必须放在 `"client"` 数组（引用了 `Screen`）；③`package` 保持 `ar.hxi.ez2rs.mixin`。
    若注入失败，功能不致命（只是没有切换按钮），D 可在编译/启动日志里看到 mixin 报错。
16. **【低】`Ez2rsMenus.register()` 里注册顺序**：新 MenuType 必须在 `bootstrap()`（`Ez2rs.java:55-65`）
    里注册完，客户端才能 `MenuScreens.register` 它 —— 沿用现有 `wirelessPatternGrid()` 的
    "未注册就抛 `IllegalStateException`"守卫即可，不要改成懒加载 register。
17. **【低】`slots` 是 `AbstractContainerMenu` 的 public 字段**，替换元素无需反射。
    `Slot#index` 与 `getContainerSlot()` 对 `PlayerInventorySlot`/`DisabledSlot` 都返回同一个值
    （两者都没重写 `getContainerSlot`），用哪个都行；PLAN 取 `index` 是因为可以直接比对，
    且 `DisabledSlot` 的 ctor 保证不改变它。
