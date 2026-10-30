package ar.hxi.ez2rs.terminal.patterngrid;

import com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.util.ContainerUtil;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;

/**
 * 无线样板终端的草稿快照：玩家没取走的样板、摆在合成/锻造矩阵里的材料等都存在这里。
 *
 * <p>关界面时整包写进物品的 {@code ez2rs:pattern_grid_draft} 组件，下次打开原样恢复，
 * 这样"关掉界面草稿就丢"的问题不存在。</p>
 */
public final class PatternGridDraft {
    private static final String PATTERN_TYPE = "pattern_type";
    private static final String FUZZY = "fuzzy";
    private static final String CRAFTING = "crafting";
    private static final String SMITHING = "smithing";
    private static final String STONECUTTER = "stonecutter";
    private static final String PATTERN_INPUT = "pattern_input";
    private static final String PATTERN_OUTPUT = "pattern_output";
    private static final String PROCESSING_INPUT = "processing_input";
    private static final String PROCESSING_OUTPUT = "processing_output";

    private PatternGridDraft() {
    }

    public static CompoundTag capture(final Level level,
                                      final PatternType patternType,
                                      final boolean fuzzy,
                                      final Container crafting,
                                      final Container smithing,
                                      final Container stonecutter,
                                      final Container patternInput,
                                      final Container patternOutput,
                                      final ResourceContainer processingInput,
                                      final ResourceContainer processingOutput) {
        final HolderLookup.Provider provider = level.registryAccess();
        final CompoundTag tag = new CompoundTag();
        tag.putString(PATTERN_TYPE, patternType.name());
        tag.putBoolean(FUZZY, fuzzy);
        tag.put(CRAFTING, ContainerUtil.write(crafting, provider));
        tag.put(SMITHING, ContainerUtil.write(smithing, provider));
        tag.put(STONECUTTER, ContainerUtil.write(stonecutter, provider));
        tag.put(PATTERN_INPUT, ContainerUtil.write(patternInput, provider));
        tag.put(PATTERN_OUTPUT, ContainerUtil.write(patternOutput, provider));
        tag.put(PROCESSING_INPUT, processingInput.toTag(provider));
        tag.put(PROCESSING_OUTPUT, processingOutput.toTag(provider));
        return tag;
    }

    public static PatternType patternType(final CompoundTag tag) {
        return PatternType.valueOf(tag.getString(PATTERN_TYPE));
    }

    public static boolean fuzzy(final CompoundTag tag) {
        return tag.getBoolean(FUZZY);
    }

    public static void restore(final CompoundTag tag,
                               final Level level,
                               final Container crafting,
                               final Container smithing,
                               final Container stonecutter,
                               final Container patternInput,
                               final Container patternOutput,
                               final ResourceContainer processingInput,
                               final ResourceContainer processingOutput) {
        final HolderLookup.Provider provider = level.registryAccess();
        ContainerUtil.read(tag.getCompound(CRAFTING), crafting, provider);
        ContainerUtil.read(tag.getCompound(SMITHING), smithing, provider);
        ContainerUtil.read(tag.getCompound(STONECUTTER), stonecutter, provider);
        ContainerUtil.read(tag.getCompound(PATTERN_INPUT), patternInput, provider);
        ContainerUtil.read(tag.getCompound(PATTERN_OUTPUT), patternOutput, provider);
        processingInput.fromTag(tag.getCompound(PROCESSING_INPUT), provider);
        processingOutput.fromTag(tag.getCompound(PROCESSING_OUTPUT), provider);
    }
}
