# Animation And Effect Boundary

## Runtime Inventory

| Operation | Animation owner | Current animation choices | Effect choices | Lifecycle owner |
| --- | --- | --- | --- | --- |
| Summon | arrival sequence / mount cinematic / vehicle pending summon | none, standard, ground emerge | none, ender, magic circle, velocity burst, custom circle | summon and transfer services |
| Rescue | rescue placement and mount cinematic | none, standard, ground emerge | none, ender, magic circle, velocity burst, custom circle | rescue and mount cinematic services |
| Storage | storage pending transition | none, standard, ground sink (living entities) | none, ender, magic circle, custom circle | storage service |
| Switch | mount switch cinematic | standard | destination summon effect currently follows summon/rescue purpose | mount switch transaction |

## Resolution

Animation and effects resolve independently:

```text
per-entity animation override -> entity preset animation -> STANDARD
per-entity effect override    -> entity preset effect    -> MAGIC_CIRCLE
```

The selected animation controls temporary movement, visibility, and presentation delay. The selected effect only sends particles, geometry, overlay, texture, and audio presentation. Both tracks may run at the same time.

## Compatibility

- Player NBT now writes `animationStyles` and `effectStyles` separately.
- Old combined player values are migrated in memory before the next save.
- Pack preset format 4 writes `*Animation` and `*Effect` properties separately.
- Pack preset format 3 remains readable. `ground_emerge` and `ground_sink` migrate to animation values plus the previous magic-circle effect.
- Legacy effect enum constants remain decode-only so old data is not rejected. New UI and new files never emit them.

## Safety Rules

- Animation completion cannot directly commit persistent lifecycle state.
- Effect completion cannot delay, remove, mount, store, or restore an entity.
- Switch animation configuration must not weaken the existing contact and controller handoff checks.
- External integration providers remain authoritative for their own entity presentation and lifecycle.

## Acceptance

- [ ] Ground emerge and magic circle play together for summon.
- [ ] Ground emerge and velocity burst play together for rescue.
- [ ] Ground sink and custom circle play together for storage.
- [ ] No-effect preserves the selected animation.
- [ ] No-transition preserves the selected visual effect and lifecycle result.
- [ ] Existing format 3 presets and old player NBT retain equivalent presentation after one save/reload.
- [ ] Mount switch remains continuous and appears under the separate Switch animation row.
