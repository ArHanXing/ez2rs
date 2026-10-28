package ar.hxi.ez2rs.client;

import com.refinedmods.refinedstorage.api.autocrafting.Ingredient;
import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.api.RefinedStorageClientApi;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceRendering;
import com.refinedmods.refinedstorage.common.support.Sprites;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static com.refinedmods.refinedstorage.common.support.Sprites.LIGHT_ARROW;
import static com.refinedmods.refinedstorage.common.support.Sprites.LIGHT_ARROW_HEIGHT;
import static com.refinedmods.refinedstorage.common.support.Sprites.LIGHT_ARROW_WIDTH;
import static com.refinedmods.refinedstorage.common.support.Sprites.SLOT;

/**
 * 样板输入 / 输出预览界面（ExtendedAE 风格）。
 *
 * <p>左侧是输入（每个 {@link Ingredient} 占一格，多备选原料每秒循环切换一次，
 * 与 RS2 自己的样板 tooltip 行为一致），中间箭头，右侧是输出；有副产品时在下方单独一栏。
 * 数值用 {@link ResourceRendering#formatAmount(long, boolean)} 的带单位格式（1k、1M），
 * 保证在 18x18 的槽位里放得下。</p>
 */
public class PatternPreviewScreen extends Screen {
    private static final int COLUMNS = 3;
    private static final int SLOT_SIZE = 18;
    private static final int LABEL_HEIGHT = 9;
    private static final int CELL_HEIGHT = SLOT_SIZE + LABEL_HEIGHT;
    private static final int ARROW_SPACING = 8;
    private static final int PADDING = 8;
    private static final int TITLE_HEIGHT = 12;
    private static final long CYCLE_MS = 1000;

    private static final int BORDER_DARK = 0xFF373737;
    private static final int PANEL_COLOR = 0xFFC6C6C6;
    private static final int BORDER_LIGHT = 0xFFFFFFFF;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int LABEL_COLOR = 0xFF404040;
    private static final int AMOUNT_COLOR = 0xFF555555;
    private static final int SEPARATOR_COLOR = 0xFF8B8B8B;

    private static final Component BYPRODUCTS_LABEL = Component.translatable(
        "gui.ez2rs.pattern_preview.byproducts"
    );

    private final Screen parent;
    private final List<Ingredient> ingredients;
    private final List<ResourceAmount> outputs;
    private final List<ResourceAmount> byproducts;
    private final int gridRows;
    private final int byproductRows;
    private final int panelWidth;
    private final int panelHeight;

    private int panelX;
    private int panelY;
    private long cycleStart;
    private int cycle;

    public PatternPreviewScreen(final Screen parent, final Component title, final Pattern pattern) {
        super(title);
        this.parent = parent;
        this.ingredients = pattern.layout().ingredients();
        this.outputs = pattern.layout().outputs();
        this.byproducts = pattern.layout().byproducts();
        final int inputRows = Math.max(1, Math.ceilDiv(ingredients.size(), COLUMNS));
        final int outputRows = Math.max(1, Math.ceilDiv(outputs.size(), COLUMNS));
        this.gridRows = Math.max(inputRows, outputRows);
        this.byproductRows = Math.ceilDiv(byproducts.size(), COLUMNS);
        this.panelWidth = (PADDING * 2)
            + (COLUMNS * SLOT_SIZE)
            + ARROW_SPACING
            + LIGHT_ARROW_WIDTH
            + ARROW_SPACING
            + (COLUMNS * SLOT_SIZE);
        this.panelHeight = PADDING
            + TITLE_HEIGHT
            + 4
            + (gridRows * CELL_HEIGHT)
            + (byproductRows > 0 ? 8 + LABEL_HEIGHT + (byproductRows * CELL_HEIGHT) : 0)
            + PADDING;
    }

    @Override
    protected void init() {
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        updateCycle();
        renderPanel(graphics);
        graphics.drawCenteredString(this.font, this.title, panelX + (panelWidth / 2), panelY + PADDING, TITLE_COLOR);
        final int contentTop = panelY + PADDING + TITLE_HEIGHT + 4;
        final int inputX = panelX + PADDING;
        final int outputX = inputX + (COLUMNS * SLOT_SIZE) + ARROW_SPACING + LIGHT_ARROW_WIDTH + ARROW_SPACING;
        renderInputs(graphics, inputX, contentTop, mouseX, mouseY);
        renderArrow(graphics, inputX + (COLUMNS * SLOT_SIZE) + ARROW_SPACING, contentTop);
        renderOutputs(graphics, outputX, contentTop, mouseX, mouseY);
        renderByproducts(graphics, inputX, contentTop + (gridRows * CELL_HEIGHT) + 8, mouseX, mouseY);
    }

