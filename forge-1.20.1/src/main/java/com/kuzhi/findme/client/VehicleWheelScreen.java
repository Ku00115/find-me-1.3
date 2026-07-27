package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionTeamAction;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.network.CompanionTeamCommandPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.VehicleCommandPacket;
import com.kuzhi.findme.network.VehicleListPacket;
import com.kuzhi.findme.common.VehicleCommandAction;
import com.kuzhi.findme.common.FindMeWheelStyle;
import com.kuzhi.findme.common.FindMeSettingsAction;
import com.kuzhi.findme.common.FindMeUiSettings;
import com.kuzhi.findme.network.FindMeSettingsPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class VehicleWheelScreen extends FindMeScreen {
    private static final int FADE_TICKS = 6;
    private int hoveredIndex = -1;
    private int selectedHoverIndex = -1;
    private int page;
    private boolean selectionRestored;
    private int openTicks;
    private boolean centerCancelHovered;
    private final FindMeEntityPreviewRenderer previewRenderer = new FindMeEntityPreviewRenderer();
    private final ClientWheelPageTransition pageTransition = new ClientWheelPageTransition();

    public VehicleWheelScreen() {
        super(Component.translatable("screen.find_me.vehicle_wheel"));
        this.page = ClientWheelSelectionMemory.page(ClientWheelSelectionMemory.Wheel.VEHICLE);
    }

    @Override
    protected void init() {
        if (ModNetwork.channel != null) {
            ModNetwork.sendToServer(new FindMeSettingsPacket(FindMeSettingsAction.SYNC,
                    FindMeUiSettings.defaults(), true, ""));
        }
        List<VehicleListPacket.Entry> entries = ClientVehicleState.wheelEntries();
        CompanionWheelLayout.Page visiblePage = CompanionWheelLayout.page(entries.size(), this.page);
        CompanionDetailPreviewRenderer.enqueueWarm(entries.subList(visiblePage.start(), visiblePage.end()).stream()
                .map(VehicleListPacket.Entry::asPreviewEntry).toList(), "minecraft:boat");
        this.send(VehicleCommandAction.SYNC, null, -1);
        this.syncTeams();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.openTicks < FADE_TICKS) {
            ++this.openTicks;
        }
        this.pageTransition.tick();
        CompanionWheelScreen.syncMovementKeys();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        FindMeWheelStyle layout = ClientWheelPresentationState.rosterLayout();
        this.centerCancelHovered = layout != FindMeWheelStyle.TACTICAL_STRIP
                && this.isCenterCancel(mouseX, mouseY);
        float openFade = this.fade(partialTick);
        ClientWheelPageTransition.Motion pageMotion = this.pageTransition.motion(partialTick);
        float fade = openFade * pageMotion.alpha();
        List<VehicleListPacket.Entry> entries = ClientVehicleState.wheelEntries();
        this.restoreSelection(entries);
        CompanionWheelLayout.Page pageState = CompanionWheelLayout.page(entries.size(), this.page);
        this.page = pageState.index();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int hoveredLocal = this.pageTransition.active() ? -1
                : CompanionWheelLayout.hoveredRosterSlot(layout, this.width, this.height,
                centerX, centerY, mouseX, mouseY, pageState.visibleSize());
        if (hoveredLocal >= 0) {
            this.hoveredIndex = pageState.start() + hoveredLocal;
            this.rememberSelection(entries.get(this.hoveredIndex), this.hoveredIndex);
        } else if (this.hoveredIndex < pageState.start() || this.hoveredIndex >= pageState.end()) {
            this.hoveredIndex = -1;
        }
        hoveredLocal = this.hoveredIndex >= pageState.start() && this.hoveredIndex < pageState.end()
                ? this.hoveredIndex - pageState.start()
                : -1;
        if (this.hoveredIndex >= 0 && this.hoveredIndex != this.selectedHoverIndex) {
            this.selectedHoverIndex = this.hoveredIndex;
            this.playHoverSound();
        }
        int active = ClientVehicleState.activeIndex();
        int selectedLocal = active >= pageState.start() && active < pageState.end()
                ? active - pageState.start()
                : -1;
        int deployedMask = 0;
        int riddenMask = 0;
        for (int i = pageState.start(); i < pageState.end(); ++i) {
            VehicleListPacket.Entry entry = entries.get(i);
            int bit = 1 << (i - pageState.start());
            if (entry.deployed() || entry.ridden()) deployedMask |= bit;
            if (entry.ridden()) riddenMask |= bit;
        }
        FindMeWheelRenderer.drawFieldScrim(graphics, this.width, this.height, openFade);
        this.beginFadeTransform(graphics, centerX, centerY, openFade, pageMotion);
        boolean auiBackdrop = FindMeAuiWheelBackdrop.drawRoster(graphics, this.width, this.height, layout,
                pageState.visibleSize(), hoveredLocal, selectedLocal, deployedMask, riddenMask, fade);
        if (!auiBackdrop) {
            if (layout == FindMeWheelStyle.TACTICAL_STRIP) {
                FindMeWheelRenderer.drawRosterRail(graphics, this.width, this.height, fade);
            } else if (layout == FindMeWheelStyle.CLASSIC_RADIAL) {
                FindMeWheelRenderer.drawBackdrop(graphics, centerX, centerY, fade);
            }
        }
        FindMeWheelRenderer.drawRosterHeader(graphics, this.title, pageState.index(), pageState.count(), layout, fade);
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.find_me.no_vehicles"), centerX, centerY + 56, FindMeWheelRenderer.guiColor(0xFFFFD166, fade));
            graphics.pose().popPose();
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        for (int i = pageState.start(); i < pageState.end(); ++i) {
            int local = i - pageState.start();
            CompanionWheelLayout.RosterSlot slot = CompanionWheelLayout.rosterSlot(layout, this.width, this.height,
                    centerX, centerY, local, pageState.visibleSize());
            VehicleListPacket.Entry entry = entries.get(i);
            boolean hovered = i == this.hoveredIndex;
            boolean selected = i == active;
            boolean deployed = entry.ridden() || entry.deployed();
            if (!auiBackdrop) {
                if (layout == FindMeWheelStyle.CLASSIC_RADIAL) {
                    FindMeWheelRenderer.drawSlotSegment(graphics, centerX, centerY, local,
                            pageState.visibleSize(), hovered, deployed, selected, fade);
                } else {
                    FindMeWheelRenderer.drawRosterCardBase(graphics, layout, slot, hovered, deployed, entry.ridden(),
                            selected, fade);
                }
            }
            CompanionWheelLayout.PreviewBounds preview = CompanionWheelLayout.rosterPreviewBounds(layout, slot,
                    hovered);
            if (pageMotion.alpha() > 0.08f) {
                this.previewRenderer.renderFittedPreview(graphics, entry.asPreviewEntry(), preview.left(), preview.top(),
                        preview.width(), preview.height(), "minecraft:boat");
            }
        }
        int focused = this.hoveredIndex >= 0 ? this.hoveredIndex : active;
        if (focused < pageState.start() || focused >= pageState.end()) focused = pageState.start();
        if (pageMotion.alpha() > 0.08f && focused >= 0 && focused < entries.size()) {
            VehicleListPacket.Entry focus = entries.get(focused);
            if (layout == FindMeWheelStyle.SIX_WING || layout == FindMeWheelStyle.FOLDED_SHARDS) {
                this.previewRenderer.renderFittedPreview(graphics, focus.asPreviewEntry(), centerX - 25, centerY - 39,
                        50, 61, "minecraft:boat");
            } else {
                int left = CompanionWheelLayout.viewportLeft(this.width);
                int top = CompanionWheelLayout.viewportTop(this.height);
                this.previewRenderer.renderFittedPreview(graphics, focus.asPreviewEntry(),
                        left + CompanionWheelLayout.VIEWPORT_WIDTH - 119, top + 34, 94, 92, "minecraft:boat");
            }
        }
        RenderSystem.disableDepthTest();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 260.0f);
        try {
            for (int i = pageState.start(); i < pageState.end(); ++i) {
                int local = i - pageState.start();
                CompanionWheelLayout.RosterSlot slot = CompanionWheelLayout.rosterSlot(layout, this.width, this.height,
                        centerX, centerY, local, pageState.visibleSize());
                VehicleListPacket.Entry entry = entries.get(i);
                boolean hovered = i == this.hoveredIndex;
                float health = 0.0f;
                if (layout == FindMeWheelStyle.CLASSIC_RADIAL) {
                    FindMeWheelRenderer.drawClassicSlotText(graphics, slot, local + 1, entry.name(), entry.alive(),
                            health, false, fade);
                } else {
                    FindMeWheelRenderer.drawRosterCardText(graphics, slot, local + 1, entry.name(), hovered,
                            entry.deployed() || entry.ridden(), entry.ridden(), i == active, entry.alive(), health,
                            false, fade);
                }
            }
            if (focused >= 0 && focused < entries.size()) {
                VehicleListPacket.Entry focus = entries.get(focused);
                Component state = focus.ridden() ? Component.translatable("screen.find_me.wheel_state_riding")
                        : focus.deployed() ? Component.translatable("screen.find_me.wheel_state_deployed")
                        : focused == active ? Component.translatable("screen.find_me.wheel_state_pending")
                        : Component.translatable("screen.find_me.wheel_state_ready");
                FindMeWheelRenderer.drawFocusedDossier(graphics, layout, this.width, this.height, centerX, centerY,
                        focus.name(), focused + 1, entries.size(), state, fade);
            }
        } finally {
            graphics.pose().popPose();
            RenderSystem.disableDepthTest();
        }
        graphics.pose().popPose();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    public void confirmSelection() {
        this.pageTransition.finish();
        if (this.centerCancelHovered) {
            Minecraft.getInstance().setScreen(null);
        } else if (this.hoveredIndex >= 0) {
            this.selectPendingSlot(this.hoveredIndex);
            Minecraft.getInstance().setScreen(null);
        } else {
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (FindMeUiKeys.isPrimaryMouse(button)) this.pageTransition.finish();
        if (FindMeUiKeys.isPrimaryMouse(button) && this.isCenterCancel(mouseX, mouseY)) {
            this.playConfirmSound();
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        if (FindMeUiKeys.isPrimaryMouse(button) && this.hoveredIndex >= 0) {
            return this.activateSlot(this.hoveredIndex);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0.0 || this.pageTransition.active()) return true;
        List<UUID> wheelUuids = ClientVehicleState.allEntries().stream().map(VehicleListPacket.Entry::uuid).toList();
        int team = ClientCompanionTeamState.peekNextNonEmptyTeam(CompanionTeamTarget.VEHICLE, wheelUuids, delta);
        if (team >= 0) {
            this.pageTransition.start(delta, () -> {
                ClientCompanionTeamState.selectTeam(CompanionTeamTarget.VEHICLE, team);
                this.page = 0;
                this.selectFirstEntry();
                this.playHoverSound();
            });
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int numberSlot = FindMeUiKeys.numberSlot(keyCode);
        if (numberSlot >= 0) {
            this.pageTransition.finish();
            List<VehicleListPacket.Entry> entries = ClientVehicleState.wheelEntries();
            CompanionWheelLayout.Page pageState = CompanionWheelLayout.page(entries.size(), this.page);
            int target = pageState.start() + numberSlot;
            if (numberSlot < pageState.visibleSize() && target < entries.size()) {
                this.hoveredIndex = target;
                this.selectedHoverIndex = target;
                this.rememberSelection(entries.get(target), target);
                return this.activateSlot(target);
            }
            return true;
        }
        if (FindMeUiKeys.isWheelModeSwitch(keyCode)) {
            this.pageTransition.finish();
            Minecraft.getInstance().setScreen(new CompanionWheelScreen(CompanionKind.MOUNT));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void beginFadeTransform(GuiGraphics graphics, int centerX, int centerY, float fade,
                                    ClientWheelPageTransition.Motion pageMotion) {
        float scale = (0.96f + fade * 0.04f) * pageMotion.scale();
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY + pageMotion.offsetY(), 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-centerX, -centerY, 0.0f);
    }

    private float fade(float partialTick) {
        float progress = Math.min((float)FADE_TICKS, (float)this.openTicks + partialTick) / (float)FADE_TICKS;
        return progress * progress * (3.0f - 2.0f * progress);
    }

    private void drawCurrentHint(GuiGraphics graphics, List<VehicleListPacket.Entry> entries, int centerX, int centerY, CompanionWheelLayout.Page pageState, float fade) {
        int index = this.hoveredIndex >= 0 ? this.hoveredIndex : ClientVehicleState.activeIndex();
        if (index < 0 || index >= entries.size()) {
            index = pageState.start();
        }
        if (index < 0 || index >= entries.size()) {
            return;
        }
        VehicleListPacket.Entry entry = entries.get(index);
        Component label = index == ClientVehicleState.activeIndex()
                ? Component.translatable("screen.find_me.wheel_current", trim(entry.name(), 16), index + 1, entries.size())
                : Component.translatable("screen.find_me.wheel_pending", trim(entry.name(), 16));
        graphics.drawCenteredString(this.font, label, centerX, centerY + CompanionWheelLayout.BACKDROP_RADIUS + 12, FindMeWheelRenderer.guiColor(0xFFFFFFFF, fade));
    }

    private void drawModeHint(GuiGraphics graphics, int centerX, int centerY, float fade) {
        Component label = Component.translatable("screen.find_me.wheel_mode_hint");
        graphics.drawCenteredString(this.font, label, centerX, centerY - CompanionWheelLayout.BACKDROP_RADIUS - 17, FindMeWheelRenderer.guiColor(0xFFFFFFFF, fade));
    }

    private boolean isCenterCancel(double mouseX, double mouseY) {
        double dx = mouseX - (double)(this.width / 2);
        double dy = mouseY - (double)(this.height / 2);
        double radius = CompanionWheelLayout.CENTER_CANCEL_RADIUS;
        return dx * dx + dy * dy <= radius * radius;
    }

    private void send(VehicleCommandAction action, UUID targetUuid, int position) {
        if (ModNetwork.channel != null) {
            if (action == VehicleCommandAction.SELECT_SUMMON) {
                ClientCameraLock.arm();
            }
            ModNetwork.sendToServer(new VehicleCommandPacket(action, targetUuid, position));
        }
    }

    private void syncTeams() {
        if (ModNetwork.channel != null) {
            ModNetwork.sendToServer(new CompanionTeamCommandPacket(CompanionTeamAction.SYNC, CompanionTeamTarget.VEHICLE, -1, -1, "", List.of()));
        }
    }

    private void applyTeam(int team) {
        if (ModNetwork.channel != null) {
            ModNetwork.sendToServer(new CompanionTeamCommandPacket(CompanionTeamAction.APPLY, CompanionTeamTarget.VEHICLE, team, -1, "", List.of()));
        }
    }

    private void restoreSelection(List<VehicleListPacket.Entry> entries) {
        if (this.selectionRestored || entries.isEmpty()) {
            return;
        }
        int restored = ClientWheelSelectionMemory.resolve(ClientWheelSelectionMemory.Wheel.VEHICLE,
                entries.stream().map(VehicleListPacket.Entry::uuid).toList());
        if (restored >= 0) {
            this.hoveredIndex = restored;
            this.selectedHoverIndex = restored;
            this.page = restored / CompanionWheelLayout.PAGE_SIZE;
        }
        this.selectionRestored = true;
    }

    private void rememberSelection(VehicleListPacket.Entry entry, int index) {
        ClientWheelSelectionMemory.remember(ClientWheelSelectionMemory.Wheel.VEHICLE, entry.uuid(), index, this.page);
    }

    private void selectFirstEntry() {
        List<VehicleListPacket.Entry> entries = ClientVehicleState.wheelEntries();
        CompanionWheelLayout.Page pageState = CompanionWheelLayout.page(entries.size(), this.page);
        this.page = pageState.index();
        this.hoveredIndex = pageState.visibleSize() > 0 ? pageState.start() : -1;
        this.selectedHoverIndex = this.hoveredIndex;
        if (this.hoveredIndex >= 0) {
            this.rememberSelection(entries.get(this.hoveredIndex), this.hoveredIndex);
        } else {
            ClientWheelSelectionMemory.rememberPage(ClientWheelSelectionMemory.Wheel.VEHICLE, this.page);
        }
    }

    private void playHoverSound() {
        if (!ClientWheelPresentationState.operationSounds()) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.45f, 0.22f));
    }

    private void playConfirmSound() {
        if (!ClientWheelPresentationState.operationSounds()) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.42f));
    }

    private static String trim(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(1, max - 1)) + "...";
    }

    private boolean activateSlot(int index) {
        List<VehicleListPacket.Entry> entries = ClientVehicleState.wheelEntries();
        if (index < 0 || index >= entries.size()) {
            return true;
        }
        this.rememberSelection(entries.get(index), index);
        this.playConfirmSound();
        VehicleListPacket.Entry entry = entries.get(index);
        int previewIndex = ClientCompanionTeamState.currentMemberIndex(CompanionTeamTarget.VEHICLE, entry.uuid());
        int serverIndex = previewIndex >= 0 ? previewIndex : ClientVehicleState.serverWheelIndex(entry.uuid());
        if (serverIndex < 0) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        if (previewIndex >= 0) {
            this.applyTeam(ClientCompanionTeamState.currentTeam(CompanionTeamTarget.VEHICLE));
        }
        int active = ClientVehicleState.activeIndex();
        if ((entry.ridden() || entry.deployed()) && index == active) {
            this.send(VehicleCommandAction.RECALL, entry.uuid(), -1);
        } else {
            this.send(VehicleCommandAction.SELECT_SUMMON, entry.uuid(), -1);
        }
        Minecraft.getInstance().setScreen(null);
        return true;
    }

    private boolean selectPendingSlot(int index) {
        List<VehicleListPacket.Entry> entries = ClientVehicleState.wheelEntries();
        if (index < 0 || index >= entries.size()) {
            return true;
        }
        VehicleListPacket.Entry entry = entries.get(index);
        this.rememberSelection(entry, index);
        this.playConfirmSound();
        int previewIndex = ClientCompanionTeamState.currentMemberIndex(CompanionTeamTarget.VEHICLE, entry.uuid());
        int serverIndex = previewIndex >= 0 ? previewIndex : ClientVehicleState.serverWheelIndex(entry.uuid());
        if (serverIndex < 0) {
            return true;
        }
        if (previewIndex >= 0) {
            this.applyTeam(ClientCompanionTeamState.currentTeam(CompanionTeamTarget.VEHICLE));
        }
        this.send(VehicleCommandAction.SELECT, entry.uuid(), -1);
        return true;
    }

}
