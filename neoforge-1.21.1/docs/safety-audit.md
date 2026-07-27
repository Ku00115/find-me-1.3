# FindMe 1.3 Safety Audit

This file records the current stability gates for the NeoForge 1.3 line. Keep it updated when adding a new lifecycle path.

## Dangerous Low-Level Calls

Allowed direct `CompanionManager.storeAndDiscard` call:

- `CompanionLifecycleFacade.storeLiving`

Allowed direct `CompanionEntityTransferService.restoreStoredEntity*` calls:

- `CompanionLifecycleFacade.restoreStored*`
- `CompanionStorageService` home-return transfer internals

Allowed direct `data.remove(...)` release paths:

- `CompanionListService.releaseAndRemove`, after `before_release_remove` backup succeeds, transient state is cancelled, and release lifecycle logging is written
- `VehicleManager.removeUuid`, after `VehicleManager.releaseVehicle` or `removeAt` creates `before_vehicle_release` backup, transient state is cancelled, and release lifecycle logging is written
- `PlayerCompanionData.remove`, as the core data method used by those controlled paths

Allowed direct `removeDeadAt(...)` paths:

- `CompanionCommandHandler.removeDead`, after `before_delete_dead_record` backup succeeds
- `WarehouseEntityService.deleteDead`, after `before_delete_dead_record` backup succeeds

Sleep revive is also guarded by a `before_sleep_revive` backup before it moves a dead record back into live storage.

Allowed `discard()` paths:

- `CompanionStorageService`, only after a valid stored snapshot exists
- `CompanionRetreatService`, only after a `STORE` lock is acquired and `storeEntity` succeeds
- `VehicleManager.storeVehicle`, only after a validated vehicle snapshot is saved
- addon temporary-action controllers and `CompanionSafetyService`, only for entities recognized by `FindMeApi.isTemporaryActionPerformer`
- `VehicleSeatService`, only for temporary seat anchors
- `CompanionEvents`, only for temporary addon-action performers

## Required Search Checks

Before release, run these searches and review every hit:

```text
rg "CompanionManager\.storeAndDiscard\(" src/main/java/com/kuzhi/findme/server
rg "CompanionEntityTransferService\.restoreStoredEntity" src/main/java/com/kuzhi/findme/server
rg "\.discard\(" src/main/java/com/kuzhi/findme/server
rg "data\.remove\(|removeDeadAt\(|storedEntities\.remove\(" src/main/java/com/kuzhi/findme/server
```

Any new hit must either route through `CompanionLifecycleFacade`, create and validate a safety snapshot first, or be documented here as a temporary-entity-only exception.

Full-player backup states include vault entries but intentionally do not include the backup list itself, avoiding recursive backup growth while still making `/findme vault clear` recoverable.

Old player data that lacks the 1.3 safety schema marker creates one login-time `before_1_3_upgrade_login` backup before the marker is saved. Empty data is only marked current and does not create a meaningless backup.

## Lifecycle Busy Gate

UI, packet, and skill actions that reorganize or reuse a companion should call `CompanionLifecycleFacade.busyReason` or `isBusy` before mutating state. The busy gate currently covers:

- active operation locks
- pending storage effects
- pending arrival sequences
- pending vehicle summon effects

This prevents team, warehouse, home, companion/vehicle wheel selection or edit, direct vehicle summon/collection, vehicle release, and skill actions from changing the same record while deploy/store animation state is still settling.

Facade deploy/store/restore entry points must check the busy gate before cancelling transient state. A locked or pending record should be rejected first, not partially cleaned and then rejected by a deeper service.

The only current exception is `FAILURE_RECOVERY` storage after a restored entity fails placement while still marked `ARRIVAL_PENDING`; that path may override arrival-pending state so the failed restored entity can be safely snapshotted and removed instead of being stranded in the world.

Vehicle storage and restore paths use the same operation lock service:

- `VehicleManager.storeVehicle` uses `STORE`
- `VehicleManager.restoreVehicle` uses `DEPLOY`
- `SableVehicleCompatibility.store` uses `STORE`
- `SableVehicleCompatibility.restore` uses `DEPLOY`
- `CompanionRetreatService` uses `STORE` before removing a retreating official entity

Recovery lock coverage:

- `recover-stored ... to-storage confirm` uses `RECOVER`
- `recover-stored ... to-world confirm` restores through `CompanionLifecycleFacade.restoreStored`, which uses the normal `DEPLOY` lock
- `drop-stale-stored ... confirm` refuses missing live entities, active locks, pending storage, dead records, and unknown records; it backs up first, then removes only the stale stored snapshot
- `clear-lock` refuses locks owned by a different player, so the safety backup and the mutation target stay aligned
- Doctor ignores foreign operation locks in normal output and warns if a known record is locked by a different player

Stored snapshot identity checks:

- Doctor reports a `CRITICAL` issue when a stored snapshot or vault snapshot has a `UUID` that differs from the owning FindMe record.
- `recover-stored` refuses UUID-mismatched snapshots instead of rewriting them during administrator recovery.
- Vault restore preview and confirm refuse empty, missing-id, UUID-mismatched, locked, or already-loaded vault snapshots before creating a safety backup or mutating data.
- Backup restore confirm refuses to run while the player has active operation locks and cancels transient player state before applying the backup.
- Vault, backup, and stored recovery mutations resync companion, vehicle, death, and team client state after saving.

Backup integrity checks:

- New full-player backup entries include a format version and SHA-256 checksum of the backed-up state, computed from a deterministic canonical NBT representation.
- Doctor reports a `CRITICAL` issue when a checksummed backup no longer matches its state.
- Backup restore preview and confirm refuse checksum-mismatched backups before making any restore mutation.

## Final Manual Gate

Run `docs/release-checklist.md` before publishing the final 1.3 jar.
