# 无限存储方块（infinite storage block）实现方案

状态：已定稿，待实现。作者：parent（A 阶段产出）。实现者（C）只需读本文件 + 按"文件清单"写代码，不需要重新调研 RS2。

## 目标

给 EZ2RS 加一种**配置驱动的无限存储方块**：每个方块在 RS2 网络里永久提供配置中指定的若干物品或流体，数量视为无限（不消耗、不接受插入）。方块由 JSON 配置数组决定，玩家改配置即可新增/删除方块，不需要写代码、不需要写 blockstates/models JSON。

用户原话要点：
- 参考 ExtendedAE 的无限物品磁盘：一个存储方块，对某一种（或几种）物品/流体提供无限量。
- 由"配置对象数组"驱动，形如 `{ name="无限水存储方块", fluid=["minecraft:water"], item=[] }`。
- 允许拆成"无限物品方块 / 无限流体方块"两类实现。
- 实现方式可以淳朴，性能好、实现简单优先。

## 设计决定

1. **一个配置条目 = 一个方块**，条目只能属于物品或流体之一（`item` 与 `fluid` 同时非空 → 跳过该条目并打 warn，README 里说明"要两种就写两条"）。理由：RS2 的 `ResourceFactory` 与过滤器容器是单资源类型的，混合类型需要自定义菜单/屏幕，代价远大于收益。
2. **完全复用 RS2 的 `StorageBlock` / `StorageBlockBlockEntity` / `StorageBlockContainerMenu` / `StorageBlockScreen`**：
   - `StorageBlock` 构造器是 **public** 的（已 javap 核对）：`StorageBlock(BlockBehaviour.Properties, StorageBlockProvider)`，直接 `extends StorageBlock<BlockEntity>` 即可，它的 `newBlockEntity` 内部会调 `RefinedStorageApi.INSTANCE.createStorageBlockEntity(pos, state, provider)`，冒号里的双双由 RS2 负责，我们什么都不用做。
   - **不要**用 `RefinedStorageApi.INSTANCE.createStorageBlock(...)` 工厂：它返回的是 RS2 自己的 `StorageBlock` 实例，我们没法覆盖 `getDrops`（见第 3 条）。工厂方法因此在本功能里不用。
   - 菜单类型直接返回 RS2 自己的 `Menus.INSTANCE.getItemStorage()` / `getFluidStorage()`。**不要注册自己的 MenuType，不要写自己的 Screen**：RS2 已经按这两个 MenuType 注册了 `StorageBlockScreen`，所有原版存储方块共用它们，我们共用即免费获得 GUI、`StorageBlockData` 同步、数量 tooltip、进度条。**也不要在客户端 `MenuScreens.register` 里重复注册这两个 MenuType**（会覆盖 RS2 的注册，属于未定义行为）。
