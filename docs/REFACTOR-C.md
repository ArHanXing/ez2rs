# REFACTOR-C — 无线自动合成仓管理终端（ez2rs:wireless_autocrafter_manager）

> 合并后的单实现阶段（原 A→B→C 中的 B+C）。PLAN.md 为实现契约。
> 注意：`docs/REFACTOR-C.md` 是本仓库另一条工作线（样板网格原生菜单重构）的旧报告，与本文件无关。
> 本文件只覆盖「无线自动合成仓管理终端」这个特性。

## 0. 结果

- `mc_gradle(compileJava)` 与 `mc_gradle(build)`（含 `remapJar`）**均通过**，产出 `build/libs/ez2rs-0.1.0.jar`。
- 没有遗留 stub：不存在任何 `// C:` / `TODO` 占位（grep 已确认）。§5.4 的 S1..S6 全部落地。
- 未改动 `build.gradle` / `gradle.properties` / `fabric.mod.json` / `README.md`；零新依赖。
- 未提交；未运行 runClient/runServer/GameTest（游戏由用户手测）。

## 1. 文件清单

新增（11）：
1. `src/main/java/com/refinedmods/refinedstorage/common/autocrafting/autocraftermanager/Ez2rsAutocrafterManagerBridge.java` — RS2 同包普通类：复刻 `getGroups()`（三条排序键 + `isFull`），`Groups` 包装（`data()` / 包私有 `list()`）。
2. `.../autocraftermanager/Ez2rsNativeAutocrafterManagerMenu.java` — 同包普通抽象类，两个 ctor 转发 + `ez2rs$disableInventorySlot` 同位替换。
3. `src/main/java/ar/hxi/ez2rs/terminal/autocraftermanager/WirelessAutocrafterManagerData.java` — `record(AutocrafterManagerData, SlotReference)` + 显式 `STREAM_CODEC`。
4. `.../autocraftermanager/WirelessAutocrafterManagerState.java` — 哑 BE + 单次 `Groups` 捕获 + `isWritable()` / `canMenuStayOpen()`。
5. `.../autocraftermanager/WirelessAutocrafterManagerContainerMenu.java` — 薄菜单：`getType` / `stillValid` / `removed` / `canEdit` + 四个槽位覆盖。
6. `src/main/java/ar/hxi/ez2rs/terminal/AutocrafterManagerOpener.java` — 解析一次 context，一次捕获，同一 `state` 喂给两条路径。
7. `src/main/java/ar/hxi/ez2rs/terminal/WirelessAutocrafterManagerItem.java` — 独立物品（物品电池）。
8. `src/main/java/ar/hxi/ez2rs/mixin/AutocrafterManagerScreenMixin.java` — 唯一的 client-only mixin。
9. `src/main/resources/data/ez2rs/recipe/wireless_autocrafter_manager.json` — 独立配方（`G` 用物品标签）。
10. `src/main/resources/data/ez2rs/recipe/terminal_upgrade_autocrafter_manager.json`
11. `src/main/resources/assets/ez2rs/models/item/wireless_autocrafter_manager.json` — `layer0: ez2rs:item/wireless_autocrafter_manager`（真实贴图，非占位）。

修改（9）：
12. `.../terminal/Ez2rsMenus.java` — 新 `MenuType`（显式 `StreamCodec<RegistryFriendlyByteBuf, WirelessAutocrafterManagerData>`）+ getter。
13. `.../client/Ez2rsClientScreens.java` — `MenuScreens.register`（泛型参数写父类）。
14. `.../terminal/Ez2rsTerminalContent.java` — `WIRELESS_AUTOCRAFTER_MANAGER_ID`、`set/getAutocrafterManagerItem`、`isAutocrafterManagerCapable`。
15. `.../terminal/UniversalTerminalType.java` — 新常量 `AUTOCRAFTER_MANAGER("autocrafter_manager")`（`getSpriteName()` **未改动**）。
16. `.../terminal/TerminalTypes.java` — 新 case → `ez2rs:wireless_autocrafter_manager`。
17. `.../terminal/WirelessUniversalGridItem.java` — `useDelegate` 新 case。
18. `src/main/java/ar/hxi/ez2rs/Ez2rs.java` — 物品注册 + Team Reborn 电池 + 创造模式标签页条目。
19. `src/main/resources/ez2rs.mixins.json` — 仅在 `"client"` 追加 `AutocrafterManagerScreenMixin`；`package` 仍是 `ar.hxi.ez2rs.mixin`。
20. `src/main/resources/assets/ez2rs/lang/{en_us,zh_cn}.json` — 两条 key 各一份。

## 2. 同包访问性：三项确认（本阶段最大技术风险，已用真实编译验证）

结论：**三项全部成立，无需反射，且已编译通过**（这些类都在 `com.refinedmods.refinedstorage.common.autocrafting.autocraftermanager` 包里）。

