package com.kuzhi.findme.client;

import com.kuzhi.findme.network.CompanionDialoguePacket;
import net.minecraft.client.resources.language.I18n;

public final class ClientCompanionDialogueState {
    private static String call = "";
    private static String reply = "";
    private static int age;
    private static int durationTicks;
    private static int replyDelayTicks;
    private static boolean urgent;

    private ClientCompanionDialogueState() {
    }

    public static void start(CompanionDialoguePacket packet) {
        call = resolve(packet.call(), packet.playerName(), packet.creatureName(), packet.seconds());
        reply = resolve(packet.reply(), packet.playerName(), packet.creatureName(), packet.seconds());
        durationTicks = Math.max(1, packet.durationTicks());
        replyDelayTicks = Math.max(0, packet.replyDelayTicks());
        age = 0;
        urgent = packet.urgent();
    }

    static void tick() {
        if (!active()) {
            return;
        }
        age++;
        if (age >= durationTicks) {
            clear();
        }
    }

    static boolean active() {
        return durationTicks > 0 && age < durationTicks && (!call.isBlank() || !reply.isBlank());
    }

    static String call() {
        return call;
    }

    static String reply() {
        return reply;
    }

    static boolean showingReply() {
        return !reply.isBlank() && age >= replyDelayTicks;
    }

    static float alpha(float partialTick) {
        if (!active()) {
            return 0.0f;
        }
        return lineAlpha(age + partialTick, durationTicks);
    }

    static float replyAlpha(float partialTick) {
        if (!active() || reply.isBlank()) {
            return 0.0f;
        }
        float lineAge = age + partialTick - replyDelayTicks;
        if (lineAge <= 0.0f) {
            return 0.0f;
        }
        int remainingDuration = Math.max(1, durationTicks - replyDelayTicks);
        return lineAlpha(lineAge, remainingDuration);
    }

    static boolean urgent() {
        return urgent;
    }

    static void clear() {
        call = "";
        reply = "";
        age = 0;
        durationTicks = 0;
        replyDelayTicks = 0;
        urgent = false;
    }

    private static String resolve(String value, String playerName, String creatureName, String seconds) {
        String resolved = value == null ? "" : value.trim();
        if (!resolved.isBlank() && I18n.exists(resolved)) {
            resolved = I18n.get(resolved);
        }
        return resolved
                .replace("{player}", resolveArgument(playerName))
                .replace("{name}", resolveArgument(creatureName))
                .replace("{seconds}", seconds == null ? "" : seconds)
                .trim();
    }

    private static String resolveArgument(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return I18n.exists(trimmed) ? I18n.get(trimmed) : trimmed;
    }

    private static float lineAlpha(float lineAge, int lineDuration) {
        float progress = Math.min(1.0f, lineAge / (float)lineDuration);
        float in = Math.min(1.0f, lineAge / 6.0f);
        float out = 1.0f - Math.max(0.0f, (progress - 0.78f) / 0.22f);
        return Math.max(0.0f, Math.min(in, out));
    }
}
