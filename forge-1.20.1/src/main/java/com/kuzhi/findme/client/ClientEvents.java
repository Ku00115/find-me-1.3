package com.kuzhi.findme.client;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.client.ClientCameraLock;
import com.kuzhi.findme.client.CompanionWheelScreen;
import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.VehicleCommandAction;
import com.kuzhi.findme.common.ModParticles;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.common.MountRosterAction;
import com.kuzhi.findme.network.CompanionCommandPacket;
import com.kuzhi.findme.network.CompanionForwardTravelTeleportPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.VehicleCommandPacket;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    private static final KeyMapping MOUNT_KEY = new KeyMapping("key.find_me.mount", InputConstants.Type.KEYSYM, 82, "key.categories.find_me");
    private static final KeyMapping COMPANION_KEY = new KeyMapping("key.find_me.companion", InputConstants.Type.KEYSYM, 86, "key.categories.find_me");
    private static final KeyMapping COMMAND_KEY = new KeyMapping("key.find_me.command", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.find_me");
    private static final KeyMapping MANAGE_KEY = new KeyMapping("key.find_me.manage", InputConstants.Type.KEYSYM, 71, "key.categories.find_me");
    private static boolean mountWasDown;
    private static boolean companionWasDown;
    private static boolean rawMountDown;
    private static boolean rawCompanionDown;
    private static boolean rawCommandDown;
    private static long mountDownAt;
    private static long lastMountShortPressAt;
    private static long companionDownAt;
    private static boolean mountWheelOpened;
    private static boolean mountEmergencyTriggered;
    private static boolean companionWheelOpened;
    private static boolean mountBlockedByScreen;
    private static boolean companionBlockedByScreen;
    private static boolean commandWasDown;
    private static boolean commandWheelOpened;
    private static boolean commandBlockedByScreen;
    private static final Set<Integer> BURROW_TRANSLATED_ENTITIES = new HashSet<>();
    private static Field iceAndFireRenderingRidersField;
    private static boolean iceAndFireRenderingRidersResolved;

    private ClientEvents() {
    }

    static String keyName(CompanionKind kind) {
        KeyMapping key = kind == CompanionKind.MOUNT ? MOUNT_KEY : COMPANION_KEY;
        return key.getTranslatedKeyMessage().getString();
    }

    static String commandKeyName() {
        return COMMAND_KEY.getTranslatedKeyMessage().getString();
    }

    public static void register(IEventBus modEventBus) {
        FindMePreviewElement.register();
        modEventBus.addListener(ClientEvents::registerKeys);
        modEventBus.addListener(ClientEvents::registerParticles);
        MinecraftForge.EVENT_BUS.register(ForgeEvents.class);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(MOUNT_KEY);
        event.register(COMPANION_KEY);
        event.register(COMMAND_KEY);
        event.register(MANAGE_KEY);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.CONTRACT_GLYPH.get(), sprites -> new ClientContractGlyphParticle.Provider(sprites, 0.58f, 0.92f, 1.0f));
        event.registerSpriteSet(ModParticles.CONTRACT_GLYPH_RESPONSE.get(), sprites -> new ClientContractGlyphParticle.Provider(sprites, 0.72f, 0.44f, 1.0f));
        event.registerSpriteSet(ModParticles.CONTRACT_GLYPH_VOW.get(), sprites -> new ClientContractGlyphParticle.Provider(sprites, 1.0f, 0.66f, 0.22f));
    }

    private static void tickKey(CompanionKind kind, KeyMapping key) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        FindMeModule module = kind == CompanionKind.MOUNT ? FindMeModule.RIDING : FindMeModule.COMPANIONS;
        if (!ClientFindMeModuleState.enabled(module)) {
            if (minecraft.screen instanceof VehicleWheelScreen && kind == CompanionKind.MOUNT
                    || minecraft.screen instanceof CompanionWheelScreen wheel && wheel.kind() == kind
                    || minecraft.screen instanceof CompanionCommandWheelScreen commandWheel
                    && commandWheel.openedFromRoster(kind)) {
                minecraft.setScreen(null);
            }
            ClientEvents.resetKeyGesture(kind);
            return;
        }
        boolean isDown = ClientEvents.isKeyDown(kind, key);
        boolean ownWheel = minecraft.screen instanceof VehicleWheelScreen && kind == CompanionKind.MOUNT
                || minecraft.screen instanceof CompanionWheelScreen wheel && wheel.kind() == kind
                || minecraft.screen instanceof CompanionCommandWheelScreen commandWheel
                && commandWheel.openedFromRoster(kind);
        if (minecraft.screen != null && !ownWheel) {
            ClientEvents.setBlockedByScreen(kind, isDown || ClientEvents.blockedByScreen(kind));
            ClientEvents.resetKeyGesture(kind);
            return;
        }
        if (ClientEvents.blockedByScreen(kind)) {
            if (!isDown) {
                ClientEvents.setBlockedByScreen(kind, false);
            }
            ClientEvents.resetKeyGesture(kind);
            return;
        }
        boolean wasDown = ClientEvents.wasDown(kind);
        long now = System.currentTimeMillis();
        if (isDown && !wasDown) {
            ClientEvents.setWasDown(kind, true);
            ClientEvents.setDownAt(kind, now);
            ClientEvents.setWheelOpened(kind, false);
            ClientEvents.send(kind, CompanionAction.SYNC, -1);
            if (kind == CompanionKind.MOUNT) {
                ClientEvents.sendVehicle(VehicleCommandAction.SYNC, -1);
                if (shouldTriggerEmergencyRescue(minecraft)) {
                    // Keep rescue classification in the key-up path so a press
                    // cannot dispatch twice or preempt the original summon timing.
                    mountEmergencyTriggered = true;
                }
            }
            return;
        }
        if (isDown && wasDown && !ClientEvents.wheelOpened(kind) && !mountEmergencyTriggered
                && now - ClientEvents.downAt(kind) >= 250L && minecraft.screen == null) {
            ClientEvents.setWheelOpened(kind, true);
            minecraft.setScreen((Screen)new CompanionWheelScreen(kind));
            return;
        }
        if (!isDown && wasDown) {
            ClientEvents.setWasDown(kind, false);
            if (ClientEvents.wheelOpened(kind)) {
                if (minecraft.screen instanceof CompanionCommandWheelScreen commandWheel
                        && commandWheel.openedFromRoster(kind)) {
                    commandWheel.closeFromSourceRelease();
                    ClientEvents.setWheelOpened(kind, false);
                    return;
                }
                CompanionWheelScreen screen;
                Screen screen2 = minecraft.screen;
                if (screen2 instanceof CompanionWheelScreen && (screen = (CompanionWheelScreen)screen2).kind() == kind) {
                    screen.confirmSelection();
                }
                ClientEvents.setWheelOpened(kind, false);
            } else {
                // Commit short presses on release, matching the original control path.
                handleShortPress(kind);
                if (kind == CompanionKind.MOUNT) {
                    mountEmergencyTriggered = false;
                }
            }
        }
    }

    private static void handleShortPress(CompanionKind kind) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (kind == CompanionKind.MOUNT) {
            UUID forwardTravelUuid = findForwardTravelTarget(kind);
            if (forwardTravelUuid != null) {
                ModNetwork.sendToServer(new CompanionForwardTravelTeleportPacket(kind, forwardTravelUuid));
                return;
            }
            handleMountShortPress(minecraft);
            return;
        }
        UUID forwardTravelUuid = findForwardTravelTarget(kind);
        if (forwardTravelUuid != null) {
            ModNetwork.sendToServer(new CompanionForwardTravelTeleportPacket(kind, forwardTravelUuid));
            return;
        }
        UUID pendingUuid = ClientCompanionWheelController.pendingUuid(kind);
        UUID currentRideUuid = minecraft.player.getVehicle() == null
                ? null : minecraft.player.getVehicle().getUUID();
        UUID targetUuid = pendingUuid != null ? pendingUuid : ClientCompanionState.activeUuid(kind);
        if (targetUuid != null) {
            sendPendingFindMe(kind, targetUuid);
        } else {
            ClientEvents.send(kind, CompanionAction.SUMMON, -1);
        }
    }

    private static UUID findForwardTravelTarget(CompanionKind kind) {
        if (kind == null) return null;
        if (kind == CompanionKind.MOUNT) {
            ClientMountRosterState.Entry selected = ClientMountRosterState.selectedEntry();
            if (selected != null && selected.source() == com.kuzhi.findme.common.MountRosterSource.FIND_ME
                    && selected.findMe() != null
                    && selected.findMe().tacticalAction() == com.kuzhi.findme.common.CompanionTacticalAction.MOVE_FORWARD) {
                return selected.uuid();
            }
        }
        return ClientCompanionState.allEntries(kind).stream()
                .filter(entry -> entry.tacticalAction() == com.kuzhi.findme.common.CompanionTacticalAction.MOVE_FORWARD
                        && entry.alive() && entry.deployed())
                .map(com.kuzhi.findme.network.CompanionListPacket.Entry::uuid)
                .findFirst().orElse(null);
    }

    private static void handleMountShortPress(Minecraft minecraft) {
        ClientMountRosterState.Entry selected = ClientMountRosterState.selectedEntry();
        if (selected == null || selected.empty()) return;
        UUID currentRideUuid = minecraft.player.getVehicle() == null ? null : minecraft.player.getVehicle().getUUID();
        if (currentRideUuid != null && currentRideUuid.equals(selected.uuid())) {
            if (!nowWithinDoublePressWindow()) {
                lastMountShortPressAt = System.currentTimeMillis();
                return;
            }
            lastMountShortPressAt = 0L;
            dispatchMountEntry(selected, true);
            return;
        }
        if (!ClientCompanionWheelController.switchInProgress(CompanionKind.MOUNT, selected.uuid())) {
            dispatchMountEntry(selected, false);
        }
    }

    private static void dispatchMountEntry(ClientMountRosterState.Entry entry, boolean recall) {
        ClientMountRosterState.select(entry.source(), entry.uuid());
        ClientMountRosterTransactionState.send(recall ? MountRosterAction.RECALL : MountRosterAction.ACTIVATE,
                entry.source(), entry.uuid(), entry.sourceSlot(), entry.teamIndex());
    }

    private static boolean shouldTriggerEmergencyRescue(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.player.getVehicle() != null
                || minecraft.player.onGround() || minecraft.player.isInWater()
                || minecraft.player.isFallFlying() || minecraft.player.onClimbable()
                || minecraft.player.getAbilities().flying) {
            return false;
        }
        if (minecraft.player.getDeltaMovement().y >= -0.01) {
            return false;
        }
        if (minecraft.player.fallDistance >= Math.max(3.0f, Config.rescueMinFallDistance)) {
            return true;
        }
        // Short drops used to be missed because fallDistance has not reached
        // the configured threshold before the player is already near the floor.
        // Mirror the server's bounded low-altitude window without ray-tracing
        // the whole world: inspect the six blocks directly below the player.
        int baseY = minecraft.player.blockPosition().getY();
        for (int y = baseY - 1; y >= Math.max(minecraft.player.level().getMinBuildHeight(), baseY - 6); y--) {
            if (!minecraft.player.level().getBlockState(new net.minecraft.core.BlockPos(
                    minecraft.player.blockPosition().getX(), y,
                    minecraft.player.blockPosition().getZ())).getCollisionShape(minecraft.player.level(),
                    new net.minecraft.core.BlockPos(minecraft.player.blockPosition().getX(), y,
                            minecraft.player.blockPosition().getZ())).isEmpty()) {
                return minecraft.player.getY() - y <= 6.0;
            }
        }
        return false;
    }

    private static void sendPendingFindMe(CompanionKind kind, UUID uuid) {
        if (ModNetwork.channel == null || uuid == null
                || ClientCompanionWheelController.switchInProgress(kind, uuid)) {
            return;
        }
        if (kind == CompanionKind.MOUNT) {
            ClientCameraLock.arm();
        }
        ClientCompanionWheelController.sendIntent(kind, uuid, CompanionAction.SELECT_SUMMON);
    }

    private static void tickCommandKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            resetCommandGesture();
            return;
        }
        if (!ClientFindMeModuleState.enabled(FindMeModule.RIDING)
                && !ClientFindMeModuleState.enabled(FindMeModule.COMPANIONS)) {
            if (minecraft.screen instanceof CompanionCommandWheelScreen) {
                minecraft.setScreen(null);
            }
            resetCommandGesture();
            return;
        }
        boolean isDown = commandKeyDown();
        boolean ownScreen = minecraft.screen instanceof CompanionCommandWheelScreen;
        if (minecraft.screen != null && !ownScreen) {
            commandBlockedByScreen = isDown || commandBlockedByScreen;
            resetCommandGesture();
            return;
        }
        if (commandBlockedByScreen) {
            if (!isDown) {
                commandBlockedByScreen = false;
            }
            resetCommandGesture();
            return;
        }
        if (isDown && !commandWasDown) {
            commandWasDown = true;
            ClientCompanionCommandTarget.sync();
            com.kuzhi.findme.api.client.CompanionCommandTarget target = ClientCompanionCommandTarget.resolve();
            if (target == null) {
                commandWheelOpened = false;
                ClientCompanionCommandTarget.showNoTarget();
                return;
            }
            commandWheelOpened = true;
            minecraft.setScreen(new CompanionCommandWheelScreen(target));
            return;
        }
        if (!isDown && commandWasDown) {
            commandWasDown = false;
            if (commandWheelOpened) {
                if (minecraft.screen instanceof CompanionCommandWheelScreen) minecraft.setScreen(null);
                commandWheelOpened = false;
            }
        }
    }

    private static boolean commandKeyDown() {
        if (COMMAND_KEY.isDown() || rawCommandDown) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) {
            return false;
        }
        InputConstants.Key bound = COMMAND_KEY.getKey();
        if (bound.getValue() < 0) {
            return false;
        }
        long window = minecraft.getWindow().getWindow();
        if (bound.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(window, bound.getValue());
        }
        return bound.getType() == InputConstants.Type.MOUSE
                && GLFW.glfwGetMouseButton(window, bound.getValue()) == GLFW.GLFW_PRESS;
    }

    private static void resetCommandGesture() {
        commandWasDown = false;
        commandWheelOpened = false;
    }

    private static boolean nowWithinDoublePressWindow() {
        long now = System.currentTimeMillis();
        return lastMountShortPressAt > 0L && now - lastMountShortPressAt <= 350L;
    }

    static void resetSessionInput() {
        mountWasDown = false;
        companionWasDown = false;
        rawMountDown = false;
        rawCompanionDown = false;
        rawCommandDown = false;
        mountDownAt = 0L;
        companionDownAt = 0L;
        lastMountShortPressAt = 0L;
        mountWheelOpened = false;
        mountEmergencyTriggered = false;
        companionWheelOpened = false;
        mountBlockedByScreen = false;
        companionBlockedByScreen = false;
        commandWasDown = false;
        commandWheelOpened = false;
        commandBlockedByScreen = false;
        BURROW_TRANSLATED_ENTITIES.clear();
    }

    private static void send(CompanionKind kind, CompanionAction action, int targetEntityId) {
        if (ModNetwork.channel != null) {
            if (kind == CompanionKind.MOUNT && action == CompanionAction.SUMMON) {
                ClientCameraLock.arm();
            }
            ModNetwork.sendToServer(new CompanionCommandPacket(kind, action, targetEntityId));
        }
    }

    private static void sendVehicle(VehicleCommandAction action, int value) {
        if (ModNetwork.channel != null) {
            if (action == VehicleCommandAction.SELECT_SUMMON) {
                ClientCameraLock.arm();
            }
            UUID targetUuid = null;
            if (action == VehicleCommandAction.SELECT || action == VehicleCommandAction.SELECT_SUMMON
                    || action == VehicleCommandAction.RECALL) {
                java.util.List<com.kuzhi.findme.network.VehicleListPacket.Entry> entries = ClientVehicleState.wheelEntries();
                if (value >= 0 && value < entries.size()) targetUuid = entries.get(value).uuid();
            }
            ModNetwork.sendToServer(new VehicleCommandPacket(action, targetUuid, -1));
        }
    }

    private static boolean isKeyDown(CompanionKind kind, KeyMapping key) {
        if (key.isDown() || (kind == CompanionKind.MOUNT ? rawMountDown : rawCompanionDown)) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) {
            return false;
        }
        long window = minecraft.getWindow().getWindow();
        InputConstants.Key bound = key.getKey();
        if (bound.getValue() < 0) {
            return false;
        }
        if (bound.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(window, bound.getValue());
        }
        if (bound.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, bound.getValue()) == GLFW.GLFW_PRESS;
        }
        return false;
    }

    private static boolean wasDown(CompanionKind kind) {
        return kind == CompanionKind.MOUNT ? mountWasDown : companionWasDown;
    }

    private static void setWasDown(CompanionKind kind, boolean value) {
        if (kind == CompanionKind.MOUNT) {
            mountWasDown = value;
        } else {
            companionWasDown = value;
        }
    }

    private static long downAt(CompanionKind kind) {
        return kind == CompanionKind.MOUNT ? mountDownAt : companionDownAt;
    }

    private static void setDownAt(CompanionKind kind, long value) {
        if (kind == CompanionKind.MOUNT) {
            mountDownAt = value;
        } else {
            companionDownAt = value;
        }
    }

    private static boolean wheelOpened(CompanionKind kind) {
        return kind == CompanionKind.MOUNT ? mountWheelOpened : companionWheelOpened;
    }

    private static void setWheelOpened(CompanionKind kind, boolean value) {
        if (kind == CompanionKind.MOUNT) {
            mountWheelOpened = value;
        } else {
            companionWheelOpened = value;
        }
    }

    public static class ForgeEvents {
        @SubscribeEvent
        public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
            ClientFindMeSessionState.reset();
        }

        @SubscribeEvent
        public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientFindMeSessionState.reset();
        }

        @SubscribeEvent
        public static void onKey(InputEvent.Key event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (MANAGE_KEY.matches(event.getKey(), event.getScanCode()) && event.getAction() == GLFW.GLFW_PRESS
                    && minecraft.screen == null
                    && (ClientFindMeModuleState.enabled(FindMeModule.MANAGEMENT)
                    || ClientFindMeModuleState.canManage())) {
                FindMeAuiManageScreen.open();
                return;
            }
            if (event.getAction() == 1 && minecraft.options.keyTogglePerspective.matches(event.getKey(), event.getScanCode())) {
                ClientCameraLock.allowManualChange();
            }
            if (MOUNT_KEY.matches(event.getKey(), event.getScanCode())) {
                rawMountDown = event.getAction() != 0;
            } else if (COMPANION_KEY.matches(event.getKey(), event.getScanCode())) {
                rawCompanionDown = event.getAction() != 0;
            } else if (COMMAND_KEY.matches(event.getKey(), event.getScanCode())) {
                rawCommandDown = event.getAction() != GLFW.GLFW_RELEASE;
            }
        }

        @SubscribeEvent
        public static void onMouseButton(InputEvent.MouseButton.Pre event) {
            if (MOUNT_KEY.matchesMouse(event.getButton())) {
                rawMountDown = event.getAction() != 0;
            } else if (COMPANION_KEY.matchesMouse(event.getButton())) {
                rawCompanionDown = event.getAction() != 0;
            } else if (COMMAND_KEY.matchesMouse(event.getButton())) {
                rawCommandDown = event.getAction() != GLFW.GLFW_RELEASE;
            }
        }

        @SubscribeEvent
        public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            Screen screen = Minecraft.getInstance().screen;
            if (screen != null) {
                return;
            }
            if (ClientFindMeModuleState.enabled(FindMeModule.RIDING)
                    && ClientEvents.isKeyDown(CompanionKind.MOUNT, MOUNT_KEY)) {
                ClientMountRosterState.Entry selected = ClientMountRosterState.selectRelative(
                        event.getScrollDelta() < 0.0 ? 1 : -1);
                if (selected != null) {
                    ClientMountRosterState.select(selected.source(), selected.uuid());
                    ClientMountRosterTransactionState.send(MountRosterAction.SELECT, selected.source(),
                            selected.uuid(), selected.sourceSlot(), selected.teamIndex());
                }
                event.setCanceled(true);
            } else if (ClientFindMeModuleState.enabled(FindMeModule.COMPANIONS)
                    && ClientEvents.isKeyDown(CompanionKind.COMPANION, COMPANION_KEY)) {
                ClientEvents.send(CompanionKind.COMPANION, event.getScrollDelta() > 0.0 ? CompanionAction.PREVIOUS : CompanionAction.NEXT, -1);
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onClientTickPre(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                ClientRideHomeOrientationState.tickBeforePlayer();
            }
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            FindMeAuiManageScreen.prewarmIfPossible();
            if (Minecraft.getInstance().screen instanceof CompanionWheelScreen
                    || Minecraft.getInstance().screen instanceof VehicleWheelScreen
                    || Minecraft.getInstance().screen instanceof CompanionCommandWheelScreen) {
                CompanionWheelScreen.syncMovementKeys();
            }
            ClientEvents.tickKey(CompanionKind.MOUNT, MOUNT_KEY);
            ClientEvents.tickKey(CompanionKind.COMPANION, COMPANION_KEY);
            ClientEvents.tickCommandKey();
            ClientCameraLock.tick();
            ClientContractCamera.tick();
            ClientMountApproachPresentationState.tick();
            ClientRidingCameraState.tick();
            ClientRideHomeReadyState.tick();
            ClientRideHomeTransitionState.tick();
            ClientContractRenderState.tick();
            ClientRescueMagicRenderState.tick();
            ClientTacticalFormationState.tick();
            ClientTacticalTargetOutlineState.tick();
            ClientStorageEffectState.tick();
            ClientBurrowEffectState.tick();
            ClientVehicleSealEffectState.tick();
            ClientCompanionDialogueState.tick();
            ClientExternalRideHandoffState.tick();
            CompanionDetailPreviewRenderer.tickLiveMirrors();
            CompanionDetailPreviewRenderer.tickWarmCache();
        }

        @SubscribeEvent
        public static void onMovementInput(MovementInputUpdateEvent event) {
            if (ClientContractRenderState.locksInput()) {
                event.getInput().up = false;
                event.getInput().down = false;
                event.getInput().left = false;
                event.getInput().right = false;
                event.getInput().jumping = false;
                event.getInput().shiftKeyDown = false;
                event.getInput().forwardImpulse = 0.0f;
                event.getInput().leftImpulse = 0.0f;
                return;
            }
            if (Minecraft.getInstance().screen instanceof CompanionWheelScreen
                    || Minecraft.getInstance().screen instanceof VehicleWheelScreen
                    || Minecraft.getInstance().screen instanceof CompanionCommandWheelScreen) {
                CompanionWheelScreen.syncMovementKeys();
            }
        }

        @SubscribeEvent
        public static void onInteractionInput(InputEvent.InteractionKeyMappingTriggered event) {
            if (ClientContractRenderState.locksInput()) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            ClientContractCeremonyRenderer.renderWorld(event);
            ClientBurrowEffectRenderer.renderWorld(event);
            ClientRescueMagicRenderer.renderWorld(event);
            ClientTacticalFormationRenderer.renderWorld(event);
            ClientStorageEffectRenderer.renderWorld(event);
            ClientVehicleSealEffectRenderer.renderWorld(event);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (!ClientEntityPreviewRenderGuard.active()
                    && ClientMountApproachPresentationState.hidden(event.getEntity())) {
                event.setCanceled(true);
                return;
            }
            if (ClientRiddenMountRenderPolicy.shouldHide(minecraft, event.getEntity())) {
                event.setCanceled(true);
                return;
            }
            if (minecraft.screen instanceof FindMeAuiHouseScreen
                    && !ClientEntityPreviewRenderGuard.active()
                    && ClientHouseState.isCurrentHouseResident(event.getEntity().getUUID())) {
                event.setCanceled(true);
                return;
            }
            double yOffset = ClientBurrowEffectState.renderYOffset(event.getEntity().getId(), event.getPartialTick());
            if (Math.abs(yOffset) < 0.001) {
                return;
            }
            event.getPoseStack().pushPose();
            event.getPoseStack().translate(0.0, yOffset, 0.0);
            BURROW_TRANSLATED_ENTITIES.add(event.getEntity().getId());
        }

        @SubscribeEvent
        public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
            if (BURROW_TRANSLATED_ENTITIES.remove(event.getEntity().getId())) {
                event.getPoseStack().popPose();
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (event.getEntity() == minecraft.player) {
                ClientRideHomeOrientationState.applyForRender();
            }
            if (ClientContractCamera.renderLocalRider() && event.getEntity() == minecraft.player
                    && minecraft.player != null && isIceAndFireRide(minecraft.player.getVehicle())) {
                event.setCanceled(!isIceAndFireRiderFeaturePass(minecraft.player));
            }
        }

        private static boolean isIceAndFireRide(net.minecraft.world.entity.Entity entity) {
            return entity != null && net.minecraft.world.entity.EntityType.getKey(entity.getType())
                    .getNamespace().equals("iceandfire");
        }

        private static boolean isIceAndFireRiderFeaturePass(net.minecraft.world.entity.player.Player player) {
            if (!iceAndFireRenderingRidersResolved) {
                iceAndFireRenderingRidersResolved = true;
                try {
                    Class<?> renderer = Class.forName("com.iafenvoy.iceandfire.render.entity.feature.DragonRiderFeatureRenderer");
                    iceAndFireRenderingRidersField = renderer.getField("RENDERING_RIDERS");
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    iceAndFireRenderingRidersField = null;
                }
            }
            if (iceAndFireRenderingRidersField == null) {
                return false;
            }
            try {
                Object value = iceAndFireRenderingRidersField.get(null);
                return value instanceof Collection<?> riders && riders.contains(player);
            } catch (IllegalAccessException | RuntimeException ignored) {
                return false;
            }
        }

        @SubscribeEvent
        public static void onRenderGuiLayer(RenderGuiOverlayEvent.Pre event) {
            if (ClientContractSkyStage.active()) {
                event.setCanceled(true);
                return;
            }
            Screen screen = Minecraft.getInstance().screen;
            if ((screen instanceof CompanionWheelScreen || screen instanceof VehicleWheelScreen || screen instanceof CompanionCommandWheelScreen)
                    && event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onRenderHand(RenderHandEvent event) {
            if (ClientContractSkyStage.active()) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onScreenRender(ScreenEvent.Render.Pre event) {
            FindMePreviewElement.beginFrame(Minecraft.getInstance().getFrameTimeNs());
        }

        @SubscribeEvent
        public static void onScreenMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
            Screen screen = event.getScreen();
            if (screen instanceof CompanionWheelScreen wheel) {
                wheel.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDelta());
                event.setCanceled(true);
            } else if (screen instanceof VehicleWheelScreen wheel) {
                wheel.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDelta());
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            ClientContractCeremonyRenderer.renderGui(event);
            ClientCompanionDialogueRenderer.renderGui(event);
            ClientRideHomeTransitionState.render(event.getGuiGraphics(), event.getPartialTick());
        }

        @SubscribeEvent
        public static void onRenderGuiLayerPost(RenderGuiOverlayEvent.Post event) {
            if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
                FindMeHudRenderer.render(event.getGuiGraphics());
            }
        }
    }

    private static boolean blockedByScreen(CompanionKind kind) {
        return kind == CompanionKind.MOUNT ? mountBlockedByScreen : companionBlockedByScreen;
    }

    private static void setBlockedByScreen(CompanionKind kind, boolean value) {
        if (kind == CompanionKind.MOUNT) {
            mountBlockedByScreen = value;
        } else {
            companionBlockedByScreen = value;
        }
    }

    private static void resetKeyGesture(CompanionKind kind) {
        ClientEvents.setWasDown(kind, false);
        ClientEvents.setWheelOpened(kind, false);
        if (kind == CompanionKind.MOUNT) {
            mountEmergencyTriggered = false;
        }
    }

    private static String fallbackPreviewType(CompanionKind kind) {
        return kind == CompanionKind.MOUNT ? "minecraft:horse" : "minecraft:wolf";
    }

}
