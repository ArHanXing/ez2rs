package ar.hxi.ez2rs.terminal.patterngrid;

import ar.hxi.ez2rs.terminal.Ez2rsTerminalContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference;
import com.refinedmods.refinedstorage.common.autocrafting.CraftingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.ProcessingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.SmithingTablePatternState;
import com.refinedmods.refinedstorage.common.autocrafting.StonecutterPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.Ez2rsPatternGridBridge;
import ar.hxi.ez2rs.mixin.PatternGridContainerMenuAccessor;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.Ez2rsStonecutterRecipeInput;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridContainerMenu;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridData;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.content.DataComponents;
import com.refinedmods.refinedstorage.common.support.RecipeMatrix;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/**
 * 无线样板终端的菜单。
 *
 * <p>直接继承 RS2 的 {@link PatternGridContainerMenu}，用它的公开构造器（本来是给客户端用的那种）
 * 在双端各自建一套容器，于是 RS2 的样板网格界面、槽位布局、C2S 包全部原样复用。</p>
 *
 * <p>区别只有两处：</p>
 * <ul>
 *     <li>合成/锻造需要配方解析，而方块实体版是方块实体在维护。这里自建
 *     {@link RecipeMatrix}，并在父类容器变化时同步过去、把结果写回父类的 result 槽，
 *     这样父类的 {@code canCreatePattern()} 也能正常工作。</li>
 *     <li>状态（草稿、样板类型、模糊模式、石板机选中项）不落在方块上，关界面时写回物品组件。</li>
 * </ul>
 */
public class WirelessPatternGridContainerMenu extends PatternGridContainerMenu {
    private final SlotReference slotReference;
    private final RecipeMatrix<CraftingRecipe, CraftingInput> crafting;
    private final RecipeMatrix<SmithingRecipe, SmithingRecipeInput> smithing;

    public WirelessPatternGridContainerMenu(final int syncId,
                                            final Inventory playerInventory,
                                            final WirelessPatternGridData data) {
        this(syncId, playerInventory, data.patternGridData(), data.slotReference(), true);
    }

    public WirelessPatternGridContainerMenu(final int syncId,
                                            final Inventory playerInventory,
                                            final PatternGridData data,
                                            final SlotReference slotReference,
                                            final boolean restore) {
        super(syncId, playerInventory, data);
        // 父类的这个构造器注册的是 ClientProperty（面向纯客户端）；
        // 服务端一侧必须是 ServerProperty，否则客户端改属性时
        // AbstractBaseContainerMenu#receivePropertyChangeFromClient 会 ClassCastException。
        // 所以这里把父类注册的那几个 property 换成服务端的可写版本。
        this.slotReference = slotReference;
        this.disabledSlot = slotReference;
        final Player player = playerInventory.player;
        this.crafting = RecipeMatrix.crafting(() -> {
        }, player::level);
        this.smithing = RecipeMatrix.smithingTable(() -> {
        }, player::level);
        this.processingInput = Ez2rsPatternGridBridge.newProcessingInput();
        this.processingOutput = Ez2rsPatternGridBridge.newProcessingOutput();
        this.stonecutter = new StonecutterDraftContainer(player::level);
        if (restore) {
            restoreFromItem(player);
        }
    }

    /** 处理矩阵输入/输出、石板机输入：父类里的那两个字段是包私有类型，外部拿不到，这里自建一份。 */
    private final com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer processingInput;
    private final com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer processingOutput;
    private final StonecutterDraftContainer stonecutter;

    /**
     * 自己处理客户端的属性变更。
     *
     * <p>父类 {@code receivePropertyChangeFromClient} 会无条件把属性强转成 {@code ServerProperty}，
     * 但无线版用的是服务端也是客户端构造器（{@code ClientProperty}），于是强转必然失败。
     * 这里改为直接写回我们自己的字段。</p>
     */
    @Override
    public void receivePropertyChangeFromClient(final net.minecraft.resources.ResourceLocation id,
                                                final int newValue) {
        if (Ez2rsPatternGridBridge.patternTypePropertyId().equals(id)) {
            final PatternType[] values = PatternType.values();
            if (newValue >= 0 && newValue < values.length) {
                self().ez2rs$setPatternType(values[newValue]);
            }
            return;
        }
        if (Ez2rsPatternGridBridge.fuzzyModePropertyId().equals(id)) {
            self().ez2rs$setFuzzyMode(newValue != 0);
            return;
        }
        super.receivePropertyChangeFromClient(id, newValue);
    }

