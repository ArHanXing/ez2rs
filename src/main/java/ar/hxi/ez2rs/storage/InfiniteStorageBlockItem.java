package ar.hxi.ez2rs.storage;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * 无限存储方块的物品形态，只为了覆盖名字。
 *
 * <p><b>为什么必须覆盖。</b>方块实体（{@code Nameable#getName}）与方块（{@link InfiniteStorageBlock}）
 * 都走配置里的显示名，但物品名默认走翻译键 {@code block.ez2rs.infinite_storage_<slug>}。
 * 这些条目是玩家在配置文件里动态生成的，我们不可能预先写 lang，所以不覆盖的话物品栏里显示的就是
 * 那个原始键。</p>
 */
public class InfiniteStorageBlockItem extends BlockItem {
    private final Component displayName;

    public InfiniteStorageBlockItem(final Block block,
                                    final Properties properties,
                                    final Component displayName) {
        super(block, properties);
        this.displayName = displayName;
    }

    @Override
    public Component getName(final ItemStack stack) {
        return this.displayName;
    }
}
