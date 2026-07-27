package com.kuzhi.findme.network;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Keeps packet handlers loader-neutral while Forge owns the transport context.
 */
public final class FindMeNetworkContext {
    private FindMeNetworkContext() {
    }

    public static final class Context {
        private final NetworkEvent.Context forgeContext;

        private Context(NetworkEvent.Context forgeContext) {
            this.forgeContext = forgeContext;
        }

        public static Context of(NetworkEvent.Context forgeContext) {
            return new Context(forgeContext);
        }

        public ServerPlayer getSender() {
            return this.forgeContext.getSender();
        }

        public boolean isServerReception() {
            return this.forgeContext.getDirection().getReceptionSide().isServer();
        }

        public CompletableFuture<Void> enqueueWork(Runnable task) {
            return this.forgeContext.enqueueWork(task);
        }

        public void setPacketHandled(boolean handled) {
            this.forgeContext.setPacketHandled(handled);
        }
    }
}