3. **方块唯一需要自定义的地方是掉落物**：动态注册的方块没有 loot table JSON，`BlockBehaviour.getDrops` 默认按方块 id 推导 loot table 路径 → 文件不存在 → 返回空表 → 破坏后什么都不掉。用 `InfiniteStorageBlock` 覆盖 `protected List<ItemStack> getDrops(BlockState, LootParams.Builder)` 返回 `List.of(new ItemStack(this))`。
4. **"无限"的实现**：自定义 `SerializableStorage`，`getAll()` 对每个配置资源返回固定大数量 `INFINITE = 1_000_000_000_000L`（1e12），`insert` 恒返回 0（我们永不接收物品），`extract` 对配置内的资源恒返回请求量。

   **关键决定：`getStored()` 必须返回 `0`，不能返回 `INFINITE`。** 原因是 RS2 的清理逻辑：
   `StorageBlockBlockEntity` 在 `setLevel` 里先给新方块随机生成一个 UUID 并 `storageRepository.set(uuid, createStorage(...))`；
   随后 `loadAdditional` 读到存档里的 `sid` 时调 `setStorageId(old)` → `tryRemoveCurrentStorage` →
   `storageRepository.removeIfEmpty(newUuid)`。`removeIfEmpty` 的判据是 **`storage.getStored() == 0`**。
   如果我们的 `getStored()` 返回 `INFINITE`，这个"临时存储"永远删不掉：每次进世界、每个这种方块都会
   在 `refinedstorage_storages` 里留一个删不掉的孤儿条目（并且 RS2 每次都会打一条 WARN
   `Storage ... could not be removed`）。返回 0 则清理正常、无孤儿、无日志噪音。
   `getStored() == 0` 也让方块自己的 GUI 与 RS2 原版**创造存储方块**表现一致（那个方块初始也是 0，
   它的"创造"指的是无限**容量**而不是无限内容）。

   **重要更正（已核对 RS2 2.0.9 源码，不要用 `withoutNotifyingList()` 绕过缓存）**：
   `CompositeAwareChild` 的"不修改父缓存 list"技巧在这条链路上**到不了**。链路是
   `StorageNetworkComponentImpl`(RootStorageImpl.list) → `ExposedStorage`(CompositeAwareChild)
   → `AbstractStorageContainerNetworkNode.initializeStorage` 装出来的 **`StateTrackedStorage`**
   → 我们的 storage。`StateTrackedStorage` 只 implements `TrackedStorage, LimitedStorage`，
   **不是 `CompositeAwareChild`**，所以 `CompositeStorageImpl.extract` 走 else 分支：
   `source.extract(...)` 返回多少就从缓存 list 里 `remove` 多少。因此网络缓存 list 会**缓慢递减**，
   我们无法阻止。这不影响功能（1e12 的余量，正常玩法抽不完；而且每次 `addSource`——区块重载 /
   网络重建 / 节点重新激活——都会把 `getAll()` 的数量重新加回去），只是网格里显示的数字会缓慢变小。
   结论：按"大数量 + 允许缓慢衰减"实现，**不要**试图绕过缓存。
   - **不能**用 `Long.MAX_VALUE` 作为数量：`MutableResourceListImpl.Entry.increment` 没有溢出检查，
     同网络上另一个存储插入同一资源就会 `MAX + n` 溢出成负数，把整个网络该资源的数量弄成负值。
     也**不要**用 1e15 或更大：`addSource` 每次把 `getAll()` 的数量加进缓存 list，1e15 只需要几千次
     重新激活就可能溢出；1e12 需要上百万次，实际安全。1e12 显示成 `1.0T`，视觉上就是"用不完"。
   - **不要**实现 `LimitedStorage`（不要给出 capacity）：给了非 0 capacity，方块会被 RS2 当成有限存储，
     `StateTrackedStorage` 会算出 `FULL` 状态，可能干扰网络的容量/状态判断。
5. **每个配置条目注册一个独立的自定义 `StorageType`**（`ez2rs:infinite_storage_0` 之类）。RS2 的 `StorageRepositoryImpl` 用 `getStorageTypeRegistry().codec().dispatch(SerializableStorage::getType, s -> s.getMapCodec(listener))` 序列化所有存储，未注册的 `StorageType` 会在存档存/读时抛异常。我们的存储不持久化任何状态，`getMapCodec(listener)` 返回 `MapCodec.unit(() -> create(null, listener))` 即可。
6. **方块/物品/方块实体类型 id 用 `ez2rs:infinite_storage_<序号>`**，同时支持配置里可选 `id` 字段覆盖（得到 `ez2rs:infinite_storage_<id>`），便于老存档稳定。README 里说明改条目顺序/删除条目会让老存档里的该方块失效。
7. **模型**：动态方块不可能手写 `blockstates/*.json`，用 `ModelLoadingPlugin`：
   - `Context.registerBlockStateResolver(block, resolver)`：对每个 `BlockState` 调 `context.setModel(state, context.getOrLoadModel(MODEL_ID))`，`MODEL_ID = ez2rs:block/infinite_storage_block`（一个 `cube_all` 模型，贴图就是用户已给的 `textures/block/infinite_storage_block.png`）。
   - 物品模型：`Context.resolveModel()` 上挂 `ModelResolver`，id 形如 `ez2rs:item/infinite_storage_0` 时返回 `context.getOrLoadModel(ez2rs:item/infinite_storage_block_item)`（`item/generated`，layer0 同一张贴图）。
   - 全部动态方块共用这两个模型文件，所以模型数量与配置条目数无关。
