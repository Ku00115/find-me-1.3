package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.ClientPackAnimationPresetState;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.CompanionRescueMotion;
import com.kuzhi.findme.common.PackAnimationPresetCategory;
import com.kuzhi.findme.common.PackEntityCategoryOverride;
import com.kuzhi.findme.common.PackEntityMovementOverride;
import com.kuzhi.findme.common.PackEntityBindingRequirement;
import com.kuzhi.findme.common.BindingAnimationPolicy;
import com.kuzhi.findme.common.MountInteractionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PackAnimationPresetListPacket(boolean replace, List<Entry> entries) implements CustomPacketPayload {
    private static final int MAX_PREVIEW_NBT_LENGTH = 32767;
    public static final Type<PackAnimationPresetListPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "pack_animation_presets"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PackAnimationPresetListPacket> STREAM_CODEC = NetworkCodecs.of(PackAnimationPresetListPacket::encode, PackAnimationPresetListPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(PackAnimationPresetListPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.replace);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeUtf(entry.entityType, 128);
            buffer.writeUtf(entry.name, 128);
            buffer.writeEnum(entry.category);
            buffer.writeEnum(entry.categoryOverride);
            buffer.writeEnum(entry.movementOverride);
            buffer.writeEnum(entry.bindingRequirement);
            buffer.writeEnum(entry.mountInteractionPolicy);
            buffer.writeEnum(entry.bindingAnimationPolicy);
            buffer.writeEnum(entry.rescueMotion);
            buffer.writeEnum(entry.summonAnimation);
            buffer.writeEnum(entry.rescueAnimation);
            buffer.writeEnum(entry.storageAnimation);
            buffer.writeEnum(entry.switchAnimation);
            buffer.writeFloat(entry.boundsScale);
            buffer.writeFloat(entry.circleScale);
            buffer.writeUtf(entry.arrivalSound, 128);
            buffer.writeFloat(entry.arrivalVolume);
            buffer.writeFloat(entry.arrivalPitch);
            buffer.writeEnum(entry.summonStyle);
            buffer.writeEnum(entry.rescueStyle);
            buffer.writeEnum(entry.storageStyle);
            buffer.writeUtf(entry.previewNbt, MAX_PREVIEW_NBT_LENGTH);
        }
    }

    public static PackAnimationPresetListPacket decode(FriendlyByteBuf buffer) {
        boolean replace = buffer.readBoolean();
        int size = buffer.readVarInt();
        ArrayList<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer.readUtf(128), buffer.readUtf(128), buffer.readEnum(PackAnimationPresetCategory.class),
                    buffer.readEnum(PackEntityCategoryOverride.class), buffer.readEnum(PackEntityMovementOverride.class),
                    buffer.readEnum(PackEntityBindingRequirement.class),
                    buffer.readEnum(MountInteractionPolicy.class),
                    buffer.readEnum(BindingAnimationPolicy.class),
                    buffer.readEnum(CompanionRescueMotion.class), buffer.readEnum(CompanionAnimationStyle.class),
                    buffer.readEnum(CompanionAnimationStyle.class), buffer.readEnum(CompanionAnimationStyle.class),
                    buffer.readEnum(CompanionAnimationStyle.class), buffer.readFloat(), buffer.readFloat(), buffer.readUtf(128),
                    buffer.readFloat(), buffer.readFloat(), buffer.readEnum(CompanionEffectStyle.class),
                    buffer.readEnum(CompanionEffectStyle.class), buffer.readEnum(CompanionEffectStyle.class),
                    buffer.readUtf(MAX_PREVIEW_NBT_LENGTH)));
        }
        return new PackAnimationPresetListPacket(replace, entries);
    }

    public static void handle(PackAnimationPresetListPacket packet, Supplier<FindMeNetworkContext.Context> supplier) {
        FindMeNetworkContext.Context context = supplier.get();
        context.enqueueWork(() -> ClientPackAnimationPresetState.update(packet.replace, packet.entries));
        context.setPacketHandled(true);
    }

    public record Entry(String entityType, String name, PackAnimationPresetCategory category,
                        PackEntityCategoryOverride categoryOverride, PackEntityMovementOverride movementOverride,
                        PackEntityBindingRequirement bindingRequirement,
                        MountInteractionPolicy mountInteractionPolicy,
                        BindingAnimationPolicy bindingAnimationPolicy,
                        CompanionRescueMotion rescueMotion, CompanionAnimationStyle summonAnimation,
                        CompanionAnimationStyle rescueAnimation, CompanionAnimationStyle storageAnimation,
                        CompanionAnimationStyle switchAnimation, float boundsScale, float circleScale, String arrivalSound,
                        float arrivalVolume, float arrivalPitch, CompanionEffectStyle summonStyle,
                        CompanionEffectStyle rescueStyle, CompanionEffectStyle storageStyle, String previewNbt) {
    }
}
