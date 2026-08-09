package com.kuzhi.findme.api.event;

import com.kuzhi.findme.api.CompanionDescriptor;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

/** Posted on the NeoForge event bus after a changed FindMe player root is committed. */
public final class CompanionLifecycleEvent extends Event {
    private final Change change;
    private final MinecraftServer server;
    private final UUID ownerUuid;
    private final UUID companionUuid;
    private final CompanionDescriptor before;
    private final CompanionDescriptor after;
    private final long revision;

    public CompanionLifecycleEvent(MinecraftServer server, Change change, UUID ownerUuid, UUID companionUuid,
                                   CompanionDescriptor before, CompanionDescriptor after, long revision) {
        this.server = server;
        this.change = change;
        this.ownerUuid = ownerUuid;
        this.companionUuid = companionUuid;
        this.before = before;
        this.after = after;
        this.revision = Math.max(0L, revision);
    }

    public MinecraftServer server() {
        return server;
    }

    public Change change() {
        return change;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public UUID companionUuid() {
        return companionUuid;
    }

    public Optional<CompanionDescriptor> before() {
        return Optional.ofNullable(before);
    }

    public Optional<CompanionDescriptor> after() {
        return Optional.ofNullable(after);
    }

    public long revision() {
        return revision;
    }

    public enum Change {
        REGISTERED,
        DEPLOYED,
        STORED,
        RELEASED,
        DIED,
        RECOVERED,
        HOME_ASSIGNED,
        HOME_CLEARED,
        LIFECYCLE_CHANGED
    }
}