8. **不持久化任何方块级状态**：没有每方块 UUID、没有 `StorageRepository` 残留、没有 loot function。存储实例由 RS2 的 `StorageBlockBlockEntity.setLevel` 通过 `provider.createStorage(listener)` 创建并放进 repository（这是 RS2 的行为，我们只是提供 `createStorage`）。资源列表来自配置，不来自存档。
9. **不做热重载**（配置改动需重启游戏）。不写 GameTest，不跑 `runClient`。

## 已验证的 API 事实（直接照用，勿再查）

Fabric / MC 1.21.1 mojmap（类路径 = loom `minecraft-merged` jar + RS2 2.0.9 jars）：

```
// RS2 公开工厂
RefinedStorageApi.INSTANCE.createStorageBlock(BlockBehaviour.Properties, StorageBlockProvider) -> Block
RefinedStorageApi.INSTANCE.createStorageBlockEntity(BlockPos, BlockState, StorageBlockProvider) -> AbstractNetworkNodeContainerBlockEntity<?>
RefinedStorageApi.INSTANCE.getStorageTypeRegistry() -> PlatformRegistry<StorageType>
    PlatformRegistry: register(ResourceLocation, T) / getId(T) / get(ResourceLocation) / getAll() / nextOrNullIfLast(T) / codec() / streamCodec()
RefinedStorageApi.INSTANCE.getItemResourceFactory() / getFluidResourceFactory() -> ResourceFactory

// StorageBlockProvider（要实现这 6 个方法）
SerializableStorage createStorage(Runnable listener);
Component getDisplayName();
long getEnergyUsage();
ResourceFactory getResourceFactory();
BlockEntityType<?> getBlockEntityType();
MenuType<?> getMenuType();

// StorageType（要实现这 4 个方法）
SerializableStorage create(@Nullable Long capacity, Runnable listener);
MapCodec<SerializableStorage> getMapCodec(Runnable listener);
boolean isAllowed(ResourceKey resource);
long getDiskInterfaceTransferQuota(boolean stackUpgrade);

// 接口组合
SerializableStorage extends Storage { StorageType getType(); }
Storage { Collection<ResourceAmount> getAll(); long getStored(); long insert(ResourceKey, long, Action, Actor); long extract(ResourceKey, long, Action, Actor); }

// 注意：StorageNetworkComponentImpl / ExposedStorage 会经过 StateTrackedStorage 包一层，
// StateTrackedStorage 只实现 TrackedStorage + LimitedStorage，不是 CompositeAwareChild。
// 所以 CompositeStorageImpl.extract 对我们调的是 extract() 而非 compositeExtract()，
// 并且会按我们返回的量扣减父缓存 list。按 plan 第 4 条实现即可。

// 资源
new com.refinedmods.refinedstorage.common.support.resource.ItemResource(Item)
new com.refinedmods.refinedstorage.common.support.resource.FluidResource(Fluid)
new com.refinedmods.refinedstorage.api.resource.ResourceAmount(ResourceKey, long)

// RS2 存储方块（可子类化）
public class StorageBlock<T extends BlockEntity> extends AbstractBaseBlock implements EntityBlock {
    public StorageBlock(BlockBehaviour.Properties properties, StorageBlockProvider provider);
    // 内部 ticker 只在服务端生效，取自 provider.getBlockEntityType()（懒查询，允许后设）
}
public abstract class AbstractBaseBlock extends Block { protected AbstractBaseBlock(Properties); }
com.refinedmods.refinedstorage.common.content.BlockConstants.PROPERTIES
    = BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.STONE)
com.refinedmods.refinedstorage.common.content.Menus.INSTANCE.getItemStorage() / getFluidStorage() -> MenuType<AbstractContainerMenu>
com.refinedmods.refinedstorage.common.content.DefaultEnergyUsage.CREATIVE_STORAGE_BLOCK = 16 (long)

// 方块实体类型：构造器 public，可直接 new（RS2 的 Fabric 实现就是传 null datafixer）
public BlockEntityType(BlockEntityType.BlockEntitySupplier<? extends T>, Set<Block>, Type<?>)
// BlockEntityType$BlockEntitySupplier 是接口（非 public），lambda 直接写即可
new BlockEntityType<>((pos, state) -> RefinedStorageApi.INSTANCE.createStorageBlockEntity(pos, state, provider), Set.of(block), null)

// 掉落物覆盖点
protected List<ItemStack> getDrops(BlockState, LootParams.Builder)   // BlockBehaviour 上，可覆盖

// 注册
Registry.register(BuiltInRegistries.BLOCK, ResourceLocation, Block)
Registry.register(BuiltInRegistries.ITEM, ResourceLocation, new BlockItem(block, new Item.Properties()))
Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ResourceLocation, BlockEntityType)
BuiltInRegistries.ITEM.getOptional(ResourceLocation) / BuiltInRegistries.FLUID.getOptional(ResourceLocation)

// Fabric Model Loading API v1（1.21.1 线上 1.0.7，随 fabric-api 引入）
net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(plugin)
ModelLoadingPlugin.onInitializeModelLoader(Context)
Context: addModels(ResourceLocation...)/ addModels(Collection)/ registerBlockStateResolver(Block, BlockStateResolver)/ resolveModel()->Event<ModelResolver>/ modifyModelOnLoad()/ ...
BlockStateResolver: void resolveBlockStates(Context)  // 每个 state 必须且只能 setModel 一次
BlockStateResolver$Context: block()/ setModel(BlockState, UnbakedModel)/ getOrLoadModel(ResourceLocation)/ loader()
ModelResolver: UnbakedModel resolveModel(UnbakedModel, ModelResolver$Context)  // Context: id()/ getOrLoadModel(...)/ loader()
```

