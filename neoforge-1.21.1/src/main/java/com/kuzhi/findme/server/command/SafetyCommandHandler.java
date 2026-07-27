package com.kuzhi.findme.server.command;

import com.kuzhi.findme.server.safety.CompanionIntegrityService;
import com.kuzhi.findme.server.safety.CompanionSafetyService;
import net.minecraft.server.level.ServerPlayer;

public final class SafetyCommandHandler {
    private SafetyCommandHandler() {
    }

    public static CompanionIntegrityService.Report diagnose(ServerPlayer player, boolean verbose) {
        return CompanionIntegrityService.diagnose(player, verbose);
    }

    public static int listBackups(ServerPlayer player) {
        return CompanionSafetyService.listBackups(player);
    }

    public static int createBackup(ServerPlayer player) {
        return CompanionSafetyService.createBackup(player, "manual");
    }

    public static int previewBackupRestore(ServerPlayer player, int index) {
        return CompanionSafetyService.previewBackupRestore(player, index);
    }

    public static int restoreBackup(ServerPlayer player, int index) {
        return CompanionSafetyService.restoreBackup(player, index);
    }

    public static int restoreBackup(ServerPlayer player, int index, long expectedSavedAt) {
        return CompanionSafetyService.restoreBackup(player, index, expectedSavedAt);
    }
}
