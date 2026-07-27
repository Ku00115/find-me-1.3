# FindMe 1.3 User and Configuration Guide

## 1. Read before use

FindMe 1.3 is a test release that went through repeated refactoring. It may be less
stable or compatible than earlier versions.

1. Back up the entire world directory before installation or upgrade.
2. Test important creatures, vehicles, rescue, switching, and dimension travel in a copied world.
3. Store deployed entries and create a manual backup before changing FindMe, AUI, or entity mods.
4. Do not remove an entity-providing mod while FindMe still stores records from it.
5. Entities with custom AI, riding controllers, multipart models, or unusual NBT are not guaranteed to work.

## 2. Versions and installation

| Game version | Loader | Java | File |
|---|---|---:|---|
| 1.21.1 | NeoForge 21.1.230 or newer | 21 | `find_me-1.3.0-test.1-1.21.1-neoforge.jar` |
| 1.20.1 | Forge 47.x | 17 | `find_me-1.3.0-test.1-1.20.1-forge.jar` |

The client requires the ApricityUI build matching the game version and loader. In
multiplayer, install FindMe on the server and participating clients. Never install both FindMe JARs.

NeoForge 1.21.1 exposes optional Cobblemon, Sable/Aeronautics, MachineMax, and
Waystones integration points. Forge 1.20.1 has no Cobblemon or Sable-specific features.

## 3. Items and recipes

| Item | Recipe | Purpose |
|---|---|---|
| Name Paper | 3 Paper, shapeless | Right-click a creature to bind it under the active requirement |
| Vehicle Binder | 1 Iron Ingot + 1 Name Paper, shapeless | Bind a supported vehicle |
| Small House | Any wooden slab + 1 Name Paper, shapeless | House bound companions |
| Positioning Cushion | Name Paper above a Stick | NeoForge only; set a Sable vehicle seat |

A successful non-Creative binding consumes the Name Paper. The administrator tame
command changes ownership only; it does not bind or store the entity.

## 4. Default controls and wheels

All keys can be rebound in Minecraft Controls.

| Action | Default |
|---|---|
| Mount summon/rescue | `R` |
| Companion summon | `V` |
| Management | `G` |
| Current-entry command | Unbound |

- Hold a summon key for at least 250 ms to open its wheel.
- Tap and release to act on the pending entry.
- Hold the key and scroll to change selection without opening the wheel.
- Left-click an undeployed entry to summon it; click a deployed/switching entry to store or cancel it.
- Hover and close without activating to make that entry pending.
- Right-click a creature entry to open its command wheel.
- Middle-click a creature entry to open the ability wheel when an ability add-on provides one.
- Click the center or press `Esc` to cancel.
- Press `Tab` to switch between mount and vehicle wheels.
- Scroll between teams/pages; number keys `1` through `9` select slots.

State colors: blue is deployed, gray is pending, yellow is switching, and red is dead.

## 5. Mounts, companions, and vehicles

Use Name Paper on a creature. The entity must satisfy its editor-defined binding
requirement. FindMe can automatically create or join a team and stores the resulting record.

- Mounts participate in riding, switching, and fall rescue.
- Companions deploy independently and can follow, hold, guard, or fight.
- Vehicles use the Vehicle Binder and appear under `Tab` in the mount wheel.

Contextual companion commands include follow/cancel, hold, guard here, protect owner,
stop action, move to crosshair, attack crosshair target, land, go home, ride home, and
the Waystones destination entry when available. A third-party entity's native AI may override commands.

Rescue can activate when an unmounted player is falling dangerously. The default minimum
fall distance is 5 blocks. Movement classification controls placement and rescue behavior.
During a switch, FindMe should commit storage of the old mount after contact and riding succeed.

## 6. Small Houses, teams, warehouse, and backups

- Small Houses accept companions. Residents patrol instead of being permanently forced to sit.
- The default patrol radius is 64 blocks and hard boundary is 128 blocks.
- Residents are stored when no player tracks the area and restored when any player tracks it again.
- Same-type residents remain separate; residents do not push one another.
- Flying residents may use airborne positions when no ground collision-box slot is available.
- Management supports team creation, rename, deletion, and moving entries through the warehouse.
- Diagnostics provides manual backup, comparison, and restore tools.

