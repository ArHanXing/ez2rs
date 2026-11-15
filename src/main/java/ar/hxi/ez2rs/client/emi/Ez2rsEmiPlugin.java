package ar.hxi.ez2rs.client.emi;

import ar.hxi.ez2rs.terminal.Ez2rsMenus;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridContainerMenu;
import com.refinedmods.refinedstorage.emi.common.Ez2rsEmiBridge;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import net.minecraft.world.inventory.MenuType;

/** Register the official native handler for our native-menu subtype. Loaded through the EMI entrypoint only. */
@EmiEntrypoint
public final class Ez2rsEmiPlugin implements EmiPlugin {
    @Override
    public void register(final EmiRegistry registry) {
        // The menu type produces a PatternGridContainerMenu subclass; isolate the invariant cast here.
        @SuppressWarnings("unchecked")
        final MenuType<PatternGridContainerMenu> type =
            (MenuType<PatternGridContainerMenu>) (MenuType<?>) Ez2rsMenus.wirelessPatternGrid();
        registry.addRecipeHandler(type, Ez2rsEmiBridge.patternGridHandler());
    }
}
