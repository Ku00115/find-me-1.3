package com.kuzhi.findme.client;

import com.kuzhi.findme.api.client.CompanionCommandTarget;
import com.kuzhi.findme.api.client.FindMeClientAbilityActionRegistry;
import com.kuzhi.findme.api.client.FindMeClientCommandActionRegistry;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionTacticalAction;
import com.kuzhi.findme.common.CompanionTeamCommandAction;
import com.kuzhi.findme.common.CompanionTeamTarget;
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
    private final CompanionTeamTarget teamTarget;
    private final int teamIndex;
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
        this.teamTarget = null;
        this.teamIndex = -1;
        this.returnScreen = returnScreen;
        this.mode = mode;
        this.aimedPosition = aim.position();
        this.aimedEntityId = aim.entityId();
    }

    CompanionCommandWheelScreen(CompanionTeamTarget teamTarget, int teamIndex,
                                CompanionWheelScreen returnScreen, AimSnapshot aim) {
        super(Component.translatable("screen.find_me.command.team_title"));
        this.target = null;
        this.teamTarget = teamTarget;
        this.teamIndex = teamIndex;
        this.returnScreen = returnScreen;
        this.mode = Mode.COMMAND;
        this.aimedPosition = aim.position();
        this.aimedEntityId = aim.entityId();
    }

    boolean openedFromRoster(CompanionKind kind) {
        return this.returnScreen != null && (this.target != null && this.target.kind() == kind
                || this.teamTarget == (kind == CompanionKind.MOUNT
                ? CompanionTeamTarget.MOUNT : CompanionTeamTarget.COMPANION));
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

        Component subject = this.target == null
                ? Component.translatable("screen.find_me.command.team_subject", this.teamIndex + 1)
                : Component.literal(this.target.entry().name());
        FindMeWheelRenderer.drawCommandHeader(graphics, this.title, subject.getString(), fade);
        com.kuzhi.findme.api.CompanionMagicState magicState = displayedMagicState();
        if (magicState.available()) {
            String mana = Component.translatable("screen.find_me.spell_slot.mana").getString() + "  "
                    + Math.round(magicState.mana()) + " / " + Math.round(magicState.maxMana());
            FindMeWheelRenderer.drawManaBar(graphics, mana, magicState.mana() / magicState.maxMana(), fade);
        }
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

    private com.kuzhi.findme.api.CompanionMagicState displayedMagicState() {
        if (this.target != null) return this.target.entry().magicState();
        if (this.teamTarget == null) return com.kuzhi.findme.api.CompanionMagicState.EMPTY;
        List<UUID> members = ClientCompanionTeamState.members(this.teamTarget, this.teamIndex);
        CompanionKind kind = this.teamTarget == CompanionTeamTarget.MOUNT
                ? CompanionKind.MOUNT : CompanionKind.COMPANION;
        return ClientCompanionState.allEntries(kind).stream()
                .filter(entry -> members.contains(entry.uuid()) && entry.magicState().available())
                .map(com.kuzhi.findme.network.CompanionListPacket.Entry::magicState)
                .findFirst().orElse(com.kuzhi.findme.api.CompanionMagicState.EMPTY);
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
                    if (Minecraft.getInstance().screen == this) Minecraft.getInstance().setScreen(null);
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
                    if (Minecraft.getInstance().screen == this) Minecraft.getInstance().setScreen(null);
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
        if (this.teamTarget != null) return teamCommandActions();
        return this.mode == Mode.COMMAND ? commandActions() : abilityActions();
    }

    private List<ActionView> teamCommandActions() {
        ArrayList<ActionView> actions = new ArrayList<>();
        boolean protecting = hasActiveTeamAction(CompanionTacticalAction.PROTECT_OWNER);
        actions.add(teamAction(protecting ? "team_cancel_protect" : "team_protect_owner",
                protecting ? "screen.find_me.command.cancel_protect" : "screen.find_me.command.team_protect_owner",
                protecting ? CompanionTeamCommandAction.CANCEL_PROTECT : CompanionTeamCommandAction.PROTECT_OWNER,
                null, -1));
        if (teamHasSpellRole(com.kuzhi.findme.api.CompanionSpellRole.DEFENSE)) {
            boolean magicProtecting = hasActiveTeamAction(CompanionTacticalAction.MAGIC_PROTECT);
            actions.add(teamAction(magicProtecting ? "team_cancel_magic_protect" : "team_magic_protect",
                    magicProtecting ? "screen.find_me.command.cancel_magic_protect"
                            : "screen.find_me.command.team_magic_protect",
                    magicProtecting ? CompanionTeamCommandAction.CANCEL_MAGIC_PROTECT
                            : CompanionTeamCommandAction.MAGIC_PROTECT, null, -1));
        }
        if (teamHasSpellRole(com.kuzhi.findme.api.CompanionSpellRole.HEAL)) {
            boolean supporting = hasActiveTeamAction(CompanionTacticalAction.MAGIC_SUPPORT);
            actions.add(teamAction(supporting ? "team_cancel_magic_support" : "team_magic_support",
                    supporting ? "screen.find_me.command.cancel_magic_support"
                            : "screen.find_me.command.team_magic_support",
                    supporting ? CompanionTeamCommandAction.CANCEL_MAGIC_SUPPORT
                            : CompanionTeamCommandAction.MAGIC_SUPPORT, null, -1));
        }
        actions.add(teamAction("team_follow", "screen.find_me.command.team_follow", CompanionTeamCommandAction.FOLLOW,
                null, -1));
        boolean guarding = hasActiveTeamAction(CompanionTacticalAction.GUARD_HERE);
        actions.add(teamAction(guarding ? "team_cancel_guard" : "team_guard_here",
                guarding ? "screen.find_me.command.cancel_guard" : "screen.find_me.command.team_guard_here",
                guarding ? CompanionTeamCommandAction.CANCEL_GUARD : CompanionTeamCommandAction.GUARD_HERE,
                guarding || Minecraft.getInstance().player == null ? null
                        : Minecraft.getInstance().player.blockPosition(), -1));
        if (this.aimedEntityId >= 0) {
            if (teamHasSpellRole(com.kuzhi.findme.api.CompanionSpellRole.ATTACK)) {
                actions.add(teamAction("team_magic_attack", "screen.find_me.command.team_magic_attack",
                        CompanionTeamCommandAction.MAGIC_ATTACK, null, this.aimedEntityId));
            }
            actions.add(teamAction("team_attack_target", "screen.find_me.command.team_attack_target",
                    CompanionTeamCommandAction.ATTACK_TARGET, null, this.aimedEntityId));
        }
        boolean paused = hasPausedTeamMember();
        actions.add(teamAction(paused ? "team_resume" : "team_pause",
                paused ? "screen.find_me.command.team_resume" : "screen.find_me.command.team_pause",
                CompanionTeamCommandAction.PAUSE_RESUME, null, -1));
        actions.add(new ActionView(ResourceLocation.fromNamespaceAndPath("find_me", "team_recall_all"),
                () -> Component.translatable("screen.find_me.command.team_recall_all"),
                () -> true,
                () -> ClientCompanionCommandTarget.sendTeamCommand(this.teamTarget, this.teamIndex,
                        CompanionTeamCommandAction.RECALL_ALL, null, -1)));
        if (hasDeployedFlyingTeamMember()) {
            actions.add(teamAction("team_land", "screen.find_me.command.team_land", CompanionTeamCommandAction.LAND,
                    null, -1));
        }
        return actions;
    }

    private ActionView teamAction(String id, String label, CompanionTeamCommandAction action,
                                  BlockPos targetPos, int targetEntityId) {
        return new ActionView(ResourceLocation.fromNamespaceAndPath("find_me", id),
                () -> Component.translatable(label),
                () -> !ClientCompanionTeamState.currentMembers(this.teamTarget).isEmpty(),
                () -> ClientCompanionCommandTarget.sendTeamCommand(this.teamTarget, this.teamIndex,
                        action, targetPos, targetEntityId));
    }

    private boolean hasPausedTeamMember() {
        List<UUID> members = ClientCompanionTeamState.members(this.teamTarget, this.teamIndex);
        return ClientCompanionState.allEntries(CompanionKind.COMPANION).stream()
                .anyMatch(entry -> members.contains(entry.uuid()) && entry.deployed()
                        && entry.tacticalAction() == CompanionTacticalAction.HOLD);
    }

    private boolean hasActiveTeamAction(CompanionTacticalAction action) {
        List<UUID> members = ClientCompanionTeamState.members(this.teamTarget, this.teamIndex);
        return ClientCompanionState.allEntries(CompanionKind.COMPANION).stream()
                .anyMatch(entry -> members.contains(entry.uuid()) && entry.tacticalAction() == action);
    }

    private boolean hasDeployedFlyingTeamMember() {
        List<UUID> members = ClientCompanionTeamState.members(this.teamTarget, this.teamIndex);
        return ClientCompanionState.allEntries(CompanionKind.COMPANION).stream()
                .anyMatch(entry -> members.contains(entry.uuid()) && entry.deployed()
                        && entry.moveType() == com.kuzhi.findme.common.CompanionMoveType.FLY);
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

        for (FindMeClientCommandActionRegistry.Entry entry : FindMeClientCommandActionRegistry.entries()) {
            actions.add(new ActionView(entry.id(), () -> entry.label(this.target),
                    () -> entry.available(this.target), () -> entry.activate(this.target)));
        }

        if (hasSpellRole(this.target.entry(), com.kuzhi.findme.api.CompanionSpellRole.DEFENSE)) {
            boolean magicProtecting = ClientCompanionCommandTarget.hasAction(this.target,
                    com.kuzhi.findme.common.CompanionTacticalAction.MAGIC_PROTECT);
            actions.add(new ActionView(ResourceLocation.fromNamespaceAndPath("find_me",
                    magicProtecting ? "cancel_magic_protect" : "magic_protect"),
                    () -> Component.translatable(magicProtecting
                            ? "screen.find_me.command.cancel_magic_protect"
                            : "screen.find_me.command.magic_protect"),
                    () -> ClientCompanionCommandTarget.canIssueTacticalOrder(this.target),
                    () -> magicProtecting ? ClientCompanionCommandTarget.stopCurrent(this.target)
                            : ClientCompanionCommandTarget.magicProtect(this.target)));
        }
        if (hasSpellRole(this.target.entry(), com.kuzhi.findme.api.CompanionSpellRole.HEAL)) {
            boolean supporting = ClientCompanionCommandTarget.hasAction(this.target,
                    com.kuzhi.findme.common.CompanionTacticalAction.MAGIC_SUPPORT);
            actions.add(new ActionView(ResourceLocation.fromNamespaceAndPath("find_me",
                    supporting ? "cancel_magic_support" : "magic_support"),
                    () -> Component.translatable(supporting
                            ? "screen.find_me.command.cancel_magic_support"
                            : "screen.find_me.command.magic_support"),
                    () -> ClientCompanionCommandTarget.canIssueTacticalOrder(this.target),
                     () -> supporting ? ClientCompanionCommandTarget.stopCurrent(this.target)
                         : ClientCompanionCommandTarget.magicSupport(this.target)));
        }

        boolean movingForward = ClientCompanionCommandTarget.hasAction(this.target,
                com.kuzhi.findme.common.CompanionTacticalAction.MOVE_FORWARD);
        actions.add(new ActionView(ResourceLocation.fromNamespaceAndPath("find_me",
                movingForward ? "cancel_move_forward" : "move_forward"),
                () -> Component.translatable(movingForward
                        ? "screen.find_me.command.cancel_move_forward"
                        : "screen.find_me.command.move_forward"),
                () -> ClientCompanionCommandTarget.canIssueTacticalOrder(this.target),
                () -> movingForward ? ClientCompanionCommandTarget.stopCurrent(this.target)
                        : ClientCompanionCommandTarget.moveForward(this.target)));

        com.kuzhi.findme.common.CompanionTacticalAction current = this.target.entry().tacticalAction();
        if (current != null && current != com.kuzhi.findme.common.CompanionTacticalAction.FOLLOW
                && current != com.kuzhi.findme.common.CompanionTacticalAction.GUARD_HERE
                && current != com.kuzhi.findme.common.CompanionTacticalAction.PROTECT_OWNER
                && current != com.kuzhi.findme.common.CompanionTacticalAction.MAGIC_PROTECT
                && current != com.kuzhi.findme.common.CompanionTacticalAction.MAGIC_SUPPORT
                && current != com.kuzhi.findme.common.CompanionTacticalAction.MOVE_FORWARD) {
            actions.add(new ActionView(ResourceLocation.fromNamespaceAndPath("find_me", "stop_current"),
                    () -> Component.translatable("screen.find_me.command.stop_current"),
                    () -> true, () -> ClientCompanionCommandTarget.stopCurrent(this.target)));
        }
        if (this.aimedEntityId >= 0) {
            if (hasSpellRole(this.target.entry(), com.kuzhi.findme.api.CompanionSpellRole.ATTACK)) {
                actions.add(new ActionView(ResourceLocation.fromNamespaceAndPath("find_me", "magic_attack"),
                        () -> Component.translatable("screen.find_me.command.magic_attack"),
                        () -> ClientCompanionCommandTarget.canAttackCrosshair(this.target, this.aimedEntityId),
                        () -> ClientCompanionCommandTarget.magicAttackCrosshair(this.target, this.aimedEntityId)));
            }
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

    private boolean teamHasSpellRole(com.kuzhi.findme.api.CompanionSpellRole role) {
        java.util.Set<UUID> members = new java.util.HashSet<>(
                ClientCompanionTeamState.members(this.teamTarget, this.teamIndex));
        return ClientCompanionState.allEntries(CompanionKind.COMPANION).stream()
                .anyMatch(entry -> members.contains(entry.uuid()) && hasSpellRole(entry, role));
    }

    private static boolean hasSpellRole(com.kuzhi.findme.network.CompanionListPacket.Entry entry,
                                        com.kuzhi.findme.api.CompanionSpellRole role) {
        return entry != null && entry.spellBindings().stream()
                .anyMatch(binding -> binding != null && binding.role() == role);
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
        FindMeAuiSound.wheelHover();
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
