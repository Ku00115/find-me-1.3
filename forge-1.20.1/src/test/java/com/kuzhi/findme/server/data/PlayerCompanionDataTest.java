package com.kuzhi.findme.server.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionTeamTarget;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PlayerCompanionDataTest {
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
    void deletingATeamKeepsStableNumbersAndReusesTheGapAtTheEnd() {
        PlayerCompanionData data = new PlayerCompanionData();
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
}
