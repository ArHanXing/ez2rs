package ar.hxi.ez2rs.terminal.patterngrid;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;

/**
 * 石板机草稿容器：一个输入槽 + 当前选中的配方。
 *
 * <p>RS2 的石板机容器是包私有类型，外部无法持有，这里实现等价的公开版本，
 * 供无线样板终端使用。</p>
 */
public class StonecutterDraftContainer extends SimpleContainer {
    private final Supplier<Level> levelSupplier;
    private List<RecipeHolder<StonecutterRecipe>> recipes = List.of();
    private int selectedRecipe = -1;

    public StonecutterDraftContainer(final Supplier<Level> levelSupplier) {
        super(1);
        this.levelSupplier = levelSupplier;
    }

    public List<RecipeHolder<StonecutterRecipe>> getRecipes() {
        return recipes;
    }

    public int getSelectedRecipe() {
        return selectedRecipe;
    }

    public void setSelectedRecipe(final int selectedRecipe) {
        this.selectedRecipe = selectedRecipe;
    }

    public boolean hasSelectedRecipe() {
        return selectedRecipe >= 0 && selectedRecipe < recipes.size();
    }

    /** 输入变化后重算候选配方，与方块版行为一致。 */
    public void refresh() {
        final Level level = levelSupplier.get();
        final ItemStack input = getItem(0);
        if (level == null || input.isEmpty()) {
            recipes = List.of();
            selectedRecipe = -1;
            return;
        }
        recipes = level.getRecipeManager().getRecipesFor(
            RecipeType.STONECUTTING,
            new SingleRecipeInput(input),
            level
        );
        if (selectedRecipe >= recipes.size()) {
            selectedRecipe = recipes.isEmpty() ? -1 : 0;
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        refresh();
    }
}
