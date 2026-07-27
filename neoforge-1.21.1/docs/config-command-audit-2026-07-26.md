# Configuration and Waystones Update

## Restored player settings

The player settings model now includes the accepted presentation controls: custom/original names, health display, preview rotation, reduced background motion, operation sounds, control hints, and default team. Existing player saves retain their values; an absent player root uses the exported UI defaults when present.

The settings page keeps the current three-page layout and current section mapping. `Export as new-world defaults` is server-authoritative and requires operator permission. It writes `config/find_me/ui_defaults.json` and copies the current world server configuration to `defaultconfigs/find_me-server.toml` using temporary files and atomic replacement. Creature records, houses, teams, and backups are never exported.

## Waystones boundary

Waystones is an optional compile-only dependency. FindMe does not bundle Waystones or Balm. The command wheel exposes `Teleport` only for a mount when the mod is present. The destination list is a FindMe screen backed by `WaystonesAPI.getActivatedWaystones`; the server revalidates the UUID and activation state when a destination is selected.

The teleport journey owns only presentation and lifecycle coordination. It starts the normal FindMe summon/board path, uses the existing ride-home camera and movement helpers, blacks out before the transfer, then invokes `createDefaultTeleportContext` and `tryTeleportAsync`. The player is the Waystones context entity and the current mount is an additional entity, allowing Waystones to preserve its permission, XP, cooldown, event, chunk-loading, and passenger rules. Failure returns the native Waystones error component and rolls back FindMe state.

In-game acceptance remains required with and without Waystones, especially a cross-dimension mounted transfer, an insufficient-XP failure, a denied/private destination, cancellation during departure, and a missing Waystones client. Minecraft was not launched during this change.
