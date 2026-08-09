package com.kuzhi.findme.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kuzhi.findme.common.CompanionTeamAction;
import com.kuzhi.findme.common.CompanionTeamTarget;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class PacketDecodeLimitsTest {
    @Test
    void rejectsNegativeAndOversizedCountsBeforeAllocation() {
        assertInvalidCount(-1);
        assertInvalidCount(PacketDecodeLimits.MAX_TEAM_MEMBERS + 1);
    }

    @Test
    void capsCollectionInitialCapacity() {
        assertEquals(256, PacketDecodeLimits.initialCapacity(Integer.MAX_VALUE));
        assertEquals(12, PacketDecodeLimits.initialCapacity(12));
    }

    private static void assertInvalidCount(int count) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeEnum(CompanionTeamAction.values()[0]);
            buffer.writeEnum(CompanionTeamTarget.values()[0]);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeUtf("");
            buffer.writeVarInt(count);
            assertThrows(DecoderException.class, () -> CompanionTeamCommandPacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
