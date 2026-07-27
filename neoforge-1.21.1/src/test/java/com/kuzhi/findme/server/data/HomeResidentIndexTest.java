package com.kuzhi.findme.server.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.SavedPosition;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class HomeResidentIndexTest {
    @Test
    void projectsOnlyHomeLifecycleFieldsFromThePlayerRoot() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID storedMount = UUID.randomUUID();
        UUID deployedCompanion = UUID.randomUUID();
        UUID house = UUID.randomUUID();
        SavedPosition home = new SavedPosition(Level.OVERWORLD, 12.0, 64.0, -8.0, 0.0f, 0.0f);
        SavedPosition nest = new SavedPosition(Level.OVERWORLD, 10.0, 63.0, -10.0, 0.0f, 0.0f);

        data.add(CompanionKind.MOUNT, storedMount);
        data.setHomePosition(storedMount, home);
        data.setHomeNestBlock(storedMount, nest);
        data.setHomeHouseId(storedMount, house);
        CompoundTag storedTag = new CompoundTag();
        storedTag.putString("id", "minecraft:horse");
        storedTag.putFloat("CompanionPreviewWidth", 1.4f);
        storedTag.putFloat("CompanionPreviewHeight", 2.2f);
        data.storeEntity(storedMount, storedTag);
        data.setLifecycleState(storedMount, CompanionLifecycleState.HOME_STORED);

        data.add(CompanionKind.COMPANION, deployedCompanion);
        data.setDeployed(CompanionKind.COMPANION, deployedCompanion);
        data.setLifecycleState(deployedCompanion, CompanionLifecycleState.DEPLOYED);

        CompoundTag container = new CompoundTag();
        data.save(container);
        HomeResidentIndex index = HomeResidentIndex.fromRoot(PlayerCompanionDataCodec.rootCopy(container));

        assertEquals(2, index.entries().size());
        HomeResidentIndex.Entry mount = index.entries().get(0);
        assertEquals(storedMount, mount.uuid());
        assertEquals(CompanionKind.MOUNT, mount.kind());
        assertEquals(CompanionLifecycleState.HOME_STORED, mount.lifecycleState());
        assertFalse(mount.deployed());
        assertTrue(mount.stored());
        assertEquals(home, mount.homePosition());
        assertEquals(nest, mount.homeNestBlock());
        assertEquals(house, mount.houseId());
        assertEquals(1.4f, mount.width());
        assertEquals(2.2f, mount.height());

        HomeResidentIndex.Entry companion = index.entries().get(1);
        assertEquals(deployedCompanion, companion.uuid());
        assertEquals(CompanionKind.COMPANION, companion.kind());
        assertEquals(CompanionLifecycleState.DEPLOYED, companion.lifecycleState());
        assertTrue(companion.deployed());
        assertFalse(companion.stored());
        assertNull(companion.homePosition());
        assertThrows(UnsupportedOperationException.class, () -> index.entries().clear());
    }
}
