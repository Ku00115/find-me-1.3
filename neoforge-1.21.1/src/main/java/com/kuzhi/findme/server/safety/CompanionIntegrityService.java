package com.kuzhi.findme.server.safety;

import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.vehicle.SableVehicleCompatibility;
import com.kuzhi.findme.api.FindMeApi;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.common.DoctorArea;
import com.kuzhi.findme.common.DoctorIssueCode;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.compat.cobblemon.CobblemonCompat;
import com.kuzhi.findme.server.module.FindMeModuleService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class CompanionIntegrityService {
    private CompanionIntegrityService() {
    }

    public static Report diagnose(ServerPlayer player, boolean verbose) {
        PlayerCompanionData data = CompanionDataService.data(player);
        Report report = new Report(player.getGameProfile().getName(), player.getUUID(), verbose);
        Map<UUID, String> owners = collectKnownRecords(data, report);
        checkModules(player, data, report);
        checkTeams(data, owners, report);
        checkWheels(data, owners, report);
        checkDeployment(player.getServer(), data, owners, report);
        checkStoredSnapshots(data, owners, report);
        checkArchiveSnapshots(data, report);
        checkOrphanMetadata(data, owners, report);
        checkHomes(data, owners, report);
        checkOperationLocks(player.getServer(), player.getUUID(), owners, report);
        checkLoadedEntities(player.getServer(), data, owners, report);
        FindMeDebugLogger.info("doctor", "player={} issues={} worst={}", player.getUUID(), report.issues().size(), report.worst());
        return report;
    }

    public static void checkOnLogin(ServerPlayer player) {
        Report report = diagnose(player, false);
        if (!report.hasIssues()) {
            return;
        }
        com.kuzhi.findme.FindMeMod.LOGGER.warn("FindMe integrity warning on login: player={} uuid={} worst={} issues={}",
                player.getGameProfile().getName(), player.getUUID(), report.worst(), report.issueCount());
        int limit = Math.min(report.issues().size(), 5);
        for (int i = 0; i < limit; i++) {
            com.kuzhi.findme.FindMeMod.LOGGER.warn("FindMe integrity issue: {}", report.issues().get(i).line());
        }
        if (limit < report.issues().size()) {
            com.kuzhi.findme.FindMeMod.LOGGER.warn("FindMe integrity issue: ... {} more issue(s). Open management diagnostics for player {}",
                    report.issues().size() - limit, player.getGameProfile().getName());
        }
    }

    private static Map<UUID, String> collectKnownRecords(PlayerCompanionData data, Report report) {
        Map<UUID, String> owners = new HashMap<>();
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : data.list(kind)) {
                addOwner(owners, report, uuid, kind.name().toLowerCase());
            }
        }
        for (UUID uuid : data.vehicleList()) {
            addOwner(owners, report, uuid, "vehicle");
        }
        for (UUID uuid : data.deadList()) {
            String previous = owners.get(uuid);
            if (previous != null) {
                report.add(Severity.CONFLICT, "record", uuid, DoctorIssueCode.DEAD_RECORD_DUPLICATE, previous);
            }
            addOwner(owners, report, uuid, "dead");
        }
        if (report.verbose()) {
            report.add(Severity.OK, "record", null, DoctorIssueCode.KNOWN_RECORDS, owners.size());
        }
        return owners;
    }

    private static void checkModules(ServerPlayer player, PlayerCompanionData data, Report report) {
        int ridingActive = data.deployedList(CompanionKind.MOUNT).size()
                + (data.deployedVehicle().isPresent() ? 1 : 0);
        if (!FindMeModuleService.enabled(FindMeModule.RIDING)) {
            ridingActive += CobblemonCompat.deployedCount(player);
            addModuleRuntimeIssue(report, FindMeModule.RIDING, ridingActive);
        }
        if (!FindMeModuleService.enabled(FindMeModule.COMPANIONS)) {
            addModuleRuntimeIssue(report, FindMeModule.COMPANIONS,
                    data.deployedList(CompanionKind.COMPANION).size());
        }
        if (!FindMeModuleService.enabled(FindMeModule.HOUSES)) {
            int activeResidents = 0;
            for (CompanionKind kind : CompanionKind.values()) {
                for (UUID uuid : data.list(kind)) {
                    if (data.lifecycleState(uuid) == CompanionLifecycleState.HOME_ACTIVE) {
                        activeResidents++;
                    }
                }
            }
            addModuleRuntimeIssue(report, FindMeModule.HOUSES, activeResidents);
        }
        if (FindMeModuleService.enabled(FindMeModule.RIDING)
                && !FindMeModuleService.enabled(FindMeModule.COBBLEMON_INTEGRATION)) {
            addModuleRuntimeIssue(report, FindMeModule.COBBLEMON_INTEGRATION,
                    CobblemonCompat.deployedCount(player));
        }
        if (FindMeModuleService.enabled(FindMeModule.RIDING)
                && !FindMeModuleService.enabled(FindMeModule.SABLE_INTEGRATION)) {
            int activeSable = data.deployedVehicle()
                    .filter(uuid -> SableVehicleCompatibility.isStored(data, uuid)
                            || SableVehicleCompatibility.find(player, uuid).isPresent())
                    .isPresent() ? 1 : 0;
            addModuleRuntimeIssue(report, FindMeModule.SABLE_INTEGRATION, activeSable);
        }
        if (report.verbose()) {
            for (FindMeModule module : FindMeModule.values()) {
                report.add(Severity.OK, "deployment", null, DoctorIssueCode.MODULE_STATE,
                        module.id(), com.kuzhi.findme.Config.moduleConfigured(module),
                        FindMeModuleService.enabled(module),
                        (FindMeModuleService.availableMask() & module.bit()) != 0L);
            }
        }
    }

    private static void addModuleRuntimeIssue(Report report, FindMeModule module, int activeCount) {
        if (activeCount > 0) {
            report.add(Severity.CONFLICT, "deployment", null, DoctorIssueCode.MODULE_RUNTIME_ACTIVE,
                    module.id(), activeCount);
        }
    }

    private static void addOwner(Map<UUID, String> owners, Report report, UUID uuid, String owner) {
        if (uuid == null) {
            report.add(Severity.CRITICAL, "record", null, DoctorIssueCode.NULL_UUID, owner);
            return;
        }
        String previous = owners.putIfAbsent(uuid, owner);
        if (previous != null && !previous.equals(owner)) {
            report.add(Severity.CONFLICT, "record", uuid, DoctorIssueCode.UUID_MULTIPLE_CATEGORIES, previous, owner);
        }
    }

    private static void checkTeams(PlayerCompanionData data, Map<UUID, String> owners, Report report) {
        Map<UUID, String> seen = new HashMap<>();
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            for (int teamIndex = 0; teamIndex < data.teamCount(target); teamIndex++) {
                for (UUID uuid : data.team(target, teamIndex)) {
                    String slot = target.name().toLowerCase() + "[" + (teamIndex + 1) + "]";
                    if (!owners.containsKey(uuid)) {
                        report.add(Severity.CONFLICT, "team", uuid, DoctorIssueCode.TEAM_MISSING_RECORD, slot);
                    }
                    if (!data.allowedInTeam(target, uuid)) {
                        report.add(Severity.CONFLICT, "team", uuid, DoctorIssueCode.TEAM_WRONG_CATEGORY, slot);
                    }
                    String previous = seen.putIfAbsent(uuid, slot);
                    if (previous != null) {
                        report.add(Severity.CONFLICT, "team", uuid, DoctorIssueCode.TEAM_DUPLICATE, previous, slot);
                    }
                }
            }
        }
    }

    private static void checkWheels(PlayerCompanionData data, Map<UUID, String> owners, Report report) {
        for (CompanionKind kind : CompanionKind.values()) {
            Set<UUID> seen = new HashSet<>();
            for (UUID uuid : data.wheelOrder(kind)) {
                if (!data.contains(kind, uuid)) {
                    report.add(Severity.CONFLICT, "wheel", uuid, DoctorIssueCode.WHEEL_MISSING_RECORD, kind.name());
                }
                if (!owners.containsKey(uuid)) {
                    report.add(Severity.CONFLICT, "wheel", uuid, DoctorIssueCode.WHEEL_UNKNOWN_UUID);
                }
                if (!seen.add(uuid)) {
                    report.add(Severity.CONFLICT, "wheel", uuid, DoctorIssueCode.WHEEL_DUPLICATE, kind.name());
                }
            }
        }
        Set<UUID> seenVehicles = new HashSet<>();
        for (UUID uuid : data.vehicleWheelOrder()) {
            if (!data.containsVehicle(uuid)) {
                report.add(Severity.CONFLICT, "wheel", uuid, DoctorIssueCode.VEHICLE_WHEEL_MISSING);
            }
            if (!seenVehicles.add(uuid)) {
                report.add(Severity.CONFLICT, "wheel", uuid, DoctorIssueCode.VEHICLE_WHEEL_DUPLICATE);
            }
        }
    }

    private static void checkDeployment(MinecraftServer server, PlayerCompanionData data, Map<UUID, String> owners, Report report) {
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : data.deployedList(kind)) {
                if (!data.contains(kind, uuid)) {
                    report.add(Severity.CONFLICT, "deployment", uuid, DoctorIssueCode.DEPLOYMENT_MISSING_RECORD, kind.name());
                    continue;
                }
                boolean stored = data.storedEntity(uuid).isPresent();
                boolean loaded = server != null && CompanionEntityLookup.findLoadedEntity(server, data, uuid).isPresent();
                if (stored && loaded) {
                    report.add(Severity.CONFLICT, "deployment", uuid, DoctorIssueCode.STORED_AND_LOADED);
                } else if (!loaded && !stored) {
                    report.add(Severity.UNKNOWN, "deployment", uuid, DoctorIssueCode.DEPLOYED_UNRESOLVED);
                } else if (stored) {
                    report.add(Severity.WARNING, "deployment", uuid, DoctorIssueCode.DEPLOYED_STORED_ONLY);
                }
            }
        }
        data.deployedVehicle().ifPresent(uuid -> {
            if (!data.containsVehicle(uuid)) {
                report.add(Severity.CONFLICT, "deployment", uuid, DoctorIssueCode.DEPLOYED_VEHICLE_MISSING);
            }
            boolean stored = data.storedEntity(uuid).isPresent();
            boolean loaded = server != null && CompanionEntityLookup.findLoadedEntity(server, data, uuid).isPresent();
            if (stored && loaded) {
                report.add(Severity.CONFLICT, "deployment", uuid, DoctorIssueCode.VEHICLE_STORED_AND_LOADED);
            } else if (!loaded && !stored) {
                report.add(Severity.UNKNOWN, "deployment", uuid, DoctorIssueCode.VEHICLE_DEPLOYED_UNRESOLVED);
            }
        });
        if (report.verbose()) {
            report.add(Severity.OK, "deployment", null, DoctorIssueCode.DEPLOYABLE_RECORDS, owners.size());
        }
    }

    private static void checkStoredSnapshots(PlayerCompanionData data, Map<UUID, String> owners, Report report) {
        for (UUID uuid : owners.keySet()) {
            data.storedEntity(uuid).ifPresent(tag -> checkStoredTag(uuid, tag, report));
        }
        for (UUID uuid : data.storedEntityIds()) {
            if (!owners.containsKey(uuid)) {
                report.add(Severity.WARNING, "snapshot", uuid, DoctorIssueCode.ORPHAN_SNAPSHOT);
                data.rawStoredEntityForDiagnostics(uuid).ifPresent(tag -> checkStoredTag(uuid, tag, report));
            }
        }
    }

    private static void checkStoredTag(UUID uuid, CompoundTag tag, Report report) {
        if (tag == null || tag.isEmpty()) {
            report.add(Severity.CRITICAL, "snapshot", uuid, DoctorIssueCode.SNAPSHOT_EMPTY);
            return;
        }
        if (!tag.contains("id")) {
            report.add(Severity.CRITICAL, "snapshot", uuid, DoctorIssueCode.SNAPSHOT_MISSING_TYPE);
        }
        if (tag.hasUUID("UUID") && uuid != null && !uuid.equals(tag.getUUID("UUID"))) {
            report.add(Severity.CRITICAL, "snapshot", uuid, DoctorIssueCode.SNAPSHOT_UUID_MISMATCH, tag.getUUID("UUID"));
        }
    }

    private static void checkHomes(PlayerCompanionData data, Map<UUID, String> owners, Report report) {
        for (UUID uuid : owners.keySet()) {
            if (data.homeNestBlock(uuid).isPresent() && !data.contains(uuid)) {
                report.add(Severity.WARNING, "home", uuid, DoctorIssueCode.HOME_ORPHAN);
            }
        }
    }

    private static void checkArchiveSnapshots(PlayerCompanionData data, Report report) {
        int backupIndex = 0;
        for (PlayerCompanionData.BackupEntry backup : data.backupList()) {
            if (!data.backupStateMeaningful(backup)) {
                report.add(Severity.WARNING, "backup", null, DoctorIssueCode.BACKUP_EMPTY, backupIndex);
            } else if (data.backupChecksumKnown(backup) && !data.backupChecksumMatches(backup)) {
                report.add(Severity.CRITICAL, "backup", null, DoctorIssueCode.BACKUP_CHECKSUM_MISMATCH, backupIndex);
            }
            backupIndex++;
        }
        int vaultIndex = 0;
        for (PlayerCompanionData.VaultEntry vault : data.vaultList()) {
            if (vault.uuid() == null) {
                report.add(Severity.CRITICAL, "vault", null, DoctorIssueCode.VAULT_NULL_UUID, vaultIndex);
            }
            CompoundTag tag = vault.entityTag();
            if (tag == null || tag.isEmpty()) {
                report.add(Severity.CRITICAL, "vault", vault.uuid(), DoctorIssueCode.VAULT_EMPTY, vaultIndex);
            } else if (!tag.contains("id") && !SableVehicleCompatibility.isStoredSable(tag)) {
                report.add(Severity.CRITICAL, "vault", vault.uuid(), DoctorIssueCode.VAULT_MISSING_TYPE, vaultIndex);
            } else if (vault.uuid() != null && tag.hasUUID("UUID") && !vault.uuid().equals(tag.getUUID("UUID"))) {
                report.add(Severity.CRITICAL, "vault", vault.uuid(), DoctorIssueCode.VAULT_UUID_MISMATCH, vaultIndex, tag.getUUID("UUID"));
            }
            vaultIndex++;
        }
        if (report.verbose()) {
            report.add(Severity.OK, "backup", null, DoctorIssueCode.BACKUP_SUMMARY, backupIndex, vaultIndex);
        }
    }

    private static void checkOrphanMetadata(PlayerCompanionData data, Map<UUID, String> owners, Report report) {
        for (Map.Entry<String, Set<UUID>> entry : data.metadataIdsForDiagnostics().entrySet()) {
            checkMetadataKeys(entry.getKey(), entry.getValue(), owners, report);
        }
    }

    private static void checkMetadataKeys(String area, Set<UUID> keys, Map<UUID, String> owners, Report report) {
        for (UUID uuid : keys) {
            if (!owners.containsKey(uuid)) {
                report.add(Severity.WARNING, "metadata", uuid, DoctorIssueCode.ORPHAN_METADATA, area);
            }
        }
    }

    private static void checkOperationLocks(MinecraftServer server, UUID playerUuid, Map<UUID, String> owners, Report report) {
        long now = server == null ? -1L : server.overworld().getGameTime();
        for (Map.Entry<UUID, CompanionOperationLockService.ActiveOperation> entry : CompanionOperationLockService.snapshot().entrySet()) {
            UUID uuid = entry.getKey();
            CompanionOperationLockService.ActiveOperation operation = entry.getValue();
            boolean ownRecord = owners.containsKey(uuid);
            boolean ownOperation = playerUuid != null && playerUuid.equals(operation.playerUuid());
            if (!ownRecord && !ownOperation) {
                if (report.verbose()) {
                    report.add(Severity.OK, "operation", uuid, DoctorIssueCode.FOREIGN_OPERATION, operation.operation(), operation.source());
                }
                continue;
            }
            if (ownRecord && !ownOperation) {
                report.add(Severity.WARNING, "operation", uuid, DoctorIssueCode.OPERATION_OWNER_MISMATCH, operation.operation(), operation.playerUuid());
            } else if (!ownRecord) {
                report.add(Severity.WARNING, "operation", uuid, DoctorIssueCode.ORPHAN_OPERATION, operation.operation());
            } else if (now >= 0L && operation.expiresAt() < now) {
                report.add(Severity.WARNING, "operation", uuid, DoctorIssueCode.EXPIRED_OPERATION, operation.operation(), operation.source());
            } else if (report.verbose()) {
                report.add(Severity.OK, "operation", uuid, DoctorIssueCode.ACTIVE_OPERATION, operation.operation(), operation.source());
            }
        }
    }

    private static void checkLoadedEntities(MinecraftServer server, PlayerCompanionData data, Map<UUID, String> owners, Report report) {
        if (server == null) {
            report.add(Severity.UNKNOWN, "world", null, DoctorIssueCode.SERVER_UNAVAILABLE);
            return;
        }
        Map<UUID, Integer> loadedMatches = new HashMap<>();
        int temporaryPerformers = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (FindMeApi.isTemporaryActionPerformer(entity)) {
                    temporaryPerformers++;
                    continue;
                }
                UUID uuid = entity.getUUID();
                if (owners.containsKey(uuid) && !entity.isRemoved()) {
                    loadedMatches.merge(uuid, 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<UUID, Integer> entry : loadedMatches.entrySet()) {
            if (entry.getValue() > 1) {
                report.add(Severity.CRITICAL, "world", entry.getKey(), DoctorIssueCode.DUPLICATE_LOADED_ENTITY, entry.getValue());
            }
            if (data.storedEntity(entry.getKey()).isPresent()) {
                report.add(Severity.CONFLICT, "world", entry.getKey(), DoctorIssueCode.LOADED_AND_STORED);
            }
        }
        if (temporaryPerformers > 0) {
            report.add(Severity.WARNING, "temporary", null, DoctorIssueCode.TEMPORARY_ENTITIES, temporaryPerformers);
        } else if (report.verbose()) {
            report.add(Severity.OK, "temporary", null, DoctorIssueCode.NO_TEMPORARY_ENTITIES);
        }
    }

    public enum Severity {
        OK,
        UNKNOWN,
        WARNING,
        CONFLICT,
        CRITICAL;

        boolean worseThan(Severity other) {
            return this.ordinal() > other.ordinal();
        }
    }

    public record Issue(Severity severity, DoctorArea area, UUID uuid, DoctorIssueCode code, List<String> arguments) {
        public Issue {
            arguments = arguments == null ? List.of() : List.copyOf(arguments);
        }

        public String line() {
            String target = uuid == null ? "-" : uuid.toString();
            return "[" + severity + "] " + area + " " + target + " - " + code + " " + String.join(" | ", arguments);
        }
    }

    public static final class Report {
        private final String playerName;
        private final UUID playerUuid;
        private final boolean verbose;
        private final List<Issue> issues = new ArrayList<>();

        private Report(String playerName, UUID playerUuid, boolean verbose) {
            this.playerName = playerName;
            this.playerUuid = playerUuid;
            this.verbose = verbose;
        }

        void add(Severity severity, String area, UUID uuid, DoctorIssueCode code, Object... arguments) {
            if (severity == Severity.OK && !this.verbose) {
                return;
            }
            ArrayList<String> values = new ArrayList<>();
            if (arguments != null) {
                for (Object argument : arguments) values.add(String.valueOf(argument));
            }
            this.issues.add(new Issue(severity, DoctorArea.fromLegacy(area), uuid, code, values));
        }

        public List<AreaStatus> areaStatuses() {
            ArrayList<AreaStatus> statuses = new ArrayList<>();
            for (DoctorArea area : DoctorArea.values()) {
                Severity severity = Severity.OK;
                int issueCount = 0;
                for (Issue issue : this.issues) {
                    if (issue.area() != area || issue.severity() == Severity.OK) continue;
                    issueCount++;
                    if (issue.severity().worseThan(severity)) severity = issue.severity();
                }
                statuses.add(new AreaStatus(area, severity, issueCount));
            }
            return List.copyOf(statuses);
        }

        boolean verbose() {
            return this.verbose;
        }

        public List<Issue> issues() {
            return List.copyOf(this.issues);
        }

        public boolean hasIssues() {
            return this.issueCount() > 0;
        }

        public Severity worst() {
            Severity worst = Severity.OK;
            for (Issue issue : this.issues) {
                if (issue.severity().worseThan(worst)) {
                    worst = issue.severity();
                }
            }
            return worst;
        }

        public List<String> lines() {
            List<String> lines = new ArrayList<>();
            lines.add("FindMe doctor: player=" + this.playerName + " uuid=" + this.playerUuid + " worst=" + this.worst() + " issues=" + this.issueCount());
            if (this.issues.isEmpty()) {
                lines.add("[OK] No loaded-data conflicts found.");
                return lines;
            }
            int limit = this.verbose ? this.issues.size() : Math.min(this.issues.size(), 12);
            for (int i = 0; i < limit; i++) {
                lines.add(this.issues.get(i).line());
            }
            if (limit < this.issues.size()) {
                lines.add("... " + (this.issues.size() - limit) + " more issue(s). Use verbose for the full report.");
            }
            return lines;
        }

        public long issueCount() {
            return this.issues.stream().filter(issue -> issue.severity() != Severity.OK).count();
        }
    }

    public record AreaStatus(DoctorArea area, Severity severity, int issueCount) {
    }
}

