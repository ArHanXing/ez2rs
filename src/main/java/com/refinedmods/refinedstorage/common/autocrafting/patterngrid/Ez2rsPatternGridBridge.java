package com.refinedmods.refinedstorage.common.autocrafting.patterngrid;

import com.refinedmods.refinedstorage.common.autocrafting.ProcessingPatternState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer;
import com.refinedmods.refinedstorage.common.support.resource.ResourceContainerData;

import net.minecraft.world.Container;

/**
 * 桥接类：放在 RS2 的包里，用来访问该包私有的类型
 * （{@code ProcessingInputData} / {@code ProcessingMatrixInputResourceContainer} / {@code StonecutterInputContainer}）。
 *
 * <p>菜单里的 private 容器字段则通过 {@code PatternGridContainerMenuAccessor} mixin 读取，
 * 因为 access widener 只对游戏类生效、对第三方模组的 private 成员无效。</p>
 */
public final class Ez2rsPatternGridBridge {
    private Ez2rsPatternGridBridge() {
    }

    public static Optional<ProcessingPatternState.ProcessingIngredient> processingIngredient(
        final ResourceContainer container, final int index) {
        if (!(container instanceof ProcessingMatrixInputResourceContainer matrix)) {
            return Optional.empty();
        }
        return matrix.getIngredient(index);
    }

    public static List<net.minecraft.world.item.crafting.RecipeHolder<
        net.minecraft.world.item.crafting.StonecutterRecipe>> stonecutterRecipes(final Container container) {
        return container instanceof StonecutterInputContainer stonecutter ? stonecutter.getRecipes() : List.of();
    }

    public static int stonecutterSelectedRecipe(final Container container) {
        return container instanceof StonecutterInputContainer stonecutter ? stonecutter.getSelectedRecipe() : -1;
    }

    public static boolean stonecutterHasSelectedRecipe(final Container container) {
        return container instanceof StonecutterInputContainer stonecutter && stonecutter.hasSelectedRecipe();
    }

    /** 样板类型属性（属性类型类本身是包私有的）。 */
    public static com.refinedmods.refinedstorage.common.support.containermenu.PropertyType<PatternType>
        patternTypeProperty() {
        return PatternGridPropertyTypes.PATTERN_TYPE;
    }

    /** 样板类型属性的 id（属性类型类本身是包私有的）。 */
    public static net.minecraft.resources.ResourceLocation patternTypePropertyId() {
        return PatternGridPropertyTypes.PATTERN_TYPE.id();
    }

    /** 模糊模式属性的 id。 */
    public static net.minecraft.resources.ResourceLocation fuzzyModePropertyId() {
        return com.refinedmods.refinedstorage.common.support.containermenu.PropertyTypes.FUZZY_MODE.id();
    }

    /** 自建处理矩阵输入容器（工厂是包私有，只能在这里调用）。 */
    public static ResourceContainer newProcessingInput() {
        return PatternGridBlockEntity.createProcessingMatrixInputContainer();
    }

    /** 自建处理矩阵输出容器。 */
    public static ResourceContainer newProcessingOutput() {
        return PatternGridBlockEntity.createProcessingMatrixOutputContainer();
    }

    /** 造一个空的 ProcessingInputData（外部无法直接 new）。 */
    public static ProcessingInputData emptyProcessingInput() {
        return new ProcessingInputData(new ResourceContainerData(List.of()), new ArrayList<>());
    }

    /** 由当前处理矩阵输入容器生成下发给客户端的 ProcessingInputData。 */
    public static ProcessingInputData processingInputData(final ResourceContainer container) {
        return container instanceof ProcessingMatrixInputResourceContainer matrix
            ? ProcessingInputData.of(matrix)
            : emptyProcessingInput();
    }

    public static boolean processingInputEmpty(final ResourceContainer container) {
        return !(container instanceof ProcessingMatrixInputResourceContainer matrix) || matrix.isEmpty();
    }

    public static int processingInputSize(final ResourceContainer container) {
        return container instanceof ProcessingMatrixInputResourceContainer matrix ? matrix.size() : 0;
    }

    public static ResourceContainerData processingOutputData(final ResourceContainer container) {
        return ResourceContainerData.of(container);
    }

    public static List<Optional<ResourceAmount>> processingOutputs(final ResourceContainer container) {
        final List<Optional<ResourceAmount>> outputs = new ArrayList<>();
        for (int i = 0; i < container.size(); i++) {
            outputs.add(Optional.ofNullable(container.get(i)));
        }
        return outputs;
    }
}
