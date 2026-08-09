package com.kuzhi.findme.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionMoveType;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class RecoveryCompanionListPacketTest {
    @Test
    void roundTripsRecoveryDiagnosticsAndPreview() {
        CompoundTag preview = new CompoundTag();
        preview.putString("id", "minecraft:horse");
        RecoveryCompanionListPacket.Entry entry = new RecoveryCompanionListPacket.Entry(
                UUID.randomUUID(), CompanionKind.MOUNT, "minecraft:horse", "Lost mount", UUID.randomUUID(),
                2, 100L, 120L, "minecraft:overworld", 10.0, 64.0, -3.0,
                "discarded", "missing snapshot", CompanionLifecycleState.DEPLOYED,
                CompanionMoveType.WALK, preview);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RecoveryCompanionListPacket.encode(new RecoveryCompanionListPacket(List.of(entry)), buffer);
            RecoveryCompanionListPacket decoded = RecoveryCompanionListPacket.decode(buffer);

            assertEquals(1, decoded.entries().size());
            assertEquals(entry, decoded.entries().get(0));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsOversizedRecoveryListsBeforeAllocation() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(PacketDecodeLimits.MAX_ROSTER_ENTRIES + 1);
            assertThrows(DecoderException.class, () -> RecoveryCompanionListPacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
