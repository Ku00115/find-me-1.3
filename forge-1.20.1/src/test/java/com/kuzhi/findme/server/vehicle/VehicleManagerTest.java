package com.kuzhi.findme.server.vehicle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
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
}
