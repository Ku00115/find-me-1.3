package com.kuzhi.findme.api.event;

import com.kuzhi.findme.common.CompanionTacticalAction;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

/** Posted after a validated companion command becomes the active server order. */
public final class CompanionCommandAcceptedEvent extends Event {
    private final MinecraftServer server;
    private final UUID ownerUuid;
    private final UUID companionUuid;
    private final CompanionTacticalAction action;
    private final UUID targetUuid;

    public CompanionCommandAcceptedEvent(MinecraftServer server, UUID ownerUuid, UUID companionUuid,
                                         CompanionTacticalAction action, UUID targetUuid) {
        this.server = server;
        this.ownerUuid = ownerUuid;
        this.companionUuid = companionUuid;
        this.action = action;
        this.targetUuid = targetUuid;
    }

    public MinecraftServer server() {
        return server;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public UUID companionUuid() {
        return companionUuid;
    }

    public CompanionTacticalAction action() {
        return action;
    }

    public Optional<UUID> targetUuid() {
        return Optional.ofNullable(targetUuid);
    }
}