(a) **调用包私有服务端构造器** — 成立。
`AutocrafterManagerContainerMenu(int, Inventory, AutocrafterManagerBlockEntity, List<AutocrafterManagerBlockEntity.Group>)`
是包私有（源文件 `:59-74`）。`Ez2rsNativeAutocrafterManagerMenu`（同包、普通类、非 mixin）里
`super(syncId, inventory, autocrafterManager, groups.list())` 编译通过，构建产物 `javap` 可见该 super 调用。

(b) **`new AutocrafterManagerBlockEntity.Group(...)` / `new ...SubGroup(...)`** — 成立，编译通过。
两者是 `AutocrafterManagerBlockEntity` 的嵌套 **包私有 record**（`:114-132`）；record 的 canonical 构造器
与访问器（`name()` / `subGroups()` / `container()` / `visibleToTheAutocrafterManager()` / `full()`）
对同包类型可见。桥里直接用 `new AutocrafterManagerBlockEntity.Group(name, subs)` 与
`new AutocrafterManagerBlockEntity.SubGroup(container, visible, full)`。
`SubGroup.of(...)` / `SubGroup.isFull(...)` 是 private，桥内**内联等价逻辑**（不反射）。
`AutocrafterManagerBlockEntity#getGroups()` 本身是 private（同包也拿不到）⇒ 查询 + 三条排序键逐字复刻。

(c) **`new AutocrafterManagerData.Group(...)` / `new ...SubGroup(...)`** — 成立。它们是 **public** record
（`AutocrafterManagerData.java:29-43`），本来跨包就可用；桥里直接构造。`Group.of` 是包私有、`SubGroup.of` 是
private，故桥内整体内联映射（`slotCount = container.getContainerSize()`）。

附加确认（顺带验证的两点）：
- `Ez2rsAutocrafterManagerBridge.Groups.list()` 是**包私有**，只有同包的
  `Ez2rsNativeAutocrafterManagerMenu` 能读 —— 这正是把服务端 ctor 放进那个包的原因。
- `AutocrafterManagerContainerMenu.playerInventory` / `groups` 都是 **private**（即使同包也读不到），
  所以我们的菜单必须自己存一份 `PlayerInventory`，且服务端**不能**靠 `resized` 重建槽位。

## 3. 三条铁律的落地位置（D 可 grep 校验）

1. **服务端零次 `resized`**：`WirelessAutocrafterManagerContainerMenu` 只在**客户端 ctor** 里
   `disabledSlot = data.slotReference(); resized(0, 0, 0);`（赋值后调用）。服务端 ctor 完全不调，
   改调 `ez2rs$disableInventorySlot(inventory, i)`。
2. **同一个 `Groups`**：`AutocrafterManagerOpener` 只调一次 `new WirelessAutocrafterManagerState(...)`，
   其构造期只调一次 `Ez2rsAutocrafterManagerBridge.capture(context.resolveNetwork().orElse(null))`；
   `createMenu()` 读 `state.groups()`，`getScreenOpeningData()` 读 `state.menuData()`（= `groups.data()`）。
   两处都不重新查询、不重新排序。
3. **`active` 恒 true**：唯一出口是 `Groups#data()` 里的 `new AutocrafterManagerData(..., true)`，
   并写了「不要改回 `mainNetworkNode.isActive()`」的注释。全特性**不发** `AutocrafterManagerActivePacket`，
   也不订阅 `AutocrafterManagerWatcher`。

其余硬规则：
- 哑 BE 只在 state 构造期 `new AutocrafterManagerBlockEntity(BlockPos.ZERO, Blocks.INSTANCE.getAutocrafterManager().getDefault().defaultBlockState())`。
  全特性无 `setLevel` / `clearRemoved` / `setBlockState` / `doWork` / `updateActiveness` /
  `getContainerProvider().initialize|update|remove` / `setNetwork` / 任何 Level/chunk/registry 注册。
- 唯一 mixin 包仍是 `ar.hxi.ez2rs.mixin`；没有任何 `com.refinedmods.*` 的 mixin 包/配置。
- common 侧没有 import `net.minecraft.client.*`（`AutocrafterManagerScreen` 只出现在
  `mixin/AutocrafterManagerScreenMixin.java` 与 `client/Ez2rsClientScreens.java`）。

## 4. 相对 PLAN.md 的偏差（都是事实修正，非设计变更）

1. **`capture` 参数没有 `@Nullable`**：PLAN §9.12 说 `javax.annotation.Nullable` 已在编译路径上，实测
   **不在**（`compileJava` 报 `程序包javax.annotation不存在`，本模块没有 jsr305 依赖，且不允许加依赖）。
   改为无注解的 `capture(Network network)`，方法第一行判 `network == null → Groups.EMPTY`，
   并在 javadoc 里写明接受 null。行为与 PLAN 完全一致；包级 `@ParametersAreNonnullByDefault`
   只是静态分析标注，无运行时强制。
