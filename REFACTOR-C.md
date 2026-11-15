# Stage C implementation handoff

## Result

Implemented the native-menu refactor against inspected RS2 2.0.9 source. The wireless menu is now 212 lines of native construction, lifecycle, persistence hooks and server guards, replacing the 774-line copied recipe/encoding/property implementation. Native encoding, recipe resolution, all four transfers, pattern availability/consumption, output replacement and one-shot Slot.set copying remain upstream behavior. No commits, builds, tests, runtime launches, world control or screenshots were performed by C. D owns wrapper builds through mc_gradle under the stage-role instruction.

## Changed implementation files

- `src/main/java/com/refinedmods/refinedstorage/common/autocrafting/patterngrid/WirelessPatternGridState.java`: concrete unattached adapter, real server Level recipe context, inert directional initialization, local dirty tracking only, identity-safe draft persistence, native container restoration before opening, selected stone recipe restored last, native type/fuzzy/selection/copy guards, managed Grid delegation and authorized comparator context. Its own mainNetworkNode remains unattached.
- `src/main/java/com/refinedmods/refinedstorage/common/autocrafting/patterngrid/Ez2rsNativePatternGridMenu.java`: retain typed native constructors and direct server adapter getters; remove obsolete handoff comments.
- `src/main/java/ar/hxi/ez2rs/terminal/patterngrid/WirelessPatternGridContainerMenu.java`: native server constructor, protected terminal slot rebuilt after assignment, no extra DataSlots, close/broadcast draft flush, native invalidation with old-delta reset, offline edit guards and pattern removal recovery. Refresh before network strategy entry also fixes native fluid strategy's retained bucket itemStorage reference. Slot policy uses container identity rather than indices.
- `src/main/java/ar/hxi/ez2rs/terminal/PatternGridOpener.java`: flush previous pattern menu BEFORE constructing context/state; restore one adapter, share it between server construction and opening data derived from getMenuData.
- `src/main/java/ar/hxi/ez2rs/net/CycleUniversalTerminalPacket.java`: flush old pattern menu before changing page/opening its next prepared state.
- `src/main/java/ar/hxi/ez2rs/terminal/patterngrid/PatternGridDraft.java`: safe missing/unknown pattern type fallback and optional backward-compatible stonecutter_selected_recipe helpers, existing keys and native resource serialization retained.
- `src/main/java/com/refinedmods/refinedstorage/common/grid/Ez2rsManagedWirelessGrid.java`: subclass native WirelessGrid, retain original watched storage/actor identity, one opening energy charge, defer transitions until ready, exactly-once detach/rebind/close, empty inactive resources/craftables/storage, immediate guarded native operations, no node registration.
- `src/main/java/com/refinedmods/refinedstorage/common/grid/Ez2rsGridBridge.java`: cached single native pendingUpdates field reset only at transitions, constructed with the actual package-private PendingGridUpdates type. This deliberately replaces the typed same-package accessor: a mixin config prefix covering native ordinary classes provably triggers IllegalClassLoadError (parent verified MixinProcessor). No repair polling remains.
- `src/main/java/ar/hxi/ez2rs/net/PatternGridSnapshotPacket.java`: targeted container-ID-scoped complete native GridData replay; clear before applying verified DELTA updates, replace sticky craftables through narrow accessor, sort existing repository to retain listener/filter/sort settings. One initial post-open snapshot also establishes a clean pending-delta baseline.
- `src/main/java/ar/hxi/ez2rs/mixin/ResourceRepositoryAccessor.java`: exact native Set<ResourceKey> stickyResources accessor; native clear does NOT clear sticky resources and public API cannot modify them. This accessor allows complete offline/reconnect snapshots without replacing the repository.
- `src/main/java/ar/hxi/ez2rs/mixin/PatternGridSortingMixin.java`: three-site native comparator redirect, name-only external selectors avoid named Minecraft descriptors in annotation strings. Handler Inventory descriptor remaps normally; require/allow=3 retained. Ordinary block grids pass through unchanged.
- `src/main/java/ar/hxi/ez2rs/terminal/Ez2rsMenus.java`: common S2C payload type registration.
- `src/main/java/ar/hxi/ez2rs/client/Ez2rsClientScreens.java`: client-only snapshot receiver checks current player menu type/container ID and live screen before applying. Preview overlays can keep the same player menu; do not require the screen itself to be the native container screen.
- `src/main/java/com/refinedmods/refinedstorage/emi/common/Ez2rsEmiBridge.java`: constructs official native PatternGridEmiRecipeHandler directly.
- `src/main/java/ar/hxi/ez2rs/client/emi/Ez2rsEmiPlugin.java`: register the exact native handler for our menu subtype using one invariant cast; no conversion or recipe loops.
- `src/main/resources/ez2rs.mixins.json`: unregister old menu accessor/attach polling; register narrow sorter and sticky accessor; unrelated hooks retained.
- `src/main/resources/ez2rs.native.mixins.json`: reduced to an empty harmless narrow-prefix config pending deletion. All live hooks are now in ez2rs.mixins.json. Parent already agreed to remove native config registration; do NOT reactivate the broad native-package config.

