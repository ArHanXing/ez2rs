# Refined Fluid Substitution — Fabric build crashes on every production launch

**Mod version:** `refinedfluidsubstitution-fabric-1.21.1-2.0.0`
**Environment:** Minecraft 1.21.1, Fabric Loader 0.17.2, Fabric API 0.116.17+1.21.1,
Refined Storage 2.0.9 (Fabric), Cable Tiers 0.6.14 (Fabric)

## Symptom

The Fabric build cannot start at all — client and dedicated server both die during
entrypoint invocation:

```
java.lang.RuntimeException: Could not execute entrypoint stage 'main' due to errors,
  provided by 'refinedstorage' at 'com.refinedmods.refinedstorage.fabric.ModInitializerImpl'!

Caused by: java.lang.RuntimeException: Mixin transformation of
  com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternOutputContainer failed

Caused by: org.spongepowered.asm.mixin.throwables.MixinApplyError:
  Mixin [refinedfluidsubstitution.mixins.json:PatternOutputContainerMixin ...] FAILED during APPLY

Caused by: ... InvalidInjectionException: Critical injection failure: @Inject annotation on
  acceptMappedFluidSubstitutionPattern could not find any targets matching 'canPlaceItem'
  in com/refinedmods/refinedstorage/common/autocrafting/patterngrid/PatternOutputContainer.
  No refMap loaded.
```

## Root cause

`PatternOutputContainer` has no method of its own called `canPlaceItem` — it **overrides
`Container#canPlaceItem`**, so in the remapped (intermediary) jar the method is named
`method_5437`:

```
$ javap -p PatternOutputContainer.class        # from refinedstorage-fabric-2.0.9.jar
class ...PatternOutputContainer extends ...FilteredContainer {
  public int method_5444();
  public boolean method_5437(int, net.minecraft.class_1799);
}
```

`PatternOutputContainerMixin` declares `remap = false` and the mixin config has no `refmap`
key, so Mixin looks for a method literally named `canPlaceItem` and finds nothing.

Development runs use named mappings, which is why this never shows up in `runClient` /
`runServer` — it only breaks in the production jar.

The generated refmap was empty for exactly this reason: with `remap = false` the annotation
processor skips the member.

## Fix

Two lines:

1. `PatternOutputContainerMixin` — let this one injection be remapped:

```diff
-    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
+    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true, remap = true)
```

2. `refinedfluidsubstitution.mixins.json` — reference the generated refmap:

```diff
   "minVersion": "0.8",
+  "refmap": "refinedfluidsubstitution-fabric-refmap.json",
```

Only this member needs remapping: every other injection/shadow targets a Refined Storage
member (`createPattern`, `isValidPattern`, `patternChanged`, `doWork`, `getPatternContainer`,
`mainNetworkNode`, `patterns`), and those keep their names in the remapped jar, so
`remap = false` stays correct for them.

The refmap now resolves:

```json
"canPlaceItem": "...PatternOutputContainer;method_5437(ILnet/minecraft/class_1799;)Z"
```

## Verification

Same production server, same mod set, only the RFS jar differs:

| Jar | Result |
|---|---|
| unfixed | `Could not execute entrypoint stage 'main' ... No refMap loaded` → server exits |
| fixed | `Done (1.123s)! For help, type "help"` — no mixin warnings |

## Notes

- **NeoForge is unaffected**: 1.20.2+ NeoForge runs on official Mojang names at runtime, so
  `canPlaceItem` is not remapped there. The `refmap` key in the shared `common` config is
  harmless on NeoForge (Mixin logs that the refmap is missing and falls back to the literal
  name, which is correct there). If you prefer a warning-free NeoForge log, move the mixin
  config into each platform module — the same way Refined Storage itself does it
  (`refinedstorage-fabric/src/main/resources/refinedstorage.mixins.json` and the neoforge
  twin), each with its own refmap name.
- **Unrelated observation while testing**: `AutocrafterBlockEntityMixin` sets
  `targets = "com.ultramega.cabletiers.common.autocrafting.autocrafter.TieredAutocrafterBlockEntity"`.
  Because `targets` replaces the declared `value`, the mixin only ever applies to Cable Tiers'
  tiered autocrafter. On a plain Refined Storage install (no Cable Tiers) the substitution
  refresh never runs, so the pattern is accepted and stored but never applied. Adding a second
  mixin without `targets` (targeting `AutocrafterBlockEntity` directly, all three members exist
  there) would make it work without Cable Tiers.
