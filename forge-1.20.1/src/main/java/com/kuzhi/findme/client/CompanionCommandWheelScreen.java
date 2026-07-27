package com.kuzhi.findme.client;

import com.kuzhi.findme.api.client.CompanionCommandTarget;
import com.kuzhi.findme.api.client.FindMeClientAbilityActionRegistry;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.common.FindMeWheelStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class CompanionCommandWheelScreen extends FindMeScreen {
    enum Mode {
        COMMAND("screen.find_me.command.title"),
        ABILITY("screen.find_me.ability.title");

        private final String titleKey;

        Mode(String titleKey) {
            this.titleKey = titleKey;
        }
    }

    private static final int FADE_TICKS = 6;
    private final CompanionCommandTarget target;
    private final CompanionWheelScreen returnScreen;
    private final Mode mode;
    private final BlockPos aimedPosition;
    private final int aimedEntityId;
    private int hoveredAction = -1;
    private int previousHoveredAction = -1;
    private int page;
    private int openTicks;

    CompanionCommandWheelScreen(CompanionCommandTarget target) {
        this(target, null, Mode.COMMAND, captureAim(target));
    }

    CompanionCommandWheelScreen(CompanionCommandTarget target, CompanionWheelScreen returnScreen, Mode mode,
                                BlockPos aimedPosition, int aimedEntityId) {
        this(target, returnScreen, mode, new AimSnapshot(aimedPosition, aimedEntityId));
    }

    private CompanionCommandWheelScreen(CompanionCommandTarget target, CompanionWheelScreen returnScreen, Mode mode,
                                        AimSnapshot aim) {
        super(Component.translatable(mode.titleKey));
        this.target = target;
        this.returnScreen = returnScreen;
        this.mode = mode;
        this.aimedPosition = aim.position();
        this.aimedEntityId = aim.entityId();
    }

    boolean openedFromRoster(CompanionKind kind) {
        return this.returnScreen != null && this.target.kind() == kind;
    }

    void closeFromSourceRelease() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.openTicks < FADE_TICKS) this.openTicks++;
        CompanionWheelScreen.syncMovementKeys();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        FindMeWheelStyle layout = ClientWheelPresentationState.rosterLayout();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        List<ActionView> actions = actions();
        CompanionWheelLayout.Page pageState = CompanionWheelLayout.page(actions.size(), this.page);
        this.page = pageState.index();
        int hoveredLocal = CompanionWheelLayout.hoveredRosterSlot(layout, this.width, this.height,
                centerX, centerY, mouseX, mouseY, pageState.visibleSize());
        this.hoveredAction = hoveredLocal < 0 ? -1 : pageState.start() + hoveredLocal;
        if (this.hoveredAction >= 0 && !actions.get(this.hoveredAction).available()) this.hoveredAction = -1;
        if (this.hoveredAction >= 0 && this.hoveredAction != this.previousHoveredAction) playHoverSound();
        this.previousHoveredAction = this.hoveredAction;

        float fade = fade(partialTick);
        int availableMask = 0;
        for (int i = pageState.start(); i < pageState.end(); ++i) {
            if (actions.get(i).available()) availableMask |= 1 << (i - pageState.start());
        }
        int hoveredVisible = this.hoveredAction < pageState.start() || this.hoveredAction >= pageState.end()
                ? -1 : this.hoveredAction - pageState.start();
        boolean auiBackdrop = FindMeAuiWheelBackdrop.drawCommand(graphics, this.width, this.height, layout,
                hoveredVisible, availableMask, pageState.visibleSize(), fade);
        if (!auiBackdrop) {
            FindMeWheelRenderer.drawFieldScrim(graphics, this.width, this.height, fade);
            if (layout == FindMeWheelStyle.TACTICAL_STRIP) {
                FindMeWheelRenderer.drawRosterRail(graphics, this.width, this.height, fade);
            }
            for (int i = pageState.start(); i < pageState.end(); ++i) {
                int local = i - pageState.start();
                CompanionWheelLayout.RosterSlot slot = CompanionWheelLayout.rosterSlot(layout, this.width,
                        this.height, centerX, centerY, local, pageState.visibleSize());
                ActionView action = actions.get(i);
                if (layout == FindMeWheelStyle.CLASSIC_RADIAL) {
                    FindMeWheelRenderer.drawSlotSegment(graphics, centerX, centerY, local,
                            pageState.visibleSize(), i == this.hoveredAction, false, false, fade);
                } else {
                    FindMeWheelRenderer.drawRosterCardBase(graphics, layout, slot,
                            i == this.hoveredAction, false, false, false, fade);
                }
            }
        }

        FindMeWheelRenderer.drawCommandHeader(graphics, this.title, this.target.entry().name(), fade);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 280.0f);
        for (int i = pageState.start(); i < pageState.end(); ++i) {
            int local = i - pageState.start();
            CompanionWheelLayout.RosterSlot slot = CompanionWheelLayout.rosterSlot(layout, this.width,
                    this.height, centerX, centerY, local, pageState.visibleSize());
            ActionView action = actions.get(i);
            FindMeWheelRenderer.drawContextActionText(graphics, layout, slot, local + 1, action.label(),
                    i == this.hoveredAction, action.available(), fade);
        }
        graphics.pose().popPose();
        if (actions.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.find_me.ability.empty"),
                    centerX, centerY + 54, FindMeWheelRenderer.guiColor(0xFFFFD166, fade));
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    void confirmSelection() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (FindMeUiKeys.isSecondaryMouse(button) || FindMeUiKeys.isMiddleMouse(button)) {
            returnToRoster();
            return true;
        }
        if (FindMeUiKeys.isPrimaryMouse(button)) {
            FindMeWheelStyle layout = ClientWheelPresentationState.rosterLayout();
            if (layout != FindMeWheelStyle.TACTICAL_STRIP && isCenter(mouseX, mouseY)) {
                playConfirmSound();
                Minecraft.getInstance().setScreen(null);
                return true;
            }
            List<ActionView> actions = actions();
            if (this.hoveredAction >= 0 && this.hoveredAction < actions.size()) {
                if (activate(actions.get(this.hoveredAction))) {
                    Minecraft.getInstance().setScreen(null);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0.0) return true;
        CompanionWheelLayout.Page current = CompanionWheelLayout.page(actions().size(), this.page);
        int next = current.index() + (delta < 0.0 ? 1 : -1);
        if (next >= 0 && next < current.count()) {
            this.page = next;
            this.hoveredAction = -1;
            this.previousHoveredAction = -1;
            playHoverSound();
        }
        return true;
    }

    private void returnToRoster() {
        if (this.returnScreen == null) {
            Minecraft.getInstance().setScreen(null);
            return;
        }
        this.returnScreen.resumeFromCommandWheel();
        Minecraft.getInstance().setScreen(this.returnScreen);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int slot = FindMeUiKeys.numberSlot(keyCode);
        List<ActionView> actions = actions();
        CompanionWheelLayout.Page pageState = CompanionWheelLayout.page(actions.size(), this.page);
        int actionIndex = slot < 0 ? -1 : pageState.start() + slot;
        if (slot >= 0 && slot < pageState.visibleSize() && actionIndex < actions.size()) {
            if (actions.get(actionIndex).available()) {
                if (activate(actions.get(actionIndex))) {
                    Minecraft.getInstance().setScreen(null);
                }
            }
            return true;
        }
        if (FindMeUiKeys.isCancel(keyCode)) {
            returnToRoster();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<ActionView> actions() {
        return this.mode == Mode.COMMAND ? commandActions() : abilityActions();
    }

    private List<ActionView> commandActions() {
        ArrayList<ActionView> actions = new ArrayList<>();
        boolean following = ClientCompanionCommandTarget.hasAction(this.target,
                com.kuzhi.findme.common.CompanionTacticalAction.FOLLOW);
        actions.add(new ActionView(ResourceLocation.fromNamespaceAndPath("find_me", following ? "cancel_follow" : "follow"),
                () -> Component.translatable(following
                        ? "screen.find_me.command.cancel_follow" : "screen.find_me.command.follow"),
                () -> ClientCompanionCommandTarget.canIssueTacticalOrder(this.target),
                        () -> following ? ClientCompanionCommandTarget.stopCurrent(this.target)
                        : ClientCompanionCommandTarget.follow(this.target)));

        boolean holding = ClientCompanionCommandTarget.hasAction(this.target,
                com.kuzhi.findme.common.CompanionTacticalAction.HOLD);
        actions.add(new ActionView(ResourceLocation.fromNamespaceAndPath("find_me", holding ? "cancel_hold" : "hold"),
                () -> Component.translatable(holding
                        ? "screen.find_me.command.cancel_hold" : "screen.find_me.command.hold"),
                () -> ClientCompanionCommandTarget.canIssueTacticalOrder(this.target),
                () -> holding ? ClientCompanionCommandTarget.stopCurrent(this.target)
                        : ClientCompanionCommandTarget.hold(this.target)));

        boolean guarding = ClientCompanionCommandTarget.hasAction(this.target,
                com.kuzhi.findme.common.CompanionTacticalAction.GUARD_HERE);
        actions.add(new ActionView(ResourceLocation.fromNamespaceAndPath("find_me", guarding ? "cancel_guard" : "guard_here"),
                () -> Component.translatable(guarding
                        ? "screen.find_me.command.cancel_guard" : "screen.find_me.command.guard_here"),
                () -> ClientCompanionCommandTarget.canIssueTacticalOrder(this.target),
                () -> guarding ? ClientCompanionCommandTarget.stopCurrent(this.target)
                        : ClientCompanionCommandTarget.guardHere(this.target)));

        boolean protecting = ClientCompanionCommandTarget.hasAction(this.target,
                com.kuzhi.findme.common.CompanionTacticalAction.PROTECT_OWNER);
        actions.add(new ActionView(ResourceLocation.fromNamespaceAndPath("find_me", protecting ? "cancel_protect" : "protect_owner"),
                () -> Component.translatable(protecting
                        ? "screen.find_me.command.cancel_protect" : "screen.find_me.command.protect_owner"),
                () -> ClientCompanionCommandTarget.canIssueTacticalOrder(this.target),
                () -> protecting ? ClientCompanionCommandTarget.stopCurrent(this.target)
                        : ClientCompanionCommandTarget.protectOwner(this.target)));

        com.kuzhi.findme.common.CompanionTacticalAction current = this.target.entry().tacticalAction();
        if (current != null && current != com.kuzhi.findme.common.CompanionTacticalAction.FOLLOW
                && current != com.kuzhi.findme.common.CompanionTacticalAction.GUARD_HERE
                && current != com.kuzhi.findme.common.CompanionTacticalAction.PROTECT_OWNER) {
            actions.add(new ActionView(ResourceLocation.fromNamespaceAndPath("find_me", "stop_current"),
                    () -> Component.translatable("screen.find_me.command.stop_current"),
                    () -> true, () -> ClientCompanionCommandTarget.stopCurrent(this.target)));
        }
        if (this.aimedEntityId >= 0) {
            actions.add(new ActionView(ResourceLocation.fromNamespaceAndPath("find_me", "attack_target"),
                    () -> Component.translatable("screen.find_me.command.attack_target"),
                    () -> ClientCompanionCommandTarget.canAttackCrosshair(this.target, this.aimedEntityId),
                    () -> ClientCompanionCommandTarget.attackCrosshair(this.target, this.aimedEntityId)));
        }
        if (ClientCompanionCommandTarget.canLand(this.target)) {
            actions.add(new ActionView(ResourceLocation.fromNamespaceAndPath("find_me", "land"),
                    () -> Component.translatable("screen.find_me.command.land"),
                    () -> true,
                    () -> ClientCompanionCommandTarget.land(this.target)));
        }
        if (ClientCompanionCommandTarget.canRideHome(this.target)) {
            actions.add(new ActionView(ResourceLocation.fromNamespaceAndPath("find_me", "ride_home"),
                    () -> Component.translatable("screen.find_me.command.ride_home"),
                    () -> true, () -> ClientCompanionCommandTarget.rideHome(this.target)));
        }
        if (ClientCompanionCommandTarget.canUseWaystones(this.target)) {
            actions.add(new ActionView(ResourceLocation.fromNamespaceAndPath("find_me", "waystone_teleport"),
                    () -> Component.translatable("screen.find_me.command.teleport"),
                    () -> true, () -> ClientCompanionCommandTarget.openWaystones(this.target)));
        }
        return actions;
    }

    static AimSnapshot captureAim(CompanionCommandTarget commandTarget) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return new AimSnapshot(null, -1);
        }
        Entity camera = minecraft.getCameraEntity() == null ? minecraft.player : minecraft.getCameraEntity();
        double range = 96.0;
        Vec3 start = camera.getEyePosition(1.0f);
        Vec3 direction = camera.getViewVector(1.0f);
        Vec3 end = start.add(direction.scale(range));
        HitResult blockAim = minecraft.player.pick(range, 1.0f, false);
        BlockPos position = blockAim instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit.getBlockPos().relative(blockHit.getDirection()) : null;
        double maxDistanceSqr = blockAim == null || blockAim.getType() == HitResult.Type.MISS
                ? range * range : start.distanceToSqr(blockAim.getLocation());
        UUID ignoredUuid = commandTarget == null || commandTarget.entry() == null
                ? null : commandTarget.entry().uuid();
        Entity rootVehicle = minecraft.player.getRootVehicle();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(camera, start, end,
                camera.getBoundingBox().expandTowards(direction.scale(range)).inflate(1.0),
                entity -> entity instanceof LivingEntity && entity.isPickable() && !entity.isSpectator()
                        && entity != minecraft.player
                        && !entity.getUUID().equals(ignoredUuid)
                        && entity.getRootVehicle() != rootVehicle,
                maxDistanceSqr);
        int entityId = entityHit == null ? -1 : entityHit.getEntity().getId();
        return new AimSnapshot(position, entityId);
    }

    private List<ActionView> abilityActions() {
        ArrayList<ActionView> actions = new ArrayList<>();
        for (FindMeClientAbilityActionRegistry.Entry entry : FindMeClientAbilityActionRegistry.entries()) {
            actions.add(new ActionView(entry.id(), () -> entry.label(this.target),
                    () -> entry.available(this.target), () -> entry.activate(this.target)));
        }
        return actions;
    }

    private boolean activate(ActionView action) {
        if (!action.available() || !action.activate()) return false;
        playConfirmSound();
        return true;
    }

    private boolean isCenter(double mouseX, double mouseY) {
        double dx = mouseX - this.width / 2.0;
        double dy = mouseY - this.height / 2.0;
        return dx * dx + dy * dy <= CompanionWheelLayout.CENTER_CANCEL_RADIUS
                * CompanionWheelLayout.CENTER_CANCEL_RADIUS;
    }

    private float fade(float partialTick) {
        float progress = Math.min(FADE_TICKS, this.openTicks + partialTick) / FADE_TICKS;
        return progress * progress * (3.0f - 2.0f * progress);
    }

    private static void playHoverSound() {
        if (!ClientWheelPresentationState.operationSounds()) return;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.45f, 0.22f));
    }

    private static void playConfirmSound() {
        if (!ClientWheelPresentationState.operationSounds()) return;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.42f));
    }

    private record ActionView(ResourceLocation id, Supplier<Component> labelSupplier,
                              BooleanSupplier availableSupplier, BooleanSupplier activation) {
        Component label() {
            return this.labelSupplier.get();
        }

        boolean available() {
            return this.availableSupplier.getAsBoolean();
        }

        boolean activate() {
            return this.activation.getAsBoolean();
        }
    }

    record AimSnapshot(BlockPos position, int entityId) {
    }
}