`StorageBlockBlockEntity` 关键行为（决定了我们什么都不用管）：`setLevel` 时（仅服务端）随机生成 UUID → `storageRepository.set(uuid, provider.createStorage(repository::markAsChanged))` → `mainNetworkNode.setProvider(this)`；`createMenu` 用 `provider.getMenuType()` 构造 RS2 的 `StorageBlockContainerMenu`；`getMenuData()` 用 `mainNetworkNode.getStored()/getCapacity()`。门面 `getName()` = `provider.getDisplayName()`（可被铁砧改名覆盖）。

## 文件清单

新建（包 `ar.hxi.ez2rs.storage`）：

1. `InfiniteStorageConfig.java`
   - `public static List<Entry> load()`（或 `load()` 返回 `InfiniteStorageConfig` 实例带 `entries()`，二选一，实现者自定，保持简单）。
   - JSON 路径：`FabricLoader.getInstance().getConfigDir().resolve("ez2rs-infinite-storage.json")`；不存在则写入默认内容再读。
   - 默认内容（两条例子里给用户抄）：
     ```json
     {
       "blocks": [
         { "id": "water", "name": "无限水存储方块", "fluid": ["minecraft:water"], "item": [] },
         { "id": "cobblestone", "name": "无限圆石存储方块", "fluid": [], "item": ["minecraft:cobblestone"] }
       ]
     }
     ```
   - 结构：`{ "blocks": [ { "id": "water", "name": "无限水存储方块", "item": [...], "fluid": [...] } ] }`；`id` 可选（缺省用下标序号 `0`、`1`…）。
   - 用手写 Gson `JsonObject` 解析（不要 record + Gson 反射，避免 `FieldNamingPolicy` 噪音）。Gson 已经是 MC 依赖，`com.google.gson.JsonParser.parseString` 可用，不需要新增依赖。
   - `id` 会被拼成 `ez2rs:infinite_storage_<id>`，所以必须过 `ResourceLocation.tryParse` 校验（小写、`[a-z0-9_.-/]`）；非法 id → warn 并退回下标序号。
   - 校验：`item` 与 `fluid` 同时非空 → warn 跳过该条目；两者都空 → warn 跳过；某个资源 id 未知 → warn 跳过该资源（条目里还有其他有效资源就继续）；整条都无效 → 跳过条目。
   - 解析结果 `Entry`：`slug`（最终 id 后缀）、`name`（`Component.literal` 直接用）、`kind`（`ITEM`/`FLUID`）、`resources`（`List<ResourceKey>`，已构造好 `ItemResource`/`FluidResource`）、`id`（`ResourceLocation`，`ez2rs:infinite_storage_<slug>`）。
   - 资源查询：`BuiltInRegistries.ITEM.getOptional(rl)` / `BuiltInRegistries.FLUID.getOptional(rl)`；`ResourceLocation.tryParse` 返回 null 即视为未知。

