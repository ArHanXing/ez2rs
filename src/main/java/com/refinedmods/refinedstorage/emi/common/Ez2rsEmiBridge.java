package com.refinedmods.refinedstorage.emi.common;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridContainerMenu;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;

/** Client EMI entrypoint uses the exact official handler, including its fluid/category conversion. */
public final class Ez2rsEmiBridge {
    private Ez2rsEmiBridge() { }
    public static EmiRecipeHandler<PatternGridContainerMenu> patternGridHandler() {
        return new PatternGridEmiRecipeHandler();
    }
}
