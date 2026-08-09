package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionTeamAction;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.common.FindMeWheelStyle;
import com.kuzhi.findme.common.FindMeSettingsAction;
import com.kuzhi.findme.common.FindMeUiSettings;
import com.kuzhi.findme.common.MountRosterSource;
import com.kuzhi.findme.common.MountRosterAction;
import com.kuzhi.findme.common.VehicleCommandAction;
import com.kuzhi.findme.network.CompanionCommandPacket;
import com.kuzhi.findme.network.CompanionWheelIntentPacket;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.CompanionTeamCommandPacket;
import com.kuzhi.findme.network.VehicleCommandPacket;
import com.kuzhi.findme.network.VehicleListPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.FindMeSettingsPacket;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class CompanionWheelScreen
extends FindMeScreen {
    private static final int FADE_TICKS = 6;
    private final CompanionKind kind;
    private int hoveredIndex = -1;
    private int selectedHoverIndex = -1;
    private int page;
    private boolean selectionRestored;
    private int openTicks;
    private boolean followCommandMode;
    private final BlockPos commandAimPosition;
    private final int commandAimEntityId;
    private final FindMeEntityPreviewRenderer previewRenderer = new FindMeEntityPreviewRenderer();
    private final ClientWheelPageTransition pageTransition = new ClientWheelPageTransition();

    public CompanionWheelScreen(CompanionKind kind) {
        super((Component)Component.translatable((String)(kind == CompanionKind.MOUNT ? "screen.find_me.mount_wheel" : "screen.find_me.companion_wheel")));
        this.kind = kind;
        this.page = ClientWheelSelectionMemory.page(this.memoryWheel());
        CompanionCommandWheelScreen.AimSnapshot aim = CompanionCommandWheelScreen.captureAim(null);
        this.commandAimPosition = aim.position();
        this.commandAimEntityId = aim.entityId();
    }

    public CompanionKind kind() {
        return this.kind;
    }

    protected void init() {
        if (ModNetwork.channel != null) {
            ModNetwork.sendToServer(new FindMeSettingsPacket(FindMeSettingsAction.SYNC,
                    FindMeUiSettings.defaults(), true, ""));
        }
        if (this.kind == CompanionKind.MOUNT) {
            List<WheelEntry> entries = this.mergedMountEntries();
            CompanionWheelLayout.Page visiblePage = CompanionWheelLayout.page(entries.size(), this.page);
            CompanionDetailPreviewRenderer.enqueueWarm(entries.subList(visiblePage.start(), visiblePage.end()).stream()
                    .filter(entry -> !entry.empty())
                    .map(WheelEntry::asPreviewEntry)
                    .toList(), this.fallbackPreviewType());
        } else {
            List<CompanionListPacket.Entry> entries = this.wheelEntries();
            CompanionWheelLayout.Page visiblePage = CompanionWheelLayout.page(entries.size(), this.page);
            CompanionDetailPreviewRenderer.enqueueWarm(entries.subList(visiblePage.start(), visiblePage.end()),
                    this.fallbackPreviewType());
        }
        this.send(CompanionAction.SYNC, -1);
        if (this.kind == CompanionKind.MOUNT) {
            ModNetwork.sendToServer(new VehicleCommandPacket(VehicleCommandAction.SYNC, null, -1));
        }
        this.syncTeams();
    }

    public void tick() {
        super.tick();
        if (this.openTicks < FADE_TICKS) {
            ++this.openTicks;
        }
        this.pageTransition.tick();
        CompanionWheelScreen.syncMovementKeys();
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        FindMeWheelStyle layout = ClientWheelPresentationState.rosterLayout();
        float openFade = this.fade(partialTick);
        ClientWheelPageTransition.Motion pageMotion = this.pageTransition.motion(partialTick);
        float fade = openFade * pageMotion.alpha();
        List<CompanionListPacket.Entry> entries = this.wheelEntries();
        int centerX = this.wheelCenterX();
        int centerY = this.wheelCenterY();
        if (this.kind == CompanionKind.MOUNT) {
            this.renderMergedMountWheel(graphics, mouseX, mouseY, partialTick, openFade, fade, pageMotion,
                    centerX, centerY);
            return;
        }
        this.restoreSelection(entries.stream().map(CompanionListPacket.Entry::uuid).toList());
        CompanionWheelLayout.Page pageState = CompanionWheelLayout.page(entries.size(), this.page);
        this.page = pageState.index();
        int hoveredLocal = this.pageTransition.active() ? -1
                : CompanionWheelLayout.hoveredRosterSlot(layout, this.width, this.height,
                centerX, centerY, mouseX, mouseY, pageState.visibleSize());
        if (hoveredLocal >= 0) {
            this.hoveredIndex = pageState.start() + hoveredLocal;
            this.rememberSelection(entries.get(this.hoveredIndex).uuid(), this.hoveredIndex);
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
        int active = ClientCompanionState.activeIndex(this.kind);
        UUID activeUuid = ClientCompanionState.activeUuid(this.kind);
        int selectedLocal = active >= pageState.start() && active < pageState.end()
                ? active - pageState.start()
                : -1;
        int deployedMask = 0;
        int riddenMask = 0;
        int stateCode = 0;
        for (int i = pageState.start(); i < pageState.end(); ++i) {
            CompanionListPacket.Entry entry = entries.get(i);
            int bit = 1 << (i - pageState.start());
            if (entry.deployed() || entry.ridden()) deployedMask |= bit;
            if (entry.ridden()) riddenMask |= bit;
            CompanionWheelVisualState state = ClientCompanionWheelController.state(this.kind, entry.uuid(),
                    entry.alive(), entry.deployed(), entry.ridden(), activeUuid);
            stateCode |= state.ordinal() << ((i - pageState.start()) * 3);
        }
        FindMeWheelRenderer.drawFieldScrim(graphics, this.width, this.height, openFade);
        this.beginFadeTransform(graphics, centerX, centerY, openFade, pageMotion);
        boolean auiBackdrop = FindMeAuiWheelBackdrop.drawRoster(graphics, this.width, this.height, layout,
                pageState.visibleSize(), hoveredLocal, stateCode, fade);
        if (!auiBackdrop) {
            if (layout == FindMeWheelStyle.TACTICAL_STRIP) {
                FindMeWheelRenderer.drawRosterRail(graphics, this.width, this.height, fade);
            } else if (layout == FindMeWheelStyle.CLASSIC_RADIAL) {
                FindMeWheelRenderer.drawBackdrop(graphics, centerX, centerY, fade);
            }
        }
        FindMeWheelRenderer.drawRosterHeader(graphics, this.title, pageState.index(), pageState.count(), layout, fade);
        drawSharedMana(graphics, fade);
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, (Component)Component.translatable((String)(this.kind == CompanionKind.MOUNT ? "screen.find_me.no_mounts" : "screen.find_me.no_companions")), centerX, centerY + 56, FindMeWheelRenderer.guiColor(0xFFFFD166, fade));
            graphics.pose().popPose();
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        for (int i = pageState.start(); i < pageState.end(); ++i) {
            int local = i - pageState.start();
            CompanionWheelLayout.RosterSlot slot = CompanionWheelLayout.rosterSlot(layout, this.width, this.height,
                    centerX, centerY, local, pageState.visibleSize());
            CompanionListPacket.Entry entry = entries.get(i);
            boolean hovered = i == this.hoveredIndex;
            boolean selected = i == active;
            boolean deployed = entry.ridden() || entry.deployed();
            CompanionWheelVisualState visualState = ClientCompanionWheelController.state(this.kind, entry.uuid(),
                    entry.alive(), entry.deployed(), entry.ridden(), activeUuid);
            if (!auiBackdrop) {
                if (layout == FindMeWheelStyle.CLASSIC_RADIAL) {
                    FindMeWheelRenderer.drawSlotSegment(graphics, centerX, centerY, local,
                            pageState.visibleSize(), hovered, visualState, fade);
                } else {
                    FindMeWheelRenderer.drawRosterCardBase(graphics, layout, slot, hovered, visualState, fade);
                }
            }
            CompanionWheelLayout.PreviewBounds preview = CompanionWheelLayout.rosterPreviewBounds(layout, slot,
                    hovered);
            if (pageMotion.alpha() > 0.08f) {
                this.previewRenderer.renderFittedPreview(graphics, entry, preview.left(), preview.top(),
                        preview.width(), preview.height(), this.fallbackPreviewType());
            }
        }
        int focused = this.hoveredIndex >= 0 ? this.hoveredIndex : active;
        if (focused < pageState.start() || focused >= pageState.end()) focused = pageState.start();
        if (pageMotion.alpha() > 0.08f && focused >= 0 && focused < entries.size()) {
            CompanionListPacket.Entry focus = entries.get(focused);
            if (layout == FindMeWheelStyle.SIX_WING || layout == FindMeWheelStyle.FOLDED_SHARDS) {
                this.previewRenderer.renderFittedPreview(graphics, focus, centerX - 25, centerY - 39, 50, 61,
                        this.fallbackPreviewType());
            } else {
                int left = CompanionWheelLayout.viewportLeft(this.width);
                int top = CompanionWheelLayout.viewportTop(this.height);
                this.previewRenderer.renderFittedPreview(graphics, focus, left + CompanionWheelLayout.VIEWPORT_WIDTH - 119,
                        top + 34, 94, 92, this.fallbackPreviewType());
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
                CompanionListPacket.Entry entry = entries.get(i);
                boolean hovered = i == this.hoveredIndex;
                float pct = entry.maxHealth() > 0.0f
                        ? Math.max(0.0f, Math.min(1.0f, entry.health() / entry.maxHealth())) : 0.0f;
                if (layout == FindMeWheelStyle.CLASSIC_RADIAL) {
                    FindMeWheelRenderer.drawClassicSlotText(graphics, slot, local + 1, displayName(entry), entry.alive(),
                            pct, ClientWheelPresentationState.showHealth() && entry.maxHealth() > 0.0f, fade);
                } else {
                    FindMeWheelRenderer.drawRosterCardText(graphics, slot, local + 1, displayName(entry), hovered,
                            entry.deployed() || entry.ridden(), entry.ridden(), i == active, entry.alive(), pct,
                            ClientWheelPresentationState.showHealth() && entry.maxHealth() > 0.0f, fade);
                }
                if (entry.hasHome()) {
                    FindMeWheelRenderer.drawHomeBadge(graphics, slot, fade);
                }
            }
            if (focused >= 0 && focused < entries.size()) {
                CompanionListPacket.Entry focus = entries.get(focused);
                CompanionWheelVisualState visualState = ClientCompanionWheelController.state(this.kind,
                        focus.uuid(), focus.alive(), focus.deployed(), focus.ridden(), activeUuid);
                Component state = wheelStateLabel(visualState, focus.ridden());
                FindMeWheelRenderer.drawFocusedDossier(graphics, layout, this.width, this.height, centerX, centerY,
                        displayName(focus), focused + 1, entries.size(), state, fade);
            }
        } finally {
            graphics.pose().popPose();
            RenderSystem.disableDepthTest();
        }
        graphics.pose().popPose();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String displayName(CompanionListPacket.Entry entry) {
        if (entry == null) return "";
        String custom = ClientWheelPresentationState.showCustomNames() ? entry.name() : "";
        String original = ClientWheelPresentationState.showOriginalNames() ? entry.entityType() : "";
        if (custom.isBlank()) return original;
        if (original.isBlank() || custom.equals(original)) return custom;
        return custom + " / " + original;
    }

    private void renderMergedMountWheel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                        float openFade, float fade, ClientWheelPageTransition.Motion pageMotion,
                                        int centerX, int centerY) {
        FindMeWheelStyle layout = ClientWheelPresentationState.rosterLayout();
        List<WheelEntry> entries = this.mergedMountEntries();
        this.restoreSelection(entries.stream().map(WheelEntry::uuid).toList());
        CompanionWheelLayout.Page pageState = CompanionWheelLayout.page(entries.size(), this.page);
        this.page = pageState.index();
        int hoveredLocal = this.pageTransition.active() ? -1
                : CompanionWheelLayout.hoveredRosterSlot(layout, this.width, this.height,
                centerX, centerY, mouseX, mouseY, pageState.visibleSize());
        int hovered = hoveredLocal >= 0 ? pageState.start() + hoveredLocal : -1;
        if (hovered >= 0 && (hovered >= entries.size() || entries.get(hovered).empty())) {
            hovered = -1;
            hoveredLocal = -1;
        }
        if (hovered >= 0) {
            this.hoveredIndex = hovered;
            this.rememberSelection(entries.get(hovered).uuid(), hovered);
        } else if (this.hoveredIndex < pageState.start() || this.hoveredIndex >= pageState.end()
                || this.hoveredIndex >= entries.size() || entries.get(this.hoveredIndex).empty()) {
            this.hoveredIndex = -1;
        }
        hoveredLocal = this.hoveredIndex >= pageState.start() && this.hoveredIndex < pageState.end()
                ? this.hoveredIndex - pageState.start()
                : -1;
        if (this.hoveredIndex >= 0 && this.hoveredIndex != this.selectedHoverIndex) {
            this.selectedHoverIndex = this.hoveredIndex;
            this.playHoverSound();
        }

        ClientMountRosterState.Entry selectedEntry = ClientMountRosterState.selectedEntry();
        UUID activeUuid = selectedEntry == null ? null : selectedEntry.uuid();
        int selectedLocal = -1;
        int deployedMask = 0;
        int riddenMask = 0;
        int stateCode = 0;
        for (int i = pageState.start(); i < pageState.end(); ++i) {
            WheelEntry entry = entries.get(i);
            boolean deployed = entry.deployed();
            boolean ridden = entry.ridden();
            int bit = 1 << (i - pageState.start());
            if (deployed) deployedMask |= bit;
            if (ridden) riddenMask |= bit;
            CompanionWheelVisualState visualState = this.visualState(entry, activeUuid);
            stateCode |= visualState.ordinal() << ((i - pageState.start()) * 3);
            if (entry.isSelected()) {
                selectedLocal = i - pageState.start();
            }
        }

        FindMeWheelRenderer.drawFieldScrim(graphics, this.width, this.height, openFade);
        this.beginFadeTransform(graphics, centerX, centerY, openFade, pageMotion);
        boolean auiBackdrop = FindMeAuiWheelBackdrop.drawRoster(graphics, this.width, this.height, layout,
                pageState.visibleSize(), hoveredLocal, stateCode, fade);
        if (!auiBackdrop) {
            if (layout == FindMeWheelStyle.TACTICAL_STRIP) {
                FindMeWheelRenderer.drawRosterRail(graphics, this.width, this.height, fade);
            } else if (layout == FindMeWheelStyle.CLASSIC_RADIAL) {
                FindMeWheelRenderer.drawBackdrop(graphics, centerX, centerY, fade);
            }
        }
        FindMeWheelRenderer.drawRosterHeader(graphics, this.title, pageState.index(), pageState.count(), layout, fade);
        drawSharedMana(graphics, fade);
        if (entries.isEmpty() || (pageState.visibleSize() == 0)) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.find_me.no_mounts"), centerX, centerY + 56,
                    FindMeWheelRenderer.guiColor(0xFFFFD166, fade));
            graphics.pose().popPose();
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        for (int i = pageState.start(); i < pageState.end(); ++i) {
            WheelEntry entry = entries.get(i);
            int local = i - pageState.start();
            CompanionWheelLayout.RosterSlot slot = CompanionWheelLayout.rosterSlot(layout, this.width, this.height,
                    centerX, centerY, local, pageState.visibleSize());
            boolean hoveredSlot = i == this.hoveredIndex;
            boolean ridden = entry.ridden();
            CompanionWheelVisualState visualState = this.visualState(entry, activeUuid);
            if (!auiBackdrop) {
                if (layout == FindMeWheelStyle.CLASSIC_RADIAL) {
                    FindMeWheelRenderer.drawSlotSegment(graphics, centerX, centerY, local,
                            pageState.visibleSize(), hoveredSlot, visualState, fade);
                } else {
                    FindMeWheelRenderer.drawRosterCardBase(graphics, layout, slot, hoveredSlot, visualState, fade);
                }
            }
            if (pageMotion.alpha() > 0.08f && !entry.empty()) {
                CompanionWheelLayout.PreviewBounds preview = CompanionWheelLayout.rosterPreviewBounds(layout, slot,
                        hoveredSlot);
                this.previewRenderer.renderFittedPreview(graphics, entry.asPreviewEntry(), preview.left(),
                        preview.top(), preview.width(), preview.height(),
                        entry.fallbackType(this.fallbackPreviewType()));
            }
        }

        int focused = this.hoveredIndex;
        if (focused < pageState.start() || focused >= pageState.end() || entries.get(focused).empty()) {
            focused = selectedLocal >= 0 ? pageState.start() + selectedLocal : pageState.start();
        }
        if (pageMotion.alpha() > 0.08f && focused >= 0 && focused < entries.size()
                && !entries.get(focused).empty()) {
            WheelEntry focus = entries.get(focused);
            String fallback = focus.fallbackType(this.fallbackPreviewType());
            if (layout == FindMeWheelStyle.SIX_WING || layout == FindMeWheelStyle.FOLDED_SHARDS) {
                this.previewRenderer.renderFittedPreview(graphics, focus.asPreviewEntry(), centerX - 25, centerY - 39,
                        50, 61, fallback);
            } else {
                int left = CompanionWheelLayout.viewportLeft(this.width);
                int top = CompanionWheelLayout.viewportTop(this.height);
                this.previewRenderer.renderFittedPreview(graphics, focus.asPreviewEntry(),
                        left + CompanionWheelLayout.VIEWPORT_WIDTH - 119, top + 34, 94, 92, fallback);
            }
        }

        RenderSystem.disableDepthTest();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 260.0f);
        try {
            for (int i = pageState.start(); i < pageState.end(); ++i) {
                WheelEntry entry = entries.get(i);
                int local = i - pageState.start();
                CompanionWheelLayout.RosterSlot slot = CompanionWheelLayout.rosterSlot(layout, this.width, this.height,
                        centerX, centerY, local, pageState.visibleSize());
                if (entry.empty()) continue;
                boolean alive = entry.alive();
                boolean ridden = entry.ridden();
                float health = 0.0f;
                boolean hasHealth = false;
                CompanionListPacket.Entry previewEntry = entry.asPreviewEntry();
                if (previewEntry != null && previewEntry.maxHealth() > 0.0f) {
                    health = Math.max(0.0f, Math.min(1.0f, previewEntry.health() / previewEntry.maxHealth()));
                    hasHealth = true;
                }
                if (layout == FindMeWheelStyle.CLASSIC_RADIAL) {
                    FindMeWheelRenderer.drawClassicSlotText(graphics, slot, local + 1, entry.name(), alive,
                            health, hasHealth, fade);
                } else {
                    FindMeWheelRenderer.drawRosterCardText(graphics, slot, local + 1, entry.name(),
                            i == this.hoveredIndex, entry.deployed(), ridden, local == selectedLocal, alive,
                            health, hasHealth, fade);
                }
                if (entry.findMe() != null && entry.findMe().hasHome()) {
                    FindMeWheelRenderer.drawHomeBadge(graphics, slot, fade);
                }
            }
            if (focused >= 0 && focused < entries.size() && !entries.get(focused).empty()) {
                WheelEntry focus = entries.get(focused);
                boolean ridden = focus.ridden();
                Component state = wheelStateLabel(this.visualState(focus, activeUuid), ridden);
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

    private void drawMergedMountHint(GuiGraphics graphics, List<WheelEntry> entries, int centerX, int centerY,
                                     CompanionWheelLayout.Page pageState, float fade) {
        int index = this.hoveredIndex >= 0 ? this.hoveredIndex : pageState.start();
        if (index < 0 || index >= entries.size() || entries.get(index).empty()) return;
        WheelEntry entry = entries.get(index);
        boolean selected = this.isMergedSelected(entry);
        Component label = selected
                ? Component.translatable("screen.find_me.wheel_current", trim(entry.name(), 16),
                entry.sourceSlot() + 1, entries.size())
                : Component.translatable("screen.find_me.wheel_pending", trim(entry.name(), 16));
        graphics.drawCenteredString(this.font, label, centerX, centerY + CompanionWheelLayout.BACKDROP_RADIUS + 12,
                FindMeWheelRenderer.guiColor(0xFFFFFFFF, fade));
    }

    private static void drawSharedMana(GuiGraphics graphics, float fade) {
        com.kuzhi.findme.api.CompanionMagicState state = ClientCompanionState.allEntries(CompanionKind.COMPANION)
                .stream().map(CompanionListPacket.Entry::magicState)
                .filter(com.kuzhi.findme.api.CompanionMagicState::available).findFirst()
                .orElseGet(() -> ClientCompanionState.allEntries(CompanionKind.MOUNT).stream()
                        .map(CompanionListPacket.Entry::magicState)
                        .filter(com.kuzhi.findme.api.CompanionMagicState::available).findFirst()
                        .orElse(com.kuzhi.findme.api.CompanionMagicState.EMPTY));
        if (!state.available()) return;
        String label = Component.translatable("screen.find_me.spell_slot.mana").getString() + "  "
                + Math.round(state.mana()) + " / " + Math.round(state.maxMana());
        FindMeWheelRenderer.drawManaBar(graphics, label, state.mana() / state.maxMana(), fade);
    }

    private List<WheelEntry> mergedMountEntries() {
        return ClientMountRosterState.entries().stream().map(WheelEntry::from).toList();
    }

    private WheelEntry mergedMountEntry(int index) {
        List<WheelEntry> entries = this.mergedMountEntries();
        return index < 0 || index >= entries.size() ? null : entries.get(index);
    }

    public void confirmSelection() {
        this.pageTransition.finish();
        if (this.hoveredIndex >= 0) {
            if (this.followCommandMode) {
                this.activateSlot(this.hoveredIndex);
            } else {
                this.selectPendingSlot(this.hoveredIndex);
            }
        }
        Minecraft.getInstance().setScreen(null);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (FindMeUiKeys.isPrimaryMouse(button)) this.pageTransition.finish();
        if (FindMeUiKeys.isMiddleMouse(button) && this.kind == CompanionKind.COMPANION) {
            this.pageTransition.finish();
            this.playConfirmSound();
            this.openTeamCommandWheel();
            return true;
        }
        if (FindMeUiKeys.isSecondaryMouse(button) && this.hoveredIndex >= 0) {
            this.pageTransition.finish();
            return this.openContextWheel(this.hoveredIndex, CompanionCommandWheelScreen.Mode.COMMAND);
        }
        if (FindMeUiKeys.isPrimaryMouse(button) && this.hoveredIndex >= 0) {
            return this.activateSlot(this.hoveredIndex);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    void resumeFromCommandWheel() {
        this.openTicks = 0;
        this.pageTransition.finish();
    }
    private void openTeamCommandWheel() {
        CompanionTeamTarget target = CompanionTeamTarget.COMPANION;
        int team = ClientCompanionTeamState.currentTeam(target);
        Minecraft.getInstance().setScreen(new CompanionCommandWheelScreen(target, team, this,
                new CompanionCommandWheelScreen.AimSnapshot(this.commandAimPosition, this.commandAimEntityId)));
    }
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0.0 || this.pageTransition.active()) return true;
        if (this.kind == CompanionKind.MOUNT) {
            this.startMergedMountWheelTransition(delta);
            return true;
        }
        CompanionTeamTarget target = this.teamTarget();
        List<UUID> wheelUuids = ClientCompanionState.allEntries(this.kind).stream()
                .map(CompanionListPacket.Entry::uuid).toList();
        int team = ClientCompanionTeamState.peekNextNonEmptyTeam(target, wheelUuids, delta);
        if (team >= 0) {
            this.pageTransition.start(delta, () -> {
                ClientCompanionTeamState.selectTeam(target, team);
                this.page = 0;
                this.selectFirstCompanionOnPage();
                this.playHoverSound();
            });
        }
        return true;
    }

    private void startMergedMountWheelTransition(double scrollY) {
        List<WheelEntry> merged = this.mergedMountEntries();
        CompanionWheelLayout.Page pageState = CompanionWheelLayout.page(merged.size(), this.page);
        if (pageState.count() <= 1) return;
        int direction = scrollY < 0.0 ? 1 : -1;
        int nextPage = Math.floorMod(pageState.index() + direction, pageState.count());
        this.pageTransition.start(scrollY, () -> {
            this.page = nextPage;
            this.selectFirstMergedEntryOnPage();
            this.playHoverSound();
        });
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int numberSlot = FindMeUiKeys.numberSlot(keyCode);
        if (numberSlot >= 0) {
            this.pageTransition.finish();
            List<CompanionListPacket.Entry> entries = this.wheelEntries();
            if (this.kind == CompanionKind.MOUNT) {
                List<WheelEntry> wheelEntries = this.mergedMountEntries();
                CompanionWheelLayout.Page pageState = CompanionWheelLayout.page(wheelEntries.size(), this.page);
                int target = pageState.start() + numberSlot;
                if (numberSlot < pageState.visibleSize() && target < wheelEntries.size() && !wheelEntries.get(target).empty()) {
                    this.hoveredIndex = target;
                    this.selectedHoverIndex = target;
                    this.rememberSelection(wheelEntries.get(target).uuid(), target);
                    return this.activateSlot(target);
                }
                return true;
            }
            CompanionWheelLayout.Page pageState = CompanionWheelLayout.page(entries.size(), this.page);
            int target = pageState.start() + numberSlot;
            if (numberSlot < pageState.visibleSize() && target < entries.size()) {
                this.hoveredIndex = target;
                this.selectedHoverIndex = target;
                this.rememberSelection(entries.get(target).uuid(), target);
                return this.activateSlot(target);
            }
            return true;
        }
        if (FindMeUiKeys.isWheelModeSwitch(keyCode) && this.kind == CompanionKind.MOUNT) {
            this.pageTransition.finish();
            Minecraft.getInstance().setScreen(new VehicleWheelScreen());
            return true;
        }
        if (FindMeUiKeys.isFollowModeSwitch(keyCode)) {
            this.pageTransition.finish();
            this.followCommandMode = !this.followCommandMode;
            this.playConfirmSound();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public static void syncMovementKeys() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        long window = minecraft.getWindow().getWindow();
        boolean up = CompanionWheelScreen.rawKey(minecraft.options.keyUp, window);
        boolean down = CompanionWheelScreen.rawKey(minecraft.options.keyDown, window);
        boolean left = CompanionWheelScreen.rawKey(minecraft.options.keyLeft, window);
        boolean right = CompanionWheelScreen.rawKey(minecraft.options.keyRight, window);
        boolean jump = CompanionWheelScreen.rawKey(minecraft.options.keyJump, window);
        boolean shift = CompanionWheelScreen.rawKey(minecraft.options.keyShift, window);
        boolean sprint = CompanionWheelScreen.rawKey(minecraft.options.keySprint, window);
        minecraft.player.input.up = up;
        minecraft.player.input.down = down;
        minecraft.player.input.left = left;
        minecraft.player.input.right = right;
        minecraft.player.input.jumping = jump;
        minecraft.player.input.shiftKeyDown = shift;
        minecraft.player.input.forwardImpulse = up == down ? 0.0f : (up ? 1.0f : -1.0f);
        minecraft.player.input.leftImpulse = left == right ? 0.0f : (left ? 1.0f : -1.0f);
        if (shift) {
            minecraft.player.input.forwardImpulse *= 0.3f;
            minecraft.player.input.leftImpulse *= 0.3f;
        }
        if (sprint && up && !shift) {
            minecraft.player.setSprinting(true);
        }
    }

    private static boolean rawKey(KeyMapping key, long window) {
        int value = key.getKey().getValue();
        return value >= 0 && InputConstants.isKeyDown(window, value);
    }

    private static String trim(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(1, max - 1)) + "...";
    }

    private boolean activateSlot(int index) {
        if (this.kind == CompanionKind.MOUNT) {
            WheelEntry wheelEntry = this.mergedMountEntry(index);
            if (wheelEntry == null || wheelEntry.empty()) {
                return true;
            }
            if (this.visualState(wheelEntry, null) == CompanionWheelVisualState.DEAD) {
                return true;
            }
            this.rememberSelection(wheelEntry.uuid(), index);
            this.playConfirmSound();
            ClientMountRosterState.select(wheelEntry.source(), wheelEntry.uuid());
            if (wheelEntry.findMe() != null) {
                ClientCompanionCommandTarget.remember(this.kind, wheelEntry.findMe().uuid());
            }
            CompanionWheelVisualState state = this.visualState(wheelEntry, null);
            ClientMountRosterTransactionState.send(state == CompanionWheelVisualState.SWITCHING
                            || wheelEntry.deployed() ? MountRosterAction.RECALL : MountRosterAction.ACTIVATE,
                    wheelEntry.source(), wheelEntry.uuid(), wheelEntry.sourceSlot(), wheelEntry.teamIndex());
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        List<CompanionListPacket.Entry> entries = this.wheelEntries();
        if (index < 0 || index >= entries.size()) {
            return true;
        }
        CompanionListPacket.Entry entry = entries.get(index);
        if (this.visualState(entry) == CompanionWheelVisualState.DEAD) {
            return true;
        }
        this.rememberSelection(entry.uuid(), index);
        this.playConfirmSound();
        if (this.followCommandMode) {
            this.sendIntent(CompanionAction.ESCORT, entry.uuid());
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        ClientCompanionCommandTarget.remember(this.kind, entry.uuid());
        CompanionTeamTarget target = this.teamTarget();
        int previewIndex = ClientCompanionTeamState.currentMemberIndex(target, entry.uuid());
        int serverIndex = previewIndex >= 0 ? previewIndex : ClientCompanionState.serverWheelIndex(this.kind, entry.uuid());
        if (serverIndex < 0) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        if (previewIndex >= 0) {
            this.applyTeam(target, ClientCompanionTeamState.currentTeam(target));
        }
        CompanionWheelVisualState state = this.visualState(entry);
        this.sendIntent(state == CompanionWheelVisualState.SWITCHING || entry.ridden() || entry.deployed()
                ? CompanionAction.RECALL : CompanionAction.SELECT_SUMMON, entry.uuid());
        Minecraft.getInstance().setScreen(null);
        return true;
    }

    private boolean openContextWheel(int index, CompanionCommandWheelScreen.Mode mode) {
        CompanionListPacket.Entry entry;
        if (this.kind == CompanionKind.MOUNT) {
            WheelEntry wheelEntry = this.mergedMountEntry(index);
            if (wheelEntry == null || wheelEntry.empty() || wheelEntry.findMe() == null) {
                ClientCompanionCommandTarget.showUnavailable();
                return true;
            }
            entry = wheelEntry.findMe();
            if (this.visualState(wheelEntry, null) == CompanionWheelVisualState.DEAD) return true;
            this.rememberSelection(entry.uuid(), index);
        } else {
            List<CompanionListPacket.Entry> entries = this.wheelEntries();
            if (index < 0 || index >= entries.size()) return true;
            entry = entries.get(index);
            if (this.visualState(entry) == CompanionWheelVisualState.DEAD) return true;
            this.rememberSelection(entry.uuid(), index);
        }
        com.kuzhi.findme.api.client.CompanionCommandTarget target =
                ClientCompanionCommandTarget.fromEntry(this.kind, entry);
        if (target == null) {
            ClientCompanionCommandTarget.showUnavailable();
            return true;
        }
        this.playConfirmSound();
        CompanionCommandWheelScreen.AimSnapshot aim = CompanionCommandWheelScreen.captureAim(target);
        Minecraft.getInstance().setScreen(new CompanionCommandWheelScreen(target, this, mode,
                aim.position(), aim.entityId()));
        return true;
    }

    private boolean selectPendingSlot(int index) {
        if (this.kind == CompanionKind.MOUNT) {
            WheelEntry wheelEntry = this.mergedMountEntry(index);
            if (wheelEntry == null || wheelEntry.empty()) {
                return true;
            }
            if (this.visualState(wheelEntry, null) == CompanionWheelVisualState.DEAD) {
                return true;
            }
            this.rememberSelection(wheelEntry.uuid(), index);
            this.playConfirmSound();
            ClientMountRosterState.select(wheelEntry.source(), wheelEntry.uuid());
            if (wheelEntry.findMe() != null) {
                ClientCompanionCommandTarget.remember(this.kind, wheelEntry.findMe().uuid());
            }
            ClientMountRosterTransactionState.send(MountRosterAction.SELECT, wheelEntry.source(),
                    wheelEntry.uuid(), wheelEntry.sourceSlot(), wheelEntry.teamIndex());
            return true;
        }
        List<CompanionListPacket.Entry> entries = this.wheelEntries();
        if (index < 0 || index >= entries.size()) {
            return true;
        }
        CompanionListPacket.Entry entry = entries.get(index);
        if (this.visualState(entry) == CompanionWheelVisualState.DEAD) {
            return true;
        }
        this.rememberSelection(entry.uuid(), index);
        this.playConfirmSound();
        ClientCompanionCommandTarget.remember(this.kind, entry.uuid());
        return this.selectPendingFindMe(entry);
    }

    private boolean selectPendingFindMe(CompanionListPacket.Entry entry) {
        CompanionTeamTarget target = this.teamTarget();
        int previewIndex = ClientCompanionTeamState.currentMemberIndex(target, entry.uuid());
        int serverIndex = previewIndex >= 0 ? previewIndex : ClientCompanionState.serverWheelIndex(this.kind, entry.uuid());
        if (serverIndex < 0) {
            return true;
        }
        if (previewIndex >= 0) {
            this.applyTeam(target, ClientCompanionTeamState.currentTeam(target));
        }
        ClientCompanionWheelController.rememberPending(this.kind, entry.uuid());
        this.sendIntent(CompanionAction.SELECT, entry.uuid());
        return true;
    }

    private void drawCurrentHint(GuiGraphics graphics, List<CompanionListPacket.Entry> entries, int centerX, int centerY, CompanionWheelLayout.Page pageState, float fade) {
        int index = this.hoveredIndex >= 0 ? this.hoveredIndex : ClientCompanionState.activeIndex(this.kind);
        if (index < 0 || index >= entries.size()) {
            index = pageState.start();
        }
        if (index < 0 || index >= entries.size()) {
            return;
        }
        CompanionListPacket.Entry entry = entries.get(index);
        int active = ClientCompanionState.activeIndex(this.kind);
        Component current = index == active
                ? Component.translatable("screen.find_me.wheel_current", CompanionWheelScreen.trim(entry.name(), 16), index + 1, entries.size())
                : Component.translatable("screen.find_me.wheel_pending", CompanionWheelScreen.trim(entry.name(), 16));
        graphics.drawCenteredString(this.font, current, centerX, centerY + CompanionWheelLayout.BACKDROP_RADIUS + 12, FindMeWheelRenderer.guiColor(0xFFFFFFFF, fade));
    }

    private boolean isMergedSelected(WheelEntry entry) {
        if (entry == null || entry.empty()) {
            return false;
        }
        return entry.isSelected();
    }

    private void drawModeHint(GuiGraphics graphics, int centerX, int centerY, float fade) {
        Component label = this.followCommandMode
                ? Component.translatable("screen.find_me.wheel_follow_mode")
                : Component.translatable("screen.find_me.wheel_mode_hint");
        int color = this.followCommandMode ? 0xFF58E2C2 : 0xFFFFFFFF;
        graphics.drawCenteredString(this.font, label, centerX, centerY - CompanionWheelLayout.BACKDROP_RADIUS - 17, FindMeWheelRenderer.guiColor(color, fade));
    }

    private boolean isCenterCancel(double mouseX, double mouseY) {
        double dx = mouseX - (double)this.wheelCenterX();
        double dy = mouseY - (double)this.wheelCenterY();
        double radius = CompanionWheelLayout.CENTER_CANCEL_RADIUS;
        return dx * dx + dy * dy <= radius * radius;
    }

    private int wheelCenterX() {
        return this.width / 2;
    }

    private int wheelCenterY() {
        return this.height / 2;
    }

    private void beginFadeTransform(GuiGraphics graphics, int centerX, int centerY, float fade,
                                    ClientWheelPageTransition.Motion pageMotion) {
        float scale = (0.96f + fade * 0.04f) * pageMotion.scale();
        graphics.pose().pushPose();
        graphics.pose().translate((float)centerX, (float)centerY + pageMotion.offsetY(), 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate((float)(-centerX), (float)(-centerY), 0.0f);
    }

    private float fade(float partialTick) {
        float progress = Math.min((float)FADE_TICKS, (float)this.openTicks + partialTick) / (float)FADE_TICKS;
        return progress * progress * (3.0f - 2.0f * progress);
    }

    private String fallbackPreviewType() {
        return this.kind == CompanionKind.MOUNT ? "minecraft:horse" : "minecraft:wolf";
    }

    private List<CompanionListPacket.Entry> wheelEntries() {
        return ClientCompanionWheelController.mergeDeathGhosts(this.kind,
                ClientCompanionState.entries(this.kind));
    }

    private CompanionWheelVisualState visualState(CompanionListPacket.Entry entry) {
        return ClientCompanionWheelController.state(this.kind, entry.uuid(), entry.alive(), entry.deployed(),
                entry.ridden(), ClientCompanionState.activeUuid(this.kind));
    }

    private CompanionWheelVisualState visualState(WheelEntry entry, UUID activeUuid) {
        if (entry == null || entry.empty()) {
            return CompanionWheelVisualState.AVAILABLE;
        }
        if (ClientMountRosterTransactionState.switching(entry.source(), entry.uuid())) {
            return CompanionWheelVisualState.SWITCHING;
        }
        return ClientCompanionWheelController.state(CompanionKind.MOUNT, entry.uuid(), entry.alive(),
                entry.deployed(), entry.ridden(), activeUuid);
    }

    private static Component wheelStateLabel(CompanionWheelVisualState state, boolean ridden) {
        if (ridden && state == CompanionWheelVisualState.DEPLOYED) {
            return Component.translatable("screen.find_me.wheel_state_riding");
        }
        return switch (state) {
            case PENDING -> Component.translatable("screen.find_me.wheel_state_pending");
            case DEPLOYED -> Component.translatable("screen.find_me.wheel_state_deployed");
            case SWITCHING -> Component.translatable("screen.find_me.wheel_state_switching");
            case DEAD -> Component.translatable("screen.find_me.wheel_state_dead");
            case AVAILABLE -> Component.translatable("screen.find_me.wheel_state_ready");
        };
    }

    private void send(CompanionAction action, int value) {
        if (ModNetwork.channel != null) {
            if (this.kind == CompanionKind.MOUNT && action == CompanionAction.SELECT_SUMMON) {
                ClientCameraLock.arm();
            }
            ModNetwork.sendToServer(new CompanionCommandPacket(this.kind, action, value));
        }
    }

    private void sendIntent(CompanionAction action, UUID uuid) {
        if (ModNetwork.channel == null || uuid == null) {
            return;
        }
        if (this.kind == CompanionKind.MOUNT && action == CompanionAction.SELECT_SUMMON) {
            ClientCameraLock.arm();
        }
        ClientCompanionWheelController.sendIntent(this.kind, uuid, action);
    }

    private void syncTeams() {
        if (ModNetwork.channel != null) {
            ModNetwork.sendToServer(new CompanionTeamCommandPacket(CompanionTeamAction.SYNC, this.teamTarget(), -1, -1, "", List.of()));
        }
    }

    private void applyTeam(CompanionTeamTarget target, int team) {
        if (ModNetwork.channel != null) {
            ModNetwork.sendToServer(new CompanionTeamCommandPacket(CompanionTeamAction.APPLY, target, team, -1, "", List.of()));
        }
    }

    private CompanionTeamTarget teamTarget() {
        return this.kind == CompanionKind.MOUNT ? CompanionTeamTarget.MOUNT : CompanionTeamTarget.COMPANION;
    }

    private ClientWheelSelectionMemory.Wheel memoryWheel() {
        return this.kind == CompanionKind.MOUNT
                ? ClientWheelSelectionMemory.Wheel.MOUNT
                : ClientWheelSelectionMemory.Wheel.COMPANION;
    }

    private void restoreSelection(List<UUID> order) {
        if (this.selectionRestored || order.isEmpty()) {
            return;
        }
        int restored = ClientWheelSelectionMemory.resolve(this.memoryWheel(), order);
        if (restored >= 0) {
            this.hoveredIndex = restored;
            this.selectedHoverIndex = restored;
            this.page = restored / CompanionWheelLayout.PAGE_SIZE;
        }
        this.selectionRestored = true;
    }

    private void rememberSelection(UUID uuid, int index) {
        ClientWheelSelectionMemory.remember(this.memoryWheel(), uuid, index, this.page);
    }

    private void selectFirstCompanionOnPage() {
        List<CompanionListPacket.Entry> entries = this.wheelEntries();
        CompanionWheelLayout.Page pageState = CompanionWheelLayout.page(entries.size(), this.page);
        this.page = pageState.index();
        this.hoveredIndex = pageState.visibleSize() > 0 ? pageState.start() : -1;
        this.selectedHoverIndex = this.hoveredIndex;
        if (this.hoveredIndex >= 0) {
            this.rememberSelection(entries.get(this.hoveredIndex).uuid(), this.hoveredIndex);
        } else {
            ClientWheelSelectionMemory.rememberPage(this.memoryWheel(), this.page);
        }
    }

    private void selectFirstMergedEntryOnPage() {
        List<WheelEntry> entries = this.mergedMountEntries();
        CompanionWheelLayout.Page pageState = CompanionWheelLayout.page(entries.size(), this.page);
        this.page = pageState.index();
        this.hoveredIndex = -1;
        for (int i = pageState.start(); i < pageState.end(); i++) {
            if (!entries.get(i).empty()) {
                this.hoveredIndex = i;
                break;
            }
        }
        this.selectedHoverIndex = this.hoveredIndex;
        if (this.hoveredIndex >= 0) {
            WheelEntry entry = entries.get(this.hoveredIndex);
            if (entry.teamIndex() >= 0) {
                ClientCompanionTeamState.selectTeam(CompanionTeamTarget.MOUNT, entry.teamIndex());
            }
            this.rememberSelection(entry.uuid(), this.hoveredIndex);
        } else {
            ClientWheelSelectionMemory.rememberPage(this.memoryWheel(), this.page);
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

    private record WheelEntry(MountRosterSource source,
                              CompanionListPacket.Entry findMe, VehicleListPacket.Entry vehicle,
                              int sourceSlot, int teamIndex) {
        static WheelEntry from(ClientMountRosterState.Entry entry) {
            return new WheelEntry(entry.source(), entry.findMe(), entry.vehicle(),
                    entry.sourceSlot(), entry.teamIndex());
        }

        boolean empty() {
            return this.findMe == null && this.vehicle == null;
        }

        boolean deployed() {
            return this.vehicle != null ? this.vehicle.deployed() || this.vehicle.ridden()
                    : this.findMe != null && (this.findMe.deployed() || this.findMe.ridden());
        }

        boolean ridden() {
            return this.vehicle != null ? this.vehicle.ridden()
                    : this.findMe != null && this.findMe.ridden();
        }

        boolean alive() {
            return this.vehicle != null ? this.vehicle.alive()
                    : this.findMe != null && this.findMe.alive();
        }

        String name() {
            return this.vehicle != null ? this.vehicle.name()
                    : this.findMe == null ? "" : this.findMe.name();
        }

        UUID uuid() {
            return this.vehicle != null ? this.vehicle.uuid()
                    : this.findMe == null ? null : this.findMe.uuid();
        }

        CompanionListPacket.Entry asPreviewEntry() {
            return this.vehicle != null ? this.vehicle.asPreviewEntry() : this.findMe;
        }

        String fallbackType(String fallback) {
            CompanionListPacket.Entry preview = asPreviewEntry();
            return preview != null && !preview.entityType().isBlank() ? preview.entityType() : fallback;
        }

        boolean isSelected() {
            return !empty() && ClientMountRosterState.isSelected(this.source, uuid());
        }
    }
}