2. `InfiniteStorage.java`
   - `implements SerializableStorage`（**不要**实现 `CompositeAwareChild`，也不要实现 `LimitedStorage`）
   - 字段 `StorageType type`、`Collection<ResourceAmount> contents`（构造时算好、不可变）、`Runnable listener`、`Set<ResourceKey> allowed` 用于 O(1) 判断。
   - 常量 `public static final long INFINITE = 1_000_000_000_000L;`（注释解释用什么值、为什么不是 `Long.MAX_VALUE`）。
   - `getAll()` → `contents`；`getStored()` → **`0`**（理由见设计决定第 4 条，`removeIfEmpty` 的判据）。
   - `insert(...)` → `0`（永不接收，避免网络把东西塞进"无限"方块）。
   - `extract(resource, amount, action, actor)` → 资源不在配置内返回 `0`；否则 `Action.EXECUTE` 时 `listener.run()`，返回 `amount`。
     调用方保证 `amount > 0` 且会被网络自己的缓存值截断。
   - `getType()` → `type`。

3. `InfiniteStorageType.java`
   - `implements StorageType`；字段 `String id`（仅日志用）、`List<ResourceKey> allowed`、`Supplier<InfiniteStorage>` 工厂（或直接持有构造参数）。
   - `create(capacity, listener)` → 新的 `InfiniteStorage`（忽略 capacity）。
   - `getMapCodec(listener)` → `MapCodec.unit(() -> create(null, listener))`（**注意泛型**：返回类型必须是 `MapCodec<SerializableStorage>`，必要时写成 `MapCodec.<SerializableStorage>unit(...)` 或用中间变量标注）。
   - `isAllowed(resource)` → `allowed.contains(resource)`。
   - `getDiskInterfaceTransferQuota(boolean)` → `0`（无限方块不参与磁盘接口搬运）。

4. `InfiniteStorageProvider.java`
   - `implements StorageBlockProvider`；持有 `kind`、`displayName`、`StorageType type`、可变的 `BlockEntityType<?> blockEntityType`（`setBlockEntityType`）。
   - `createStorage(listener)` → `type.create(null, listener)`。
   - `getEnergyUsage()` → `DefaultEnergyUsage.CREATIVE_STORAGE_BLOCK`（16，与原版创造存储方块一致）。
   - `getResourceFactory()` → 按 kind 取 `getItemResourceFactory()` / `getFluidResourceFactory()`。
   - `getMenuType()` → 按 kind 取 `Menus.INSTANCE.getItemStorage()` / `getFluidStorage()`。
   - `getBlockEntityType()` → 未设置时抛 `IllegalStateException`（明确报错胜过 NPE）。

5. `InfiniteStorageBlock.java`
   - `extends StorageBlock<BlockEntity>`，构造 `(BlockBehaviour.Properties, StorageBlockProvider)`。
   - 覆盖 `protected List<ItemStack> getDrops(BlockState, LootParams.Builder)` → `List.of(new ItemStack(this))`。
   - 所以 `getDrops` 不再使用参数，但签名必须保留（`@Override`）。
   - 加注释说明为什么必须自己给掉落物（动态方块没有 loot table）。

