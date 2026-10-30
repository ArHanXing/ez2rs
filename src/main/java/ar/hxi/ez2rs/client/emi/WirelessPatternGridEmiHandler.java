package ar.hxi.ez2rs.client.emi;

import ar.hxi.ez2rs.terminal.patterngrid.WirelessPatternGridContainerMenu;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import java.util.List;
import java.util.stream.Collectors;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * 让 EMI 能把配方拖进无线样板终端。
 *
 * <p>RS2 官方的 EMI 集成（{@code refinedstorage_emi_integration}）是按<b>菜单类型</b>注册处理器的：</p>
 *
 * <pre>
 * registry.addRecipeHandler(Menus.INSTANCE.getPatternGrid(), new PatternGridEmiRecipeHandler());
 * </pre>
 *
 * <p>它只认 RS2 自己的 {@code refinedstorage:pattern_grid}，不认我们的
 * {@code ez2rs:wireless_pattern_grid}，所以在无线终端里拖配方没有反应。
 * 官方的那个处理器是 package-private、无法复用，这里按它的逻辑实现一份。</p>
 *
 * <p>好消息是真正干活的逻辑在 {@code PatternGridContainerMenu} 的 public 方法里：
 * 当菜单没有方块实体（无线版正是如此）时，它们会发 C2S 包交由服务端处理，
 * 所以这里只需要把 EMI 的配方形式转成 RS2 的资源形式再调过去。</p>
 */
public final class WirelessPatternGridEmiHandler
    implements EmiRecipeHandler<WirelessPatternGridContainerMenu> {

    @Override
    public EmiPlayerInventory getInventory(final AbstractContainerScreen<WirelessPatternGridContainerMenu> screen) {
        // 样板网格不使用玩家背包里的物品来填配方（拖拽是把配方"编码成样板"，不是真的合成），
        // 官方处理器同样返回空清单。
        return new EmiPlayerInventory(List.of());
    }

    @Override
    public boolean supportsRecipe(final EmiRecipe recipe) {
        return true;
    }

    @Override
    public boolean canCraft(final EmiRecipe recipe, final EmiCraftContext<WirelessPatternGridContainerMenu> context) {
        return true;
    }

    @Override
    public boolean craft(final EmiRecipe recipe, final EmiCraftContext<WirelessPatternGridContainerMenu> context) {
        if (recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING) {
            context.getScreenHandler().transferCraftingRecipe(collectItems(recipe.getInputs()));
        } else if (recipe.getCategory() == VanillaEmiRecipeCategories.STONECUTTING) {
            transferStonecutter(recipe, context);
        } else if (recipe.getCategory() == VanillaEmiRecipeCategories.SMITHING) {
            transferSmithing(recipe, context);
        } else {
            transferProcessing(recipe, context);
        }
        return true;
    }

    private static void transferStonecutter(final EmiRecipe recipe,
                                            final EmiCraftContext<WirelessPatternGridContainerMenu> context) {
        final List<List<ItemResource>> inputs = collectItems(recipe.getInputs());
        final List<List<ItemResource>> outputs = recipe.getOutputs()
            .stream()
            .map(stack -> stack.getItemStack())
            .filter(stack -> !stack.isEmpty())
            .map(ItemResource::ofItemStack)
            .map(List::of)
            .toList();
        if (inputs.isEmpty() || outputs.isEmpty()
            || inputs.getFirst().isEmpty() || outputs.getFirst().isEmpty()) {
            return;
        }
        context.getScreenHandler().transferStonecutterRecipe(
            inputs.getFirst().getFirst(),
            outputs.getFirst().getFirst()
        );
    }

    private static void transferSmithing(final EmiRecipe recipe,
                                         final EmiCraftContext<WirelessPatternGridContainerMenu> context) {
        final List<List<ItemResource>> inputs = collectItems(recipe.getInputs());
        if (inputs.size() != 3) {
            return;
        }
        context.getScreenHandler().transferSmithingTableRecipe(
            inputs.get(0),
            inputs.get(1),
            inputs.get(2)
        );
    }

    private static void transferProcessing(final EmiRecipe recipe,
                                           final EmiCraftContext<WirelessPatternGridContainerMenu> context) {
        context.getScreenHandler().transferProcessingRecipe(
            recipe.getInputs().stream().map(WirelessPatternGridEmiHandler::toResourceAmounts).toList(),
            recipe.getOutputs().stream().map(WirelessPatternGridEmiHandler::toResourceAmounts).toList()
        );
    }

    private static List<List<ItemResource>> collectItems(final List<EmiIngredient> ingredients) {
        return ingredients.stream()
            .map(ingredient -> ingredient.getEmiStacks()
                .stream()
                .map(EmiStack::getItemStack)
                .filter(stack -> !stack.isEmpty())
                .map(ItemResource::ofItemStack)
                .collect(Collectors.toList()))
            .toList();
    }

    /** 处理样板可以把非物品资源（流体等）也编进去，所以走资源转换而不是直接取物品。 */
    private static List<ResourceAmount> toResourceAmounts(final EmiIngredient ingredient) {
        return ingredient.getEmiStacks()
            .stream()
            .flatMap(stack -> com.refinedmods.refinedstorage.common.api.RefinedStorageApi.INSTANCE
                .getIngredientConverter()
                .convertToResourceAmount(stack)
                .stream())
            .toList();
    }
}
