package ar.hxi.ez2rs.mixin;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridContainerMenu;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;

import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 读取样板网格菜单里那些 private 成员。
 *
 * <p>为什么必须用 accessor mixin：这些字段是 private，同包桥接类也读不到；
 * access widener 只对游戏类生效，对第三方模组无效。</p>
 *
 * <p>{@code processingInput} / {@code stonecutterInput} 的声明类型是包私有类，accessor 必须按
 * 真实类型声明才能匹配，而那样会导致 remap 失败，因此这两个容器不从这里取：
 * 处理矩阵走 RS2 公开的 {@code ProcessingMatrixInputResourceContainer} 工厂，
 * 石板机则在无线终端里自建一个等价容器。</p>
 */
@Mixin(PatternGridContainerMenu.class)
public interface PatternGridContainerMenuAccessor {
    @Accessor(value = "patternInput", remap = false)
    Container ez2rs$patternInput();

    @Accessor(value = "patternOutput", remap = false)
    Container ez2rs$patternOutput();

    @Accessor(value = "craftingMatrix", remap = false)
    Container ez2rs$craftingMatrix();

    @Accessor(value = "craftingResult", remap = false)
    Container ez2rs$craftingResult();

    @Accessor(value = "processingOutput", remap = false)
    com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer ez2rs$processingOutput();

    @Accessor(value = "smithingTableMatrix", remap = false)
    Container ez2rs$smithingTableMatrix();

    @Accessor(value = "smithingTableResult", remap = false)
    Container ez2rs$smithingTableResult();

    @Invoker(value = "getPatternType", remap = false)
    PatternType ez2rs$patternType();

    @Invoker(value = "setPatternType", remap = false)
    void ez2rs$setPatternType(PatternType type);

    @Invoker(value = "isFuzzyMode", remap = false)
    boolean ez2rs$fuzzyMode();

    @Invoker(value = "setFuzzyMode", remap = false)
    void ez2rs$setFuzzyMode(boolean fuzzy);
}
