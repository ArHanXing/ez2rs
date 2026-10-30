package ar.hxi.ez2rs.terminal;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 通用终端解锁配方的实现。
 *
 * <p>形状是"一个通用终端 + 一个对应的无线终端"，无序。合成结果是一个带着新页的通用终端，
 * 用来解锁的那个终端被消耗；已经拥有的能力不重复添加。</p>
 *
 * <p>类型由 {@link #type} 固定，所以每种终端各有一条配方 json，
 * 分别对应 {@code refinedstorage:wireless_grid} / 石英兵工厂的无线合成终端 / 无线监视器。</p>
 */
public class TerminalUpgradeRecipe extends CustomRecipe {
    private final UniversalTerminalType type;

    public TerminalUpgradeRecipe(final CraftingBookCategory category, final UniversalTerminalType type) {
        super(category);
        this.type = type;
    }

    public UniversalTerminalType getTerminalType() {
        return type;
    }

    @Override
    public boolean matches(final CraftingInput input, final Level level) {
        return findIngredients(input) != null;
    }

    @Override
    public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
        final Slots slots = findIngredients(input);
        if (slots == null) {
            return ItemStack.EMPTY;
        }
        final ItemStack universal = input.getItem(slots.universal).copyWithCount(1);
        final TerminalPages pages = Ez2rsTerminalContent.getPages(universal);
        universal.set(Ez2rsTerminalContent.PAGES, pages.with(type));
        return universal;
    }

    /** 找一个通用终端 + 一个本配方对应的终端；找不到返回 null。 */
    private Slots findIngredients(final CraftingInput input) {
        int universal = -1;
        int source = -1;
        for (int i = 0; i < input.size(); i++) {
            final ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof WirelessUniversalGridItem) {
                if (universal != -1) {
                    return null;
                }
                universal = i;
            } else if (TerminalTypes.isItemOf(stack, type)) {
                if (source != -1) {
                    return null;
                }
                source = i;
            } else {
                return null;
            }
        }
        return universal == -1 || source == -1 ? null : new Slots(universal, source);
    }

    @Override
    public boolean canCraftInDimensions(final int width, final int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return Ez2rsRecipes.terminalUpgrade(type);
    }

    private record Slots(int universal, int source) {
    }

    /** 这个配方固定序列化，不需要额外字段。 */
    public static class Serializer implements RecipeSerializer<TerminalUpgradeRecipe> {
        private final UniversalTerminalType type;
        private final MapCodec<TerminalUpgradeRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, TerminalUpgradeRecipe> streamCodec;

        public Serializer(final UniversalTerminalType type) {
            this.type = type;
            this.codec = MapCodec.unit(() -> new TerminalUpgradeRecipe(CraftingBookCategory.MISC, type));
            // 不能用 StreamCodec.unit(instance)：它校验"被编码的对象 == 预设实例"，
            // 而这里的配方是 json 反序列化出来的另一个实例，同步配方时会抛
            // "Can't encode ..., expected ..."。配方本身无字段，写一个不写字节的编解码器即可。
            this.streamCodec = StreamCodec.of(
                (buffer, recipe) -> {
                },
                buffer -> new TerminalUpgradeRecipe(CraftingBookCategory.MISC, type)
            );
        }

        public UniversalTerminalType getType() {
            return type;
        }

        @Override
        public MapCodec<TerminalUpgradeRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, TerminalUpgradeRecipe> streamCodec() {
            return streamCodec;
        }
    }
}
