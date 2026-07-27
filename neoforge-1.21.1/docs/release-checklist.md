# FindMe 1.3 Release Checklist

Use this checklist before cutting the NeoForge 1.3 release jar. The goal is not feature coverage; it is proving that official creatures and vehicles cannot be silently lost or duplicated.

## Data Safety

- Bind a vanilla mount, store it, restart the game, and summon it again.
- Bind a modded large flying creature, store it, restart the game, and summon it again.
- Bind a vehicle, store it, restart the game, and summon it again.
- Spam summon while a deploy animation is still running.
- Spam recall/store while a storage animation is still running.
- Switch mount A to mount B repeatedly while moving.
- Switch mount to vehicle, then vehicle to mount.
- Try release/unbind while the target is deploying, storing, arriving, or locked.
- Delete a death record and confirm a backup exists first.
- Restore a backup with dry-run, then confirm restore, then run `/findme doctor verbose`.

## Lifecycle Interruptions

- Start a storage animation and close the game before it finishes.
- Start a summon animation and close the game before it finishes.
- Use a skill performer, then log out before it finishes.
- Trigger emergency rescue, then die before it finishes.
- Ride a mount through a dimension change.
- Store a deployed companion during travel/logout/death.
- Break a small house while residents are assigned to it.

## Recovery Commands

- Run `/findme doctor` on a clean player and confirm it reports OK.
- Run `/findme doctor <player> verbose` as an operator.
- Run `/findme recovery remove-temporary <player> dry-run`, then `confirm`.
- Run `/findme recovery clear-lock <player> <uuid> dry-run`, then `confirm` on a known stale lock only.
- Run `/findme recovery recover-stored <player> <uuid> dry-run`.
- Test `recover-stored ... to-storage confirm` only when no loaded official entity exists.
- Test `recover-stored ... to-world confirm` only when no loaded official entity exists.
- Test `drop-stale-stored <player> <uuid> dry-run`, then `confirm` only on a deliberate stored+loaded conflict.

## Regression Matrix

- Singleplayer integrated server.
- Dedicated NeoForge server.
- Two players operating companions at the same time.
- Vanilla animals.
- Large modded land mount.
- Large modded flying mount.
- Shoulder companion if available.
- Sable/Aeronautics vehicle if installed.
- [x] Old 1.2 save upgraded directly to 1.3, then reopened without duplicate migration or authoritative-data changes.

## Release Gate

- `compileJava` passes.
- `build` passes.
- Safety audit searches have only documented hits.
- No official entity is removed before a valid snapshot is written.
- All destructive recovery commands create a backup first.
- Doctor does not auto-fix data.
- Temporary skill performers do not enter storage, teams, death records, or backups as official companions.
