package com.kuzhi.findme.network;

import net.minecraft.resources.ResourceLocation;

import com.kuzhi.findme.FindMeMod;

import com.kuzhi.findme.api.CompanionSpellBinding;
import com.kuzhi.findme.api.CompanionMagicState;
import com.kuzhi.findme.client.ClientCompanionState;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.CompanionTacticalAction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import com.kuzhi.findme.network.FindMeNetworkContext;

public record CompanionListPacket(CompanionKind kind, long revision, int activeIndex,
                                  List<Entry> entries, List<Entry> allEntries) {
public static void encode(CompanionListPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum((Enum)packet.kind);
        buffer.writeLong(packet.revision);
        buffer.writeInt(packet.activeIndex);
        writeEntries(buffer, packet.entries);
        writeEntries(buffer, packet.allEntries);
    }

    private static void writeEntries(FriendlyByteBuf buffer, List<Entry> entries) {
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUUID(entry.uuid);
            buffer.writeInt(entry.entityId);
            buffer.writeUtf(entry.entityType, 128);
            buffer.writeUtf(entry.name, 128);
            buffer.writeBoolean(entry.loaded);
            buffer.writeBoolean(entry.alive);
            buffer.writeBoolean(entry.critical);
            buffer.writeBoolean(entry.deployed);
            buffer.writeBoolean(entry.ridden);
            buffer.writeBoolean(entry.hasHome);
            buffer.writeBoolean(entry.homeResident);
            buffer.writeBoolean(entry.tacticalAction != null);
            if (entry.tacticalAction != null) buffer.writeEnum(entry.tacticalAction);
            buffer.writeFloat(entry.health);
            buffer.writeFloat(entry.maxHealth);
            buffer.writeFloat(entry.armor);
            buffer.writeEnum((Enum)entry.moveType);
            buffer.writeEnum(entry.summonAnimation); buffer.writeEnum(entry.rescueAnimation);
            buffer.writeEnum(entry.storageAnimation); buffer.writeEnum(entry.switchAnimation);
            buffer.writeEnum(entry.summonStyle); buffer.writeEnum(entry.rescueStyle); buffer.writeEnum(entry.storageStyle);
            int bindingCount = Math.min(3, entry.spellBindings.size());
            buffer.writeVarInt(bindingCount);
            for (int slot = 0; slot < bindingCount; slot++) {
                CompanionSpellBinding binding = entry.spellBindings.get(slot);
                buffer.writeNbt(binding == null ? null : binding.saveMetadata());
            }
            buffer.writeFloat(entry.magicState.mana());
            buffer.writeFloat(entry.magicState.maxMana());
            buffer.writeNbt(entry.previewTag);
        }
    }

    public static CompanionListPacket decode(FriendlyByteBuf buffer) {
        CompanionKind kind = (CompanionKind)buffer.readEnum(CompanionKind.class);
        long revision = buffer.readLong();
        int activeIndex = buffer.readInt();
        ArrayList<Entry> entries = readEntries(buffer);
        ArrayList<Entry> allEntries = readEntries(buffer);
        return new CompanionListPacket(kind, revision, activeIndex, entries, allEntries);
    }

    private static ArrayList<Entry> readEntries(FriendlyByteBuf buffer) {
        int size = PacketDecodeLimits.readCount(buffer, PacketDecodeLimits.MAX_ROSTER_ENTRIES,
                "companion roster entry");
        ArrayList<Entry> entries = new ArrayList<Entry>(PacketDecodeLimits.initialCapacity(size));
        for (int i = 0; i < size; ++i) {
            UUID uuid = buffer.readUUID();
            int entityId = buffer.readInt();
            String entityType = buffer.readUtf(128);
            String name = buffer.readUtf(128);
            boolean loaded = buffer.readBoolean();
            boolean alive = buffer.readBoolean();
            boolean critical = buffer.readBoolean();
            boolean deployed = buffer.readBoolean();
            boolean ridden = buffer.readBoolean();
            boolean hasHome = buffer.readBoolean();
            boolean homeResident = buffer.readBoolean();
            CompanionTacticalAction tacticalAction = buffer.readBoolean()
                    ? buffer.readEnum(CompanionTacticalAction.class) : null;
            float health = buffer.readFloat();
            float maxHealth = buffer.readFloat();
            float armor = buffer.readFloat();
            CompanionMoveType moveType = buffer.readEnum(CompanionMoveType.class);
            CompanionAnimationStyle summonAnimation = buffer.readEnum(CompanionAnimationStyle.class);
            CompanionAnimationStyle rescueAnimation = buffer.readEnum(CompanionAnimationStyle.class);
            CompanionAnimationStyle storageAnimation = buffer.readEnum(CompanionAnimationStyle.class);
            CompanionAnimationStyle switchAnimation = buffer.readEnum(CompanionAnimationStyle.class);
            CompanionEffectStyle summonStyle = buffer.readEnum(CompanionEffectStyle.class);
            CompanionEffectStyle rescueStyle = buffer.readEnum(CompanionEffectStyle.class);
            CompanionEffectStyle storageStyle = buffer.readEnum(CompanionEffectStyle.class);
            List<CompanionSpellBinding> spellBindings = new ArrayList<>();
            int declaredBindingCount = buffer.readVarInt();
            if (declaredBindingCount < 0 || declaredBindingCount > 64) {
                throw new DecoderException("Invalid FindMe companion spell binding count: " + declaredBindingCount);
            }
            for (int slot = 0; slot < declaredBindingCount; slot++) {
                CompanionSpellBinding binding = CompanionSpellBinding.load(buffer.readNbt());
                if (slot < 3) spellBindings.add(binding);
            }
            CompanionMagicState magicState = new CompanionMagicState(buffer.readFloat(), buffer.readFloat());
            entries.add(new Entry(uuid, entityId, entityType, name, loaded, alive, critical, deployed, ridden, hasHome, homeResident, tacticalAction,
                    health, maxHealth, armor, moveType, summonAnimation, rescueAnimation, storageAnimation,
                    switchAnimation, summonStyle, rescueStyle, storageStyle,
                    spellBindings, magicState, buffer.readNbt()));
        }
        return entries;
    }

    public static void handle(CompanionListPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientCompanionState.update(packet.kind, packet.revision,
                packet.activeIndex, packet.entries, packet.allEntries));
        context.setPacketHandled(true);
    }

    public record Entry(UUID uuid, int entityId, String entityType, String name, boolean loaded, boolean alive,
                        boolean critical,
                        boolean deployed, boolean ridden, boolean hasHome, boolean homeResident,
                        CompanionTacticalAction tacticalAction, float health,
                        float maxHealth, float armor, CompanionMoveType moveType,
                        CompanionAnimationStyle summonAnimation, CompanionAnimationStyle rescueAnimation,
                        CompanionAnimationStyle storageAnimation, CompanionAnimationStyle switchAnimation,
                        CompanionEffectStyle summonStyle, CompanionEffectStyle rescueStyle,
                        CompanionEffectStyle storageStyle, List<CompanionSpellBinding> spellBindings,
                        CompanionMagicState magicState,
                        CompoundTag previewTag) {
        public Entry {
            spellBindings = spellBindings == null ? List.of()
                    : java.util.Collections.unmodifiableList(new ArrayList<>(
                            spellBindings.subList(0, Math.min(3, spellBindings.size()))));
            magicState = magicState == null ? CompanionMagicState.EMPTY : magicState;
        }
        public Entry(UUID uuid, int entityId, String entityType, String name, boolean loaded, boolean alive,
                     boolean deployed, boolean ridden, boolean hasHome, boolean homeResident,
                     CompanionTacticalAction tacticalAction, float health,
                     float maxHealth, float armor, CompanionMoveType moveType,
                     CompanionAnimationStyle summonAnimation, CompanionAnimationStyle rescueAnimation,
                     CompanionAnimationStyle storageAnimation, CompanionAnimationStyle switchAnimation,
                     CompanionEffectStyle summonStyle, CompanionEffectStyle rescueStyle,
                     CompanionEffectStyle storageStyle, CompoundTag previewTag) {
            this(uuid, entityId, entityType, name, loaded, alive, false, deployed, ridden, hasHome, homeResident,
                    tacticalAction, health, maxHealth, armor, moveType, summonAnimation, rescueAnimation,
                    storageAnimation, switchAnimation, summonStyle, rescueStyle, storageStyle,
                    List.of(), CompanionMagicState.EMPTY, previewTag);
        }
    }
}