## 7. In-game settings

Press `G`, then open Settings. These are per-player preferences, not TOML entries.

| Setting | Default | Values/range |
|---|---|---|
| Custom/original name/health | on/off/on | on or off |
| Rotate models | off | on or off |
| Reduce background animation | on | on or off |
| Drag hold duration | 300 ms | 150-600 ms |
| Operation sounds/control hints | on/on | on or off |
| Auto join/create teams | on/on | on or off |
| Default team | first | non-negative team index |
| Name colors | off | on or off |
| Maximum name length | 32 | 8-64 |
| Wheel style | Classic Radial | Six Wing, Tactical Strip, Folded Shards, Classic Radial |
| Text mode | Practical | Off, Practical, Immersive |
| UI animation | on | on or off |
| Font | Default | Default, Sans, UI, Humanist, Serif, FangSong, KaiTi, Monospace |
| Font size | Medium | Extra Small, Small, Medium, Large, Extra Large |
| Riding camera | None | None, First Person, Third Person Back, Third Person Front |
| Default binding cinematic | First type | Always, First Type, Never |

The original camera is restored after dismounting. Temporary cinematics take short-term priority.

## 8. TOML configuration

Files are generated after first launch. Common settings normally use
`config/find_me-common.toml`; server settings normally use the world's
`serverconfig/find_me-server.toml`. Stop a dedicated server before editing. `/findme reload`
reloads entity presets; it is not a universal hot reload for every TOML value.

### Server and world rules

| Key | Default | Range | Purpose |
|---|---:|---:|---|
| `enableAutoBackups` | `true` | boolean | Periodically back up player FindMe lists |
| `backupIntervalMinutes` | `30` | 1-1440 | Automatic backup interval in minutes |
| `preventBoundCreatureDeathDrops` | `true` | boolean | Avoid duplicating drops and FindMe death snapshots |
| `enableContractAnimation` | `true` | boolean | Play the manual binding ceremony |
| `enableContractCinematicCamera` | `true` | boolean | Use the temporary side-view ceremony camera |
| `enableDiagnosticLogging` | `false` | boolean | Write diagnostic/performance/lifecycle logs; enable only while debugging |
| `sleepReviveCooldownMinutes` | `10` | 0-10080 | Per-player sleep revival cooldown |
| `sleepReviveChance` | `0.7` | 0.0-1.0 | Chance to revive one dead companion on sleep |
| `sleepReviveSpawnEntity` | `true` | boolean | Try to deploy the revived creature nearby |
| `summonCooldownTicks` | `60` | 0-1200 | Shared summon, combat summon, companion, and return cooldown |
| `companionDeploymentLimit` | `2` | 1-32 | Simultaneously deployed companion limit, escort included |
| `rescueMinFallDistance` | `5` | 0-128 | Minimum fall distance for rescue summon |
| `rescueHoverBaseHeight` | `12.0` | 2.0-64.0 | Base flying-mount wait height above predicted landing point |
| `rescueHoverHeightRatio` | `0.25` | 0.0-1.0 | Extra wait height per fall block above the low-altitude window |
| `rescueHoverMaxHeight` | `48.0` | 4.0-256.0 | Maximum flying rescue wait height |
| `enableCreatureArrivalVoice` | `true` | boolean | Play the creature's voice when summon/rescue is accepted |
| `creatureArrivalVoiceVolume` | `0.4` | 0.0-2.0 | Arrival voice volume |
| `housePatrolRadius` | `64` | 64-4096 | Resident patrol radius |
| `houseHardRadius` | `128` | 128-8192 | Resident hard boundary; raised to patrol radius when needed |

### Feature modules

All switches under `modules` default to enabled. Disabling a module preserves saved records.

