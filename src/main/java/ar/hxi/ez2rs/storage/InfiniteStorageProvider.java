package ar.hxi.ez2rs.storage;

import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.storage.SerializableStorage;
import com.refinedmods.refinedstorage.common.api.storage.StorageBlockProvider;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceFactory;
import com.refinedmods.refinedstorage.common.content.DefaultEnergyUsage;
import com.refinedmods.refinedstorage.common.content.Menus;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 把「一个配置条目」接进 RS2 的存储方块体系。
 *
 * <p>RS2 的 {@code StorageBlockBlockEntity} 只通过这个接口拿东西：建 storage、显示名、耗电、
 * 过滤器用的 {@code ResourceFactory}、方块实体类型、菜单类型。除了这些我们不需要任何自定义——
 * 菜单与界面直接复用 RS2 的 {@code Menus.INSTANCE.getItemStorage()/getFluidStorage()}，
 * 它们已经绑定了 {@code StorageBlockScreen}，所以这个方块免费获得 GUI、数量同步与侧边按钮。
 * <b>绝不能</b>再注册自己的 MenuType / Screen（会覆盖 RS2 的注册）。</p>
 */
public final class InfiniteStorageProvider implements StorageBlockProvider {
    private final InfiniteStorageConfig.Kind kind;
    private final Component displayName;
    private final InfiniteStorageType type;

    /**
     * 方块实体类型要在方块之后才注册（构造 {@code BlockEntityType} 需要方块实例）。
     *
     * <p>RS2 的 {@code StorageBlockBlockEntity} 构造器确实会读它，但方块实体只在世界加载后才会被创建，
     * 那时注册早已结束；{@code StorageBlock} 自己那个 ticker 更是懒查询。所以先建 provider、
     * 后补类型是安全的。</p>
     */
    private BlockEntityType<?> blockEntityType;

    public InfiniteStorageProvider(final InfiniteStorageConfig.Kind kind,
                                   final Component displayName,
                                   final InfiniteStorageType type) {
        this.kind = kind;
        this.displayName = displayName;
        this.type = type;
    }

    public void setBlockEntityType(final BlockEntityType<?> blockEntityType) {
        this.blockEntityType = blockEntityType;
    }

    @Override
    public SerializableStorage createStorage(final Runnable listener) {
        return this.type.create(null, listener);
    }

    @Override
    public Component getDisplayName() {
        return this.displayName;
    }

    /** 与原版创造存储方块一致（16 FE/t）：无限存储的"价值"等价于创造级。 */
    @Override
    public long getEnergyUsage() {
        return DefaultEnergyUsage.CREATIVE_STORAGE_BLOCK;
    }

    /** 决定方块界面里的过滤槽接受什么：物品型只收物品、流体型只收流体。 */
    @Override
    public ResourceFactory getResourceFactory() {
        return this.kind == InfiniteStorageConfig.Kind.ITEM
            ? RefinedStorageApi.INSTANCE.getItemResourceFactory()
            : RefinedStorageApi.INSTANCE.getFluidResourceFactory();
    }

    /** 未设置时明确报错胜过后面某个 tick 里的 NPE（谁会先用到它取决于 RS2 的调用顺序）。 */
    @Override
    public BlockEntityType<?> getBlockEntityType() {
        if (this.blockEntityType == null) {
            throw new IllegalStateException("Block entity type for infinite storage block has not been set yet");
        }
        return this.blockEntityType;
    }

    @Override
    public MenuType<?> getMenuType() {
        return this.kind == InfiniteStorageConfig.Kind.ITEM
            ? Menus.INSTANCE.getItemStorage()
            : Menus.INSTANCE.getFluidStorage();
    }
}
