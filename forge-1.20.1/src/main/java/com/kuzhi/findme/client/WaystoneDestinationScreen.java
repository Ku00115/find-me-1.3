package com.kuzhi.findme.client;

import com.kuzhi.findme.compat.waystones.WaystoneDestination;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.WaystoneJourneyRequestPacket;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class WaystoneDestinationScreen extends Screen {
    private static final int PAGE_SIZE = 7;
    private final UUID mountUuid;
    private final List<WaystoneDestination> destinations;
    private int page;

    private WaystoneDestinationScreen(UUID mountUuid, List<WaystoneDestination> destinations) {
        super(Component.translatable("screen.find_me.waystone_destinations"));
        this.mountUuid = mountUuid;
        this.destinations = destinations == null ? List.of() : List.copyOf(destinations);
    }

    public static void open(UUID mountUuid, List<WaystoneDestination> destinations) {
        Minecraft.getInstance().setScreen(new WaystoneDestinationScreen(mountUuid, destinations));
    }

    @Override
    protected void init() {
        clearWidgets();
        int start = page * PAGE_SIZE;
        int center = width / 2;
        for (int i = 0; i < PAGE_SIZE && start + i < destinations.size(); i++) {
            WaystoneDestination value = destinations.get(start + i);
            String dimension = value.dimension().location().getPath();
            addRenderableWidget(Button.builder(Component.literal(value.name() + "  [" + dimension + "]"),
                    button -> select(value)).bounds(center - 120, 46 + i * 25, 240, 21).build());
        }
        if (page > 0) addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuildWidgets(); }).bounds(center - 120, height - 32, 40, 20).build());
        if ((page + 1) * PAGE_SIZE < destinations.size()) addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuildWidgets(); }).bounds(center + 80, height - 32, 40, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose()).bounds(center - 50, height - 32, 100, 20).build());
    }

    private void select(WaystoneDestination destination) {
        ModNetwork.sendToServer(new WaystoneJourneyRequestPacket(mountUuid, destination.uuid()));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
        if (destinations.isEmpty()) graphics.drawCenteredString(font, Component.translatable("screen.find_me.waystone_empty"), width / 2, height / 2, 0xFFB9C2C7);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
