package com.kuzhi.findme.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kuzhi.findme.api.CompanionMagicState;
import com.kuzhi.findme.api.CompanionSpellBinding;
import com.kuzhi.findme.api.CompanionSpellRole;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.CompanionMoveType;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CompanionListPacketTest {
    @Test
    void entryCanonicalizesSpellBindingsToThreeSlots() {
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:paper");
        item.putByte("Count", (byte)64);
        CompanionSpellBinding binding = new CompanionSpellBinding(ResourceLocation.fromNamespaceAndPath("test", "provider"),
                ResourceLocation.fromNamespaceAndPath("test", "spell"), null, CompanionSpellRole.UTILITY,
                "Test", 1, item);

        CompanionListPacket.Entry entry = new CompanionListPacket.Entry(UUID.randomUUID(), -1,
                "minecraft:wolf", "Wolf", false, true, false, false, false, false, null,
                20.0F, 20.0F, 0.0F, CompanionMoveType.WALK,
                CompanionAnimationStyle.values()[0], CompanionAnimationStyle.values()[0],
                CompanionAnimationStyle.values()[0], CompanionAnimationStyle.values()[0],
                CompanionEffectStyle.values()[0], CompanionEffectStyle.values()[0],
                CompanionEffectStyle.values()[0], List.of(binding, binding, binding, binding),
                CompanionMagicState.EMPTY, new CompoundTag());

        assertEquals(3, entry.spellBindings().size());
        assertEquals(1, entry.spellBindings().get(0).itemTag().getByte("Count"));
        assertEquals(1, entry.spellBindings().get(0).itemTag().getInt("count"));
    }
}
