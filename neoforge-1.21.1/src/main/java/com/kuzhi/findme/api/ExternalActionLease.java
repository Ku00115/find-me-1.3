package com.kuzhi.findme.api;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Exclusive, server-authoritative permission for an addon to control one companion task. */
public interface ExternalActionLease extends AutoCloseable {
    UUID leaseId();

    UUID ownerUuid();

    UUID companionUuid();

    ResourceLocation actionId();

    int priority();

    long expiresAt();

    boolean renew(int timeoutTicks);

    boolean isValid();

    @Override
    void close();
}
