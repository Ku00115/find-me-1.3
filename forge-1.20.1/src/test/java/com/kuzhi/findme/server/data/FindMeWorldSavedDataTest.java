package com.kuzhi.findme.server.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import com.kuzhi.findme.common.HouseResidentMode;
import com.kuzhi.findme.common.SavedPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class FindMeWorldSavedDataTest {
    @Test
    void revisionChangesOnlyWhenTheStoredRootChanges() {
        FindMeWorldSavedData data = new FindMeWorldSavedData();
        UUID player = UUID.randomUUID();
        CompoundTag root = new CompoundTag();
        root.putString("name", "first");

        assertEquals(1L, data.putPlayerRoot(player, root));
        assertEquals(1L, data.putPlayerRoot(player, root.copy()));

        root.putString("name", "second");
        assertEquals(2L, data.putPlayerRoot(player, root));
        assertEquals(2L, data.playerRevision(player));
    }

    @Test
    void playerRootsAreDefensiveCopiesInBothDirections() {
        FindMeWorldSavedData data = new FindMeWorldSavedData();
        UUID player = UUID.randomUUID();
        CompoundTag original = new CompoundTag();
        original.putString("value", "stored");
        data.putPlayerRoot(player, original);

        original.putString("value", "caller-mutated");
        CompoundTag firstRead = data.playerRoot(player).orElseThrow();
        assertEquals("stored", firstRead.getString("value"));

        firstRead.putString("value", "read-mutated");
        assertEquals("stored", data.playerRoot(player).orElseThrow().getString("value"));
        assertTrue(data.hasAuthoritativePlayer(player));
        assertFalse(data.hasAuthoritativePlayer(UUID.randomUUID()));
    }

    @Test
    void replacingAPlayerRootInvalidatesTheHomeIndex() {
        FindMeWorldSavedData data = new FindMeWorldSavedData();
        UUID player = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        data.putPlayerRoot(player, rootWithMount(first));
        HomeResidentIndex firstIndex = data.homeResidentIndex(player);

        data.putPlayerRoot(player, rootWithMount(second));
        HomeResidentIndex secondIndex = data.homeResidentIndex(player);

        assertNotSame(firstIndex, secondIndex);
        assertEquals(first, firstIndex.entries().get(0).uuid());
        assertEquals(second, secondIndex.entries().get(0).uuid());
    }

    @Test
    void replacingAPlayerRootInvalidatesTheRuntimeIndex() {
        FindMeWorldSavedData data = new FindMeWorldSavedData();
        UUID player = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        data.putPlayerRoot(player, rootWithMount(first));
        CompanionRuntimeIndex firstIndex = data.companionRuntimeIndex(player);

        data.putPlayerRoot(player, rootWithMount(second));
        CompanionRuntimeIndex secondIndex = data.companionRuntimeIndex(player);

        assertNotSame(firstIndex, secondIndex);
        assertTrue(firstIndex.contains(first));
        assertFalse(firstIndex.contains(second));
        assertTrue(secondIndex.contains(second));
    }

    @Test
    void runtimeIndexExposesAnImmutableUuidProjection() {
        FindMeWorldSavedData data = new FindMeWorldSavedData();
        UUID player = UUID.randomUUID();
        UUID companion = UUID.randomUUID();
        data.putPlayerRoot(player, rootWithMount(companion));

        Set<UUID> uuids = data.companionRuntimeIndex(player).uuids();

        assertEquals(Set.of(companion), uuids);
        assertThrows(UnsupportedOperationException.class, uuids::clear);
    }

    @Test
    void companionOwnerIndexTracksRootReplacementAndRemoval() {
        FindMeWorldSavedData data = new FindMeWorldSavedData();
        UUID player = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        data.putPlayerRoot(player, rootWithMount(first));

        assertEquals(player, data.companionOwner(first).orElseThrow());
        data.putPlayerRoot(player, rootWithMount(second));

        assertTrue(data.companionOwner(first).isEmpty());
        assertEquals(player, data.companionOwner(second).orElseThrow());
        data.removePlayerRoot(player);
        assertTrue(data.companionOwner(second).isEmpty());
    }

    @Test
    void runtimeIndexProjectsTheRiddenPromotionSettingWithoutFullDataDecode() {
        FindMeWorldSavedData data = new FindMeWorldSavedData();
        UUID player = UUID.randomUUID();
        CompoundTag root = rootWithMount(UUID.randomUUID());
        CompoundTag settings = new CompoundTag();
        settings.putBoolean("autoPromoteRiddenCompanions", false);
        root.put("uiSettings", settings);

        data.putPlayerRoot(player, root);

        assertFalse(data.companionRuntimeIndex(player).autoPromoteRiddenCompanions());
        assertTrue(CompanionRuntimeIndex.empty().autoPromoteRiddenCompanions());
    }

    @Test
    void internalDecodeDoesNotExposeTheAuthoritativeRoot() {
        FindMeWorldSavedData data = new FindMeWorldSavedData();
        UUID player = UUID.randomUUID();
        UUID stored = UUID.randomUUID();
        UUID unsaved = UUID.randomUUID();
        data.putPlayerRoot(player, rootWithMount(stored));

        PlayerCompanionData decoded = data.decodePlayerData(player);
        decoded.add(com.kuzhi.findme.common.CompanionKind.MOUNT, unsaved);

        assertTrue(data.companionRuntimeIndex(player).contains(stored));
        assertFalse(data.companionRuntimeIndex(player).contains(unsaved));
        assertEquals(1L, data.playerRevision(player));
    }

    @Test
    void conflictingHouseIdentityDoesNotReplaceTheOriginalRecord() {
        FindMeWorldSavedData data = new FindMeWorldSavedData();
        UUID houseId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        SavedPosition original = position(4, 64, 8);

        assertTrue(data.registerHouse(houseId, owner, original, "Original"));
        assertFalse(data.registerHouse(houseId, UUID.randomUUID(), position(40, 70, 80), "Collision"));

        FindMeWorldSavedData.HouseRecord record = data.house(houseId).orElseThrow();
        assertEquals(owner, record.owner());
        assertEquals(original.blockPos(), record.position().blockPos());
        assertEquals("Original", record.displayName());
    }

    @Test
    void residentModesSerializeAndLegacyValuesDefaultToWander() {
        FindMeWorldSavedData data = new FindMeWorldSavedData();
        UUID houseId = UUID.randomUUID();
        UUID guard = UUID.randomUUID();
        data.registerHouse(houseId, UUID.randomUUID(), position(0, 64, 0), "Home");
        data.addResident(houseId, guard);
        assertTrue(data.setResidentMode(houseId, guard, HouseResidentMode.GUARD));

        ListTag residents = FindMeWorldSavedData.saveResidents(data.house(houseId).orElseThrow());
        assertEquals("GUARD", residents.getCompound(0).getString("mode"));
        assertEquals(HouseResidentMode.WANDER, HouseResidentMode.parse(""));
        assertEquals(HouseResidentMode.WANDER, HouseResidentMode.parse("unknown-future-mode"));
    }

    @Test
    void removingAResidentFromAllHousesAlsoRemovesItsMode() {
        FindMeWorldSavedData data = new FindMeWorldSavedData();
        UUID firstHouse = UUID.randomUUID();
        UUID secondHouse = UUID.randomUUID();
        UUID resident = UUID.randomUUID();
        data.registerHouse(firstHouse, UUID.randomUUID(), position(0, 64, 0), "First");
        data.registerHouse(secondHouse, UUID.randomUUID(), position(20, 64, 0), "Second");
        data.addResident(firstHouse, resident);
        data.addResident(secondHouse, resident);
        data.setResidentMode(firstHouse, resident, HouseResidentMode.REST);
        data.setResidentMode(secondHouse, resident, HouseResidentMode.GUARD);

        assertEquals(2, data.removeResidentFromAllHouses(resident));
        assertFalse(data.house(firstHouse).orElseThrow().residents().contains(resident));
        assertFalse(data.house(secondHouse).orElseThrow().residents().contains(resident));
        assertEquals(HouseResidentMode.WANDER, data.residentMode(firstHouse, resident));
        assertEquals(HouseResidentMode.WANDER, data.residentMode(secondHouse, resident));
    }

    private static SavedPosition position(double x, double y, double z) {
        return new SavedPosition(null, x, y, z, 0.0f, 0.0f);
    }

    private static CompoundTag rootWithMount(UUID uuid) {
        CompoundTag root = new CompoundTag();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("uuid", uuid);
        ListTag mounts = new ListTag();
        mounts.add(entry);
        root.put("mounts", mounts);
        return root;
    }

}
