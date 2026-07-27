package com.kuzhi.findme.network;

import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

final class NetworkCodecs {
    private NetworkCodecs() {
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> of(BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder) {
        return new StreamCodec<>() {
            @Override
            public T decode(RegistryFriendlyByteBuf buffer) {
                return decoder.apply(buffer);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, T packet) {
                encoder.accept(packet, buffer);
            }
        };
    }
}
