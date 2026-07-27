package com.kuzhi.findme.server.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
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