## Retired source bodies / physical deletion needed by parent

The provided tool set exposes read/edit/write but no delete/exec filesystem operation. After reading each file and auditing callers with grep, C cleared these obsolete source bodies (empty .java files compile harmlessly); parent should physically delete them and the inactive empty native config:

- `src/main/java/ar/hxi/ez2rs/mixin/AbstractGridContainerMenuMixin.java`
- `src/main/java/ar/hxi/ez2rs/mixin/PatternGridContainerMenuAccessor.java`
- `src/main/java/ar/hxi/ez2rs/terminal/patterngrid/GridAttachable.java`
- `src/main/java/ar/hxi/ez2rs/terminal/patterngrid/WirelessPatternGrid.java`
- `src/main/java/ar/hxi/ez2rs/terminal/patterngrid/WirelessPatternGridMenuProvider.java`
- `src/main/java/ar/hxi/ez2rs/terminal/patterngrid/StonecutterDraftContainer.java`
- `src/main/java/ar/hxi/ez2rs/client/emi/WirelessPatternGridEmiHandler.java`
- `src/main/java/com/refinedmods/refinedstorage/common/autocrafting/patterngrid/Ez2rsStonecutterRecipeInput.java`
- `src/main/java/com/refinedmods/refinedstorage/common/autocrafting/patterngrid/Ez2rsPatternGridBridge.java`
- `src/main/java/com/refinedmods/refinedstorage/common/autocrafting/patterngrid/Ez2rsPatternGridSortingMixin.java`
- `src/main/java/com/refinedmods/refinedstorage/common/grid/Ez2rsPendingGridUpdatesAccessor.java`
- `src/test/java/com/refinedmods/refinedstorage/common/autocrafting/patterngrid/WirelessPatternGridStateContractTest.java` (compile-only stub, not a real test)
- `src/main/resources/ez2rs.native.mixins.json`

## Verification evidence and D follow-up

Read actual native PatternGridBlockEntity/Menu, inherited lifecycle, Grid/WirelessGrid/operations, context, watcher manager/registration/root listener, transfer manager, GridData/PendingGridUpdates, recipe containers, processing alternatives serialization, server properties, ResourceRepositoryImpl/Builder and official EMI handler. Native ResourceRepository.update takes a delta and rejects zero; clear clears backing/view lists but retains stickyResources; builder supplies a mutable HashSet; sort rebuilds zero-quantity sticky entries and notifies its existing listener. Native GridWatcherManager.addWatcher attaches without replay/invalidation, allowing safe native constructor ordering. Transition replay uses custom full GridData instead of adding native replay deltas a second time.

Caller grep found no remaining old helper/attach/ensurePendingUpdates references after retirement. Grep found no copied RecipeMatrix, draft snapshot polling, encoding fill helper, direct world registration/dirty/update calls or container lifecycle initialization in implementation. Current menu has no getProperty calls and adapter persistence reads inherited BE state. Client classes are referenced only from client screen registration / EMI entrypoint; common payload registration contains no Minecraft client imports. README/build.gradle/gradle.properties/fabric.mod.json were not edited by C.

D must run compileJava/build via mc_gradle, inspect production remapped sorter annotation and method signatures, and verify any compile-time API discrepancies. Name-only selector string syntax is the remaining mixin-specific uncertainty (MemberInfo accepts owner+name without descriptor in standard Mixin, but no runtime apply was run). Verify exactly three generic comparator sites. The cached reflection field is external RS2 pendingUpdates, so it is not subject to Minecraft name remapping. Native side effects, real terminal recovery and subscription identity were statically audited; this is not runtime evidence.

Runtime remains user-owned per PLAN: all four native EMI categories/fluid amounts/alternatives, native encoding/replacement rules, one-shot copy/type edits, selected stone recipe close/reopen, stale-preparation page switch, offline edits/recovery, energy exhaustion/network disappearance and reconnect, identity replacement and repeated close listener cleanup. No runtime success claim.
