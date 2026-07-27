package com.kuzhi.findme.server.lifecycle;

import net.minecraft.world.phys.Vec3;

record PlayerMotionSample(Vec3 position, int tick, double speed) {
}