5b. `InfiniteStorageBlockItem.java`
   - `extends BlockItem`，构造 `(Block block, Item.Properties, Component displayName)`。
   - 覆盖 `public Component getName(ItemStack stack)` → 返回配置里的显示名。
   - **为什么必须**：方块名走 `provider.getDisplayName()`（BE 的 `Nameable#getName`），但物品名默认走翻译键 `block.ez2rs.infinite_storage_0`，我们没有 lang 文件（条目是动态的），不覆盖就会在物品栏里显示原始键名。

6. `InfiniteStorageContent.java`
   - `public static void register(RefinedStorageApi api)`：幂等（`boolean registered`），从 `InfiniteStorageConfig.load()` 拿条目，逐条走上面的注册顺序；收集 `List<Block>` / `List<Item>` 供客户端模型插件与创造模式标签使用。
   - 每条的处理顺序（**不能颠倒**）：config → `InfiniteStorageType` → `register(api.getStorageTypeRegistry(), id, type)` → `InfiniteStorageProvider` → `new InfiniteStorageBlock(BlockConstants.PROPERTIES, provider)` → `Registry.register(BLOCK)` → `Registry.register(ITEM, new BlockItem(block, new Item.Properties()))` → `new BlockEntityType<>(...)` → `Registry.register(BLOCK_ENTITY_TYPE)` → `provider.setBlockEntityType(beType)`。
   - 每步包 try/catch，单条失败只 warn 并跳过该条，不能让整体崩溃。
   - 暴露 `getBlocks()` / `getItems()`（不可变视图，客户端模型插件用）。
   - 日志：注册了几条、跳过了哪条及原因。

7. `client/InfiniteStorageModels.java`（包 `ar.hxi.ez2rs.client`）
   - 遍历 `InfiniteStorageContent.getBlocks()`：`context.registerBlockStateResolver(block, resolver)`，resolver 里 `for (BlockState state : block.getStateDefinition().getPossibleStates()) context.setModel(state, context.getOrLoadModel(MODEL));`，**每个 state 必须恰好设置一次**（少设置 → Fabric 记 error 日志并对该方块全部用 missing model）；`MODEL = ResourceLocation.fromNamespaceAndPath("ez2rs", "block/infinite_storage_block")`。
     - 已核对 Fabric 实现：`registerBlockStateResolver` 内部按方块的注册 id 建索引，方块必须已经注册（我们是注册完之后才在客户端 init 里调它，安全）；resolver 抛异常时 Fabric 捕获并对整个方块回退 missing model，不会崩游戏。
   - `context.resolveModel().register(resolver)`：**1.21.1 线上（fabric-model-loading-api-v1 1.0.7）的签名是
     `@Nullable UnbakedModel resolveModel(ModelResolver.Context context)`——单参数，不匹配返回 `null`**（不是返回传入的 model；那是 `ModelModifier.OnLoad` 的形态）。上下文里只有 `id()` / `getOrLoadModel(ResourceLocation)` / `loader()`。
     - `ctx.id()` 形如 `ez2rs:item/<slug>`。**不要按 id 前缀匹配 `infinite_storage_`**：目标模型 `ez2rs:item/infinite_storage_block_item` 自己也命中该前缀，`getOrLoadModel(ITEM_MODEL)` 会重新进入同一个 resolver，无限递归到 StackOverflow。要按"已注册物品 id 集合"判定，并显式放过 `ITEM_MODEL` 自身。
     - 物品模型的 id 由 `ModelBakery.loadItemModelAndDependencies` 用 `id.withPrefix("item/")` 构造，所以匹配字符串是 `ez2rs:item/<slug>`；代码里同时容忍不带前缀的形态更稳。

新建资源：

8. `src/main/resources/assets/ez2rs/models/block/infinite_storage_block.json`
   ```json
   { "textures": { "particle": "ez2rs:block/infinite_storage_block", "all": "ez2rs:block/infinite_storage_block" },
     "elements": [ 标准 cube_all 六个面 ], "parent": "minecraft:block/block" }
   ```
   直接照抄原版 `minecraft:block/cube_all` 的结构（`parent: minecraft:block/block` + 6 个 element）。
