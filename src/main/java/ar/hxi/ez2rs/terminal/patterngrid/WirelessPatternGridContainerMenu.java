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
        // 注意：这里绝不能 registerProperty 一个新的属性来"替换"父类那份。
        // registerProperty 除了 put 进 map，还会 addDataSlot —— 两端 slot 数就会不一致
        // （服务端多两个），服务端下发索引 4 时客户端越界：
        //   IndexOutOfBoundsException: Index 4 out of bounds for length 4
        // 所以沿用父类的 ClientProperty，只在 receivePropertyChangeFromClient 里写它。
        this.slotReference = slotReference;
        this.disabledSlot = slotReference;
        final Player player = playerInventory.player;
        this.processingInput = Ez2rsPatternGridBridge.readProcessingInput(this);
        this.stonecutter = Ez2rsPatternGridBridge.readStonecutterInput(this);
        this.crafting = RecipeMatrix.crafting(() -> {
        }, player::level);
        this.smithing = RecipeMatrix.smithingTable(() -> {
        }, player::level);
        if (restore) {
            restoreFromItem(player);
        }
    }

    /** 样板类型的真实存储（服务端权威）。 */
    private PatternType draftPatternType = PatternType.CRAFTING;
    /** 模糊模式的真实存储（服务端权威）。 */
    private boolean draftFuzzy;

    private PatternType getDraftPatternType() {
        return draftPatternType;
    }

    private void setDraftPatternType(final PatternType type) {
        this.draftPatternType = type;
    }

    private boolean isDraftFuzzy() {
        return draftFuzzy;
    }

    private void setDraftFuzzy(final boolean fuzzy) {
        this.draftFuzzy = fuzzy;
    }

    /**
     * 处理矩阵 / 石板机用的是父类的那几个容器 —— 槽位绑定的就是它们。
     *
     * <p>之前这里自建了一份，导致放进槽位的材料进了父类容器、我们读自己的空容器，
     * 于是处理样板放不了东西、切石机也选不出产出。声明类型是包私有类，
     * 所以通过 accessor 以 Object 取出、再由桥接强转。</p>
     */
    private com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer processingInput;

    private Object stonecutter;

    /** 上一 tick 的草稿快照，用来发现矩阵/石板机的变化。 */
    private List<String> lastDraftSnapshot = List.of();

    private com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer processingOutput() {
        return self().ez2rs$processingOutput();
    }

    /**
     * 自己处理客户端的属性变更。
     *
     * <p>父类 {@code receivePropertyChangeFromClient} 会无条件把属性强转成 {@code ServerProperty}，
     * 但无线版两端用的都是父类的 {@code ClientProperty}，强转必然失败。</p>
     *
     * <p>这里直接写父类那个 {@code ClientProperty}（它的 {@code set(int)} 只更新本地
     * value/rawValue，不会再发包），于是父类的 {@code getPatternType()} 立刻拿到新值 ——
     * 槽位的 {@code isActive()} 就是靠它判断的，不写就会导致切换类型后矩阵槽位被禁用。</p>
     */
    @Override
    public void receivePropertyChangeFromClient(final net.minecraft.resources.ResourceLocation id,
                                                final int newValue) {
        if (Ez2rsPatternGridBridge.patternTypePropertyId().equals(id)) {
            final PatternType[] values = PatternType.values();
            if (newValue >= 0 && newValue < values.length) {
                draftPatternType = values[newValue];
                getProperty(Ez2rsPatternGridBridge.patternTypeProperty()).set(newValue);
            }
            return;
        }
        if (Ez2rsPatternGridBridge.fuzzyModePropertyId().equals(id)) {
            draftFuzzy = newValue != 0;
            getProperty(com.refinedmods.refinedstorage.common.support.containermenu.PropertyTypes.FUZZY_MODE)
                .set(newValue);
            return;
        }
        if (Ez2rsPatternGridBridge.stonecutterRecipePropertyId().equals(id)) {
            // 切石机选中项：存到父类的 granitecutter 容器里，
            // canCreatePattern() 与编码都读它。
            Ez2rsPatternGridBridge.setStonecutterSelected(stonecutter, newValue);
            getProperty(Ez2rsPatternGridBridge.stonecutterRecipeProperty()).set(newValue);
            return;
        }
        // 不要调 super：它会把属性无条件强转成 ServerProperty，而这里是 ClientProperty。
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

    /**
     * 样板槽放进一个已编码样板时，自动切到它所属的类型并把内容铺回矩阵。
     *
     * <p>对应方块版的 {@code PatternGridBlockEntity#copyPattern}：
     * 读样板类型 -> 切类型 -> 把材料/产出填回对应的矩阵，玩家不用手动切换再重摆。</p>
     */
    public void ez2rs$copyPattern(final ItemStack stack) {
        final com.refinedmods.refinedstorage.common.autocrafting.PatternState state =
            stack.get(com.refinedmods.refinedstorage.common.content.DataComponents.INSTANCE.getPatternState());
        if (state == null) {
            return;
        }
        draftPatternType = state.type();
        // 同步给客户端，让按钮图标和矩阵区跟着切
        getProperty(Ez2rsPatternGridBridge.patternTypeProperty()).set(state.type().ordinal());
        switch (state.type()) {
            case CRAFTING -> copyCrafting(stack);
            case PROCESSING -> copyProcessing(stack);
            case STONECUTTER -> copyStonecutter(stack);
            case SMITHING_TABLE -> copySmithing(stack);
        }
    }

    private void copyCrafting(final ItemStack stack) {
        final var state = stack.get(
            com.refinedmods.refinedstorage.common.content.DataComponents.INSTANCE.getCraftingPatternState());
        if (state == null) {
            return;
        }
        draftFuzzy = state.fuzzyMode();
        getProperty(com.refinedmods.refinedstorage.common.support.containermenu.PropertyTypes.FUZZY_MODE)
            .set(draftFuzzy ? 1 : 0);
        parentCraftingMatrix.clearContent();
        final var positioned = state.input();
        final var input = positioned.input();
        for (int x = 0; x < input.width(); x++) {
            for (int y = 0; y < input.height(); y++) {
                final int index = x + positioned.left() + (y + positioned.top()) * 3;
                if (index >= 0 && index < parentCraftingMatrix.getContainerSize()) {
                    parentCraftingMatrix.setItem(index, input.getItem(x + y * input.width()));
                }
            }
        }
        syncCrafting();
    }

    private void copyProcessing(final ItemStack stack) {
        final var state = stack.get(
            com.refinedmods.refinedstorage.common.content.DataComponents.INSTANCE.getProcessingPatternState());
        if (state == null) {
            return;
        }
        for (int i = 0; i < state.ingredients().size() && i < processingInput.size(); i++) {
            final int index = i;
            state.ingredients().get(i).ifPresent(ingredient ->
                Ez2rsPatternGridBridge.setProcessingIngredient(processingInput, this, index, ingredient));
        }
        final var output = processingOutput();
        for (int i = 0; i < state.outputs().size() && i < output.size(); i++) {
            final int index = i;
            state.outputs().get(i).ifPresent(amount -> output.set(index, amount));
        }
    }

    private void copyStonecutter(final ItemStack stack) {
        final var state = stack.get(
            com.refinedmods.refinedstorage.common.content.DataComponents.INSTANCE.getStonecutterPatternState());
        if (state == null) {
            return;
        }
        final Container stonecutterContainer =
            (Container) Ez2rsPatternGridBridge.asStonecutterRaw(stonecutter);
        stonecutterContainer.clearContent();
        stonecutterContainer.setItem(0, state.input().toItemStack());
        Ez2rsPatternGridBridge.setStonecutterSelected(stonecutter, -1);
        final ItemStack wanted = state.selectedOutput().toItemStack();
        final var recipes = Ez2rsPatternGridBridge.stonecutterRecipesOf(stonecutter);
        for (int i = 0; i < recipes.size(); i++) {
            final ItemStack result = recipes.get(i).value().assemble(
                new SingleRecipeInput(state.input().toItemStack()),
                playerInventory.player.level().registryAccess()
            );
            if (ItemStack.isSameItemSameComponents(result, wanted)) {
                Ez2rsPatternGridBridge.setStonecutterSelected(stonecutter, i);
                break;
            }
        }
    }

    private void copySmithing(final ItemStack stack) {
        final var state = stack.get(
            com.refinedmods.refinedstorage.common.content.DataComponents.INSTANCE.getSmithingTablePatternState());
        if (state == null) {
            return;
        }
        parentSmithingMatrix.clearContent();
        parentSmithingMatrix.setItem(0, state.template().toItemStack());
        parentSmithingMatrix.setItem(1, state.base().toItemStack());
        parentSmithingMatrix.setItem(2, state.addition().toItemStack());
        syncSmithing();
    }

    /**
     * 槽位一变就重算配方矩阵，把结果写进父类的 result 容器。
     *
     * <p>方块版靠 RecipeMatrix 自己监听矩阵变化，我们是从 GridData 构造的，
     * 没有那个监听链。不在这里主动同步，父类的 {@code canCreatePattern()}
     * 会一直看到空的 result，编码按钮永远不可用。</p>
     */
    /**
     * 每 tick 检查一次草稿变化。
     *
     * <p>不能靠 {@code slotsChanged}：矩阵容器是原版 {@code TransientCraftingContainer}，
     * 它的 {@code setChanged()} 是空实现，压根不会通知菜单；方块版是靠自己给
     * {@code RecipeMatrixContainer} 传了 listener 才有通知链，而父类给我们构造的那个
     * 传的是 null。</p>
     *
     * <p>所以这里自己比对上一 tick 的快照，变了就重算一次。</p>
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (playerInventory.player.level().isClientSide()) {
            return;
        }
        final List<String> snapshot = snapshotDraft();
        if (snapshot.equals(lastDraftSnapshot)) {
            return;
        }
        lastDraftSnapshot = snapshot;
        syncCrafting();
        syncSmithing();
        autoSwitchToPatternInOutput();
    }

    private List<String> snapshotDraft() {
        final List<String> items = new java.util.ArrayList<>();
        for (int i = 0; i < parentCraftingMatrix.getContainerSize(); i++) {
            items.add(parentCraftingMatrix.getItem(i).toString());
        }
        for (int i = 0; i < parentSmithingMatrix.getContainerSize(); i++) {
            items.add(parentSmithingMatrix.getItem(i).toString());
        }
        final Container stonecutterContainer =
            (Container) Ez2rsPatternGridBridge.asStonecutterRaw(stonecutter);
        items.add(stonecutterContainer.getItem(0).toString());
        items.add(String.valueOf(Ez2rsPatternGridBridge.stonecutterSelectedOf(stonecutter)));
        items.add(String.valueOf(draftPatternType));
        items.add(self().ez2rs$patternOutput().getItem(0).toString());
        return items;
    }

    /**
     * 样板输出槽里出现已编码样板时，自动切到它所属的类型。
     *
     * <p>对应方块版在 {@code patternOutputSlot.set()} 里调用 copyPattern 的行为。
     * 这里用容器变化作为触发点：只要槽里是样板且类型与当前不同，就切过去。</p>
     */
    private void autoSwitchToPatternInOutput() {
        if (playerInventory.player.level().isClientSide()) {
            return;
        }
        final ItemStack inOutput = self().ez2rs$patternOutput().getItem(0);
        if (inOutput.isEmpty()) {
            return;
        }
        final var state = inOutput.get(
            com.refinedmods.refinedstorage.common.content.DataComponents.INSTANCE.getPatternState());
        if (state == null || state.type() == draftPatternType) {
            return;
        }
        ez2rs$copyPattern(inOutput);
    }

    @Override
    public void createPattern() {
        final Player player = playerInventory.player;
        if (player.level() == null) {
            return;
        }
        syncCrafting();
        syncSmithing();
        final ItemStack result = switch (draftPatternType) {
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
            new CraftingPatternState(draftFuzzy, crafting.getMatrix().asPositionedCraftInput())
        );
        return result;
    }

    private ItemStack createProcessingPattern() {
        if (processingInput.isEmpty()
            || processingOutput().isEmpty()) {
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
            processingOutput()
        );
        result.set(
            DataComponents.INSTANCE.getProcessingPatternState(),
            new ProcessingPatternState(ingredients, outputs)
        );
        return result;
    }

    private ItemStack createStonecutterPattern(final Player player) {
        final Container stonecutterContainer =
            (Container) Ez2rsPatternGridBridge.asStonecutterRaw(stonecutter);
        if (stonecutterContainer.getItem(0).isEmpty()
            || !Ez2rsPatternGridBridge.stonecutterHasSelectedOf(stonecutter)) {
            return ItemStack.EMPTY;
        }
        final ItemStack input = stonecutterContainer.getItem(0);
        final List<RecipeHolder<StonecutterRecipe>> recipes =
            Ez2rsPatternGridBridge.stonecutterRecipesOf(stonecutter);
        final int selected = Ez2rsPatternGridBridge.stonecutterSelectedOf(stonecutter);
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
        final Player player = playerInventory.player;
        // 只在服务端算：客户端那次调用里 RecipeMatrix.updateResult 会直接 return，
        // getResult() 是空的，把这个空值写回容器会覆盖掉刚同步下来的服务端结果
        // （表现为"放完东西不显示，重开界面才有"）。
        if (player.level() == null || player.level().isClientSide()) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            crafting.getMatrix().setItem(i, parentCraftingMatrix.getItem(i));
        }
        crafting.updateResult(player.level());
        self().ez2rs$craftingResult().setItem(0, crafting.getResult().getItem(0));
    }

    private void syncSmithing() {
        final Player player = playerInventory.player;
        if (player.level() == null || player.level().isClientSide()) {
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
        // 通用终端的样板页与独立「无线样板终端」都走同一套草稿组件
        if (!ar.hxi.ez2rs.terminal.Ez2rsTerminalContent.isPatternGridCapable(stack)) {
            return;
        }
        stack.set(
            Ez2rsTerminalContent.PATTERN_GRID_DRAFT,
            PatternGridDraft.capture(
                player.level(),
                draftPatternType,
                draftFuzzy,
                parentCraftingMatrix,
                parentSmithingMatrix,
                Ez2rsPatternGridBridge.asStonecutterRaw(stonecutter),
                self().ez2rs$patternInput(),
                self().ez2rs$patternOutput(),
                processingInput,
                processingOutput()
            )
        );
    }

    private void restoreFromItem(final Player player) {
        final ItemStack stack = slotReference.resolve(player).orElse(ItemStack.EMPTY);
        // 通用终端的样板页与独立「无线样板终端」都走同一套草稿组件
        if (!ar.hxi.ez2rs.terminal.Ez2rsTerminalContent.isPatternGridCapable(stack)) {
            return;
        }
        final net.minecraft.nbt.CompoundTag draft = stack.get(Ez2rsTerminalContent.PATTERN_GRID_DRAFT);
        if (draft == null) {
            return;
        }
        draftPatternType = PatternGridDraft.patternType(draft);
        draftFuzzy = PatternGridDraft.fuzzy(draft);
        PatternGridDraft.restore(
            draft,
            player.level(),
            parentCraftingMatrix,
            parentSmithingMatrix,
            Ez2rsPatternGridBridge.asStonecutterRaw(stonecutter),
            self().ez2rs$patternInput(),
            self().ez2rs$patternOutput(),
            processingInput,
            processingOutput()
        );
        syncCrafting();
        syncSmithing();
    }
}
