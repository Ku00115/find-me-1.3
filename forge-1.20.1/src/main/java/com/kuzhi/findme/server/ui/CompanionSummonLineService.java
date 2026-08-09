package com.kuzhi.findme.server.ui;

import com.kuzhi.findme.server.ui.CompanionMessageService;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.FindMeTextMode;
import com.kuzhi.findme.network.CompanionDialoguePacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.lifecycle.CompanionArrivalSequenceService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class CompanionSummonLineService {
    private static final String SEPARATOR = "|";
    private static final int DIALOGUE_TICKS = 46;
    private static final int DIRECT_REPLY_DELAY_TICKS = 14;
    private static final Map<UUID, PendingDialogue> PENDING = new HashMap<>();

    private CompanionSummonLineService() {
    }

    public static void showOrSchedule(ServerPlayer player, LivingEntity living, CompanionKind kind, boolean rescue, boolean combatRescue, boolean arrivalStarted) {
        if (player == null || living == null || kind == null) {
            return;
        }
        FindMeDebugLogger.info("dialogue-timing",
                "stage=TRIGGER player={} entity={} kind={} rescue={} combatRescue={} arrivalStarted={} gameTime={}",
                player.getUUID(), FindMeDebugLogger.entity(living), kind, rescue, combatRescue, arrivalStarted,
                player.level().getGameTime());
        if (!immersive(player)) {
            PENDING.remove(living.getUUID());
            if (practical(player)) showSummonFallback(player, living, rescue || combatRescue);
            return;
        }
        Dialogue dialogue = choose(player, living.getDisplayName().getString(), templates(player, living, kind, rescue, combatRescue));
        if (dialogue == null) {
            return;
        }
        boolean urgent = rescue || combatRescue;
        if (arrivalStarted && CompanionArrivalSequenceService.isPending(living)) {
            FindMeDebugLogger.info("dialogue-timing",
                    "stage=SCHEDULE_CALL player={} entity={} gameTime={}",
                    player.getUUID(), FindMeDebugLogger.entity(living), player.level().getGameTime());
            send(player, dialogue.call(), "", dialogue.context(), urgent, 0);
            PENDING.put(living.getUUID(), new PendingDialogue(player.getUUID(), dialogue.call(), dialogue.reply(), dialogue.context(), urgent));
            return;
        }
        send(player, dialogue.call(), dialogue.reply(), dialogue.context(), urgent, DIRECT_REPLY_DELAY_TICKS);
    }

    public static void showScheduled(LivingEntity living) {
        if (living == null) {
            return;
        }
        PendingDialogue pending = PENDING.remove(living.getUUID());
        if (!immersive(living == null || living.getServer() == null ? null : living.getServer().getPlayerList().getPlayer(pending == null ? null : pending.playerUuid())) || pending == null || living.getServer() == null) {
            return;
        }
        ServerPlayer player = living.getServer().getPlayerList().getPlayer(pending.playerUuid());
        if (player != null) {
            send(player, pending.call(), pending.reply(), pending.context(), pending.urgent(), 0);
        }
    }

    public static void cancelScheduled(LivingEntity living) {
        if (living != null) {
            PENDING.remove(living.getUUID());
        }
    }

    public static void cancelScheduled(UUID uuid) {
        if (uuid != null) {
            PENDING.remove(uuid);
        }
    }

    public static void showBusy(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
        if (!immersive(player)) {
            if (practical(player)) showFallback(player, "message.find_me.busy", ChatFormatting.YELLOW, creatureMessageArg(player, data, uuid, kind));
            return;
        }
        String creatureName = creatureName(player, data, uuid, kind);
        showImmediatePair(player, creatureName, Config.busyDialogueLines, false);
    }

    public static void showCooldown(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, long seconds) {
        if (!immersive(player)) {
            if (practical(player)) showFallback(player, "message.find_me.cooldown", ChatFormatting.YELLOW, Long.toString(Math.max(0L, seconds)));
            return;
        }
        String creatureName = activeCreatureName(player, data, kind);
        showImmediatePair(player, creatureName, Config.cooldownDialogueLines, false, Long.toString(Math.max(0L, seconds)));
    }

    public static void showNoRegistered(ServerPlayer player, CompanionKind kind) {
        if (!immersive(player)) {
            if (practical(player)) showFallback(player, "message.find_me.no_registered", ChatFormatting.YELLOW, CompanionMessageService.label(kind));
            return;
        }
        showImmediatePair(player, kindName(kind), Config.noRegisteredDialogueLines, false);
    }

    public static void showUnavailable(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        if (!immersive(player)) {
            if (practical(player)) showFallback(player, "message.find_me.unavailable", ChatFormatting.RED, activeCreatureMessageArg(player, data, kind));
            return;
        }
        showImmediatePair(player, activeCreatureName(player, data, kind), Config.unavailableDialogueLines, true);
    }

    public static void showAlreadyRiding(ServerPlayer player, LivingEntity living) {
        if (!immersive(player)) {
            if (practical(player)) showFallback(player, "message.find_me.already_riding", ChatFormatting.YELLOW);
            return;
        }
        showImmediatePair(player, living == null ? kindName(CompanionKind.MOUNT) : living.getDisplayName().getString(), Config.alreadyRidingDialogueLines, false);
    }

    public static void showOccupied(ServerPlayer player, LivingEntity living, CompanionKind kind) {
        if (!immersive(player)) {
            if (practical(player)) showFallback(player, "message.find_me.occupied", ChatFormatting.YELLOW, living == null ? CompanionMessageService.label(kind) : living.getDisplayName());
            return;
        }
        showImmediatePair(player, living == null ? kindName(kind) : living.getDisplayName().getString(), Config.occupiedDialogueLines, true);
    }

    public static void showTooHigh(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living) {
        if (!immersive(player)) {
            if (practical(player)) showFallback(player, "message.find_me.companion_too_high", ChatFormatting.YELLOW);
            return;
        }
        String creatureName = living == null ? activeCreatureName(player, data, kind) : living.getDisplayName().getString();
        showImmediatePair(player, creatureName, Config.tooHighDialogueLines, true);
    }

    public static void showNeedOpenSpace(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living) {
        if (!immersive(player)) {
            if (practical(player)) showFallback(player, "message.find_me.need_open_space", ChatFormatting.YELLOW);
            return;
        }
        String creatureName = living == null ? activeCreatureName(player, data, kind) : living.getDisplayName().getString();
        showImmediatePair(player, creatureName, Config.blockedDialogueLines, true);
    }

    public static void showVoidUnsafe(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        if (!immersive(player)) {
            String key = kind == CompanionKind.MOUNT ? "message.find_me.void_mount_unsafe" : "message.find_me.void_companion_unsafe";
            if (practical(player)) showFallback(player, key, ChatFormatting.YELLOW);
            return;
        }
        List<String> lines = kind == CompanionKind.MOUNT ? Config.voidMountUnsafeDialogueLines : Config.voidCompanionUnsafeDialogueLines;
        showImmediatePair(player, activeCreatureName(player, data, kind), lines, true);
    }

    public static void showVoidFlyingMount(ServerPlayer player, LivingEntity living) {
        if (!immersive(player)) {
            if (practical(player) && living != null) {
                showSummonFallback(player, living, false);
            }
            return;
        }
        String creatureName = living == null ? kindName(CompanionKind.MOUNT) : living.getDisplayName().getString();
        showImmediatePair(player, creatureName, Config.voidFlyingMountDialogueLines, true);
    }

    public static void showBlocked(ServerPlayer player, LivingEntity living) {
        if (player == null || living == null) {
            return;
        }
        if (!immersive(player)) {
            if (practical(player)) showFallback(player, "message.find_me.rescue_space_too_small", ChatFormatting.YELLOW, living.getDisplayName());
            return;
        }
        showImmediatePair(player, living.getDisplayName().getString(), Config.blockedDialogueLines, true);
    }

    public static void showStorage(ServerPlayer player, LivingEntity living) {
        if (player == null || living == null) {
            return;
        }
        if (!immersive(player)) {
            String key = hasHome(player, living) ? "message.find_me.returned_home" : "message.find_me.collected";
            if (practical(player)) showFallback(player, key, ChatFormatting.GRAY, living.getDisplayName());
            return;
        }
        List<String> templates = hasHome(player, living) ? Config.homeStorageDialogueLines : Config.storageDialogueLines;
        showImmediatePair(player, living.getDisplayName().getString(), templates, false);
    }

    public static void showHomeSet(ServerPlayer player, String creatureName) {
        if (!immersive(player)) {
            if (practical(player)) showFallback(player, "message.find_me.home_set", ChatFormatting.GREEN, Component.literal(creatureName == null ? "" : creatureName));
            return;
        }
        showImmediatePair(player, creatureName, Config.homeSetDialogueLines, false);
    }

    public static void showHomeCleared(ServerPlayer player, String creatureName) {
        if (!immersive(player)) {
            if (practical(player)) showFallback(player, "message.find_me.home_cleared", ChatFormatting.GREEN, Component.literal(creatureName == null ? "" : creatureName));
            return;
        }
        showImmediatePair(player, creatureName, Config.homeClearDialogueLines, false);
    }

    public static void showRegistered(ServerPlayer player, LivingEntity living) {
        if (player == null || living == null) {
            return;
        }
        if (!immersive(player)) {
            if (practical(player)) showFallback(player, "message.find_me.registered_creature", ChatFormatting.GREEN, living.getDisplayName());
            return;
        }
        showImmediatePair(player, living.getDisplayName().getString(), Config.registeredDialogueLines, false);
    }

    public static void showSleepRevived(ServerPlayer player, String creatureName, boolean spawned) {
        if (!immersive(player)) {
            if (practical(player)) showFallback(player, spawned ? "message.find_me.sleep_revived" : "message.find_me.sleep_revived_stored", ChatFormatting.LIGHT_PURPLE, Component.literal(creatureName == null ? "" : creatureName));
            return;
        }
        showImmediatePair(player, creatureName, spawned ? Config.sleepReviveDialogueLines : Config.sleepReviveStoredDialogueLines, false);
    }

    private static void showSummonFallback(ServerPlayer player, LivingEntity living, boolean rescue) {
        showFallback(player, rescue ? "message.find_me.rescue_summoned" : "message.find_me.summoned", ChatFormatting.AQUA, living.getDisplayName());
    }

    private static void showFallback(ServerPlayer player, String key, ChatFormatting color, Object... args) {
        if (player != null && key != null) {
            CompanionMessageService.tell(player, key, color, args);
        }
    }

    private static void showImmediatePair(ServerPlayer player, String creatureName, List<String> templates, boolean urgent) {
        showImmediatePair(player, creatureName, templates, urgent, "");
    }

    private static void showImmediatePair(ServerPlayer player, String creatureName, List<String> templates, boolean urgent, String seconds) {
        Dialogue dialogue = choose(player, creatureName, templates);
        if (dialogue != null) {
            int replyDelay = dialogue.call().isBlank() ? 0 : DIRECT_REPLY_DELAY_TICKS;
            DialogueContext context = seconds == null || seconds.isBlank() ? dialogue.context() : dialogue.context().withSeconds(seconds);
            send(player, dialogue.call(), dialogue.reply(), context, urgent, replyDelay);
        }
    }

    private static Dialogue choose(ServerPlayer player, String creatureName, List<String> templates) {
        if (!immersive(player) || player == null || templates == null || templates.isEmpty()) {
            return null;
        }
        String template = templates.get(player.getRandom().nextInt(templates.size()));
        Dialogue dialogue = Dialogue.parse(template, defaultCall(), player.getGameProfile().getName(), creatureName);
        return dialogue.call().isBlank() && dialogue.reply().isBlank() ? null : dialogue;
    }

    private static void send(ServerPlayer player, String call, String reply, DialogueContext context, boolean urgent, int replyDelayTicks) {
        if (!immersive(player)) {
            return;
        }
        FindMeDebugLogger.info("dialogue-timing",
                "stage=PACKET player={} callBlank={} replyBlank={} replyDelayTicks={} urgent={} gameTime={}",
                player.getUUID(), call == null || call.isBlank(), reply == null || reply.isBlank(),
                replyDelayTicks, urgent, player.level().getGameTime());
        ModNetwork.sendToPlayer(player, new CompanionDialoguePacket(call, reply, context.playerName(), context.creatureName(), context.seconds(), DIALOGUE_TICKS, replyDelayTicks, urgent));
    }

    private static List<String> templates(ServerPlayer player, LivingEntity living, CompanionKind kind, boolean rescue, boolean combatRescue) {
        if (!rescue && !combatRescue && hasHome(player, living)) {
            return Config.homeSummonDialogueLines;
        }
        if (kind == CompanionKind.MOUNT) {
            return rescue ? Config.mountRescueDialogueLines : Config.mountSummonDialogueLines;
        }
        if (combatRescue) {
            return Config.companionCombatRescueDialogueLines;
        }
        return Config.companionSummonDialogueLines;
    }

    private static boolean hasHome(ServerPlayer player, LivingEntity living) {
        if (player == null || living == null) {
            return false;
        }
        return CompanionDataService.data(player).homePosition(living.getUUID()).isPresent();
    }

    private static FindMeTextMode textMode(ServerPlayer player) {
        return player == null ? FindMeTextMode.OFF : CompanionDataService.data(player).uiSettings().textMode();
    }

    private static boolean practical(ServerPlayer player) {
        return textMode(player) == FindMeTextMode.PRACTICAL;
    }

    private static boolean immersive(ServerPlayer player) {
        return textMode(player) == FindMeTextMode.IMMERSIVE;
    }

    private static String defaultCall() {
        return "dialogue.find_me.default_call";
    }

    private static String creatureName(ServerPlayer player, PlayerCompanionData data, UUID uuid, CompanionKind kind) {
        if (data != null && uuid != null && data.storedEntity(uuid).isPresent()) {
            return CompanionEntitySnapshots.storedEntityName(data.storedEntity(uuid).get(), uuid);
        }
        if (player != null && player.getServer() != null && uuid != null && data != null) {
            if (CompanionEntityLookup.findLoadedEntity(player.getServer(), data, uuid).orElse(null) instanceof LivingEntity found) {
                return found.getDisplayName().getString();
            }
        }
        return kind == CompanionKind.MOUNT ? "dialogue.find_me.kind.mount" : "dialogue.find_me.kind.companion";
    }

    private static Object creatureMessageArg(ServerPlayer player, PlayerCompanionData data, UUID uuid, CompanionKind kind) {
        String name = creatureName(player, data, uuid, kind);
        return isDialogueKindName(name) ? CompanionMessageService.label(kind) : Component.literal(name);
    }

    private static String activeCreatureName(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        if (data == null) {
            return kindName(kind);
        }
        return data.active(kind).map(uuid -> creatureName(player, data, uuid, kind)).orElseGet(() -> kindName(kind));
    }

    private static Object activeCreatureMessageArg(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        String name = activeCreatureName(player, data, kind);
        return isDialogueKindName(name) ? CompanionMessageService.label(kind) : Component.literal(name);
    }

    private static String kindName(CompanionKind kind) {
        return kind == CompanionKind.MOUNT ? "dialogue.find_me.kind.mount" : "dialogue.find_me.kind.companion";
    }

    private static boolean isDialogueKindName(String name) {
        return "dialogue.find_me.kind.mount".equals(name) || "dialogue.find_me.kind.companion".equals(name);
    }

    private record PendingDialogue(UUID playerUuid, String call, String reply, DialogueContext context, boolean urgent) {
    }

    private record Dialogue(String call, String reply, DialogueContext context) {
        static Dialogue parse(String template, String fallbackCall, String playerName, String creatureName) {
            String safe = template == null ? "" : template;
            int separator = safe.indexOf(SEPARATOR);
            String call = separator >= 0 ? safe.substring(0, separator) : fallbackCall;
            String reply = separator >= 0 ? safe.substring(separator + SEPARATOR.length()) : safe;
            return new Dialogue(call.trim(), reply.trim(), new DialogueContext(playerName, creatureName, ""));
        }
    }

    private record DialogueContext(String playerName, String creatureName, String seconds) {
        DialogueContext withSeconds(String value) {
            return new DialogueContext(playerName, creatureName, value == null ? "" : value);
        }
    }
}