9. `src/main/resources/assets/ez2rs/models/item/infinite_storage_block_item.json`
   `{ "parent": "minecraft:item/generated", "textures": { "layer0": "ez2rs:block/infinite_storage_block" } }`
10. 贴图已存在：`assets/ez2rs/textures/block/infinite_storage_block.png`（不要动）。

修改：

11. `src/main/java/ar/hxi/ez2rs/Ez2rs.java`
    - `onApiAvailable` 里 `registerContent()` 之后调用 `InfiniteStorageContent.register(refinedStorageApi)`（必须在这个位置：RS2 静态编解码器已就绪）。
    - `registerCreativeModeTabListener`：在现有的 3 个 `entries.accept(...)` 后追加 `InfiniteStorageContent.getItems().forEach(entries::accept)`。
12. `src/main/java/ar/hxi/ez2rs/client/Ez2rsClient.java`
    - `onInitializeClient` 里调用 `InfiniteStorageModels.register()`（放在现有的 `Ez2rsClientScreens.register()` 附近）。
13. `README.md`：新增一节"无限存储方块"，写清配置路径、字段含义、`item`/`fluid` 二选一规则、`id` 稳定性、改配置需重启、方块共用同一套 GUI（RS2 存储方块界面）。

## 硬性约束

- Mixin 只能放在 `ar.hxi.ez2rs.mixin` 包里
- 所有 RS2 相关初始化只能在 `onApiAvailable`（`refinedstorage_plugin` 入口）里做。`onInitialize` 里**一个 RS2 类都不能碰**。`InfiniteStorageContent.register` 只能从 `onApiAvailable` 调。
- 构建只能用 `mc_gradle`（projectDir `/home/arhanxing/IdeaProjects/ez2rs`，task `build`）。不要 shell 调 gradlew。

- 不要写自定义 MenuType / Screen / MenuProvider，不要覆盖 `StorageBlockBlockEntity`。
- 中文注释、中文 README 段落，与项目现有风格一致（`Ez2rs.java` 的注释密度作为基准，别写废话注释）。

## 验收

`mc_gradle` 跑 `build` 成功即可（不做运行时验证）。已知无法通过编译验证、需要用户进游戏确认的点，最后要明确列出：
1. 方块出现在创造模式物品栏 / RS2 创造标签里，放置后能被 RS2 网络识别并供电工作。
2. 网格里该资源显示为大数量（约 `1.0T`）而不是 0，抽取后数量基本不掉（记账原因会有极缓慢衰减）。
3. 破坏方块掉落自身。
4. 方块有正确模型贴图（不是紫黑格），物品有正确图标与显示名（不是 `block.ez2rs.infinite_storage_*` 原始键）。
5. 存档退出重进不崩；日志里不应有 `Storage ... could not be removed` WARN（验证 `getStored()==0` 的策略生效）。

## 已知风险 / 需要用户判断的地方

- **方块自己打开 GUI 时显示的数量是 0**（因为 `getStored()` 返回 0）。这是为了 `removeIfEmpty` 清理正常而刻意做的，
  与原版创造存储方块的表现一致（它的"创造"是无限**容量**，内容从 0 开始）。若用户希望 GUI 里显示 ∞，
  要改成 `getStored()` 返回 `INFINITE`，代价是每个这种方块每次进世界都会在 `refinedstorage_storages`
  里留下一个删不掉的孤儿条目 + 每次一条 WARN。**实现时按 plan 的 `0` 做，把这句写进 README 和最终报告**，
  让用户自己决定要不要换。
- **网格数量缓慢衰减**：见设计决定第 4 条，属于 RS2 内部缓存机制，无法在不写 mixin 的前提下避免。
- **配置改动需重启**：没有热重载。
- **删改配置条目会让老存档里的该方块失效**（id 变了），README 要写。
