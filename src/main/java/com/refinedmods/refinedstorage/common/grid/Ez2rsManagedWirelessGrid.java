package com.refinedmods.refinedstorage.common.grid;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.impl.node.grid.GridWatcherManagerImpl;
import com.refinedmods.refinedstorage.api.network.node.grid.*;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.*;
import com.refinedmods.refinedstorage.common.Platform;
import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemContext;
import com.refinedmods.refinedstorage.common.api.support.resource.PlatformResourceKey;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceType;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;

/** Native wireless operations with explicitly owned subscriptions (never a network node). */
public final class Ez2rsManagedWirelessGrid extends WirelessGrid {
    private final NetworkItemContext context;
    private final BooleanSupplier identityValid;
    private final GridWatcherManagerImpl watchers = new GridWatcherManagerImpl();
    private GridWatcher watcher;
    private Class<? extends Actor> actorType;
    private Network network;
    private StorageNetworkComponent watchedStorage;
    private Set<PlatformResourceKey> craftables = Set.of();
    private boolean ready;
    private boolean charged;
    private boolean initialSnapshot;
    private Runnable snapshot = () -> { };

    public Ez2rsManagedWirelessGrid(final NetworkItemContext context, final BooleanSupplier identityValid) {
        super(context);
        this.context = context;
        this.identityValid = identityValid;
    }

    @Override
    public boolean isGridActive() {
        return identityValid.getAsBoolean() && super.isGridActive();
    }

    public Optional<Network> authorizedNetwork() {
        return isGridActive() ? context.resolveNetwork() : Optional.empty();
    }

    @Override
    public Storage getItemStorage() { return isGridActive() ? super.getItemStorage() : new NoopStorage(); }
    @Override
    public List<TrackedResourceAmount> getResources(final Class<? extends Actor> type) {
        return isGridActive() ? super.getResources(type) : List.of();
    }
    @Override
    public Set<PlatformResourceKey> getAutocraftableResources() {
        return isGridActive() ? super.getAutocraftableResources() : Set.of();
    }

    @Override
    public void addWatcher(final GridWatcher newWatcher, final Class<? extends Actor> type) {
        if (watcher != null) throw new IllegalStateException("Wireless menu already subscribed");
        if (!charged) {
            context.drainEnergy(Platform.INSTANCE.getConfig().getWirelessGrid().getOpenEnergyUsage());
            charged = true;
        }
        watcher = newWatcher;
        actorType = type;
        network = authorizedNetwork().orElse(null);
        watchedStorage = storage(network);
        craftables = Set.copyOf(getAutocraftableResources());
        // addWatcher does not replay or invalidate: the native constructor isn't finished yet.
        watchers.addWatcher(newWatcher, type, watchedStorage);
    }

    public void menuReady(final Runnable sendSnapshot) {
        snapshot = sendSnapshot;
        ready = true;
        initialSnapshot = true;
    }

    private static StorageNetworkComponent storage(final Network network) {
        return network == null ? null : network.getComponent(StorageNetworkComponent.class);
    }

    public void refresh() {
        if (!ready || watcher == null) return;
        final Network next = authorizedNetwork().orElse(null);
        final StorageNetworkComponent nextStorage = storage(next);
        final Set<PlatformResourceKey> nextCraftables = Set.copyOf(getAutocraftableResources());
        if (network == next && watchedStorage == nextStorage && craftables.equals(nextCraftables)) {
            if (initialSnapshot) {
                initialSnapshot = false;
                watcher.invalidate(); // discard any delta queued between subscription and the opening snapshot
                watcher.onActiveChanged(next != null);
                snapshot.run();
            }
            return;
        }
        initialSnapshot = false;
        // Detach using the ORIGINAL storage, even when the context no longer resolves it.
        watchers.removeWatcher(watcher, watchedStorage);
        network = next;
        watchedStorage = nextStorage;
        craftables = nextCraftables;
        // Native invalidate reinitializes strategies and sends GridClear; our menu resets deltas first.
        watcher.invalidate();
        watchers.addWatcher(watcher, actorType, watchedStorage);
        watcher.onActiveChanged(next != null);
        // Full snapshot is the replay (including sticky craftables); do not also queue resource deltas.
        snapshot.run();
    }

    @Override
    public void removeWatcher(final GridWatcher oldWatcher) {
        if (watcher != oldWatcher) return; // idempotent close; no forced resolution
        watchers.removeWatcher(oldWatcher, watchedStorage);
        watcher = null;
        watchedStorage = null;
        network = null;
        ready = false;
        snapshot = () -> { };
    }

    @Override
    public GridOperations createOperations(final ResourceType type, final ServerPlayer player) {
        // Strategies retain this wrapper, NOT an old native operation bound to an old root storage.
        return new GridOperations() {
            @Override
            public boolean extract(final ResourceKey resource, final GridExtractMode mode,
                                   final InsertableStorage destination) {
                refresh();
                return isGridActive() && nativeOperations(type, player).extract(resource, mode, destination);
            }
            @Override
            public boolean insert(final ResourceKey resource, final GridInsertMode mode,
                                  final ExtractableStorage source) {
                refresh();
                return isGridActive() && nativeOperations(type, player).insert(resource, mode, source);
            }
        };
    }

    private GridOperations nativeOperations(final ResourceType type, final ServerPlayer player) {
        return super.createOperations(type, player);
    }
}