    private void updateCycle() {
        final long now = System.currentTimeMillis();
        if (cycleStart == 0) {
            cycleStart = now;
            return;
        }
        if (now - cycleStart >= CYCLE_MS) {
            cycle++;
            cycleStart = now;
        }
    }

    private void renderPanel(final GuiGraphics graphics) {
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, BORDER_DARK);
        graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + panelHeight - 1, PANEL_COLOR);
        graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + 2, BORDER_LIGHT);
    }

    private void renderInputs(final GuiGraphics graphics, final int x, final int y, final int mouseX,
                              final int mouseY) {
        for (int i = 0; i < ingredients.size(); ++i) {
            final Ingredient ingredient = ingredients.get(i);
            final List<ResourceKey> alternatives = ingredient.inputs();
            final ResourceKey resource = alternatives.get(Math.floorMod(cycle, alternatives.size()));
            renderCell(
                graphics,
                x + ((i % COLUMNS) * SLOT_SIZE),
                y + ((i / COLUMNS) * CELL_HEIGHT),
                resource,
                ingredient.amount(),
                mouseX,
                mouseY
            );
        }
    }

    private void renderOutputs(final GuiGraphics graphics, final int x, final int y, final int mouseX,
                               final int mouseY) {
        for (int i = 0; i < outputs.size(); ++i) {
            final ResourceAmount output = outputs.get(i);
            renderCell(
                graphics,
                x + ((i % COLUMNS) * SLOT_SIZE),
                y + ((i / COLUMNS) * CELL_HEIGHT),
                output.resource(),
                output.amount(),
                mouseX,
                mouseY
            );
        }
    }

    private void renderByproducts(final GuiGraphics graphics, final int x, final int y, final int mouseX,
                                  final int mouseY) {
        if (byproductRows == 0) {
            return;
        }
        graphics.fill(x, y - 4, x + (COLUMNS * SLOT_SIZE) - 2, y - 3, SEPARATOR_COLOR);
        graphics.drawString(this.font, BYPRODUCTS_LABEL, x, y, LABEL_COLOR, false);
        final int slotsY = y + LABEL_HEIGHT + 1;
        for (int i = 0; i < byproducts.size(); ++i) {
            final ResourceAmount byproduct = byproducts.get(i);
            renderCell(
                graphics,
                x + ((i % COLUMNS) * SLOT_SIZE),
                slotsY + ((i / COLUMNS) * CELL_HEIGHT),
                byproduct.resource(),
                byproduct.amount(),
                mouseX,
                mouseY
            );
        }
    }

    private void renderArrow(final GuiGraphics graphics, final int x, final int contentTop) {
        graphics.blitSprite(
            LIGHT_ARROW,
            x,
            contentTop + ((gridRows * CELL_HEIGHT) / 2) - (LIGHT_ARROW_HEIGHT / 2) - 2,
            LIGHT_ARROW_WIDTH,
            LIGHT_ARROW_HEIGHT
        );
    }

    private void renderCell(final GuiGraphics graphics,
                            final int x,
                            final int y,
                            final ResourceKey resource,
                            final long amount,
                            final int mouseX,
                            final int mouseY) {
        graphics.blitSprite(SLOT, x, y, SLOT_SIZE, SLOT_SIZE);
        final ResourceRendering rendering = RefinedStorageClientApi.INSTANCE.getResourceRendering(
            resource.getClass()
        );
        rendering.render(resource, graphics, x + 1, y + 1);
        graphics.drawCenteredString(
            this.font,
            rendering.formatAmount(amount, true),
            x + (SLOT_SIZE / 2),
            y + SLOT_SIZE + 1,
            AMOUNT_COLOR
        );
        if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
            setTooltipForNextRenderPass(rendering.getTooltip(resource)
                .stream()
                .map(Component::getVisualOrderText)
                .toList());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
