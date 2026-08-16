# Find Me 找到我

A Minecraft mod for binding, summoning, commanding, and housing mounts, companions, and vehicles from across your modpack.

## Introduction

**Find Me** is a creature management mod for Minecraft Java Edition. Bind supported tamed or owned creatures, organize them into wheels and teams, then call them whenever you need a mount, a companion, or an emergency rescue.

Creatures do more than teleport. Land, flying, and aquatic creatures use different arrival logic; mounts approach and switch with animated movement; companions can follow formations, guard an area, and respond to tactical orders.

## Features

🧾 Bind supported vanilla and modded creatures with **Name Paper**

🎡 Separate multi-page summon wheels for mounts and companions

🪽 Movement-aware arrivals for land, flying, and aquatic creatures

🛟 Emergency mount rescue while falling from dangerous heights

⚔️ Individual and team command wheels for movement, combat, guarding, and support

📦 Visual manager with teams, warehouse storage, profiles, search, and recovery tools

🏠 Personal houses with rest, wander, and guard modes

❤️ Critical-state recovery, house healing, backups, and sleep recovery options

🛞 Bind compatible vehicles and configure their seats

🛡️ Optional friendly-fire and bound-creature block protection

## Binding and Summoning

Use **Name Paper** on a supported creature that meets its ownership or binding requirements. The creature is added to Find Me while preserving its identity and saved data.

- **Tap** a summon key to call or switch to the selected creature.
- **Hold** the key to open its wheel.
- Move to a slot and **release** to confirm.
- The wheel remembers the page containing your active creature.

Summons account for creature size, movement type, terrain, water, available space, and player movement. During a mount switch, the old mount leaves while the new one approaches. When falling, the mount key can trigger an emergency rescue instead of an ordinary summon.

## Command System

Issue orders to one creature or an entire team:

- Follow, hold position, or move forward
- Move to the crosshair position
- Guard here or protect the owner
- Attack the crosshair target
- Land flying members
- Pause, resume, recall, or return home
- Use healing and magic actions when compatible abilities are equipped

Bound Find Me creatures are excluded from automatic defense and attack targeting. Friendly-fire protection also prevents owners and their own creatures from damaging one another.

## Manager and Houses

Press `G` to open the manager. Create teams, move creatures through warehouse storage, assign wheel slots, search your collection, inspect animated models and health, and review critical or recoverable creatures.

Place a **Small House** to give companions a persistent home. Each house has independent capacity, wander range, and activity-boundary settings. Residents can **rest**, **wander**, or **guard**. Critically injured residents heal inside the house and return automatically at full health.

## Vehicles

Use a **Vehicle Binder** on supported vehicles, then summon or store them through Find Me. A **Seat Cushion** can define passenger positions. Normal right-click behavior is preserved outside temporary summon and switching states.

## Controls

| Default Key | Action |
| --- | --- |
| `R` | Summon or switch mount; hold for the mount wheel; trigger falling rescue |
| `V` | Summon or switch companion; hold for the companion wheel |
| `G` | Open the Find Me manager |
| `C` | Open the ability wheel when abilities are available |
| Unbound | Open the current creature's command wheel; assign a key in Controls |

All keys can be changed in Minecraft's control settings.

## Commands

Player commands:

- `/findme manage` — open the manager
- `/findme hud edit` — edit the companion HUD position
- `/findme hud reset` — reset the HUD layout

Administrator and pack-author commands:

- `/findme reload` — reload entity and animation presets
- `/findme editor` — open the entity adaptation editor
- `/findme tame [target]` — force a test ownership state for administration and compatibility work

## Customization

Players can configure summon effects, creature voices, wheel style, UI appearance, HUD, camera behavior, automatic teams, deployment limits, friendly-fire protection, and block protection.

Servers can configure summon cooldown, maximum deployments, rescue thresholds, house defaults, backups, sleep recovery, contract animation, and dialogue text.

## Mod Compatibility

Find Me uses general entity logic for broad mod compatibility, with optional integration for **Waystones**, **Cobblemon**, and several dragon mods. Pack authors can use the built-in editor and entity presets for creature-specific ownership, model, animation, flight, or riding adjustments.

## Requirements

- Minecraft **1.20.1**
- Minecraft Forge **47+**
- Java **17**
- **ApricityUI 1.2.0+** — required on the client

For multiplayer, install Find Me on the server and every player's client. Each client must also install ApricityUI.

## Feedback and Suggestions

[https://github.com/Ku00115/find-me-1.3/issues](https://github.com/Ku00115/find-me-1.3/issues)

## Development

**kuzhi / Ku00115**

*Thanks to everyone who tests Find Me with large modpacks and reports creature-specific issues.*

