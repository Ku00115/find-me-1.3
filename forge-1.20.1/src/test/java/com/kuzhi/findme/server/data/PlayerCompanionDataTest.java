package com.kuzhi.findme.server.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.api.CompanionSpellBinding;
import com.kuzhi.findme.api.CompanionSpellRole;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class PlayerCompanionDataTest {
    private static CompanionSpellBinding binding(int storedCount) {
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:paper");
        item.putByte("Count", (byte)storedCount);
        return new CompanionSpellBinding(new ResourceLocation("test", "provider"),
                new ResourceLocation("test", "spell"), null, CompanionSpellRole.UTILITY,
                "Test", 1, item);
    }

    @Test
    void updatingAHouseResidentPositionKeepsTheHouseAssignmentUntilExplicitlyCleared() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID resident = UUID.randomUUID();
        UUID house = UUID.randomUUID();
        SavedPosition nest = new SavedPosition(null, 10, 64, 10, 0, 0);
        SavedPosition first = new SavedPosition(null, 12, 64, 10, 0, 0);
        SavedPosition moved = new SavedPosition(null, 13, 64, 11, 0, 0);
        data.add(CompanionKind.COMPANION, resident);
        data.setHomePosition(resident, first);
        data.setHomeNestBlock(resident, nest);
        data.setHomeHouseId(resident, house);

        data.setHouseResidentPosition(resident, moved);

        assertEquals(moved, data.homePosition(resident).orElseThrow());
        assertEquals(nest, data.homeNestBlock(resident).orElseThrow());
        assertEquals(house, data.homeHouseId(resident).orElseThrow());
        data.clearHomePosition(resident);
        assertTrue(data.homePosition(resident).isEmpty());
        assertTrue(data.homeNestBlock(resident).isEmpty());
        assertTrue(data.homeHouseId(resident).isEmpty());
    }

    @Test
    void saveLoadPreservesUuidTeamsSelectionDeploymentAndVehicles() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID mountOne = UUID.randomUUID();
        UUID mountTwo = UUID.randomUUID();
        UUID vehicleOne = UUID.randomUUID();
        UUID vehicleTwo = UUID.randomUUID();

        assertTrue(data.add(CompanionKind.MOUNT, mountOne));
        assertTrue(data.add(CompanionKind.MOUNT, mountTwo));
        int mountTeam = data.createTeam(CompanionTeamTarget.MOUNT);
        assertTrue(data.setTeam(CompanionTeamTarget.MOUNT, mountTeam, List.of(mountTwo, mountOne)));
        assertTrue(data.applyTeamToWheel(CompanionTeamTarget.MOUNT, mountTeam));
        assertTrue(data.setActiveUuid(CompanionKind.MOUNT, mountOne));
        data.setDeployed(CompanionKind.MOUNT, mountOne);

        assertTrue(data.addVehicle(vehicleOne));
        assertTrue(data.addVehicle(vehicleTwo));
        int vehicleTeam = data.createTeam(CompanionTeamTarget.VEHICLE);
        assertTrue(data.setTeam(CompanionTeamTarget.VEHICLE, vehicleTeam, List.of(vehicleTwo, vehicleOne)));
        assertTrue(data.applyTeamToWheel(CompanionTeamTarget.VEHICLE, vehicleTeam));
        assertTrue(data.setActiveVehicleUuid(vehicleOne));
        data.setDeployedVehicle(vehicleOne);

        CompoundTag root = new CompoundTag();
        data.save(root);
        PlayerCompanionData restored = PlayerCompanionData.load(root);

        assertEquals(List.of(mountTwo, mountOne), restored.wheelOrder(CompanionKind.MOUNT));
        assertEquals(mountOne, restored.active(CompanionKind.MOUNT).orElseThrow());
        assertTrue(restored.isDeployed(CompanionKind.MOUNT, mountOne));
        assertFalse(restored.isDeployed(CompanionKind.MOUNT, mountTwo));
        assertEquals(List.of(vehicleTwo, vehicleOne), restored.vehicleWheelOrder());
        assertEquals(vehicleOne, restored.activeVehicle().orElseThrow());
        assertTrue(restored.isVehicleDeployed(vehicleOne));
        assertFalse(restored.isVehicleDeployed(vehicleTwo));
    }

    @Test
    void applyingANonCurrentTeamSelectsItsFirstUuid() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID first = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        data.add(CompanionKind.MOUNT, first);
        data.add(CompanionKind.MOUNT, target);
        int targetTeam = data.createTeam(CompanionTeamTarget.MOUNT);
        data.setTeam(CompanionTeamTarget.MOUNT, targetTeam, List.of(target));

        assertTrue(data.applyTeamToWheel(CompanionTeamTarget.MOUNT, targetTeam));
        assertEquals(List.of(target), data.wheelOrder(CompanionKind.MOUNT));
        assertEquals(target, data.active(CompanionKind.MOUNT).orElseThrow());
    }

    @Test
    void disablingAutomaticOrganizationKeepsStableNumbersAndReusesTheGapAtTheEnd() {
        PlayerCompanionData data = new PlayerCompanionData();
        data.setUiSettings(data.uiSettings().withAutoOrganizeTeams(false));
        int second = data.createTeam(CompanionTeamTarget.MOUNT);
        int third = data.createTeam(CompanionTeamTarget.MOUNT);
        assertEquals(2, data.teamNumber(CompanionTeamTarget.MOUNT, second));
        assertEquals(3, data.teamNumber(CompanionTeamTarget.MOUNT, third));

        assertTrue(data.deleteTeam(CompanionTeamTarget.MOUNT, second));
        assertEquals(3, data.teamNumber(CompanionTeamTarget.MOUNT, 1));
        int appended = data.createTeam(CompanionTeamTarget.MOUNT);
        assertEquals(2, appended);
        assertEquals(2, data.teamNumber(CompanionTeamTarget.MOUNT, appended));

        CompoundTag root = new CompoundTag();
        data.save(root);
        PlayerCompanionData restored = PlayerCompanionData.load(root);
        assertEquals(3, restored.teamNumber(CompanionTeamTarget.MOUNT, 1));
        assertEquals(2, restored.teamNumber(CompanionTeamTarget.MOUNT, 2));
    }

    @Test
    void automaticOrganizationRenumbersAfterDeletingAMiddleTeamAndKeepsCustomNames() {
        PlayerCompanionData data = new PlayerCompanionData();
        int second = data.createTeam(CompanionTeamTarget.COMPANION);
        int third = data.createTeam(CompanionTeamTarget.COMPANION);
        data.setTeamName(CompanionTeamTarget.COMPANION, third, "Rescue");

        assertTrue(data.deleteTeam(CompanionTeamTarget.COMPANION, second));

        assertEquals(2, data.teamNumber(CompanionTeamTarget.COMPANION, 1));
        assertEquals("Rescue", data.teamName(CompanionTeamTarget.COMPANION, 1));
    }

    @Test
    void automaticOrganizationFillsTheFirstAvailableTeamBeforeCreatingAnother() {
        PlayerCompanionData data = new PlayerCompanionData();
        int second = data.createTeam(CompanionTeamTarget.COMPANION);
        int third = data.createTeam(CompanionTeamTarget.COMPANION);
        for (int index = 0; index < PlayerCompanionData.TEAM_SIZE; index++) {
            data.add(CompanionKind.COMPANION, UUID.randomUUID());
        }
        UUID newcomer = UUID.randomUUID();

        data.add(CompanionKind.COMPANION, newcomer);

        assertEquals(second, data.teamIndexOf(newcomer));
        assertEquals(3, data.teamCount(CompanionTeamTarget.COMPANION));
        assertTrue(data.team(CompanionTeamTarget.COMPANION, third).isEmpty());
    }

    @Test
    void automaticOrganizationCreatesANewTeamWhenEveryExistingTeamIsFull() {
        PlayerCompanionData data = new PlayerCompanionData();
        for (int index = 0; index < PlayerCompanionData.TEAM_SIZE; index++) {
            data.add(CompanionKind.MOUNT, UUID.randomUUID());
        }
        UUID newcomer = UUID.randomUUID();

        data.add(CompanionKind.MOUNT, newcomer);

        assertEquals(2, data.teamCount(CompanionTeamTarget.MOUNT));
        assertEquals(List.of(newcomer), data.team(CompanionTeamTarget.MOUNT, 1));
    }

    @Test
    void organizingTeamsAssignsAnExistingWarehouseRosterAndRemovesDefaultEmptyTeams() {
        PlayerCompanionData data = new PlayerCompanionData();
        data.setUiSettings(data.uiSettings().withAutoOrganizeTeams(false));
        List<UUID> roster = java.util.stream.IntStream.range(0, 11)
                .mapToObj(ignored -> UUID.randomUUID()).toList();
        roster.forEach(uuid -> data.add(CompanionKind.COMPANION, uuid));
        for (int index = 0; index < 4; index++) {
            data.createTeam(CompanionTeamTarget.COMPANION);
        }

        data.setUiSettings(data.uiSettings().withAutoOrganizeTeams(true));

        assertTrue(data.organizeTeams());
        assertEquals(2, data.teamCount(CompanionTeamTarget.COMPANION));
        assertEquals(roster.subList(0, 6), data.team(CompanionTeamTarget.COMPANION, 0));
        assertEquals(roster.subList(6, 11), data.team(CompanionTeamTarget.COMPANION, 1));
        assertFalse(data.organizeTeams());
    }

    @Test
    void organizingTeamsKeepsNamedEmptyTeamsAndRenumbersDefaults() {
        PlayerCompanionData data = new PlayerCompanionData();
        int named = data.createTeam(CompanionTeamTarget.MOUNT);
        data.setTeamName(CompanionTeamTarget.MOUNT, named, "Reserve");
        data.createTeam(CompanionTeamTarget.MOUNT);

        assertTrue(data.organizeTeams());
        assertEquals(1, data.teamCount(CompanionTeamTarget.MOUNT));
        assertEquals("Reserve", data.teamName(CompanionTeamTarget.MOUNT, 0));
        assertEquals(1, data.teamNumber(CompanionTeamTarget.MOUNT, 0));
    }

    @Test
    void organizingTeamsPreservesExistingAssignmentsAndMemberOrder() {
        PlayerCompanionData data = new PlayerCompanionData();
        data.setUiSettings(data.uiSettings().withAutoOrganizeTeams(false));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID unassigned = UUID.randomUUID();
        data.add(CompanionKind.MOUNT, first);
        data.add(CompanionKind.MOUNT, second);
        data.add(CompanionKind.MOUNT, unassigned);
        data.setTeam(CompanionTeamTarget.MOUNT, 0, List.of(second, first));

        data.setUiSettings(data.uiSettings().withAutoOrganizeTeams(true));

        assertTrue(data.organizeTeams());
        assertEquals(List.of(second, first, unassigned), data.team(CompanionTeamTarget.MOUNT, 0));
    }

    @Test
    void organizingTeamsDoesNothingWhileAutomaticOrganizationIsDisabled() {
        PlayerCompanionData data = new PlayerCompanionData();
        data.setUiSettings(data.uiSettings().withAutoOrganizeTeams(false));
        UUID unassigned = UUID.randomUUID();
        data.add(CompanionKind.MOUNT, unassigned);
        data.removeTeamMember(unassigned);
        data.createTeam(CompanionTeamTarget.MOUNT);

        assertFalse(data.organizeTeams());
        assertEquals(2, data.teamCount(CompanionTeamTarget.MOUNT));
        assertEquals(-1, data.teamIndexOf(unassigned));
    }

    @Test
    void manualBackupsKeepFourSlotsWithoutEvictingAutomaticBackups() {
        PlayerCompanionData data = new PlayerCompanionData();
        for (int index = 0; index < 3; index++) {
            data.createBackup(10L + index, "auto", 2, false);
        }
        for (int index = 0; index < 5; index++) {
            data.createBackup(20L + index, "manual", 4, true);
        }

        assertEquals(6, data.backupList().size());
        assertEquals(4, data.backupList().stream().filter(PlayerCompanionData.BackupEntry::manual).count());
        assertEquals(2, data.backupList().stream().filter(backup -> !backup.manual()).count());
        PlayerCompanionData.BackupEntry newestManual = data.backupList().stream()
                .filter(PlayerCompanionData.BackupEntry::manual).findFirst().orElseThrow();
        assertTrue(data.renameManualBackup(data.backupList().indexOf(newestManual), newestManual.savedAt(), "Stable slot"));
        assertEquals("Stable slot", data.backupList().stream().filter(PlayerCompanionData.BackupEntry::manual)
                .findFirst().orElseThrow().reason());
        assertFalse(data.renameManualBackup(0, -1L, "stale"));
    }

    @Test
    void backupManualFlagSurvivesSaveAndLoad() {
        PlayerCompanionData data = new PlayerCompanionData();
        data.createBackup(42L, "manual", 4, true);
        CompoundTag root = new CompoundTag();
        data.save(root);

        PlayerCompanionData restored = PlayerCompanionData.load(root);

        assertEquals(1, restored.backupList().size());
        assertTrue(restored.backupList().get(0).manual());
    }

    @Test
    void deleteBackupRequiresTheExpectedSavedAtIdentity() {
        PlayerCompanionData data = new PlayerCompanionData();
        data.createBackup(42L, "manual", 4, true);

        assertFalse(data.deleteBackup(0, 41L));
        assertEquals(1, data.backupList().size());
        assertTrue(data.deleteBackup(0, 42L));
        assertTrue(data.backupList().isEmpty());
    }

    @Test
    void restoreSafetyBackupKeepsOneReplaceableSlot() {
        PlayerCompanionData data = new PlayerCompanionData();
        data.createBackup(10L, "before_restore", 6, false);
        data.createBackup(11L, "auto", 6, false);
        data.createBackup(12L, "before_restore", 6, false);

        assertEquals(1, data.backupList().stream()
                .filter(backup -> "before_restore".equals(backup.reason())).count());
        assertEquals(12L, data.backupList().stream()
                .filter(backup -> "before_restore".equals(backup.reason()))
                .findFirst().orElseThrow().savedAt());
    }

    @Test
    void bindingCinematicHistoryPersistsByExactEntityType() {
        PlayerCompanionData data = new PlayerCompanionData();
        assertTrue(data.markBindingCinematicSeen("minecraft:wolf"));
        assertFalse(data.markBindingCinematicSeen("minecraft:wolf"));
        assertFalse(data.hasSeenBindingCinematic("minecraft:cat"));

        CompoundTag root = new CompoundTag();
        data.save(root);
        PlayerCompanionData restored = PlayerCompanionData.load(root);

        assertTrue(restored.hasSeenBindingCinematic("minecraft:wolf"));
        assertFalse(restored.hasSeenBindingCinematic("minecraft:cat"));
        restored.resetBindingCinematicHistory();
        assertFalse(restored.hasSeenBindingCinematic("minecraft:wolf"));
    }

    @Test
    void categoryTransferPreservesTeamAndMovesTheAppliedWheel() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID companion = UUID.randomUUID();
        data.add(CompanionKind.COMPANION, companion);
        assertTrue(data.applyTeamToWheel(CompanionTeamTarget.COMPANION, 0));
        assertTrue(data.applyTeamToWheel(CompanionTeamTarget.MOUNT, 0));

        assertTrue(data.transferCategory(companion, CompanionKind.MOUNT));

        assertFalse(data.contains(CompanionKind.COMPANION, companion));
        assertTrue(data.contains(CompanionKind.MOUNT, companion));
        assertEquals(List.of(companion), data.team(CompanionTeamTarget.MOUNT, 0));
        assertEquals(List.of(companion), data.wheelOrder(CompanionKind.MOUNT));
        assertTrue(data.wheelOrder(CompanionKind.COMPANION).isEmpty());
    }

    @Test
    void categoryTransferPreservesSpellBindings() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID companion = UUID.randomUUID();
        data.add(CompanionKind.COMPANION, companion);
        data.setSpellBinding(companion, 0, binding(64));

        assertTrue(data.transferCategory(companion, CompanionKind.MOUNT));

        assertTrue(data.spellBinding(companion, 0).isPresent());
        assertFalse(data.hasPendingSpellItemReturns());
    }

    @Test
    void convertingCreatureToVehicleQueuesExactlyOneItemPerBindingAndPersistsIt() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID companion = UUID.randomUUID();
        data.add(CompanionKind.MOUNT, companion);
        data.setSpellBinding(companion, 0, binding(64));

        assertTrue(data.addVehicle(companion));

        assertTrue(data.spellBindings(companion).isEmpty());
        assertTrue(data.hasPendingSpellItemReturns());
        CompoundTag root = new CompoundTag();
        data.save(root);
        PlayerCompanionData restored = PlayerCompanionData.load(root);
        CompoundTag returned = restored.drainPendingSpellItemReturns().get(0);
        assertEquals(1, returned.getByte("Count"));
        assertEquals(1, returned.getInt("count"));
    }

    @Test
    void removingCreatureQueuesStoredSpellItems() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID companion = UUID.randomUUID();
        data.add(CompanionKind.COMPANION, companion);
        data.setSpellBinding(companion, 2, binding(32));

        data.remove(companion);

        assertEquals(1, data.drainPendingSpellItemReturns().get(0).getByte("Count"));
    }

    @Test
    void lifecycleChangesAreBoundedAndDoNotSurvivePersistenceLoad() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID companion = UUID.randomUUID();
        data.add(CompanionKind.COMPANION, companion);
        assertEquals(java.util.Set.of(companion), data.lifecycleChanges());

        CompoundTag root = new CompoundTag();
        data.save(root);
        PlayerCompanionData restored = PlayerCompanionData.load(root);

        assertTrue(restored.lifecycleChanges().isEmpty());
        restored.setUiSettings(restored.uiSettings());
        assertTrue(restored.lifecycleChanges().isEmpty());
    }

    @Test
    void addingAnAlreadyRegisteredCompanionPreservesCriticalState() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID companion = UUID.randomUUID();
        data.add(CompanionKind.COMPANION, companion);
        data.setCritical(companion, true);

        assertFalse(data.add(CompanionKind.COMPANION, companion));

        assertTrue(data.isCritical(companion));
    }

    @Test
    void criticalStateSurvivesSaveAndLoad() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID companion = UUID.randomUUID();
        data.add(CompanionKind.COMPANION, companion);
        data.setCritical(companion, true);

        CompoundTag root = new CompoundTag();
        data.save(root);
        PlayerCompanionData restored = PlayerCompanionData.load(root);

        assertTrue(restored.isCritical(companion));
    }

    @Test
    void categoryTransferCreatesAFallbackTeamWhenMatchingTeamIsFull() {
        PlayerCompanionData data = new PlayerCompanionData();
        for (int index = 0; index < PlayerCompanionData.TEAM_SIZE; index++) {
            data.add(CompanionKind.MOUNT, UUID.randomUUID());
        }
        UUID companion = UUID.randomUUID();
        data.add(CompanionKind.COMPANION, companion);

        assertTrue(data.transferCategory(companion, CompanionKind.MOUNT));

        assertEquals(2, data.teamCount(CompanionTeamTarget.MOUNT));
        assertEquals(List.of(companion), data.team(CompanionTeamTarget.MOUNT, 1));
    }

    @Test
    void companionMagicContributorsIncludeCreaturesButNotVehiclesAndRespectTheCap() {
        PlayerCompanionData data = new PlayerCompanionData();
        data.add(CompanionKind.COMPANION, UUID.randomUUID());
        data.add(CompanionKind.MOUNT, UUID.randomUUID());
        data.addVehicle(UUID.randomUUID());

        assertEquals(2, data.companionMagicContributorCount(0));
        assertEquals(1, data.companionMagicContributorCount(1));
        assertEquals(2, data.companionMagicContributorCount(10));
    }

    @Test
    void sharedCompanionManaSurvivesSaveAndBackupRestore() {
        PlayerCompanionData data = new PlayerCompanionData();
        data.setCompanionMagicMana(73.5F, 420L, 300.0F);

        CompoundTag root = new CompoundTag();
        data.save(root);
        PlayerCompanionData restored = PlayerCompanionData.load(root);
        assertEquals(73.5F, restored.companionMagicMana());
        assertEquals(420L, restored.companionMagicManaTick());
        assertEquals(300.0F, restored.companionMagicCapacity());

        CompoundTag backup = PlayerCompanionDataCodec.createBackupState(data);
        restored.setCompanionMagicMana(1.0F, 999L, 100.0F);
        PlayerCompanionDataCodec.restoreBackupState(restored, backup);
        assertEquals(73.5F, restored.companionMagicMana());
        assertEquals(420L, restored.companionMagicManaTick());
        assertEquals(300.0F, restored.companionMagicCapacity());
    }

    @Test
    void addingACompanionAddsOneHundredCurrentAndMaximumMana() {
        PlayerCompanionData data = new PlayerCompanionData();
        data.add(CompanionKind.COMPANION, UUID.randomUUID());
        data.setCompanionMagicMana(20.0F, 100L, 100.0F);

        data.add(CompanionKind.MOUNT, UUID.randomUUID());

        assertEquals(120.0F, data.companionMagicMana());
        assertEquals(200.0F, data.companionMagicCapacity());
    }

    @Test
    void recoveryRecordsRoundTripAndStayOutOfWheelOrder() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID companion = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        data.add(CompanionKind.MOUNT, companion);
        data.putRecoveryRecord(new RecoveryCompanionRecord(recordId, companion, "Lost mount",
                "minecraft:horse", CompanionKind.MOUNT, 2, 100L, 120L,
                "minecraft:overworld", 10.0, 64.0, -3.0, "discarded", "missing snapshot",
                CompanionLifecycleState.DEPLOYED));
        data.setLifecycleState(companion, CompanionLifecycleState.RECOVERY);

        CompoundTag root = new CompoundTag();
        data.save(root);
        PlayerCompanionData restored = PlayerCompanionData.load(root);

        RecoveryCompanionRecord record = restored.recoveryRecord(companion).orElseThrow();
        assertEquals(recordId, record.recordId());
        assertEquals(CompanionLifecycleState.DEPLOYED, record.sourceState());
        assertEquals(List.of(), restored.wheelOrder(CompanionKind.MOUNT));
        assertFalse(restored.allowedInTeam(CompanionTeamTarget.MOUNT, companion));
    }

    @Test
    void legacyRecoveryLifecycleSynthesizesARecordOnLoad() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID companion = UUID.randomUUID();
        data.add(CompanionKind.COMPANION, companion);
        data.setDisplayName(companion, "Legacy lost companion");
        data.setLifecycleState(companion, CompanionLifecycleState.RECOVERY);

        CompoundTag root = new CompoundTag();
        data.save(root);
        PlayerCompanionData restored = PlayerCompanionData.load(root);

        assertEquals("Legacy lost companion", restored.recoveryRecord(companion).orElseThrow().customName());
    }

    @Test
    void recoveryArchiveSearchContinuesPastAnInvalidVaultSnapshot() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID companion = UUID.randomUUID();
        data.add(CompanionKind.COMPANION, companion);
        CompoundTag valid = new CompoundTag();
        valid.putString("id", "minecraft:wolf");
        valid.putUUID("UUID", companion);
        data.storeEntity(companion, valid);
        data.createBackup(10L, "valid", 6);

        CompoundTag invalid = new CompoundTag();
        invalid.putString("id", "missing:entity");
        data.addVaultSnapshot(companion, CompanionKind.COMPANION, "Broken", invalid, 20L,
                "invalid", 30);

        List<CompoundTag> candidates = data.recoverySnapshotsFromArchives(companion);
        assertEquals("missing:entity", candidates.get(0).getString("id"));
        assertTrue(candidates.stream().anyMatch(tag -> "minecraft:wolf".equals(tag.getString("id"))));
    }
}
