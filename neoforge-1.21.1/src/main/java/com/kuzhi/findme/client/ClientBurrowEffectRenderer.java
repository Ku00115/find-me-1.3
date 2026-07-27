package com.kuzhi.findme.client;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class ClientBurrowEffectRenderer {
    private ClientBurrowEffectRenderer() {
    }

    static void renderWorld(RenderLevelStageEvent event) {
        // Burrow dust is emitted as vanilla particles from ClientBurrowEffectState.tick().
    }
}
