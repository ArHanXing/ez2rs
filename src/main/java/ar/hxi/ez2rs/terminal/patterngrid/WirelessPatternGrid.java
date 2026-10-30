package ar.hxi.ez2rs.terminal.patterngrid;

import com.refinedmods.refinedstorage.common.api.grid.Grid;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.Ez2rsPatternGridBridge;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridData;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer;
import com.refinedmods.refinedstorage.common.grid.GridData;
import com.refinedmods.refinedstorage.common.support.resource.ResourceContainerData;

import net.minecraft.world.Container;

/**
 * 组装无线样板终端下发的 {@link PatternGridData}。
 *
 * <p>这里给的是<b>真实</b>的网格数据（{@link GridData#of(Grid)}），不是空快照 ——
 * 客户端靠它渲染资源列表并显示能否存取；服务端另有一份真正的 Grid 负责 watcher 与策略，
 * 见 {@code WirelessPatternGridContainerMenu}。</p>
 */
public final class WirelessPatternGrid {
    private WirelessPatternGrid() {
    }

    /** 初次打开：草稿为空，但网格数据是活的。 */
    public static PatternGridData initial(final Grid grid) {
        return new PatternGridData(
            GridData.of(grid),
            PatternType.CRAFTING,
            Ez2rsPatternGridBridge.emptyProcessingInput(),
            new ResourceContainerData(java.util.List.of()),
            -1
        );
    }

    /** 按当前容器状态组包（重开界面时用）。 */
    public static PatternGridData capture(final Grid grid,
                                          final ResourceContainer processingInput,
                                          final ResourceContainer processingOutput,
                                          final Container stonecutter,
                                          final PatternType patternType) {
        return new PatternGridData(
            GridData.of(grid),
            patternType,
            Ez2rsPatternGridBridge.processingInputData(processingInput),
            Ez2rsPatternGridBridge.processingOutputData(processingOutput),
            Ez2rsPatternGridBridge.stonecutterSelectedRecipe(stonecutter)
        );
    }
}
