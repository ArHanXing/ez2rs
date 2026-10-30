package com.refinedmods.refinedstorage.common.autocrafting.patterngrid;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/** 石板机合成只需要一个输入槽，RS2 内部用的是自己的小实现，这里补一个等价的。 */
public record Ez2rsStonecutterRecipeInput(ItemStack item) implements RecipeInput {
    @Override
    public ItemStack getItem(final int index) {
        return item;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return item.isEmpty();
    }
}