2. **`ez2rs$disableInventorySlot` 用 `getContainerSlot()` 匹配、并回抄 `Slot#index`**：PLAN §4.3 的片段用
   `s.index`。两者在当前版本等价（`PlayerInventorySlot`/`DisabledSlot` 都没重写 `getContainerSlot`），
   但 `getContainerSlot()` 表达的是"容器内编号"这个语义，匹配更稳；构造出 `DisabledSlot` 后再把原槽位的
   `index` 字段原样抄回，使替换结果在每个公开字段上都与原 `PlayerInventorySlot` 一致。
   槽位数量/顺序/坐标不变的前提不变。（`Slot#container/index/x/y` 与 `AbstractContainerMenu.slots`
   都是 public，已在本次编译中直接使用，无反射、无 AW。）
3. **占位贴图指令作废**：PLAN §7 / 风险 #10 说只能用占位贴图。父代理已生成真实贴图
   `assets/ez2rs/textures/item/wireless_autocrafter_manager.png` 与
   `assets/ez2rs/textures/gui/sprites/terminal/autocrafter_manager.png`，故：
   - 物品模型 `layer0` 直接指向 `ez2rs:item/wireless_autocrafter_manager`；
   - `UniversalTerminalType.getSpriteName()` **完全不改**（`"terminal/" + name` 自然得到
     `ez2rs:terminal/autocrafter_manager`，该贴图存在）；
   - 没有写任何 `TODO(art)`。
4. **配方 `G` 用物品标签**：`"G": { "tag": "refinedstorage:autocrafter_managers" }`。父代理解包真实
   `refinedstorage-fabric-2.0.9.jar` 确认
   `data/refinedstorage/tags/item/autocrafter_managers.json` 存在并列出全部 16 色（含
   `refinedstorage:autocrafter_manager`）。PLAN §6 最后一行/风险 #9 的 UNVERIFIED 已关闭，
   **不需要**退回 `"item": ...` 的备用写法。
5. **`AutocrafterManagerScreenMixin` 的 `@Inject` 加了 `remap = false`**：目标方法 `init(int)` 是 RS2 自己声明的
   方法（不覆写任何 Minecraft 方法），生产环境名字不变；加 `remap = false` 避免 loom 把 `init` 当成
   `Screen#init` 去重映射。描述符仍写全 `init(I)V`（`AbstractStretchingScreen` 同时有 `init()` 与 `init(int)`）。
6. **新增 `removed(Player)` 的客户端短路**：客户端 `removed` 不置 `closed`（EMI 会临时替换界面但保留菜单，
   置位会让闸门永久失效）。与原样板网格菜单的处理一致；服务端照常置位。

## 5. 自检记录（静态）

- `grep "resized("` → 仅 `WirelessAutocrafterManagerContainerMenu` 的客户端 ctor 一处真实调用（服务端 ctor 只出现在注释里）。
- `grep "mainNetworkNode"` → 特性代码 0 命中（只有注释里警告不要用它）。
- `grep "isActive()"` → 只有 `context.isActive()`（NIC 电量判定，PLAN §4.6 要求）与注释。
- `grep "// C:"` → 0 命中。
- 排序键逐字对照 `AutocrafterManagerBlockEntity.java:86-112`：组内 `Comparator.comparing(Autocrafter::getLocalPosition)` →
  `Collectors.groupingBy(a -> a.getAutocrafterName().getString())` → 组间 `Comparator.comparing(group -> group.name)`
  （桥里写成方法引用 `Group::name`，同一比较器）；槽位 index 取 `0 .. container.getContainerSize()-1`。
- `Ez2rsMenus` 的新类型显式声明 `StreamCodec<RegistryFriendlyByteBuf, WirelessAutocrafterManagerData>`（未擦除）。
- 槽位数对等推导：服务端 `addServerSideSlots` 用真实容器 `getContainerSize()`；客户端用同一 `Groups`
  派生的 `SubGroup.slotCount`（捕获时也是同一个 `getContainerSize()`），逐组逐亚组相等；之后都是
  `addPlayerInventory`（9..35 再 0..8）。两边 `active` 都为 true ⇒ 客户端不会出现"槽位叠在同一坐标"的伪影。

## 6. D 需要关注 / 用户手测

1. **mixin 注入只能在运行时验证**：`AutocrafterManagerScreenMixin` 只在 `"client"` 数组、`package` 未变。
   若注入失败，唯一后果是通用终端从该页无法用侧边按钮切页（功能不致命），日志会有 mixin 报错。
2. 配方标签 `#refinedstorage:autocrafter_managers` 已确认真实产物里存在；若用户环境仍报配方加载错误，
   请贴 `latest.log`。
3. 手测清单（用户）：独立物品可合成、右键开原生界面、标题为物品名；通用终端第 5 页可解锁并可用侧边按钮互切；
   跨维度可见同一网络全部自动合成仓、分组名=仓名且按名排序；拖入/取出后关界面到方块版确认已写穿；
   会话中途拆控制器/走远/换维度时界面不变灰、槽位仍可见但写入被拒、背包内移动照常；手持物品自身槽位被 DisabledSlot 占位。
