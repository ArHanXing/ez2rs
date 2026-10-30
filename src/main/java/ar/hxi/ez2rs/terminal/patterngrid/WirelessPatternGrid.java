package ar.hxi.ez2rs.terminal.patterngrid;

import java.util.List;
import java.util.Set;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.Ez2rsPatternGridBridge;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridData;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.grid.GridData;
import com.refinedmods.refinedstorage.common.support.resource.ResourceContainerData;

/**
 * 组装无线样板终端需要的 {@link PatternGridData}。
 *
 * <p>样板网格界面本身不显示资源列表，所以 GridData 是空的；处理矩阵输入输出与石板机选中项
 * 直接从 RS2 的菜单容器取出（经过同包桥接类，因为相关类型是包私有的）。</p>
 */
public final class WirelessPatternGrid {
    private WirelessPatternGrid() {
    }

    public static PatternGridData capture(final com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer processingInput,
                                          final com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer processingOutput,
                                          final net.minecraft.world.Container stonecutter,
                                          final PatternType patternType) {
        return new PatternGridData(
            new GridData(false, List.of(), Set.of()),
            patternType,
            Ez2rsPatternGridBridge.processingInputData(processingInput),
            Ez2rsPatternGridBridge.processingOutputData(processingOutput),
            Ez2rsPatternGridBridge.stonecutterSelectedRecipe(stonecutter)
        );
    }

    public static PatternGridData empty() {
        return new PatternGridData(
            new GridData(false, List.of(), Set.of()),
            PatternType.CRAFTING,
            Ez2rsPatternGridBridge.emptyProcessingInput(),
            new ResourceContainerData(List.of()),
            -1
        );
    }
}
