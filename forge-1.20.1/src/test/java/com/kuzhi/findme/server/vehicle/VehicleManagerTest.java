package com.kuzhi.findme.server.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class VehicleManagerTest {
    @Test
    void itemFramesAreLeftForVanillaInteraction() {
        assertFalse(VehicleManager.isBindableVehicleClass(ItemFrame.class));
        assertFalse(VehicleManager.isBindableVehicleClass(GlowItemFrame.class));
    }

    @Test
    void vanillaNonLivingVehiclesRemainBindable() {
        assertTrue(VehicleManager.isBindableVehicleClass(Boat.class));
        assertTrue(VehicleManager.isBindableVehicleClass(Minecart.class));
    }

    @Test
    void directSwitchPreservesExactSourcePositionWhenItsBlockIsUsable() {
        Vec3 source = new Vec3(4.25, 91.75, -2.5);
        BlockPos sourceBlock = BlockPos.containing(source);

        assertEquals(source, VehicleManager.preciseSwitchPosition(source, sourceBlock, sourceBlock));
    }

    @Test
    void alternateSwitchSpaceUsesTheResolvedBlockWithoutGroundProjection() {
        Vec3 source = new Vec3(4.25, 91.75, -2.5);
        BlockPos resolved = new BlockPos(7, 94, -4);

        assertEquals(Vec3.atBottomCenterOf(resolved), VehicleManager.preciseSwitchPosition(
                source, BlockPos.containing(source), resolved));
    }
}
