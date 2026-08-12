package com.kuzhi.findme.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

final class PacketDecodeLimits {
    static final int MAX_ROSTER_ENTRIES = 4096;
    static final int MAX_TEAM_ENTRIES = 256;
    static final int MAX_TEAM_MEMBERS = 4096;
    static final int MAX_PRESET_ENTRIES = 4096;
    static final int MAX_PAGE_ENTRIES = 4096;
    static final int MAX_ARGUMENTS = 64;
    static final int MAX_TACTICAL_FORMATION_MEMBERS = 256;

    private PacketDecodeLimits() {
    }

    static int readCount(FriendlyByteBuf buffer, int maximum, String label) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new DecoderException("Invalid FindMe " + label + " count: " + count);
        }
        return count;
    }

    static int initialCapacity(int count) {
        return Math.min(count, 256);
    }
}
