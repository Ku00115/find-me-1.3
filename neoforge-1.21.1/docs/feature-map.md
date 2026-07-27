# FindMe Feature Map

This file is the maintenance map for FindMe. Before adding a feature, pick one owner area below and keep the new state and tick logic inside that area.

## Core Data

Owner: `PlayerCompanionData`, `PlayerCompanionDataCodec`, `PlayerCompanionDataMirror`

Stores player-owned creature state, wheel state, teams, homes, vehicles, dead records, visual styles, cooldowns, and safety backups.

Rule: do not add behavior here unless it is a small data invariant. Put behavior in a service and let this class expose only narrow data methods.

## Binding And Registration

Owner: `CompanionContractService`, `CompanionBindingService`, `CompanionRegistrationService`, `CompanionOwnershipGuardService`

Handles Name Paper binding, manual bind checks, registration type selection, and ownership rules.

Rule: binding decides whether an entity can enter FindMe. It should not control summon, storage, escort, or skill behavior.

## Summon And Storage

Owner: `CompanionSummonService`, `CompanionCollectionService`, `CompanionDeploymentService`, `CompanionStorageService`

Handles normal summon, recall, automatic storage, deployment limits, shoulder companions, and stored NBT.

Rule: if a feature removes or stores a creature, cancel temporary states first, then call the deployment/storage services.

## Mount Cinematics

Owner: `CompanionMountCinematicFlowService`, `CompanionCinematicMovementService`, `CompanionCinematicPositionService`, `CompanionCinematicLandingService`, `CompanionRetreatService`

Handles ride rescue, mount switching, flying catch paths, old mount retreat, and settle protection.

Rule: cinematic services may soft-control real mounted creatures. They must restore physics/AI when they finish or cancel.

## Combat Rescue

Owner: `CompanionCombatRescueService`, `CompanionEmergencyRescueService`, `CompanionThreatMemoryService`, `CompanionRescueProtectionService`

Handles passive rescue, emergency rescue, threat tracking, friendly-fire protection, and terrain protection.

Rule: rescue may temporarily exceed normal companion deployment limits, but it must restore the normal limit when finished.

## Addon Actions

Core owner: `FindMeApi`, `FindMeTemporaryActionController`, `FindMeCompanionInteractionItem`, `FindMeClientAbilityActionRegistry`

Addon owner: the independent `Find Me Skills` mod owns active skills, transport, skill books, assignments, packets, client targeting, temporary performers, and their cleanup.

Rule: core FindMe contains no concrete skill catalog, skill assignment data, skill item, skill packet, transport action, or skill configuration. It exposes only generic lifecycle, interaction-item, and ability-wheel extension points.

Rule: temporary addon performers must never enter player data, storage, teams, houses, or death records. Addon actions must refuse lifecycle-busy companions and implement idempotent tick, cancellation, logout, and cleanup behavior through `FindMeTemporaryActionController`.

Rule: FindMe 1.3 was not released with the old built-in skill implementation. The addon starts with its own `findme_skills_assignments` world data and intentionally does not migrate unpublished development skill state.

## Escort

Owner: `CompanionEscortService`, `CompanionEscortMovementService`

Handles one active escort creature per player and the movement/offset logic that keeps it near the player or current ride.

Rule: escort owns temporary follow state only. It does not own storage, summon, death, or team data.

## Home And Small House

Owner: `CompanionHomeService`, `CompanionHomeResidentService`, `CompanionHomeBehaviorService`, `SmallHouseBlock`

Handles home position, house resident behavior, broken-house cleanup, and home recall.

Rule: home residents should not be pulled by rescue or escort unless the user explicitly summons or starts escort.

Rule: home actions from packets must refuse entries that are lifecycle-busy, including active operation locks, pending storage, and pending arrival.

## Vehicles

Owner: `VehicleManager`, `VehicleCinematicService`, `VehicleSeatService`, `VehicleCompatibilityService`, `SableVehicleCompatibility`

Handles vehicle binding, summon, storage, wheel, seat cushion position, switching, and mod compatibility.

