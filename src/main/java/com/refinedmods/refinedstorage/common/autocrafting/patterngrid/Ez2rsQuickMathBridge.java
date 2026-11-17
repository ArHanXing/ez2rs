package com.refinedmods.refinedstorage.common.autocrafting.patterngrid;

import ar.hxi.ez2rs.terminal.QuickMath;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.refinedmods.refinedstorage.common.support.containermenu.ResourceSlot;

import net.minecraft.resources.ResourceLocation;

/**
 * Typed access to the package-private pattern-grid menu API.
 *
 * <p>{@code PatternGridContainerMenu.getPatternType()} is package-private in RS2 2.0.9, so the
 * quick-math feature cannot read it from {@code ar.hxi.ez2rs}. Same-package bridges are how the rest
 * of this mod reaches such members (see {@code Ez2rsGridBridge}, {@code Ez2rsEmiBridge}); no
 * reflection and no widened mixin package.</p>
 *
 * <p><b>Client/server asymmetry, and why it is safe.</b>
 * {@link #isProcessing} is a <b>client-only</b> query: it reads the synchronized
 * {@code ClientProperty}, and the screen uses it purely to decide whether to show the buttons.
 * Server-side the same property is a {@code ServerProperty} whose {@code getValue()} throws
 * {@code UnsupportedOperationException} by design, so {@link #applyFactor} must not call it.
 * It does not need to: inside a pattern grid the only slots that carry a modifiable amount are the
 * processing matrix slots ({@code ResourceSlotType.FILTER_WITH_AMOUNT}), while crafting,
 * stonecutter and smithing inputs live as plain one-count {@code ItemStack}s in containers and are
 * hardcoded to {@code Ingredient(1, ...)} by {@code PatternResolver}. Scaling "every slot with an
 * amount" is therefore processing-only by construction, no pattern-type check required — and the
 * server cannot be tricked into scaling something that has no amount.</p>
 *
 * <p>Amount scaling deliberately goes through the public {@link ResourceSlot#changeAmount(long)}
 * path, i.e. exactly the same call the native "set amount" screen uses: the container clamps to the
 * resource's processing-pattern limit and fires its listener, which marks the block entity dirty or
 * the wireless draft dirty, and the regular {@code broadcastChanges} sync then reaches the client.
 * No direct container writes, no client-side prediction.</p>
 *
 * <p>Write authorization is not decided here: wireless menus get to veto through
 * {@link Ez2rsNativePatternGridMenu#ez2rs$acceptsQuickMath()}, so the offline / closed policy
 * stays in the mod's own menu class rather than being duplicated in a bridge.</p>
 */
public final class Ez2rsQuickMathBridge {
    private Ez2rsQuickMathBridge() {
    }

    /** Client-only: the screen uses this to decide whether the buttons are visible. */
    public static boolean isProcessing(final PatternGridContainerMenu menu) {
        return menu.getPatternType() == PatternType.PROCESSING;
    }

    /**
     * Scales every filled processing input/output by {@code factor}. Server-side entry point.
     *
     * @return true when at least one amount actually changed
     */
    public static boolean applyFactor(final PatternGridContainerMenu menu, final int factor) {
        if (!QuickMath.isKnownFactor(factor)) {
            return false;
        }
        if (menu instanceof Ez2rsNativePatternGridMenu nativeMenu && !nativeMenu.ez2rs$acceptsQuickMath()) {
            return false;
        }
        // ProcessingMatrixInputResourceContainer.setSilently() resets the slot's allowed-alternatives
        // set, so a plain changeAmount() would silently drop the tags the player configured.
        // Snapshot them first and put them back afterwards.
        final Map<Integer, Set<ResourceLocation>> allowedAlternatives = snapshotAllowedAlternatives(menu);
        boolean changed = false;
        for (final ResourceSlot slot : menu.getResourceSlots()) {
            // Only FILTER_WITH_AMOUNT slots carry a real amount; in the pattern grid those are
            // exactly the processing matrix slots.
            if (!slot.canModifyAmount() || slot.isEmpty()) {
                continue;
            }
            final long current = slot.getAmount();
            final long scaled = QuickMath.scale(current, factor);
            if (scaled != current) {
                slot.changeAmount(scaled);
                changed = true;
            }
        }
        if (changed) {
            restoreAllowedAlternatives(menu, allowedAlternatives);
        }
        return changed;
    }

    private static Map<Integer, Set<ResourceLocation>> snapshotAllowedAlternatives(
        final PatternGridContainerMenu menu
    ) {
        final Map<Integer, Set<ResourceLocation>> snapshot = new HashMap<>();
        for (final ResourceSlot slot : menu.getResourceSlots()) {
            if (!(slot instanceof ProcessingMatrixResourceSlot processingSlot) || !processingSlot.isInput()) {
                continue;
            }
            final Set<ResourceLocation> ids = menu.getAllowedAlternatives(slot.getContainerSlot());
            if (!ids.isEmpty()) {
                snapshot.put(slot.getContainerSlot(), Set.copyOf(ids));
            }
        }
        return snapshot;
    }

    private static void restoreAllowedAlternatives(final PatternGridContainerMenu menu,
                                                   final Map<Integer, Set<ResourceLocation>> snapshot) {
        for (final Map.Entry<Integer, Set<ResourceLocation>> entry : snapshot.entrySet()) {
            if (menu.getAllowedAlternatives(entry.getKey()).equals(entry.getValue())) {
                continue;
            }
            menu.handleAllowedAlternativesUpdate(entry.getKey(), entry.getValue());
        }
    }
}
