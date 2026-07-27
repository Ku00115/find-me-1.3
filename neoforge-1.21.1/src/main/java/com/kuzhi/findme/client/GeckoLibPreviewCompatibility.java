package com.kuzhi.findme.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.entity.PartEntity;

/** Gives detached previews identities that cannot collide in animation-instance caches. */
final class GeckoLibPreviewCompatibility {
    private static final AtomicInteger NEXT_PREVIEW_ID = new AtomicInteger(-1);

    private GeckoLibPreviewCompatibility() {
    }

    static void initializeDetachedTree(Entity root) {
        if (root == null) return;
        Set<Entity> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        root.getSelfAndPassengers().forEach(entity -> assignTree(entity, visited));
    }

    private static void assignTree(Entity entity, Set<Entity> visited) {
        if (entity == null || !visited.add(entity)) return;
        entity.setId(nextPreviewId());
        if (entity.isMultipartEntity()) {
            for (PartEntity<?> part : entity.getParts()) {
                assignTree(part, visited);
            }
        }
    }

    private static int nextPreviewId() {
        return NEXT_PREVIEW_ID.getAndUpdate(current -> current == Integer.MIN_VALUE ? -1 : current - 1);
    }
}
