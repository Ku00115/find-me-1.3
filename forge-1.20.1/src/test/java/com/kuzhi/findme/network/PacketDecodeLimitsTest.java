package com.kuzhi.findme.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kuzhi.findme.common.CompanionTeamAction;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.server.lifecycle.CompanionDeploymentPlan;
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

    @Test
    void rejectsOversizedTacticalFormationBeforeAllocation() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUUID(java.util.UUID.randomUUID());
            buffer.writeEnum(CompanionDeploymentPlan.Intent.values()[0]);
            buffer.writeDouble(0.0);
            buffer.writeDouble(0.0);
            buffer.writeDouble(0.0);
            buffer.writeFloat(0.0f);
            buffer.writeVarInt(0);
            buffer.writeVarInt(PacketDecodeLimits.MAX_TACTICAL_FORMATION_MEMBERS + 1);
            assertThrows(DecoderException.class, () -> CompanionTacticalFormationPacket.decode(buffer));
        } finally {
            buffer.release();
        }
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
