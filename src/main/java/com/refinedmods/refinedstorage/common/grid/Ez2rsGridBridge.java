package com.refinedmods.refinedstorage.common.grid;

import net.minecraft.server.level.ServerPlayer;
import java.lang.reflect.Field;

/**
 * Package access for the native pending-delta type; reset ONLY at a network transition.
 * A typed same-package mixin requires an unsafe config prefix covering normal native classes.
 * Cache the one external field instead; its name belongs to RS2 and is not Minecraft-mapped.
 */
public final class Ez2rsGridBridge {
    private static final Field PENDING_UPDATES = pendingUpdatesField();
    private Ez2rsGridBridge() { }

    private static Field pendingUpdatesField() {
        try {
            final Field field = AbstractGridContainerMenu.class.getDeclaredField("pendingUpdates");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void resetPendingUpdates(final AbstractGridContainerMenu menu, final ServerPlayer player) {
        try {
            PENDING_UPDATES.set(menu, new PendingGridUpdates(player));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot reset native grid pending updates", e);
        }
    }
}
