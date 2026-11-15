package ar.hxi.ez2rs.mixin;

import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.root.RootStorage;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.WirelessPatternGridState;
import com.refinedmods.refinedstorage.common.support.network.ResourceSorters;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Redirect the three native comparator sites; keep all native transfer loops. */
@Mixin(PatternGridBlockEntity.class)
public abstract class PatternGridSortingMixin {
    // Name-only external selectors intentionally contain no named Minecraft descriptors.
    // Loom remaps the handler's actual Inventory descriptor normally.
    @Redirect(method = {"transferCraftingRecipe", "transferProcessingRecipe", "transferSmithingTableRecipe"},
        at = @At(value = "INVOKE", target =
            "Lcom/refinedmods/refinedstorage/common/support/network/ResourceSorters;create"),
        require = 3, allow = 3, remap = false)
    private <T> Comparator<T> ez2rs$wirelessSorting(final RootStorage storage, final Set<Pattern> patterns,
                                                  final Inventory inventory,
                                                  final Function<T, ResourceKey> extractor) {
        if ((Object) this instanceof WirelessPatternGridState state) {
            final WirelessPatternGridState.SortingContext context = state.sortingContext();
            return ResourceSorters.create(context.storage(), context.patterns(), inventory, extractor);
        }
        return ResourceSorters.create(storage, patterns, inventory, extractor);
    }
}