Rule: vehicle compatibility stays isolated. Do not leak mod-specific hacks into generic companion code.

Rule: failed vehicle restore or cinematic lookup must keep the player record and report/log the unknown state instead of silently deleting the vehicle entry.

Rule: vehicle storage must validate and persist the vehicle snapshot before removing the live entity or Sable sub-level from the world.

## Teams And Warehouse

Owner: `PlayerCompanionWheelService`, `WarehouseEntityService`, `CompanionTeamCommandPacket`, `CompanionTeamListPacket`

Handles teams, warehouse membership, wheel source order, drag assignment, and team application.

Rule: teams are organization data. They should not directly summon, store, or mutate live entities.

Rule: team, warehouse, wheel selection, and direct vehicle summon/collection mutations must refuse lifecycle-busy entries instead of changing organization state mid-deploy, mid-arrival, or mid-store.

## Death And Safety

Owner: `CompanionDeathService`, `PlayerCompanionDeadService`, `CompanionSafetyService`, `PlayerCompanionArchiveService`

Handles dead records, sleep revive, drop suppression, vault snapshots, backups, and data recovery.

Rule: any risky deletion or UUID replacement should snapshot first.

Rule: marking a registered creature dead should create a safety backup before moving it out of live lists. If the creature is already dead and the backup unexpectedly fails, keep the death snapshot/record flow and write lifecycle diagnostics instead of silently dropping the death.

Rule: recovery commands must support dry-run where practical, create a backup before mutating player data, and run Doctor after the mutation.

Rule: recovery mutations, backup restore, vault restore, stale-lock clear, and temporary-entity cleanup must abort if the safety backup cannot be created.

Rule: Doctor must check backup/vault snapshot validity and orphan metadata, but it remains read-only and must not repair or delete them automatically.

Rule: full backup restore should be previewable with `dry-run`; the bare restore command is also a preview, and data mutation requires `confirm`.

Rule: vault restore should also be previewable with `dry-run`; the bare restore command is also a preview, and data mutation requires `confirm`. Restore must refuse locked or already-loaded UUIDs.

Rule: release/unbind and dead-record deletion must create a full backup before mutating player data, whether triggered by commands or the warehouse UI. If that backup fails, the destructive operation must be cancelled.

## Runtime Animations

Owner: `CompanionArrivalSequenceService`, `CompanionMountCinematicFlowService`, `CompanionMountSwitchService`, `CompanionStorageService`, `VehicleCinematicService`

Handles entity movement, reveal/retirement timing, summon approach, rescue catch, storage transition, and mount switching. Animation choices are persisted independently for `SUMMON`, `RESCUE`, `STORAGE`, and `SWITCH`.

Rule: animation may control temporary motion and report completion, but it must not own formal lifecycle state. The lifecycle transaction remains authoritative and must restore physics, AI, riding controllers, and camera ownership on completion or cancellation.

Rule: changing or disabling a visual effect must never change animation selection, operation ordering, storage safety, or riding handoff.

## Visual Effects And Audio

Owner: `CompanionArrivalMagicService`, `CompanionMagicAudioService`, `CompanionEnderEffectService`, client effect render states/renderers

Handles magic circles, ender effect, velocity burst, custom magic circle texture, storage visuals, contract visuals, and sound. Effects are independently selectable for summon, rescue, and storage and can run alongside a movement animation.

Rule: visual services must use entity visual bounds so large creatures scale correctly.

Rule: effects may render, play audio, and finish independently. They must not decide when an entity becomes authoritative, is removed, mounts a player, or commits a lifecycle state.

## UI

Owner: `FindMeAuiDocumentScreen`, `FindMeAuiLayout`, `CompanionWheelScreen`, `FindMeWheelRenderer`, `CompanionDetailPreviewRenderer`, settings screens

Handles AUI manager, wheels, previews, settings, animation selection, and dead/home/team screens.

Rule: keep UI state client-side. Server actions must go through packets/commands, not direct data mutation.

Rule: AUI fixed coordinates belong in `FindMeAuiLayout`; page logic belongs in `FindMeAuiDocumentScreen`.