| Key | Purpose |
|---|---|
| `riding` | Mount, vehicle, summon, rescue, and switching workflows |
| `companions` | Non-riding companions, deployment, escort, and combat support |
| `management` | Management, warehouse, team, details, and editor operations |
| `houses` | House assignment and restoration; requires companions |
| `sleepRevival` | Sleep revival; requires companions and revival settings |
| `cobblemonIntegration` | NeoForge only; Cobblemon party riding orchestration |
| `sableIntegration` | NeoForge only; Sable/Aeronautics vehicle integration |

### Common visual and dialogue values

| Key | Default | Purpose |
|---|---:|---|
| `guiOpacity` | `1.0` | Wheel 2D opacity, range 0.05-1.0; does not affect 3D previews |
| `previewScaleOverrides` | empty | Post-auto-fit multipliers in `entity_id=multiplier` format |
| `enableDialogue` | `false` | Enable styled title/subtitle dialogue |
| `dialogueOverrides` | empty | Central list of dialogue overrides |

Preview example: `dragonmounts:dragon=0.75`.

Dialogue format: `context=first line|second line`. Repeat a context for multiple random
pairs; use `context=` to disable it. `{player}` and `{name}` are supported, and cooldown
also supports `{seconds}`.

Contexts: `mount_summon`, `companion_summon`, `mount_rescue`, `companion_combat_rescue`,
`storage`, `home_summon`, `home_storage`, `home_set`, `home_clear`, `registered`,
`sleep_revive`, `sleep_revive_stored`, `blocked`, `busy`, `cooldown`, `no_registered`,
`unavailable`, `already_riding`, `occupied`, `too_high`, `void_mount_unsafe`,
`void_companion_unsafe`, and `void_flying_mount`.

## 9. Entity editor for pack authors

Administrators open it with `/findme editor`. Validate presets in a copied world first.

| Field | Values/format | Purpose |
|---|---|---|
| Category | Auto, Mount, Companion, Vehicle, Disabled | Select the owning FindMe system |
| Movement | Auto, Walk, Fly, Swim | Shared source for summon, rescue, switch, and placement |
| Binding requirement | Auto, Tamed, Owner, Ignore | Decide whether Name Paper may bind the entity |
| Binding animation | Inherit, Always, First Type, Never | Per-entity override for the ceremony |
| Rescue motion | Standard | Current rescue motion profile |
| Bounds scale | number | Adjust FindMe recognition and preview bounds |
| Effect scale | number | Adjust magic-circle/effect size |
| Arrival sound | sound ID | Sound played on arrival |
| Volume/pitch | number | Arrival sound parameters |
| Preview NBT | SNBT/NBT data | Select a specific variant for editor preview |

Animation styles are None, Standard, Ground Emerge, and Ground Sink. Effect styles are
None, Ender, Magic Circle, Velocity Burst, and Custom/Texture Magic Circle. Movement
classification, rather than animation choice, owns gameplay behavior.

Binding requirements: `AUTO` uses FindMe ownership detection; `TAMED` accepts any tamed
entity; `OWNER` requires the player to be the recorded owner; `IGNORE` bypasses both checks.

## 10. Administrator commands

| Command | Permission | Purpose |
|---|---:|---|
| `/findme manage` | player | Open management |
| `/findme editor` | 2 | Open entity editor |
| `/findme reload` | 2 | Reload entity presets |
| `/findme tame` | 2 | Force-tame the looked-at entity, up to 24 blocks |
| `/findme tame <target>` | 2 | Force-tame a specified entity |

`tame` never binds, stores, or assigns a team. It is a compatibility testing tool.

## 11. Troubleshooting order

1. Verify exact game, loader, FindMe, and ApricityUI versions.
2. Reproduce in a copied world with only FindMe, AUI, and the target entity mod.
3. Create a manual backup in Management.
4. Enable `enableDiagnosticLogging`, reproduce once, then disable it to avoid log overhead.
5. Keep `logs/latest.log`, crash reports, entity registry ID, exact steps, and JAR names.
6. If restoration fails, do not repeatedly summon or remove mods; preserve a world copy first.