    /**
     * 无线终端的菜单必须一直视为有效。
     *
     * <p>父类用 {@code grid.canMenuStayOpen(player)} 做方块距离校验，而无线版没有方块、
     * grid 也是空的，于是界面刚打开就会被服务端关掉（表现为闪一下消失）。
     * RS2 自己的无线监视器菜单同样是直接返回 {@code true}。</p>
     */
    @Override
    public boolean stillValid(final Player player) {
        return true;
    }

    /**
     * 覆写成我们自己的菜单类型。
     *
     * <p>父类构造器会把 menuType 固定成 RS2 的样板网格类型，而 Fabric 打开界面时按
     * {@code getType()} 决定用哪个数据 codec —— 于是它拿 RS2 的 PatternGridData codec
     * 去编码我们的 WirelessPatternGridData，发包时 ClassCastException。</p>
     */
    @Override
    public net.minecraft.world.inventory.MenuType<?> getType() {
        return ar.hxi.ez2rs.terminal.Ez2rsMenus.wirelessPatternGrid();
    }

    /** 父类里的 3x3 合成矩阵 / 3x1 锻造矩阵。 */
    private PatternGridContainerMenuAccessor self() {
        return (PatternGridContainerMenuAccessor) (Object) this;
    }

    private final Container parentCraftingMatrix = self().ez2rs$craftingMatrix();
    private final Container parentSmithingMatrix = self().ez2rs$smithingTableMatrix();

    @Override
    public void createPattern() {
        final Player player = this.player;
        if (player == null || player.level() == null) {
            return;
        }
        syncCrafting();
        syncSmithing();
        final ItemStack result = switch (self().ez2rs$patternType()) {
            case CRAFTING -> createCraftingPattern();
            case PROCESSING -> createProcessingPattern();
            case STONECUTTER -> createStonecutterPattern(player);
            case SMITHING_TABLE -> createSmithingTablePattern();
        };
        if (result != null && result != ItemStack.EMPTY) {
            if (self().ez2rs$patternOutput().getItem(0).isEmpty()) {
                self().ez2rs$patternInput().removeItem(0, 1);
            }
            self().ez2rs$patternOutput().setItem(0, result);
        }
        persistToItem(player);
    }

    private ItemStack createCraftingPattern() {
        if (!crafting.hasResult()) {
            return ItemStack.EMPTY;
        }
        final ItemStack result = PatternGridBlockEntity.createPatternStack(PatternType.CRAFTING);
        result.set(
            DataComponents.INSTANCE.getCraftingPatternState(),
            new CraftingPatternState(self().ez2rs$fuzzyMode(), crafting.getMatrix().asPositionedCraftInput())
        );
        return result;
    }

