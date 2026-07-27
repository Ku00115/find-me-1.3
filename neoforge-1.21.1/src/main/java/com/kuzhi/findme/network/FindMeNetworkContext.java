package com.kuzhi.findme.network;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Small NeoForge adapter that lets migrated packet handlers share the same
 * sender/work-queue shape without depending on Forge's NetworkEvent API.
 */
public final class FindMeNetworkContext {
    private FindMeNetworkContext() {
    }

    public static final class Context {
        private final IPayloadContext payloadContext;

        private Context(IPayloadContext payloadContext) {
            this.payloadContext = payloadContext;
        }

        public static Context of(IPayloadContext payloadContext) {
            return new Context(payloadContext);
        }

        public ServerPlayer getSender() {
            Player player = this.payloadContext.player();
            return player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        }

        public CompletableFuture<Void> enqueueWork(Runnable task) {
            return this.payloadContext.enqueueWork(task);
        }

        public void setPacketHandled(boolean handled) {
            // NeoForge payload handlers complete through their returned future.
        }
    }
}
