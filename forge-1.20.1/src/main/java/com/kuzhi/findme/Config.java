package com.kuzhi.findme;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.kuzhi.findme.common.FindMeModule;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public class Config {
    public static final int DEFAULT_SAFE_SEARCH_RADIUS = 12;
    public static final int DEFAULT_VAULT_CAPACITY = 30;
    public static final int DEFAULT_BACKUP_CAPACITY = 5;
    public static final int DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS = 30;
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.Builder DIALOGUE_DEFAULTS_BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.BooleanValue ENABLE_AUTO_BACKUPS = SERVER_BUILDER.comment("[Safety] Periodically back up each player's Find me lists. Recommended: true.").translation("config.find_me.enableAutoBackups").define("enableAutoBackups", true);
    private static final ForgeConfigSpec.IntValue BACKUP_INTERVAL_MINUTES = SERVER_BUILDER.comment("[Safety] Minutes between automatic player list backups.").translation("config.find_me.backupIntervalMinutes").defineInRange("backupIntervalMinutes", 30, 1, 1440);
    private static final ForgeConfigSpec.BooleanValue PREVENT_BOUND_CREATURE_DEATH_DROPS = SERVER_BUILDER.comment("[Safety] If true, bound mounts and companions do not drop inventory/equipment items when they die. FindMe still keeps its death snapshot for revival, preventing duplicate saddles, armor, or modded equipment.").translation("config.find_me.preventBoundCreatureDeathDrops").define("preventBoundCreatureDeathDrops", true);
    private static final ForgeConfigSpec.BooleanValue ENABLE_CONTRACT_ANIMATION = SERVER_BUILDER.comment("[Binding] Play the Name Paper contract ceremony animation before manual binding completes. Disable to bind immediately.").translation("config.find_me.enableContractAnimation").define("enableContractAnimation", true);
    private static final ForgeConfigSpec.BooleanValue ENABLE_CONTRACT_CINEMATIC_CAMERA = SERVER_BUILDER.comment("[Binding] Use the temporary side-view cinematic camera during the Name Paper ceremony. If false, effects play from the player's normal view.").translation("config.find_me.enableContractCinematicCamera").define("enableContractCinematicCamera", true);
    private static final ForgeConfigSpec.BooleanValue ENABLE_DIAGNOSTIC_LOGGING = SERVER_BUILDER.comment("[Debug] Write all FindMe diagnostic, performance, summon, cinematic, preview, and compatibility logs. Disabled by default.").translation("config.find_me.enableDiagnosticLogging").define("enableDiagnosticLogging", false);

    private static final ForgeConfigSpec.IntValue SLEEP_REVIVE_COOLDOWN_MINUTES = SERVER_BUILDER.comment("[Revive] Minutes between sleep revive attempts per player.").translation("config.find_me.sleepReviveCooldownMinutes").defineInRange("sleepReviveCooldownMinutes", 10, 0, 10080);
    private static final ForgeConfigSpec.DoubleValue SLEEP_REVIVE_CHANCE = SERVER_BUILDER.comment("[Revive] Chance that sleep revives one dead companion.").translation("config.find_me.sleepReviveChance").defineInRange("sleepReviveChance", 0.7, 0.0, 1.0);
    private static final ForgeConfigSpec.BooleanValue SLEEP_REVIVE_SPAWN_ENTITY = SERVER_BUILDER.comment("[Revive] If true, sleep revive tries to place the creature nearby. Disable for packs with destructive large mobs.").translation("config.find_me.sleepReviveSpawnEntity").define("sleepReviveSpawnEntity", true);

    private static final ForgeConfigSpec.IntValue SUMMON_COOLDOWN_TICKS = SERVER_BUILDER.comment("[Summon] Shared cooldown in ticks for summon, combat summon, companion summon, and return actions.").translation("config.find_me.summonCooldownTicks").defineInRange("summonCooldownTicks", 60, 0, 1200);
    private static final ForgeConfigSpec.IntValue COMPANION_DEPLOYMENT_LIMIT = SERVER_BUILDER.comment("[Companion] Maximum simultaneously deployed companions, including the escort slot. Default: 2.").translation("config.find_me.companionDeploymentLimit").defineInRange("companionDeploymentLimit", 2, 1, 32);
    private static final ForgeConfigSpec.IntValue RESCUE_MIN_FALL_DISTANCE = SERVER_BUILDER.comment("[Summon] If the player is falling at least this many blocks, rescue summon can trigger.").translation("config.find_me.rescueMinFallDistance").defineInRange("rescueMinFallDistance", 5, 0, 128);
    private static final ForgeConfigSpec.DoubleValue RESCUE_HOVER_BASE_HEIGHT = SERVER_BUILDER.comment("[Rescue] Base height above the predicted landing point where a flying mount waits during a hover rescue.").translation("config.find_me.rescueHoverBaseHeight").defineInRange("rescueHoverBaseHeight", 12.0, 2.0, 64.0);
    private static final ForgeConfigSpec.DoubleValue RESCUE_HOVER_HEIGHT_RATIO = SERVER_BUILDER.comment("[Rescue] Additional waiting height per block of fall distance above the ten-block landing-summon window.").translation("config.find_me.rescueHoverHeightRatio").defineInRange("rescueHoverHeightRatio", 0.25, 0.0, 1.0);
    private static final ForgeConfigSpec.DoubleValue RESCUE_HOVER_MAX_HEIGHT = SERVER_BUILDER.comment("[Rescue] Maximum height above the predicted landing point for a flying mount's hover wait.").translation("config.find_me.rescueHoverMaxHeight").defineInRange("rescueHoverMaxHeight", 48.0, 4.0, 256.0);
    private static final ForgeConfigSpec.BooleanValue ENABLE_CREATURE_ARRIVAL_VOICE = SERVER_BUILDER.comment("[Audio] Play the summoned creature's own voice when summon or rescue is accepted.").translation("config.find_me.enableCreatureArrivalVoice").define("enableCreatureArrivalVoice", true);
    private static final ForgeConfigSpec.DoubleValue CREATURE_ARRIVAL_VOICE_VOLUME = SERVER_BUILDER.comment("[Audio] Volume for summoned creature voice. 1.0 is full volume; default is 40%.").translation("config.find_me.creatureArrivalVoiceVolume").defineInRange("creatureArrivalVoiceVolume", 0.4, 0.0, 2.0);
    private static final ForgeConfigSpec.IntValue HOUSE_PATROL_RADIUS = SERVER_BUILDER.comment("[House] Resident patrol radius in blocks. Default and minimum: 64.").translation("config.find_me.housePatrolRadius").defineInRange("housePatrolRadius", 64, 64, 4096);
    private static final ForgeConfigSpec.IntValue HOUSE_HARD_RADIUS = SERVER_BUILDER.comment("[House] Hard resident boundary in blocks. Default and minimum: 128; values below the patrol radius are raised at runtime.").translation("config.find_me.houseHardRadius").defineInRange("houseHardRadius", 128, 128, 8192);
    static {
        SERVER_BUILDER.comment("Server-authoritative FindMe feature modules. Disabling a module preserves all saved records.").push("modules");
    }
    private static final ForgeConfigSpec.BooleanValue ENABLE_RIDING_MODULE = SERVER_BUILDER.comment("Enable FindMe mount, vehicle, summon, rescue, and switching workflows.").translation("config.find_me.module.riding").define("riding", true);
    private static final ForgeConfigSpec.BooleanValue ENABLE_COMPANION_MODULE = SERVER_BUILDER.comment("Enable non-riding companion binding, deployment, escort, and combat rescue.").translation("config.find_me.module.companions").define("companions", true);
    private static final ForgeConfigSpec.BooleanValue ENABLE_MANAGEMENT_MODULE = SERVER_BUILDER.comment("Enable management, warehouse, team, detail, and editor UI actions. Data remains available to Doctor and server recovery tools.").translation("config.find_me.module.management").define("management", true);
    private static final ForgeConfigSpec.BooleanValue ENABLE_HOUSE_MODULE = SERVER_BUILDER.comment("Enable house assignment, resident restoration, and house pages. Requires the companion module.").translation("config.find_me.module.houses").define("houses", true);
    private static final ForgeConfigSpec.BooleanValue ENABLE_SLEEP_REVIVAL_MODULE = SERVER_BUILDER.comment("Enable sleep-based revival attempts. Requires the companion module and the existing revival behavior setting.").translation("config.find_me.module.sleep_revival").define("sleepRevival", true);
    static {
        SERVER_BUILDER.pop();
    }

    private static final ForgeConfigSpec.DoubleValue GUI_OPACITY = BUILDER.comment("[Visual] Opacity for wheel GUI 2D elements. Does not affect 3D entity previews.").translation("config.find_me.guiOpacity").defineInRange("guiOpacity", 1.0, 0.05, 1.0);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> PREVIEW_SCALE_OVERRIDES = BUILDER.comment("[Visual] Advanced: multipliers applied after automatic GUI preview fitting, formatted as entity_id=multiplier. Example: dragonmounts:dragon=0.75").translation("config.find_me.previewScaleOverrides").defineListAllowEmpty("previewScaleOverrides", List.of(), Config::isString);
    static {
        BUILDER.comment("[Dialogue] Editable summon, rescue, storage, and warning subtitle lines. Entries use first line|second line. Either side may be spoken by the player or creature. A blank first line such as |reply makes only the second line appear.").push("dialogue");
    }
    private static final ForgeConfigSpec.BooleanValue ENABLE_DIALOGUE = BUILDER.comment("[Dialogue] Show FindMe's flavor subtitle dialogue. Disabled by default; players who like roleplay-style lines can enable it.").translation("config.find_me.enableDialogue").define("enableDialogue", false);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOUNT_SUMMON_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Title/subtitle dialogue after a mount appears. Format: first line|second line. Supports {player} and {name}. Clear to disable.").translation("config.find_me.mountSummonDialogueLines").defineListAllowEmpty("mountSummonDialogueLines", List.of(
            "dialogue.find_me.mount_summon.0.call|dialogue.find_me.mount_summon.0.reply",
            "dialogue.find_me.mount_summon.1.call|dialogue.find_me.mount_summon.1.reply",
            "dialogue.find_me.mount_summon.2.call|dialogue.find_me.mount_summon.2.reply",
            "dialogue.find_me.mount_summon.3.call|dialogue.find_me.mount_summon.3.reply",
            "dialogue.find_me.mount_summon.4.call|dialogue.find_me.mount_summon.4.reply",
            "dialogue.find_me.mount_summon.5.call|dialogue.find_me.mount_summon.5.reply"
    ), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMPANION_SUMMON_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Title/subtitle dialogue after a companion appears. Format: first line|second line. Supports {player} and {name}. Clear to disable.").translation("config.find_me.companionSummonDialogueLines").defineListAllowEmpty("companionSummonDialogueLines", List.of(
            "dialogue.find_me.companion_summon.0.call|dialogue.find_me.companion_summon.0.reply",
            "dialogue.find_me.companion_summon.1.call|dialogue.find_me.companion_summon.1.reply",
            "dialogue.find_me.companion_summon.2.call|dialogue.find_me.companion_summon.2.reply",
            "dialogue.find_me.companion_summon.3.call|dialogue.find_me.companion_summon.3.reply",
            "dialogue.find_me.companion_summon.4.call|dialogue.find_me.companion_summon.4.reply",
            "dialogue.find_me.companion_summon.5.call|dialogue.find_me.companion_summon.5.reply"
    ), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOUNT_RESCUE_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Title/subtitle dialogue after a mount rescue appears. Format: first line|second line. Supports {player} and {name}. Clear to disable.").translation("config.find_me.mountRescueDialogueLines").defineListAllowEmpty("mountRescueDialogueLines", List.of(
            "dialogue.find_me.mount_rescue.0.call|dialogue.find_me.mount_rescue.0.reply",
            "dialogue.find_me.mount_rescue.1.call|dialogue.find_me.mount_rescue.1.reply",
            "dialogue.find_me.mount_rescue.2.call|dialogue.find_me.mount_rescue.2.reply",
            "dialogue.find_me.mount_rescue.3.call|dialogue.find_me.mount_rescue.3.reply",
            "dialogue.find_me.mount_rescue.4.call|dialogue.find_me.mount_rescue.4.reply",
            "dialogue.find_me.mount_rescue.5.call|dialogue.find_me.mount_rescue.5.reply"
    ), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMPANION_COMBAT_RESCUE_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Title/subtitle dialogue after a companion appears against a threat. Format: first line|second line. Supports {player} and {name}. Clear to disable.").translation("config.find_me.companionCombatRescueDialogueLines").defineListAllowEmpty("companionCombatRescueDialogueLines", List.of(
            "dialogue.find_me.companion_combat_rescue.0.call|dialogue.find_me.companion_combat_rescue.0.reply",
            "dialogue.find_me.companion_combat_rescue.1.call|dialogue.find_me.companion_combat_rescue.1.reply",
            "dialogue.find_me.companion_combat_rescue.2.call|dialogue.find_me.companion_combat_rescue.2.reply",
            "dialogue.find_me.companion_combat_rescue.3.call|dialogue.find_me.companion_combat_rescue.3.reply",
            "dialogue.find_me.companion_combat_rescue.4.call|dialogue.find_me.companion_combat_rescue.4.reply",
            "dialogue.find_me.companion_combat_rescue.5.call|dialogue.find_me.companion_combat_rescue.5.reply"
    ), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> STORAGE_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Title/subtitle dialogue when storing a creature. Format: first line|second line. Supports {player} and {name}. Clear to disable.").translation("config.find_me.storageDialogueLines").defineListAllowEmpty("storageDialogueLines", List.of(
            "dialogue.find_me.storage.0.call|dialogue.find_me.storage.0.reply",
            "dialogue.find_me.storage.1.call|dialogue.find_me.storage.1.reply",
            "dialogue.find_me.storage.2.call|dialogue.find_me.storage.2.reply",
            "dialogue.find_me.storage.3.call|dialogue.find_me.storage.3.reply"
    ), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> HOME_SUMMON_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Title/subtitle dialogue when a creature with a home is summoned. Format: first line|second line. Supports {player} and {name}. Clear to disable.").translation("config.find_me.homeSummonDialogueLines").defineListAllowEmpty("homeSummonDialogueLines", List.of(
            "dialogue.find_me.home_summon.0.call|dialogue.find_me.home_summon.0.reply",
            "dialogue.find_me.home_summon.1.call|dialogue.find_me.home_summon.1.reply",
            "dialogue.find_me.home_summon.2.call|dialogue.find_me.home_summon.2.reply",
            "dialogue.find_me.home_summon.3.call|dialogue.find_me.home_summon.3.reply"
    ), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> HOME_STORAGE_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Title/subtitle dialogue when storing a creature that has a home. Format: first line|second line. Supports {player} and {name}. Clear to disable.").translation("config.find_me.homeStorageDialogueLines").defineListAllowEmpty("homeStorageDialogueLines", List.of(
            "dialogue.find_me.home_storage.0.call|dialogue.find_me.home_storage.0.reply",
            "dialogue.find_me.home_storage.1.call|dialogue.find_me.home_storage.1.reply",
            "dialogue.find_me.home_storage.2.call|dialogue.find_me.home_storage.2.reply",
            "dialogue.find_me.home_storage.3.call|dialogue.find_me.home_storage.3.reply"
    ), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> HOME_SET_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Dialogue when setting a creature's home. Format: first line|second line. Supports {player} and {name}. Clear to disable.").translation("config.find_me.homeSetDialogueLines").defineListAllowEmpty("homeSetDialogueLines", List.of(
            "dialogue.find_me.home_set.0.call|dialogue.find_me.home_set.0.reply",
            "dialogue.find_me.home_set.1.call|dialogue.find_me.home_set.1.reply"
    ), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> HOME_CLEAR_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Dialogue when clearing a creature's home. Format: first line|second line. Supports {player} and {name}. Clear to disable.").translation("config.find_me.homeClearDialogueLines").defineListAllowEmpty("homeClearDialogueLines", List.of(
            "dialogue.find_me.home_clear.0.call|dialogue.find_me.home_clear.0.reply",
            "dialogue.find_me.home_clear.1.call|dialogue.find_me.home_clear.1.reply"
    ), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> REGISTERED_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Dialogue when a creature is registered into FindMe. Format: first line|second line. Supports {player} and {name}. Clear to disable.").translation("config.find_me.registeredDialogueLines").defineListAllowEmpty("registeredDialogueLines", List.of(
            "dialogue.find_me.registered.0.call|dialogue.find_me.registered.0.reply",
            "dialogue.find_me.registered.1.call|dialogue.find_me.registered.1.reply"
    ), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SLEEP_REVIVE_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Dialogue when sleep revives and places a creature nearby. Format: first line|second line. Supports {player} and {name}. Clear to disable.").translation("config.find_me.sleepReviveDialogueLines").defineListAllowEmpty("sleepReviveDialogueLines", List.of(
            "dialogue.find_me.sleep_revive.0.call|dialogue.find_me.sleep_revive.0.reply",
            "dialogue.find_me.sleep_revive.1.call|dialogue.find_me.sleep_revive.1.reply"
    ), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SLEEP_REVIVE_STORED_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Dialogue when sleep revives a creature but stores it because nearby space is unsafe. Format: first line|second line. Supports {player} and {name}. Clear to disable.").translation("config.find_me.sleepReviveStoredDialogueLines").defineListAllowEmpty("sleepReviveStoredDialogueLines", List.of(
            "dialogue.find_me.sleep_revive_stored.0.call|dialogue.find_me.sleep_revive_stored.0.reply",
            "dialogue.find_me.sleep_revive_stored.1.call|dialogue.find_me.sleep_revive_stored.1.reply"
    ), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCKED_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Title/subtitle dialogue when summon/rescue was answered but space is blocked. Format: first line|second line. Supports {player} and {name}. Clear to disable.").translation("config.find_me.blockedDialogueLines").defineListAllowEmpty("blockedDialogueLines", List.of("dialogue.find_me.blocked.0.call|dialogue.find_me.blocked.0.reply", "dialogue.find_me.blocked.1.call|dialogue.find_me.blocked.1.reply"), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BUSY_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Title/subtitle dialogue when the previous summon/storage animation is still closing. Format: first line|second line. Supports {player} and {name}. Clear to disable.").translation("config.find_me.busyDialogueLines").defineListAllowEmpty("busyDialogueLines", List.of("dialogue.find_me.busy.0.call|dialogue.find_me.busy.0.reply", "dialogue.find_me.busy.1.call|dialogue.find_me.busy.1.reply"), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> COOLDOWN_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Dialogue when summon is still on cooldown. Format: first line|second line. Supports {player}, {name}, and {seconds}.").translation("config.find_me.cooldownDialogueLines").defineListAllowEmpty("cooldownDialogueLines", List.of("dialogue.find_me.cooldown.0.call|dialogue.find_me.cooldown.0.reply", "dialogue.find_me.cooldown.1.call|dialogue.find_me.cooldown.1.reply"), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> NO_REGISTERED_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Dialogue when no creature is registered for this key. Format: first line|second line. Supports {player} and {name}.").translation("config.find_me.noRegisteredDialogueLines").defineListAllowEmpty("noRegisteredDialogueLines", List.of("dialogue.find_me.no_registered.0.call|dialogue.find_me.no_registered.0.reply", "dialogue.find_me.no_registered.1.call|dialogue.find_me.no_registered.1.reply"), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> UNAVAILABLE_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Dialogue when the selected creature cannot be found or restored. Format: first line|second line. Supports {player} and {name}.").translation("config.find_me.unavailableDialogueLines").defineListAllowEmpty("unavailableDialogueLines", List.of("dialogue.find_me.unavailable.0.call|dialogue.find_me.unavailable.0.reply", "dialogue.find_me.unavailable.1.call|dialogue.find_me.unavailable.1.reply"), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALREADY_RIDING_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Dialogue when the player is already riding the selected mount. Format: first line|second line. Supports {player} and {name}.").translation("config.find_me.alreadyRidingDialogueLines").defineListAllowEmpty("alreadyRidingDialogueLines", List.of(
            "dialogue.find_me.already_riding.0.call|dialogue.find_me.already_riding.0.reply",
            "dialogue.find_me.already_riding.1.call|dialogue.find_me.already_riding.1.reply",
            "dialogue.find_me.already_riding.2.call|dialogue.find_me.already_riding.2.reply",
            "dialogue.find_me.already_riding.3.call|dialogue.find_me.already_riding.3.reply",
            "|dialogue.find_me.already_riding.4.reply",
            "|dialogue.find_me.already_riding.5.reply",
            "|dialogue.find_me.already_riding.6.reply",
            "|dialogue.find_me.already_riding.7.reply",
            "|dialogue.find_me.already_riding.8.reply"
    ), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> OCCUPIED_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Dialogue when the selected creature is occupied by another passenger. Format: first line|second line. Supports {player} and {name}.").translation("config.find_me.occupiedDialogueLines").defineListAllowEmpty("occupiedDialogueLines", List.of("dialogue.find_me.occupied.0.call|dialogue.find_me.occupied.0.reply"), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> TOO_HIGH_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Dialogue when a companion cannot appear safely at the player's height. Format: first line|second line. Supports {player} and {name}.").translation("config.find_me.tooHighDialogueLines").defineListAllowEmpty("tooHighDialogueLines", List.of("dialogue.find_me.too_high.0.call|dialogue.find_me.too_high.0.reply", "dialogue.find_me.too_high.1.call|dialogue.find_me.too_high.1.reply"), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> VOID_MOUNT_UNSAFE_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Dialogue when a non-flying mount cannot be summoned over the void. Format: first line|second line. Supports {player} and {name}.").translation("config.find_me.voidMountUnsafeDialogueLines").defineListAllowEmpty("voidMountUnsafeDialogueLines", List.of(
            "dialogue.find_me.void_mount_unsafe.0.call|dialogue.find_me.void_mount_unsafe.0.reply",
            "dialogue.find_me.void_mount_unsafe.1.call|dialogue.find_me.void_mount_unsafe.1.reply",
            "dialogue.find_me.void_mount_unsafe.2.call|dialogue.find_me.void_mount_unsafe.2.reply"
    ), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> VOID_COMPANION_UNSAFE_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Dialogue when a companion cannot be summoned over the void. Format: first line|second line. Supports {player} and {name}.").translation("config.find_me.voidCompanionUnsafeDialogueLines").defineListAllowEmpty("voidCompanionUnsafeDialogueLines", List.of(
            "dialogue.find_me.void_companion_unsafe.0.call|dialogue.find_me.void_companion_unsafe.0.reply",
            "dialogue.find_me.void_companion_unsafe.1.call|dialogue.find_me.void_companion_unsafe.1.reply",
            "dialogue.find_me.void_companion_unsafe.2.call|dialogue.find_me.void_companion_unsafe.2.reply"
    ), Config::isString);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> VOID_FLYING_MOUNT_DIALOGUE_LINES = DIALOGUE_DEFAULTS_BUILDER.comment("[Flavor] Dialogue when a flying mount answers directly over the void. Format: first line|second line. Supports {player} and {name}.").translation("config.find_me.voidFlyingMountDialogueLines").defineListAllowEmpty("voidFlyingMountDialogueLines", List.of(
            "dialogue.find_me.void_flying_mount.0.call|dialogue.find_me.void_flying_mount.0.reply",
            "dialogue.find_me.void_flying_mount.1.call|dialogue.find_me.void_flying_mount.1.reply",
            "dialogue.find_me.void_flying_mount.2.call|dialogue.find_me.void_flying_mount.2.reply",
            "dialogue.find_me.void_flying_mount.3.call|dialogue.find_me.void_flying_mount.3.reply"
    ), Config::isString);
    private static final ForgeConfigSpec DIALOGUE_DEFAULTS_SPEC = DIALOGUE_DEFAULTS_BUILDER.build();
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIALOGUE_OVERRIDES = BUILDER
            .comment("[Dialogue] All dialogue overrides in one list. Format: context=first line|second line. Repeat a context for multiple pairs; context= disables it.")
            .translation("config.find_me.dialogueOverrides")
            .defineListAllowEmpty("dialogueOverrides", List.of(), Config::isString);
    static {
        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();
    public static final ForgeConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(Config::onLoad);
    }

    public static int summonCooldownTicks = 60;
    public static int rescueMinFallDistance = 5;
    public static double rescueHoverBaseHeight = 12.0;
    public static double rescueHoverHeightRatio = 0.25;
    public static double rescueHoverMaxHeight = 48.0;
    public static int companionDeploymentLimit = 2;
    public static boolean enableRidingModule = true;
    public static boolean enableCompanionModule = true;
    public static boolean enableManagementModule = true;
    public static boolean enableHouseModule = true;
    public static boolean enableSleepRevivalModule = true;
    public static boolean enableCreatureArrivalVoice = true;
    public static double creatureArrivalVoiceVolume = 0.4;
    public static int housePatrolRadius = 64;
    public static int houseHardRadius = 128;
    public static Map<String, Double> previewScaleOverrides;
    public static double guiOpacity;
    public static boolean enableAutoBackups = true;
    public static int backupIntervalMinutes = 30;
    public static boolean preventBoundCreatureDeathDrops = true;
    public static int sleepReviveCooldownMinutes = 10;
    public static double sleepReviveChance = 0.7;
    public static boolean sleepReviveSpawnEntity = true;
    public static boolean enableContractAnimation = true;
    public static boolean enableContractCinematicCamera = true;
    public static boolean debugDiagnostics;
    public static boolean enableDiagnosticLogging;
    public static boolean enableDialogue;
    public static List<String> mountSummonDialogueLines;
    public static List<String> companionSummonDialogueLines;
    public static List<String> mountRescueDialogueLines;
    public static List<String> companionCombatRescueDialogueLines;
    public static List<String> storageDialogueLines;
    public static List<String> homeSummonDialogueLines;
    public static List<String> homeStorageDialogueLines;
    public static List<String> homeSetDialogueLines;
    public static List<String> homeClearDialogueLines;
    public static List<String> registeredDialogueLines;
    public static List<String> sleepReviveDialogueLines;
    public static List<String> sleepReviveStoredDialogueLines;
    public static List<String> blockedDialogueLines;
    public static List<String> busyDialogueLines;
    public static List<String> cooldownDialogueLines;
    public static List<String> noRegisteredDialogueLines;
    public static List<String> unavailableDialogueLines;
    public static List<String> alreadyRidingDialogueLines;
    public static List<String> occupiedDialogueLines;
    public static List<String> tooHighDialogueLines;
    public static List<String> voidMountUnsafeDialogueLines;
    public static List<String> voidCompanionUnsafeDialogueLines;
    public static List<String> voidFlyingMountDialogueLines;

    public static List<DialogueListOption> dialogueOptions() {
        return List.of(
                new DialogueListOption("config.find_me.mountSummonDialogueLines", MOUNT_SUMMON_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.companionSummonDialogueLines", COMPANION_SUMMON_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.mountRescueDialogueLines", MOUNT_RESCUE_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.companionCombatRescueDialogueLines", COMPANION_COMBAT_RESCUE_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.storageDialogueLines", STORAGE_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.homeSummonDialogueLines", HOME_SUMMON_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.homeStorageDialogueLines", HOME_STORAGE_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.homeSetDialogueLines", HOME_SET_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.homeClearDialogueLines", HOME_CLEAR_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.registeredDialogueLines", REGISTERED_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.sleepReviveDialogueLines", SLEEP_REVIVE_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.sleepReviveStoredDialogueLines", SLEEP_REVIVE_STORED_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.blockedDialogueLines", BLOCKED_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.busyDialogueLines", BUSY_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.cooldownDialogueLines", COOLDOWN_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.noRegisteredDialogueLines", NO_REGISTERED_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.unavailableDialogueLines", UNAVAILABLE_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.alreadyRidingDialogueLines", ALREADY_RIDING_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.occupiedDialogueLines", OCCUPIED_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.tooHighDialogueLines", TOO_HIGH_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.voidMountUnsafeDialogueLines", VOID_MOUNT_UNSAFE_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.voidCompanionUnsafeDialogueLines", VOID_COMPANION_UNSAFE_DIALOGUE_LINES),
                new DialogueListOption("config.find_me.voidFlyingMountDialogueLines", VOID_FLYING_MOUNT_DIALOGUE_LINES)
        );
    }

    public static List<String> dialogueValue(DialogueListOption option) {
        return dialogueLines(dialogueKey(option), option.value());
    }

    public static List<String> defaultDialogueValue(DialogueListOption option) {
        return Config.normalizeTextList((List)option.value().getDefault());
    }

    public static void setDialogueValue(DialogueListOption option, List<String> lines) {
        String key = dialogueKey(option);
        ArrayList<String> updated = new ArrayList<>((List<String>)(List<?>)DIALOGUE_OVERRIDES.get());
        updated.removeIf(entry -> key.equals(dialogueContext(entry)));
        List<String> normalized = normalizeTextList(lines);
        if (normalized.isEmpty()) {
            updated.add(key + "=");
        } else {
            normalized.forEach(line -> updated.add(key + "=" + line));
        }
        DIALOGUE_OVERRIDES.set(List.copyOf(updated));
        DIALOGUE_OVERRIDES.save();
        Config.syncFromSpec();
    }

    private static boolean isString(Object value) {
        return value instanceof String;
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) {
            return;
        }
        if (event.getConfig().getSpec() == SERVER_SPEC) {
            Config.syncServerFromSpec();
        } else if (event.getConfig().getSpec() == SPEC) {
            Config.syncFromSpec();
        }
    }

    public static void syncFromSpec() {
        previewScaleOverrides = Config.parseDoubleMap((List)PREVIEW_SCALE_OVERRIDES.get());
        guiOpacity = (Double)GUI_OPACITY.get();
        enableDialogue = (Boolean)ENABLE_DIALOGUE.get();
        mountSummonDialogueLines = dialogueLines("mount_summon", MOUNT_SUMMON_DIALOGUE_LINES);
        companionSummonDialogueLines = dialogueLines("companion_summon", COMPANION_SUMMON_DIALOGUE_LINES);
        mountRescueDialogueLines = dialogueLines("mount_rescue", MOUNT_RESCUE_DIALOGUE_LINES);
        companionCombatRescueDialogueLines = dialogueLines("companion_combat_rescue", COMPANION_COMBAT_RESCUE_DIALOGUE_LINES);
        storageDialogueLines = dialogueLines("storage", STORAGE_DIALOGUE_LINES);
        homeSummonDialogueLines = dialogueLines("home_summon", HOME_SUMMON_DIALOGUE_LINES);
        homeStorageDialogueLines = dialogueLines("home_storage", HOME_STORAGE_DIALOGUE_LINES);
        homeSetDialogueLines = dialogueLines("home_set", HOME_SET_DIALOGUE_LINES);
        homeClearDialogueLines = dialogueLines("home_clear", HOME_CLEAR_DIALOGUE_LINES);
        registeredDialogueLines = dialogueLines("registered", REGISTERED_DIALOGUE_LINES);
        sleepReviveDialogueLines = dialogueLines("sleep_revive", SLEEP_REVIVE_DIALOGUE_LINES);
        sleepReviveStoredDialogueLines = dialogueLines("sleep_revive_stored", SLEEP_REVIVE_STORED_DIALOGUE_LINES);
        blockedDialogueLines = dialogueLines("blocked", BLOCKED_DIALOGUE_LINES);
        busyDialogueLines = dialogueLines("busy", BUSY_DIALOGUE_LINES);
        cooldownDialogueLines = dialogueLines("cooldown", COOLDOWN_DIALOGUE_LINES);
        noRegisteredDialogueLines = dialogueLines("no_registered", NO_REGISTERED_DIALOGUE_LINES);
        unavailableDialogueLines = dialogueLines("unavailable", UNAVAILABLE_DIALOGUE_LINES);
        alreadyRidingDialogueLines = dialogueLines("already_riding", ALREADY_RIDING_DIALOGUE_LINES);
        occupiedDialogueLines = dialogueLines("occupied", OCCUPIED_DIALOGUE_LINES);
        tooHighDialogueLines = dialogueLines("too_high", TOO_HIGH_DIALOGUE_LINES);
        voidMountUnsafeDialogueLines = dialogueLines("void_mount_unsafe", VOID_MOUNT_UNSAFE_DIALOGUE_LINES);
        voidCompanionUnsafeDialogueLines = dialogueLines("void_companion_unsafe", VOID_COMPANION_UNSAFE_DIALOGUE_LINES);
        voidFlyingMountDialogueLines = dialogueLines("void_flying_mount", VOID_FLYING_MOUNT_DIALOGUE_LINES);
    }

    private static void syncServerFromSpec() {
        summonCooldownTicks = (Integer)SUMMON_COOLDOWN_TICKS.get();
        companionDeploymentLimit = (Integer)COMPANION_DEPLOYMENT_LIMIT.get();
        rescueMinFallDistance = (Integer)RESCUE_MIN_FALL_DISTANCE.get();
        rescueHoverBaseHeight = (Double)RESCUE_HOVER_BASE_HEIGHT.get();
        rescueHoverHeightRatio = (Double)RESCUE_HOVER_HEIGHT_RATIO.get();
        rescueHoverMaxHeight = (Double)RESCUE_HOVER_MAX_HEIGHT.get();
        enableCreatureArrivalVoice = (Boolean)ENABLE_CREATURE_ARRIVAL_VOICE.get();
        creatureArrivalVoiceVolume = (Double)CREATURE_ARRIVAL_VOICE_VOLUME.get();
        housePatrolRadius = (Integer)HOUSE_PATROL_RADIUS.get();
        houseHardRadius = Math.max(housePatrolRadius, (Integer)HOUSE_HARD_RADIUS.get());
        enableAutoBackups = (Boolean)ENABLE_AUTO_BACKUPS.get();
        backupIntervalMinutes = (Integer)BACKUP_INTERVAL_MINUTES.get();
        preventBoundCreatureDeathDrops = (Boolean)PREVENT_BOUND_CREATURE_DEATH_DROPS.get();
        enableContractAnimation = (Boolean)ENABLE_CONTRACT_ANIMATION.get();
        enableContractCinematicCamera = (Boolean)ENABLE_CONTRACT_CINEMATIC_CAMERA.get();
        enableDiagnosticLogging = (Boolean)ENABLE_DIAGNOSTIC_LOGGING.get();
        // Keep the legacy field as a compatibility alias; it no longer has an independent switch.
        debugDiagnostics = enableDiagnosticLogging;
        sleepReviveCooldownMinutes = (Integer)SLEEP_REVIVE_COOLDOWN_MINUTES.get();
        sleepReviveChance = (Double)SLEEP_REVIVE_CHANCE.get();
        sleepReviveSpawnEntity = (Boolean)SLEEP_REVIVE_SPAWN_ENTITY.get();
        enableRidingModule = (Boolean)ENABLE_RIDING_MODULE.get();
        enableCompanionModule = (Boolean)ENABLE_COMPANION_MODULE.get();
        enableManagementModule = (Boolean)ENABLE_MANAGEMENT_MODULE.get();
        enableHouseModule = (Boolean)ENABLE_HOUSE_MODULE.get();
        enableSleepRevivalModule = (Boolean)ENABLE_SLEEP_REVIVAL_MODULE.get();
    }

    public static boolean moduleConfigured(FindMeModule module) {
        return switch (module) {
            case RIDING -> enableRidingModule;
            case COMPANIONS -> enableCompanionModule;
            case MANAGEMENT -> enableManagementModule;
            case HOUSES -> enableHouseModule;
            case SLEEP_REVIVAL -> enableSleepRevivalModule;
        };
    }

    public static void setModuleConfigured(FindMeModule module, boolean enabled) {
        ForgeConfigSpec.BooleanValue value = switch (module) {
            case RIDING -> ENABLE_RIDING_MODULE;
            case COMPANIONS -> ENABLE_COMPANION_MODULE;
            case MANAGEMENT -> ENABLE_MANAGEMENT_MODULE;
            case HOUSES -> ENABLE_HOUSE_MODULE;
            case SLEEP_REVIVAL -> ENABLE_SLEEP_REVIVAL_MODULE;
        };
        value.set(enabled);
        value.save();
        syncServerFromSpec();
    }

    public static void setCompanionDeploymentLimit(int limit) {
        COMPANION_DEPLOYMENT_LIMIT.set(Math.max(1, Math.min(32, limit)));
        COMPANION_DEPLOYMENT_LIMIT.save();
        syncServerFromSpec();
    }

    public static void resetModulesToDefaults() {
        ENABLE_RIDING_MODULE.set(true);
        ENABLE_COMPANION_MODULE.set(true);
        ENABLE_MANAGEMENT_MODULE.set(true);
        ENABLE_HOUSE_MODULE.set(true);
        ENABLE_SLEEP_REVIVAL_MODULE.set(true);
        ENABLE_RIDING_MODULE.save();
        ENABLE_COMPANION_MODULE.save();
        ENABLE_MANAGEMENT_MODULE.save();
        ENABLE_HOUSE_MODULE.save();
        ENABLE_SLEEP_REVIVAL_MODULE.save();
        syncServerFromSpec();
    }

    public record DialogueListOption(String labelKey, ForgeConfigSpec.ConfigValue<List<? extends String>> value) {
    }

    private static String dialogueKey(DialogueListOption option) {
        String label = option.labelKey().substring("config.find_me.".length());
        String stem = label.endsWith("DialogueLines")
                ? label.substring(0, label.length() - "DialogueLines".length()) : label;
        return stem.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static List<String> dialogueLines(String key,
            ForgeConfigSpec.ConfigValue<List<? extends String>> defaults) {
        Map<String, List<String>> overrides = parseDialogueOverrides(DIALOGUE_OVERRIDES.get());
        return overrides.containsKey(key)
                ? overrides.get(key)
                : normalizeTextList(defaults.getDefault());
    }

    static Map<String, List<String>> parseDialogueOverrides(List<? extends String> entries) {
        LinkedHashMap<String, List<String>> parsed = new LinkedHashMap<>();
        for (String entry : entries) {
            String context = dialogueContext(entry);
            if (context == null) {
                continue;
            }
            List<String> lines = parsed.computeIfAbsent(context, ignored -> new ArrayList<>());
            String value = entry.substring(entry.indexOf('=') + 1).trim();
            if (!value.isBlank()) {
                lines.add(value);
            }
        }
        LinkedHashMap<String, List<String>> immutable = new LinkedHashMap<>();
        parsed.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        return Map.copyOf(immutable);
    }

    private static String dialogueContext(String entry) {
        if (entry == null) {
            return null;
        }
        int separator = entry.indexOf('=');
        if (separator <= 0) {
            return null;
        }
        String context = entry.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        return context.isBlank() ? null : context;
    }

    private static List<String> normalizeList(List<? extends String> entries) {
        return entries.stream().filter(value -> value != null && !value.isBlank()).map(value -> value.trim().toLowerCase(Locale.ROOT)).toList();
    }

    private static List<String> normalizeTextList(List<? extends String> entries) {
        return entries.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
    }

    private static Map<String, Double> parseDoubleMap(List<? extends String> entries) {
        HashMap<String, Double> parsed = new HashMap<String, Double>();
        for (String string : entries) {
            String[] parts = Config.splitOverride(string);
            if (parts == null) continue;
            try {
                parsed.put(parts[0], Double.parseDouble(parts[1]));
            }
            catch (NumberFormatException numberFormatException) {}
        }
        return Map.copyOf(parsed);
    }

    private static String[] splitOverride(String entry) {
        String[] stringArray;
        if (entry == null) {
            return null;
        }
        int separator = entry.indexOf(61);
        if (separator <= 0 || separator >= entry.length() - 1) {
            return null;
        }
        String key = entry.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        String value = entry.substring(separator + 1).trim();
        if (key.isBlank() || value.isBlank()) {
            stringArray = null;
        } else {
            String[] stringArray2 = new String[2];
            stringArray2[0] = key;
            stringArray = stringArray2;
            stringArray2[1] = value;
        }
        return stringArray;
    }

}