Rule: a FindMe mount or companion card opens its core command wheel with right-click and its addon ability wheel with middle-click. Both child wheels inherit the selected roster-wheel style and retain the original roster screen instance so returning preserves team, page, and focused card. Releasing the original held roster key closes either child wheel without activating its hovered action; left-click is the only confirmation path. The optional direct command-wheel key is unbound by default.

Rule: the core command wheel contains only authoritative FindMe orders for the exact selected creature: follow the player, hold position, move to the crosshair position, attack the crosshair target, land when classified as flying, return to an assigned home, and ride an assigned mount home. It has no creature preview and no manual summon/recall action. A tactical order may target a stored creature: the server selects it, runs the normal summon presentation in tactical-deploy mode without automatically boarding a mount, and starts the order only after arrival completes. The roster wheel captures the world crosshair target before it replaces world input and passes that immutable snapshot to the command wheel. The client does not require a deployed or locally alive state before sending an order; the server validates authoritative existence, ownership, lifecycle state, and legality. Orders cancel during storage, switching, logout, dimension change, timeout, or another competing movement order. Skills, transport, and future addon actions register only through `FindMeClientAbilityActionRegistry` and never enter the core command list.

Rule: management, warehouse, house, death, profile, settings, and Doctor pages share the accepted card foreground, sidebar, title, and overlay contracts. Native 3D previews render before one shared foreground text pass; context menus close on an outside click without activating the covered control.

Rule: Doctor backup detail carries complete backup and current entity record lists in addition to paged differences. Restore remains server-authoritative and requires the existing confirmation and backup identity validation.

## Pack Author Tools

Owner: `PackAnimationPresetService`, `PackAnimationPreset...Packet`, `PackEntityPresetUpdatePacket`

Handles pack edit mode, preset import/export, movement/category overrides, visual bounds scale, circle scale, default animation style, and default effect style. Format 4 stores animation and effect fields separately while reading legacy format 3 combined values.

Rule: pack presets define defaults. Player-level customization should override presets without modifying the preset file.

## Category Transfer

Owner: `CompanionCategoryTransferService`, `PlayerCompanionData.transferCategory`, `WarehouseEntityService`

Handles the bidirectional conversion between registered mounts and companions.

Rule: conversion is a server-authoritative warehouse transaction. The target must be stored and absent from all loaded worlds; deployed, ridden, shoulder, and home-resident targets must be recalled first. Create a forced safety backup before mutation, preserve UUID/NBT/name/home/team position/visual customization, remove stale source wheel/deployment references, and synchronize both categories after commit. Vehicles, Cobblemon party entries, and dead records are not convertible through this flow.

## Lifecycle And Ticks

Owner: `CompanionEvents`, `CompanionTickService`, `CompanionPlayerLifecycleService`, `CompanionTransientStateService`, `CompanionOperationLockService`, `CompanionLifecycleFacade`

Handles event entry points, server tick order, player tick order, login/logout, travel, dimension change, and death cleanup.

Rule: event classes should route to services only. Do not add large behavior blocks directly to events.

Rule: dangerous UI, packet, and command entry points should enter through `CompanionLifecycleFacade` before they call summon, store, recall, or switch services.

Rule: facade deploy, store, and restore entries must run the lifecycle busy gate before cancelling transient state or calling low-level services.

Rule: failure recovery may store through `ARRIVAL_PENDING` only when cleaning up a just-restored entity that failed placement; operation locks and storage-pending state must still refuse.

Rule: code that stores and removes a live official companion should call `CompanionLifecycleFacade.storeLiving`. Direct `CompanionManager.storeAndDiscard` calls should stay inside the facade only.

Rule: code that deploys a stored official companion should call `CompanionLifecycleFacade.restoreStored*` unless it is already inside the low-level transfer or storage service.

Rule: every new low-level `discard`, `data.remove`, `removeDeadAt`, `storeAndDiscard`, or direct restore call must be reviewed against `docs/safety-audit.md`.

Rule: login integrity checks are read-only. They may warn and log, but must not repair, delete, restore, or rewrite player data.