    private ItemStack createProcessingPattern() {
        if (processingInput.isEmpty()
            || processingOutput.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final ItemStack result = PatternGridBlockEntity.createPatternStack(PatternType.PROCESSING);
        final List<Optional<ProcessingPatternState.ProcessingIngredient>> ingredients = new ArrayList<>();
        final com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer input = processingInput;
        final int inputSize = Ez2rsPatternGridBridge.processingInputSize(input);
        for (int i = 0; i < inputSize; i++) {
            ingredients.add(Ez2rsPatternGridBridge.processingIngredient(input, i));
        }
        final List<Optional<ResourceAmount>> outputs = Ez2rsPatternGridBridge.processingOutputs(
            processingOutput
        );
        result.set(
            DataComponents.INSTANCE.getProcessingPatternState(),
            new ProcessingPatternState(ingredients, outputs)
        );
        return result;
    }

    private ItemStack createStonecutterPattern(final Player player) {
        if (stonecutter.getItem(0).isEmpty() || !stonecutter.hasSelectedRecipe()) {
            return ItemStack.EMPTY;
        }
        final ItemStack input = stonecutter.getItem(0);
        final List<RecipeHolder<StonecutterRecipe>> recipes = stonecutter.getRecipes();
        final int selected = stonecutter.getSelectedRecipe();
        if (selected < 0 || selected >= recipes.size()) {
            return ItemStack.EMPTY;
        }
        final ItemStack output = recipes.get(selected).value().assemble(
            new SingleRecipeInput(input),
            player.level().registryAccess()
        );
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final ItemStack result = PatternGridBlockEntity.createPatternStack(PatternType.STONECUTTER);
        result.set(
            DataComponents.INSTANCE.getStonecutterPatternState(),
            new StonecutterPatternState(ItemResource.ofItemStack(input), ItemResource.ofItemStack(output))
        );
        return result;
    }

    private ItemStack createSmithingTablePattern() {
        if (!smithing.hasResult()) {
            return ItemStack.EMPTY;
        }
        final ItemStack result = PatternGridBlockEntity.createPatternStack(PatternType.SMITHING_TABLE);
        result.set(
            DataComponents.INSTANCE.getSmithingTablePatternState(),
            new SmithingTablePatternState(
                ItemResource.ofItemStack(smithing.getMatrix().getItem(0)),
                ItemResource.ofItemStack(smithing.getMatrix().getItem(1)),
                ItemResource.ofItemStack(smithing.getMatrix().getItem(2))
            )
        );
        return result;
    }

    /** 把父类容器里玩家摆的物品同步到本地配方矩阵，并把结果写回父类的 result 槽。 */
    private void syncCrafting() {
        final Player player = this.player;
        if (player == null || player.level() == null) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            crafting.getMatrix().setItem(i, parentCraftingMatrix.getItem(i));
        }
        crafting.updateResult(player.level());
        self().ez2rs$craftingResult().setItem(0, crafting.getResult().getItem(0));
    }

    private void syncSmithing() {
        final Player player = this.player;
        if (player == null || player.level() == null) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            smithing.getMatrix().setItem(i, parentSmithingMatrix.getItem(i));
        }
        smithing.updateResult(player.level());
        self().ez2rs$smithingTableResult().setItem(0, smithing.getResult().getItem(0));
    }

    @Override
    public void removed(final Player player) {
        persistToItem(player);
        super.removed(player);
    }

    private void persistToItem(final Player player) {
        final ItemStack stack = slotReference.resolve(player).orElse(ItemStack.EMPTY);
        if (!(stack.getItem() instanceof ar.hxi.ez2rs.terminal.WirelessUniversalGridItem)) {
            return;
        }
        stack.set(
            Ez2rsTerminalContent.PATTERN_GRID_DRAFT,
            PatternGridDraft.capture(
                player.level(),
                self().ez2rs$patternType(),
                self().ez2rs$fuzzyMode(),
                parentCraftingMatrix,
                parentSmithingMatrix,
                stonecutter,
                self().ez2rs$patternInput(),
                self().ez2rs$patternOutput(),
                processingInput,
                processingOutput
            )
        );
    }

    private void restoreFromItem(final Player player) {
        final ItemStack stack = slotReference.resolve(player).orElse(ItemStack.EMPTY);
        if (!(stack.getItem() instanceof ar.hxi.ez2rs.terminal.WirelessUniversalGridItem)) {
            return;
        }
        final net.minecraft.nbt.CompoundTag draft = stack.get(Ez2rsTerminalContent.PATTERN_GRID_DRAFT);
        if (draft == null) {
            return;
        }
        self().ez2rs$setPatternType(PatternGridDraft.patternType(draft));
        self().ez2rs$setFuzzyMode(PatternGridDraft.fuzzy(draft));
        PatternGridDraft.restore(
            draft,
            player.level(),
            parentCraftingMatrix,
            parentSmithingMatrix,
            stonecutter,
            self().ez2rs$patternInput(),
            self().ez2rs$patternOutput(),
            processingInput,
            processingOutput
        );
        syncCrafting();
        syncSmithing();
    }
}
