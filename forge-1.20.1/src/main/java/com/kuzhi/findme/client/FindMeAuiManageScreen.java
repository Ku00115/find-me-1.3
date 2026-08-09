package com.kuzhi.findme.client;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.CompanionEffectPurpose;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.CompanionTeamAction;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.common.WarehouseEntityAction;
import com.kuzhi.findme.common.DoctorCommandAction;
import com.kuzhi.findme.common.FindMeSettingsAction;
import com.kuzhi.findme.common.FindMeUiSettings;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.common.FindMeTextMode;
import com.kuzhi.findme.common.FindMeWheelStyle;
import com.kuzhi.findme.common.FindMeFontFamily;
import com.kuzhi.findme.common.FindMeFontSize;
import com.kuzhi.findme.common.FindMeRidingCameraMode;
import com.kuzhi.findme.common.BindingAnimationPolicy;
import com.kuzhi.findme.common.SummonedOutlineMode;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.BackupWarehousePacket;
import com.kuzhi.findme.network.CompanionCommandPacket;
import com.kuzhi.findme.network.CompanionEffectStylePacket;
import com.kuzhi.findme.network.CompanionAnimationStylePacket;
import com.kuzhi.findme.network.CompanionSpellSlotCandidatesPacket;
import com.kuzhi.findme.network.CompanionSpellSlotPacket;
import com.kuzhi.findme.network.CompanionTeamCommandPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.VehicleCommandPacket;
import com.kuzhi.findme.network.WarehouseEntityCommandPacket;
import com.kuzhi.findme.network.DoctorCommandPacket;
import com.kuzhi.findme.network.DoctorPagePacket;
import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.VehicleCommandAction;
import com.kuzhi.findme.common.MountRosterAction;
import com.kuzhi.findme.common.MountRosterSource;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/** Shared AUI manager shell for mounts, vehicles, companions and teams. */
public final class FindMeAuiManageScreen extends FindMeAuiOverlayScreen {
    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private static final String PATH = "findme/manage/manager.html";
    private static final long FULL_SYNC_TTL_NANOS = 15_000_000_000L;
    private static final long MIN_SYNC_INTERVAL_NANOS = 2_000_000_000L;
    private static final long CARD_TOGGLE_IN_NANOS = 185_000_000L;
    private static final long CARD_EXIT_NANOS = 180_000_000L;
    private static final long CLICK_PULSE_NANOS = 140_000_000L;
    private static final double TEAM_ROW_HEIGHT = 27.0;
    private static final double TEAM_SCROLL_VIEWPORT_HEIGHT = 126.0;
    private static final double TEAM_SCROLL_THUMB_HEIGHT = 28.0;
    private static FindMeAuiManageScreen current;
    private static long lastFullSyncAtNanos;
    private static FindMeAuiManageScreen cachedScreen;
    private static boolean prewarmed;
    private Category category = Category.MOUNT;
    private int selectedTeam;
    private View view = View.TEAM;
    private View returnView = View.TEAM;
    private WarehouseFilter warehouseFilter = WarehouseFilter.ALL;
    private DeadFilter deadFilter = DeadFilter.ALL;
    private int detailSection;
    private int settingsSection;
    private UUID selectedUuid;
    private UUID expandedUuid;
    private UUID pendingInsertedMemberUuid;
    private int pendingInsertedTeamIndex = -1;
    private int pendingAutoJoinMotionTeam = -1;
    private int pendingTeamScrollIndex = -1;
    private boolean warehouseSelectionMode;
    private WarehouseMenuPage warehouseMenuPage = WarehouseMenuPage.MAIN;
    private TeamMenuPage teamMenuPage = TeamMenuPage.MAIN;
    private CompanionAnimationPurpose warehouseAnimationPurpose;
    private CompanionEffectPurpose warehouseEffectPurpose;
    private double teamScrollTop;
    private double warehouseScrollTop;
    private double settingsScrollTop;
    private boolean resetSettingsScrollOnRefresh;
    private String search = "";
    private String status = "";
    private int statusTicks;
    private boolean contextOpen;
    private SettingsChoiceMenu settingsChoiceMenu = SettingsChoiceMenu.NONE;
    private int contextTeam = -1;
    private int contextBackupIndex = -1;
    private long contextBackupSavedAt;
    private double contextAnchorX = -1.0;
    private double contextAnchorY = -1.0;
    private String contextMotionClass = "";
    private FindMeAuiContextMenuPlacement.Bounds contextBounds = FindMeAuiContextMenuPlacement.EMPTY;
    private String pendingDanger = "";
    private UUID spellPickerUuid;
    private UUID spellPickerRequestId;
    private int spellPickerCompanionSlot = -1;
    private List<CompanionSpellSlotCandidatesPacket.Entry> spellPickerCandidates = List.of();
    private int spellPickerSelectedSlot = -1;
    private long lastSignature;
    private FindMeUiSettings settings = FindMeUiSettings.defaults();
    private boolean settingsDirty;
    private DoctorPagePacket doctor;
    private BackupWarehousePacket backupWarehouse;
    private boolean backupWarehousePreview;
    private int pendingBackup = -1;
    private long pendingBackupSavedAt;
    private DoctorView doctorView = DoctorView.BACKUPS;
    private boolean doctorRestoreConfirm;
    private double doctorBackupScrollTop;
    private boolean doctorBackupScrollDragging;
    private double doctorBackupScrollDragOffset;
    private String lastDoctorBackupScrollThumbStyle = "";
    private int lastTeamScrollThumbTop = -1;
    private String lastTeamSelectionMarkerStyle = "";
    private String lastWarehouseScrollThumbStyle = "";
    private String lastSettingsScrollThumbStyle = "";
    private boolean settingsScrollDragging;
    private double settingsScrollDragOffset;
    private int detailPreviewDragButton = -1;
    private double detailPreviewLastX;
    private double detailPreviewLastY;
    private DragKind dragKind = DragKind.NONE;
    private int dragFrom = -1;
    private int dragOver = -1;
    private long dragPressedAtNanos;
    private Element dragSourceElement;
    private Element dragOverElement;
    private Element dragGhostElement;
    private String dragGhostAppearanceStyle = "";
    private boolean dragActive;
    private double dragMouseX;
    private double dragMouseY;
    private double dragGrabRatioX = 0.5;
    private double dragGrabRatioY = 0.5;
    private final Map<Element, String> dragInlineStyles = new IdentityHashMap<>();
    private PendingDrop pendingDrop;
    private int pendingDropTicks;
    private int suppressClickTicks;
    private int pendingSyncTicks = -1;
    private int forcedRefreshTicks = -1;
    private int refreshDebounceTicks = -1;
    private long pendingSignature = Long.MIN_VALUE;
    private String renderedMarkupKey = "";
    private String renderedLayoutStyle = "";
    private long cardExitStartedAtNanos;
    private Runnable cardExitAction;
    private UUID cardExitUuid;
    private CardTogglePhase cardTogglePhase = CardTogglePhase.IDLE;
    private long cardToggleStartedAtNanos;
    private UUID cardToggleUuid;
    private boolean cardToggleExpanding;
    private long clickPulseStartedAtNanos;
    private int clickPulseX;
    private int clickPulseY;
    private int clickPulseWidth;
    private int clickPulseHeight;
    private boolean clickPulseFromContext;

    public FindMeAuiManageScreen() {
        super(PATH);
        current = this;
    }

    public static void open() {
        boolean managementEnabled = ClientFindMeModuleState.enabled(FindMeModule.MANAGEMENT);
        if (!managementEnabled) return;
        if (cachedScreen == null) cachedScreen = new FindMeAuiManageScreen();
        cachedScreen.prepareForOpen();
        current = cachedScreen;
        MinecraftAccess.setScreen(cachedScreen);
    }

    /** Builds and lays out the retained AUI documents before the player first opens the manager. */
    public static void prewarmIfPossible() {
        if (prewarmed) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null || minecraft.screen == null
                || minecraft.screen instanceof FindMeAuiManageScreen) {
            return;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (width <= 0 || height <= 0) return;

        long startedAt = FindMeAuiPerformanceMonitor.start();
        if (cachedScreen == null) cachedScreen = new FindMeAuiManageScreen();
        cachedScreen.init(minecraft, width, height);
        Document document = cachedScreen.getLinkedDocument();
        if (document != null && document.isActive()) {
            document.commitStyleRecalc();
        }
        cachedScreen.removed();
        prewarmed = true;
        FindMeAuiPerformanceMonitor.record(cachedScreen, "prewarm.total", startedAt, 30.0);
    }

    public static void resetSession() {
        lastFullSyncAtNanos = 0L;
        if (cachedScreen != null) {
            cachedScreen.prepareForOpen();
            cachedScreen.doctor = null;
            cachedScreen.settings = FindMeUiSettings.defaults();
        }
        current = null;
    }

    private void prepareForOpen() {
        clearDrag();
        category = Category.MOUNT;
        selectedTeam = 0;
        view = View.TEAM;
        returnView = View.TEAM;
        warehouseFilter = WarehouseFilter.ALL;
        deadFilter = DeadFilter.ALL;
        detailSection = 0;
        settingsSection = 0;
        doctorView = DoctorView.BACKUPS;
        doctorRestoreConfirm = false;
        backupWarehouse = null;
        backupWarehousePreview = false;
        doctorBackupScrollTop = 0.0;
        doctorBackupScrollDragging = false;
        doctorBackupScrollDragOffset = 0.0;
        lastDoctorBackupScrollThumbStyle = "";
        selectedUuid = null;
        expandedUuid = null;
        pendingInsertedMemberUuid = null;
        pendingInsertedTeamIndex = -1;
        pendingAutoJoinMotionTeam = -1;
        pendingTeamScrollIndex = -1;
        warehouseSelectionMode = false;
        warehouseMenuPage = WarehouseMenuPage.MAIN;
        teamMenuPage = TeamMenuPage.MAIN;
        warehouseAnimationPurpose = null;
        warehouseEffectPurpose = null;
        warehouseScrollTop = 0.0;
        settingsScrollTop = 0.0;
        resetSettingsScrollOnRefresh = true;
        search = "";
        status = "";
        statusTicks = 0;
        contextOpen = false;
        settingsChoiceMenu = SettingsChoiceMenu.NONE;
        contextTeam = -1;
        contextBackupIndex = -1;
        contextBackupSavedAt = 0L;
        contextMotionClass = "";
        contextBounds = FindMeAuiContextMenuPlacement.EMPTY;
        pendingDanger = "";
        clearSpellPicker();
        pendingSyncTicks = -1;
        forcedRefreshTicks = -1;
        refreshDebounceTicks = -1;
        settingsScrollDragging = false;
        settingsScrollDragOffset = 0.0;
        pendingSignature = Long.MIN_VALUE;
        cardExitAction = null;
        cardExitUuid = null;
        cardTogglePhase = CardTogglePhase.IDLE;
        cardToggleUuid = null;
        settingsDirty = false;
        clickPulseStartedAtNanos = 0L;
    }

    public static void showResult(boolean success, String message) {
        showResult(success, message, null);
    }

    public static void showResult(boolean success, String message, UUID targetUuid) {
        if (current != null) {
            if (targetUuid != null && targetUuid.equals(current.cardExitUuid)) {
                current.finishCardExitResult(success, targetUuid);
            }
            if (message != null && !message.isBlank()) {
                current.setStatus(tr(success ? "screen.find_me.aui.status.ok" : "screen.find_me.aui.status.error", message), success ? 40 : 100);
            }
            current.scheduleRefresh(0);
        }
    }

    public static boolean updateSettings(FindMeUiSettings value, boolean success, String message) {
        if (current == null) return true;
        if (current.settingsDirty) return false;
        current.settings = value == null ? FindMeUiSettings.defaults() : value;
        if (!success && message != null && !message.isBlank()) current.setStatus(tr("screen.find_me.aui.status.error", message), 100);
        current.scheduleRefresh(2);
        return true;
    }

    public static void updateModuleState() {
        if (current == null) return;
        if (!ClientFindMeModuleState.enabled(FindMeModule.MANAGEMENT)) {
            if (net.minecraft.client.Minecraft.getInstance().screen == current) {
                net.minecraft.client.Minecraft.getInstance().setScreen(null);
            }
            return;
        }
        if (current.view == View.SETTINGS) {
            current.scheduleRefresh(0);
        }
    }

    public static void updateDoctor(DoctorPagePacket value) {
        if (current == null) {
            com.kuzhi.findme.FindMeMod.LOGGER.warn("[FindMe backup-ui] doctor packet dropped: no active manage screen");
            return;
        }
        if (value != null) {
            com.kuzhi.findme.FindMeMod.LOGGER.info("[FindMe backup-ui] doctor packet received view={} doctorView={} success={} records={} message={}",
                    current.view, current.doctorView, value.success(), value.backups().size(), value.messageKey());
        } else {
            com.kuzhi.findme.FindMeMod.LOGGER.warn("[FindMe backup-ui] doctor packet received: null payload");
        }
        // A doctor sync can arrive during the fold-in/fold-out page transition. Do not let
        // the network response cancel the visual transition that is already in progress.
        if (!current.isTransitionRunning()) current.settlePageTransition();
        current.doctor = value;
        if (value != null && value.messageKey() != null
                && (value.messageKey().endsWith("backup_created")
                || value.messageKey().endsWith("backup_restored")
                || value.messageKey().endsWith("restore_refused"))) {
            current.doctorView = DoctorView.BACKUPS;
            current.pendingBackup = -1;
            current.pendingBackupSavedAt = 0L;
            current.doctorRestoreConfirm = false;
        }
        current.pendingBackupSavedAt = value == null || current.pendingBackup < 0 ? 0L
                : value.backups().stream()
                .filter(backup -> backup.index() == current.pendingBackup)
                .findFirst()
                .map(DoctorPagePacket.Backup::savedAt)
                .orElse(0L);
        if (value != null && current.pendingBackup >= 0) {
            boolean restoreTargetValid = value.backups().stream()
                    .anyMatch(backup -> backup.index() == current.pendingBackup && backup.checksumValid());
            if (!restoreTargetValid) current.doctorRestoreConfirm = false;
        }
        if (value != null && value.messageKey() != null && !value.messageKey().isBlank()) {
            String localized = tr(value.messageKey());
            current.setStatus(tr(value.success() ? "screen.find_me.aui.status.ok" : "screen.find_me.aui.status.error", localized), value.success() ? 40 : 100);
        }
        // The packet is already handled on the client thread. Rebuild the populated shell now.
        current.scheduleRefresh(0);
    }

    public static void updateBackupWarehouse(BackupWarehousePacket value) {
        if (current == null) return;
        if (value != null) {
            com.kuzhi.findme.FindMeMod.LOGGER.info("[FindMe backup-ui] received snapshot index={} success={} entries={} message={}",
                    value.backupIndex(), value.success(), value.entries().size(), value.messageKey());
        }
        if (value != null && value.success()) {
            current.backupWarehouse = value;
            current.backupWarehousePreview = true;
            current.view = View.WAREHOUSE;
            current.warehouseFilter = WarehouseFilter.ALL;
            current.warehouseSelectionMode = false;
            current.warehouseScrollTop = 0.0;
            current.selectedUuid = null;
            current.expandedUuid = null;
            current.search = "";
            current.contextOpen = false;
        } else if (value != null && !value.success()) {
            current.backupWarehouse = null;
            current.backupWarehousePreview = false;
            current.view = View.DOCTOR;
            current.doctorView = DoctorView.BACKUPS;
        }
        if (value != null && value.messageKey() != null && !value.messageKey().isBlank()) {
            String localized = tr(value.messageKey());
            current.setStatus(tr(value.success() ? "screen.find_me.aui.status.ok" : "screen.find_me.aui.status.error", localized), value.success() ? 40 : 100);
        }
        current.scheduleRefresh(0);
    }

    public static void openSpellScrollPicker(UUID uuid, int companionSlot, UUID requestId,
                                             List<CompanionSpellSlotCandidatesPacket.Entry> entries) {
        if (current == null || uuid == null || requestId == null
                || !uuid.equals(current.spellPickerUuid)
                || companionSlot != current.spellPickerCompanionSlot
                || !requestId.equals(current.spellPickerRequestId)) return;
        current.spellPickerUuid = uuid;
        current.spellPickerCandidates = entries == null ? List.of() : List.copyOf(entries);
        boolean keepSelection = current.spellPickerCandidates.stream()
                .anyMatch(entry -> entry.inventorySlot() == current.spellPickerSelectedSlot);
        if (!keepSelection) {
            current.spellPickerSelectedSlot = current.spellPickerCandidates.isEmpty()
                    ? -1 : current.spellPickerCandidates.get(0).inventorySlot();
        }
        current.contextTeam = -1;
        current.contextBackupIndex = -1;
        current.contextBackupSavedAt = 0L;
        current.settingsChoiceMenu = SettingsChoiceMenu.NONE;
        current.warehouseMenuPage = WarehouseMenuPage.MAIN;
        current.teamMenuPage = TeamMenuPage.MAIN;
        current.warehouseAnimationPurpose = null;
        current.warehouseEffectPurpose = null;
        current.pendingDanger = "";
        current.contextOpen = true;
        current.contextMotionClass = "fm-context-morph";
        current.refreshContextOverlay();
        if (current.spellPickerCandidates.isEmpty()) {
            current.setStatus(tr("screen.find_me.spell_slot.no_candidates"), 70);
        }
    }

    @Override
    protected void init() {
        super.init();
        com.kuzhi.findme.FindMeMod.LOGGER.info("[FindMe backup-ui] manage init size={}x{} reusedMain={} mainActive={} overlayActive={} animations={}",
                width, height, reusedMainDocument(), getLinkedDocument() != null && getLinkedDocument().isActive(),
                getOverlayDocument() != null && getOverlayDocument().isActive(), ClientWheelPresentationState.uiAnimations());
        bindDocument();
        bindContextOverlay();
        refreshDocument();
        long cacheAge = System.nanoTime() - lastFullSyncAtNanos;
        if (lastFullSyncAtNanos == 0L || cacheAge >= FULL_SYNC_TTL_NANOS) {
            // First open requests immediately. Later stale opens paint cached data first, then refresh quietly.
            pendingSyncTicks = lastFullSyncAtNanos == 0L ? 0 : 8;
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateLocalMotions();
        if (pendingSyncTicks >= 0 && --pendingSyncTicks < 0) {
            syncAll();
        }
        if (forcedRefreshTicks >= 0 && --forcedRefreshTicks < 0) {
            refreshDocument();
        }
        if (pendingDropTicks > 0) {
            if (--pendingDropTicks == 0) completePendingDrop();
        } else {
            updateDragHold();
        }
        if (suppressClickTicks > 0) suppressClickTicks--;
        if (settingsScrollDragging) {
            long window = Minecraft.getInstance().getWindow().getWindow();
            if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
                settingsScrollDragging = false;
            }
        }
        if (statusTicks > 0 && --statusTicks == 0 && !status.isBlank()) {
            status = "";
            refreshDocument();
        }
        long signature = stateSignature();
        if (signature != lastSignature) {
            if (signature != pendingSignature) {
                pendingSignature = signature;
                refreshDebounceTicks = 2;
            } else if (refreshDebounceTicks >= 0 && --refreshDebounceTicks < 0) {
                refreshDocument();
            }
        } else {
            pendingSignature = Long.MIN_VALUE;
            refreshDebounceTicks = -1;
        }
        updateTeamScrollThumb();
        updateWarehouseScrollThumb();
        updateSettingsScrollThumb();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderMainDocumentOverlay(GuiGraphics graphics) {
        if (!clickPulseFromContext || !contextOpen) renderClickPulse(graphics);
        if (expandedUuid == null || cardTogglePhase != CardTogglePhase.IDLE || isTransitionRunning()) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 450.0f);
        renderSpellIcons(graphics, getLinkedDocument(),
                ".member-card.selected-card .selected-spell-slots .spell-slot-icon[data-spell-icon]", false);
        graphics.pose().popPose();
    }

    @Override
    protected void renderContextDocumentOverlay(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 50.0f);
        renderSpellIcons(graphics, getOverlayDocument(), ".spell-picker-icon[data-spell-icon]", true);
        if (clickPulseFromContext) renderClickPulse(graphics);
        graphics.pose().popPose();
    }

    private void startClickPulse(Element element, boolean fromContext) {
        if (!settings.uiAnimations() || element == null) return;
        Position position = Position.of(element);
        Size size = Size.of(element);
        clickPulseX = (int) Math.floor(position.x);
        clickPulseY = (int) Math.floor(position.y);
        clickPulseWidth = Math.max(1, (int) Math.ceil(size.width()));
        clickPulseHeight = Math.max(1, (int) Math.ceil(size.height()));
        clickPulseFromContext = fromContext;
        clickPulseStartedAtNanos = System.nanoTime();
    }

    private void renderClickPulse(GuiGraphics graphics) {
        if (clickPulseStartedAtNanos == 0L) return;
        long elapsed = System.nanoTime() - clickPulseStartedAtNanos;
        if (elapsed < 0L || elapsed >= CLICK_PULSE_NANOS) {
            clickPulseStartedAtNanos = 0L;
            return;
        }
        double progress = elapsed / (double) CLICK_PULSE_NANOS;
        int alpha = (int) Math.round(210.0 * (1.0 - progress));
        int cyan = (alpha << 24) | 0x16B5DF;
        int white = (Math.max(0, alpha - 45) << 24) | 0xF2F4F3;
        int black = (Math.max(0, alpha - 70) << 24) | 0x172027;
        int expand = progress < 0.45 ? 2 : 1;
        int left = clickPulseX - expand;
        int top = clickPulseY - expand;
        int right = clickPulseX + clickPulseWidth + expand;
        int bottom = clickPulseY + clickPulseHeight + expand;
        graphics.fill(left, top, right, top + 1, cyan);
        graphics.fill(left, bottom - 1, right, bottom, cyan);
        graphics.fill(left, top, left + 1, bottom, cyan);
        graphics.fill(right - 1, top, right, bottom, cyan);
        graphics.fill(left + 2, top + 2, Math.min(right, left + 5), bottom - 2, black);
        int sweepX = left + 1 + (int) Math.round((right - left - 2) * progress);
        graphics.fill(sweepX, top + 1, Math.min(right - 1, sweepX + 1), bottom - 1, white);
    }

    private void renderSpellIcons(GuiGraphics graphics, Document document, String selector, boolean clipToPicker) {
        if (document == null) return;
        Element clip = clipToPicker ? document.querySelector(".spell-picker-list") : null;
        if (clip != null) {
            Position position = Position.of(clip);
            Size size = Size.of(clip);
            graphics.enableScissor((int) Math.floor(position.x), (int) Math.floor(position.y),
                    (int) Math.ceil(position.x + size.width()), (int) Math.ceil(position.y + size.height()));
        }
        for (Element element : document.querySelectorAll(selector)) {
            ResourceLocation texture = ResourceLocation.tryParse(element.getAttribute("data-spell-icon"));
            if (texture == null) continue;
            Position position = Position.of(element);
            Size size = Size.of(element);
            int x = (int) Math.round(position.x);
            int y = (int) Math.round(position.y);
            int width = Math.max(1, (int) Math.round(size.width()));
            int height = Math.max(1, (int) Math.round(size.height()));
            graphics.blit(texture, x, y, 0.0f, 0.0f, width, height, width, height);
        }
        if (clip != null) graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextOpen && !contextBounds.overlaps(mouseX, mouseY, 1.0, 1.0)) {
            dismissContextMenu();
            return true;
        }
        if (!contextOpen && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            Element directAction = expandedCardActionAt(mouseX, mouseY);
            if (directAction != null) {
                settleCardToggleForDirectAction();
                startClickPulse(directAction, false);
                if ("spell-slot-bind".equals(directAction.getAttribute("data-action"))) {
                    rememberContextAnchor(mouseX, mouseY);
                }
                handleAction(directAction.getAttribute("data-action"), directAction.getAttribute("data-value"), directAction);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private Element expandedCardActionAt(double mouseX, double mouseY) {
        Document document = getLinkedDocument();
        if (document == null || view != View.TEAM || expandedUuid == null) return null;
        List<Element> directActions = new ArrayList<>();
        directActions.addAll(document.querySelectorAll(".selected-action"));
        directActions.addAll(document.querySelectorAll(".selected-spell-slots .spell-slot-frame"));
        for (Element element : directActions) {
            Position position = Position.of(element);
            Size size = Size.of(element);
            if (size.width() <= 0.0 || size.height() <= 0.0) continue;
            if (mouseX >= position.x && mouseX < position.x + size.width()
                    && mouseY >= position.y && mouseY < position.y + size.height()) {
                return element;
            }
        }
        return null;
    }

    private void settleCardToggleForDirectAction() {
        if (cardTogglePhase == CardTogglePhase.IDLE) return;
        removeCardToggleClasses();
        cardTogglePhase = CardTogglePhase.IDLE;
        cardToggleUuid = null;
    }

    private void dismissContextMenu() {
        contextOpen = false;
        doctorRestoreConfirm = false;
        settingsChoiceMenu = SettingsChoiceMenu.NONE;
        contextTeam = -1;
        contextBackupIndex = -1;
        contextBackupSavedAt = 0L;
        warehouseMenuPage = WarehouseMenuPage.MAIN;
        teamMenuPage = TeamMenuPage.MAIN;
        warehouseAnimationPurpose = null;
        warehouseEffectPurpose = null;
        pendingDanger = "";
        clearSpellPicker();
        contextBounds = FindMeAuiContextMenuPlacement.EMPTY;
        clearOverlayMarkup();
    }

    private void clearSpellPicker() {
        spellPickerUuid = null;
        spellPickerRequestId = null;
        spellPickerCompanionSlot = -1;
        spellPickerCandidates = List.of();
        spellPickerSelectedSlot = -1;
    }

    @Override
    public void removed() {
        persistSettingsDraft();
        clearDrag();
        if (current == this) current = null;
        super.removed();
    }

    @Override
    public void onClose() {
        persistSettingsDraft();
        super.onClose();
    }

    private void markSettingsDirty() {
        settingsDirty = true;
        ClientWheelPresentationState.update(settings);
        ClientCompanionTeamState.applyDefaultTeam(ClientWheelPresentationState.defaultTeamIndex());
    }

    private void updateSettingToggleCard(Element card) {
        Element toggle = card == null ? null : card.querySelector(".setting-toggle");
        updateSettingToggleCard(card, !hasClass(toggle, "on"));
    }

    private void updateSettingToggleCard(Element card, boolean enabled) {
        if (card == null) return;
        Element control = card.querySelector(".setting-card-control");
        Element label = control == null ? null : control.querySelector("em");
        Element toggle = control == null ? null : control.querySelector(".setting-toggle");
        if (label != null) label.setTextContent(bool(enabled));
        if (toggle != null) toggle.setClassName("setting-toggle" + (enabled ? " on" : ""));
        settleSettingsDomMutation();
    }

    private void updateSettingChoiceCard(SettingsChoiceMenu choice) {
        Document document = getLinkedDocument();
        if (document == null || choice == null || choice == SettingsChoiceMenu.NONE) return;
        Element card = null;
        for (Element candidate : document.querySelectorAll("[data-action]")) {
            if ("open-settings-choice".equals(candidate.getAttribute("data-action"))
                    && choice.name().equals(candidate.getAttribute("data-value"))) {
                card = candidate;
                break;
            }
        }
        Element control = card == null ? null : card.querySelector(".setting-card-control");
        Element label = control == null ? null : control.querySelector("em");
        if (label != null) label.setTextContent(settingsChoiceLabel(choice));
        settleSettingsDomMutation();
    }

    private String settingsChoiceLabel(SettingsChoiceMenu choice) {
        return switch (choice) {
            case WHEEL_STYLE -> tr("screen.find_me.aui.wheel_style."
                    + settings.wheelStyle().name().toLowerCase(Locale.ROOT));
            case DRAG_HOLD -> tr("screen.find_me.aui.milliseconds", settings.dragHoldMillis());
            case NAME_LENGTH -> Integer.toString(settings.nameMaxLength());
            case TEXT_MODE -> tr("screen.find_me.aui.text_mode."
                    + settings.textMode().name().toLowerCase(Locale.ROOT));
            case FONT_FAMILY -> tr("screen.find_me.aui.font_family."
                    + settings.fontFamily().name().toLowerCase(Locale.ROOT));
            case FONT_SIZE -> tr("screen.find_me.aui.font_size."
                    + settings.fontSize().name().toLowerCase(Locale.ROOT));
            case DEFAULT_TEAM -> defaultTeamLabel();
            case RIDING_CAMERA -> tr("screen.find_me.aui.riding_camera."
                    + settings.ridingCameraMode().name().toLowerCase(Locale.ROOT));
            case BINDING_ANIMATION -> tr("screen.find_me.aui.binding_animation."
                    + settings.bindingAnimationPolicy().name().toLowerCase(Locale.ROOT));
            case SUMMONED_OUTLINE -> tr("screen.find_me.aui.summoned_outline."
                    + settings.summonedOutlineMode().name().toLowerCase(Locale.ROOT));
            case COMPANION_LIMIT, NONE -> "";
        };
    }

    private void settleSettingsDomMutation() {
        Document document = getLinkedDocument();
        if (document == null) return;
        Element page = document.querySelector(".fm-page");
        if (page != null) page.setClassName("fm-page settings " + typographyClasses());
        renderedMarkupKey = markupKey(markup());
        lastSignature = stateSignature();
        pendingSignature = Long.MIN_VALUE;
        refreshDebounceTicks = -1;
        forcedRefreshTicks = -1;
        updateSettingsScrollThumb();
    }

    private void persistSettingsDraft() {
        if (!settingsDirty) return;
        settingsDirty = false;
        ModNetwork.sendToServer(new com.kuzhi.findme.network.FindMeSettingsPacket(
                FindMeSettingsAction.SAVE, settings, true, ""));
    }

    private void resetVisibleSettingsSection() {
        FindMeUiSettings defaults = FindMeUiSettings.defaults();
        if (settingsSection == 0) {
            if (settings.rotateModels() != defaults.rotateModels()) settings = settings.changed(0, 0);
            if (settings.reduceBackgroundAnimation() != defaults.reduceBackgroundAnimation()) settings = settings.changed(0, 1);
            if (settings.operationSounds() != defaults.operationSounds()) settings = settings.changed(0, 2);
            if (settings.controlHints() != defaults.controlHints()) settings = settings.changed(0, 3);
            settings = settings.withUiAnimations(defaults.uiAnimations())
                    .withWheelStyle(defaults.wheelStyle())
                    .withDragHoldMillis(defaults.dragHoldMillis());
            return;
        }
        if (settingsSection == 1) {
            if (settings.autoJoinTeams() != defaults.autoJoinTeams()) settings = settings.changed(1, 0);
            if (settings.autoCreateTeams() != defaults.autoCreateTeams()) settings = settings.changed(1, 1);
            settings = settings.withDefaultTeamIndex(defaults.defaultTeamIndex());
            return;
        }
        if (settings.preferNativeMountInteraction() != defaults.preferNativeMountInteraction()) {
            settings = settings.changed(0, 4);
        }
        if (settings.autoPromoteRiddenCompanions() != defaults.autoPromoteRiddenCompanions()) {
            settings = settings.changed(0, 5);
        }
        if (settings.mountSummonAnimations() != defaults.mountSummonAnimations()) settings = settings.changed(0, 6);
        if (settings.hideRiddenMountWhenLookingDown() != defaults.hideRiddenMountWhenLookingDown()) {
            settings = settings.changed(0, 7);
        }
        settings = settings.withRidingCameraMode(defaults.ridingCameraMode())
                .withBindingAnimationPolicy(defaults.bindingAnimationPolicy())
                .withSummonedOutlineMode(defaults.summonedOutlineMode());
    }

    private void syncAll() {
        long now = System.nanoTime();
        if (lastFullSyncAtNanos != 0L && now - lastFullSyncAtNanos < MIN_SYNC_INTERVAL_NANOS) return;
        lastFullSyncAtNanos = now;
        ModNetwork.sendToServer(new CompanionCommandPacket(CompanionKind.MOUNT, CompanionAction.SYNC, -1));
        ModNetwork.sendToServer(new CompanionCommandPacket(CompanionKind.COMPANION, CompanionAction.SYNC, -1));
        ModNetwork.sendToServer(new CompanionCommandPacket(CompanionKind.COMPANION, CompanionAction.DEAD_SYNC, -1));
        ModNetwork.sendToServer(new VehicleCommandPacket(VehicleCommandAction.SYNC, null, -1));
        ModNetwork.sendToServer(new com.kuzhi.findme.network.FindMeSettingsPacket(FindMeSettingsAction.SYNC, settings, true, ""));
        ModNetwork.sendToServer(new CompanionTeamCommandPacket(CompanionTeamAction.SYNC, CompanionTeamTarget.MOUNT, -1, -1, "", List.of()));
    }

    private void scheduleRefresh(int delayTicks) {
        if (delayTicks <= 0) {
            refreshDocument();
        } else {
            forcedRefreshTicks = Math.max(forcedRefreshTicks, delayTicks);
        }
    }

    private void bindDocument() {
        Document document = getLinkedDocument();
        if (document == null || document.body == null) {
            com.kuzhi.findme.FindMeMod.LOGGER.error("[FindMe backup-ui] bind main failed document={} body={}",
                    document != null, document != null && document.body != null);
            return;
        }
        Element root = document.body;
        if (!"1".equals(root.getAttribute("data-findme-bound"))) {
            com.kuzhi.findme.FindMeMod.LOGGER.info("[FindMe backup-ui] main document event binding installed");
            root.setAttribute("data-findme-bound", "1");
            root.addEventListener("click", event -> {
                if (suppressClickTicks > 0) {
                    suppressClickTicks = 0;
                    event.preventDefault();
                    return;
                }
                Element target = event.target instanceof Element element ? element : null;
                if (target == null) return;
                Element action = target.closest("[data-action]");
                if (action == null) return;
                FindMeAuiSound.click();
                startClickPulse(action, false);
                com.kuzhi.findme.FindMeMod.LOGGER.info("[FindMe backup-ui] main click action={} value={} view={} contextOpen={} transition={}",
                        action.getAttribute("data-action"), action.getAttribute("data-value"), view, contextOpen, isTransitionRunning());
                if (event instanceof MouseEvent mouse) {
                    String actionName = action.getAttribute("data-action");
                    if ("open-team-menu".equals(actionName) || "open-settings-choice".equals(actionName)
                            || "spell-slot-bind".equals(actionName)) {
                        rememberContextAnchor(mouse.clientX, mouse.clientY);
                    }
                }
                handleAction(action.getAttribute("data-action"), action.getAttribute("data-value"), action);
                event.preventDefault();
            });
            root.addEventListener("mousedown", event -> {
                if (!(event instanceof MouseEvent mouse) || !(event.target instanceof Element target)) return;
                if (view == View.DETAIL && target.closest(".profile-model") != null
                        && (mouse.button == 0 || mouse.button == 1)) {
                    detailPreviewDragButton = mouse.button;
                    detailPreviewLastX = mouse.clientX;
                    detailPreviewLastY = mouse.clientY;
                    event.preventDefault();
                    return;
                }
                if (beginSettingsScroll(mouse, target)) {
                    event.preventDefault();
                    return;
                }
                if (beginDoctorBackupScroll(mouse, target)) {
                    event.preventDefault();
                    return;
                }
                beginDrag(mouse, target);
            });
            root.addEventListener("mousemove", event -> {
                if (!(event instanceof MouseEvent mouse) || !(event.target instanceof Element target)) return;
                if (detailPreviewDragButton >= 0) {
                    double deltaX = mouse.clientX - detailPreviewLastX;
                    double deltaY = mouse.clientY - detailPreviewLastY;
                    detailPreviewLastX = mouse.clientX;
                    detailPreviewLastY = mouse.clientY;
                    if (detailPreviewDragButton == 0) FindMePreviewInteractionState.rotate("manage-detail", deltaX, deltaY);
                    else FindMePreviewInteractionState.pan("manage-detail", deltaX, deltaY);
                    event.preventDefault();
                    return;
                }
                if (settingsScrollDragging) {
                    dragSettingsScroll(mouse);
                    event.preventDefault();
                    return;
                }
                if (doctorBackupScrollDragging) {
                    dragDoctorBackupScroll(mouse);
                    event.preventDefault();
                    return;
                }
                updateDrag(mouse, target);
            });
            root.addEventListener("mouseup", event -> {
                if (!(event instanceof MouseEvent mouse)) return;
                if (detailPreviewDragButton >= 0) {
                    detailPreviewDragButton = -1;
                    event.preventDefault();
                    return;
                }
                if (settingsScrollDragging) {
                    settingsScrollDragging = false;
                    event.preventDefault();
                    return;
                }
                if (doctorBackupScrollDragging) {
                    doctorBackupScrollDragging = false;
                    event.preventDefault();
                    return;
                }
                Element target = event.target instanceof Element element ? element : null;
                finishDrag(mouse, target);
            });
            root.addEventListener("contextmenu", event -> {
                if (!(event instanceof MouseEvent mouse)) return;
                if (cardExitAction != null || cardTogglePhase != CardTogglePhase.IDLE || isTransitionRunning()) return;
                Element target = event.target instanceof Element element ? element : null;
                if (target == null) return;
                if (view == View.DETAIL && target.closest(".profile-model") != null) {
                    detailPreviewDragButton = -1;
                    event.preventDefault();
                    return;
                }
                if (backupWarehousePreview && view == View.WAREHOUSE) {
                    event.preventDefault();
                    if (contextOpen) dismissContextMenu();
                    return;
                }
                Element backup = target.closest("[data-backup-index]");
                if (backup != null && backup.hasAttribute("data-backup-index")) {
                    rememberContextAnchor(mouse.clientX, mouse.clientY);
                    contextBackupIndex = parseInt(backup.getAttribute("data-backup-index"), -1);
                    contextBackupSavedAt = parseLong(backup.getAttribute("data-backup-saved-at"), 0L);
                    pendingBackup = -1;
                    pendingBackupSavedAt = 0L;
                    doctorRestoreConfirm = false;
                    pendingDanger = "";
                    contextTeam = -1;
                    selectedUuid = null;
                    settingsChoiceMenu = SettingsChoiceMenu.NONE;
                    contextOpen = contextBackupIndex >= 0 && contextBackupSavedAt > 0L;
                    contextMotionClass = "fm-context-open";
                    refreshContextOverlay();
                    event.preventDefault();
                    return;
                }
                Element team = target.closest("[data-team-index]");
                if (team != null) {
                    rememberContextAnchor(mouse.clientX, mouse.clientY);
                    contextTeam = parseInt(team.getAttribute("data-team-index"), -1);
                    contextBackupIndex = -1;
                    contextBackupSavedAt = 0L;
                    settingsChoiceMenu = SettingsChoiceMenu.NONE;
                    contextOpen = true;
                    selectedUuid = null;
                    warehouseMenuPage = WarehouseMenuPage.MAIN;
                    teamMenuPage = TeamMenuPage.MAIN;
                    warehouseAnimationPurpose = null;
                    warehouseEffectPurpose = null;
                    contextMotionClass = "fm-context-open";
                    refreshContextOverlay();
                    event.preventDefault();
                    return;
                }
                Element card = target.closest("[data-uuid]");
                if (card == null) return;
                rememberContextAnchor(mouse.clientX, mouse.clientY);
                selectedUuid = parseUuid(card.getAttribute("data-uuid"));
                contextTeam = -1;
                contextBackupIndex = -1;
                contextBackupSavedAt = 0L;
                settingsChoiceMenu = SettingsChoiceMenu.NONE;
                contextOpen = true;
                warehouseMenuPage = WarehouseMenuPage.MAIN;
                teamMenuPage = TeamMenuPage.MAIN;
                warehouseAnimationPurpose = null;
                warehouseEffectPurpose = null;
                contextMotionClass = "fm-context-open";
                refreshContextOverlay();
                event.preventDefault();
            });
            root.addEventListener("wheel", event -> {
                if (!(event instanceof MouseEvent mouse) || !(event.target instanceof Element targetElement)) return;
                if (contextOpen) {
                    event.preventDefault();
                    return;
                }
                Element list = targetElement.closest(".team-list");
                double delta = mouse.scrollDelta != 0.0 ? mouse.scrollDelta : mouse.deltaY;
                if (view == View.DETAIL && targetElement.closest(".profile-model") != null) {
                    FindMePreviewInteractionState.zoom("manage-detail", delta);
                    event.preventDefault();
                    return;
                }
                if (list != null) {
                    list.setScrollTop(list.getTargetScrollTop() + delta);
                    teamScrollTop = list.getTargetScrollTop();
                    event.preventDefault();
                    updateTeamScrollThumb();
                    return;
                }
                Element grid = targetElement.closest(".warehouse-grid");
                if (grid == null) grid = targetElement.closest(".death-grid");
                if (grid != null) {
                    grid.setScrollTop(grid.getTargetScrollTop() + delta);
                    warehouseScrollTop = grid.getTargetScrollTop();
                    event.preventDefault();
                    updateWarehouseScrollThumb();
                    return;
                }
                Element backupRecords = targetElement.closest("#findme-doctor-backup-records");
                if (backupRecords != null) {
                    backupRecords.setScrollTop(backupRecords.getTargetScrollTop() + delta);
                    doctorBackupScrollTop = backupRecords.getTargetScrollTop();
                    event.preventDefault();
                    updateDoctorBackupScrollThumb();
                    return;
                }
                Element settingsGrid = targetElement.closest(".settings-grid-viewport");
                if (settingsGrid != null) {
                    Element scrollContent = getLinkedDocument().getElementById("findme-settings-grid");
                    if (scrollContent != null) {
                        scrollContent.setScrollTop(scrollContent.getTargetScrollTop() + delta);
                        settingsScrollTop = scrollContent.getTargetScrollTop();
                        updateSettingsScrollThumb();
                    }
                    event.preventDefault();
                }
            });
        }
        bindTeamNameScroll(document);
    }

    private void bindContextOverlay() {
        Document document = getOverlayDocument();
        if (document == null || document.body == null) {
            com.kuzhi.findme.FindMeMod.LOGGER.error("[FindMe backup-ui] bind overlay failed document={} body={}",
                    document != null, document != null && document.body != null);
            return;
        }
        if ("1".equals(document.body.getAttribute("data-findme-bound"))) return;
        Element root = document.body;
        com.kuzhi.findme.FindMeMod.LOGGER.info("[FindMe backup-ui] overlay document event binding installed");
        root.setAttribute("data-findme-bound", "1");
        root.addEventListener("click", event -> {
            if (!(event.target instanceof Element element)) return;
            Element action = element.closest("[data-action]");
            if (action != null) {
                FindMeAuiSound.click();
                startClickPulse(action, true);
                com.kuzhi.findme.FindMeMod.LOGGER.info("[FindMe backup-ui] overlay click action={} value={} view={} contextOpen={} transition={}",
                        action.getAttribute("data-action"), action.getAttribute("data-value"), view, contextOpen, isTransitionRunning());
                handleAction(action.getAttribute("data-action"), action.getAttribute("data-value"), action);
                event.preventDefault();
                return;
            }
            if (element.closest("[data-context-dismiss]") != null) {
                FindMeAuiSound.click();
                contextOpen = false;
                settingsChoiceMenu = SettingsChoiceMenu.NONE;
                warehouseMenuPage = WarehouseMenuPage.MAIN;
                teamMenuPage = TeamMenuPage.MAIN;
                warehouseAnimationPurpose = null;
                warehouseEffectPurpose = null;
                pendingDanger = "";
                clearSpellPicker();
                refreshContextOverlay();
                event.preventDefault();
            }
        });
        root.addEventListener("contextmenu", event -> {
            if (!(event.target instanceof Element element) || element.closest("[data-context-dismiss]") == null) return;
            contextOpen = false;
            settingsChoiceMenu = SettingsChoiceMenu.NONE;
            warehouseMenuPage = WarehouseMenuPage.MAIN;
            teamMenuPage = TeamMenuPage.MAIN;
            warehouseAnimationPurpose = null;
            warehouseEffectPurpose = null;
            pendingDanger = "";
            clearSpellPicker();
            refreshContextOverlay();
            event.preventDefault();
        });
        root.addEventListener("wheel", event -> {
            if (!(event instanceof MouseEvent mouse) || !(event.target instanceof Element targetElement)) return;
            Element list = targetElement.closest(".spell-picker-list");
            if (list == null) list = targetElement.closest(".warehouse-team-menu");
            if (list == null) list = targetElement.closest(".warehouse-style-menu");
            if (list == null) list = targetElement.closest(".context-menu");
            if (list != null) {
                double delta = mouse.scrollDelta != 0.0 ? mouse.scrollDelta : mouse.deltaY;
                list.setScrollTop(list.getTargetScrollTop() + delta);
            }
            event.preventDefault();
        });
    }

    private void bindTeamNameScroll(Document document) {
        for (Element row : document.querySelectorAll("[data-team-index]")) {
            Element name = row.querySelector(".team-name-scroll");
            if (name == null || "1".equals(row.getAttribute("data-findme-name-bound"))) continue;
            row.setAttribute("data-findme-name-bound", "1");
            row.addEventListener("mouseenter", event -> setTeamNameScrolled(name, true));
            row.addEventListener("mouseleave", event -> setTeamNameScrolled(name, false));
        }
    }

    private static void setTeamNameScrolled(Element name, boolean scrolled) {
        if (name == null) return;
        String base = name.getAttribute("data-fm-scroll-base");
        String shift = name.getAttribute("data-fm-scroll-shift");
        if (base == null || base.isBlank()) return;
        name.setAttribute("style", base + ";transform:translateX(" + (scrolled && shift != null ? shift : "0px") + ")");
    }

    private void beginDrag(MouseEvent mouse, Element targetElement) {
        clearDrag();
        if (mouse.button != 0 || view != View.TEAM || cardExitAction != null
                || cardTogglePhase != CardTogglePhase.IDLE || isTransitionRunning()) return;
        dragMouseX = mouse.clientX;
        dragMouseY = mouse.clientY;

        Element teamElement = targetElement.closest("[data-team-index]");
        if (teamElement != null) {
            dragKind = DragKind.TEAM;
            dragFrom = parseInt(teamElement.getAttribute("data-team-index"), -1);
            dragOver = dragFrom;
            dragSourceElement = teamElement;
            dragPressedAtNanos = System.nanoTime();
            rememberDragGrab(mouse, teamElement);
            return;
        }

        Element memberElement = targetElement.closest("[data-member-index]");
        if (memberElement == null) return;
        Element directAction = targetElement.closest("[data-action]");
        if (directAction != null && directAction != memberElement) return;
        dragKind = DragKind.MEMBER;
        dragFrom = parseInt(memberElement.getAttribute("data-member-index"), -1);
        dragOver = dragFrom;
        dragSourceElement = memberElement;
        dragPressedAtNanos = System.nanoTime();
        rememberDragGrab(mouse, memberElement);
    }

    private void rememberDragGrab(MouseEvent mouse, Element element) {
        double width = Math.max(1.0, Size.of(element).width());
        double height = Math.max(1.0, Size.of(element).height());
        Position position = Position.of(element);
        dragGrabRatioX = Math.max(0.0, Math.min(1.0, (mouse.clientX - position.x) / width));
        dragGrabRatioY = Math.max(0.0, Math.min(1.0, (mouse.clientY - position.y) / height));
    }

    private void updateDragHold() {
        if (dragKind == DragKind.NONE) return;
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            clearDrag();
            return;
        }
        if (!dragActive && System.nanoTime() - dragPressedAtNanos >= settings.dragHoldMillis() * 1_000_000L) {
            activateDragVisual();
        }
    }

    private void updateDrag(MouseEvent mouse, Element targetElement) {
        if (dragKind == DragKind.NONE || pendingDropTicks > 0) return;
        dragMouseX = mouse.clientX;
        dragMouseY = mouse.clientY;
        if (!dragActive && System.nanoTime() - dragPressedAtNanos >= settings.dragHoldMillis() * 1_000_000L) {
            activateDragVisual();
        }
        if (!dragActive) return;
        if (dragKind == DragKind.TEAM) autoScrollTeamList(mouse);
        updateDragTarget(targetElement);
        updateDragGhostPosition(false);
        mouse.preventDefault();
    }

    private void activateDragVisual() {
        if (dragActive || dragKind == DragKind.NONE) return;
        dragActive = true;
        addClass(dragSourceElement, "dragging");
        prepareDragGhost();
        applyLiveDragLayout();
        if (dragKind == DragKind.TEAM) {
            Document document = getLinkedDocument();
            Element marker = document == null ? null : document.getElementById("findme-team-selection-marker");
            if (marker != null) marker.setAttribute("style", "visibility:hidden");
        }
    }

    private void prepareDragGhost() {
        Document document = getLinkedDocument();
        if (document == null) return;
        dragGhostElement = document.getElementById("findme-drag-ghost");
        if (dragGhostElement == null) return;
        if (dragKind == DragKind.TEAM) {
            dragGhostAppearanceStyle = "";
            List<ClientCompanionTeamState.TeamEntry> entries = teams();
            if (dragFrom < 0 || dragFrom >= entries.size()) return;
            ClientCompanionTeamState.TeamEntry team = entries.get(dragFrom);
            String name = team.name().isBlank() ? tr("screen.find_me.team_number", team.number()) : team.name();
            dragGhostElement.setClassName("drag-ghost team-ghost active");
            dragGhostElement.setInnerHTML("<b>" + twoDigits(team.number()) + "</b><span><strong>" + escape(name)
                    + "</strong><small>" + team.uuids().size() + " / 6</small></span>");
        } else {
            UUID sourceUuid = parseUuid(dragSourceElement == null ? "" : dragSourceElement.getAttribute("data-uuid"));
            Card card = sourceUuid == null ? null : findCard(sourceUuid);
            if (card == null) return;
            int accent = dragFrom == 0 ? 3 : Math.floorMod(dragFrom - 1, 5);
            String state = card.active ? "active" : card.alive ? "stored" : "dead";
            dragGhostAppearanceStyle = cardThemeStyle(card);
            dragGhostElement.setClassName("drag-ghost member-ghost member-card compact " + state
                    + " accent-" + accent + " active");
            dragGhostElement.setInnerHTML("<span class='member-number'>" + twoDigits(dragFrom + 1)
                    + "</span><div class='compact-info'><span class='compact-name'>" + escape(card.name)
                    + "</span><span class='compact-state'>" + escape(stateLabel(card)) + "</span></div>");
        }
        document.rebuildSelectorIndex();
        document.reapplyStylesFromCache();
        updateDragGhostPosition(false);
    }

    private void autoScrollTeamList(MouseEvent mouse) {
        Document document = getLinkedDocument();
        if (document == null) return;
        Element list = document.getElementById("findme-team-list");
        if (list == null) return;
        double top = Position.of(list).y;
        double height = Math.max(1.0, Box.of(list).innerSize().height());
        double edge = Math.min(12.0, height / 4.0);
        double delta = mouse.clientY < top + edge ? -4.0 : mouse.clientY > top + height - edge ? 4.0 : 0.0;
        if (delta == 0.0) return;
        list.setScrollTop(list.getTargetScrollTop() + delta);
        updateTeamScrollThumb();
    }

    private void updateDragTarget(Element targetElement) {
        String selector = dragKind == DragKind.TEAM ? "[data-team-index]" : "[data-member-index]";
        Element candidate = targetElement == null ? null : targetElement.closest(selector);
        int candidateIndex = candidate == null ? -1 : parseInt(candidate.getAttribute(dragKind == DragKind.TEAM ? "data-team-index" : "data-member-index"), -1);
        if (candidateIndex < 0 || candidateIndex == dragOver) return;
        removeClass(dragOverElement, "drag-over");
        dragOver = candidateIndex;
        dragOverElement = candidate;
        if (dragOver != dragFrom) addClass(dragOverElement, "drag-over");
        applyLiveDragLayout();
    }

    private void finishDrag(MouseEvent mouse, Element targetElement) {
        if (dragKind == DragKind.NONE || mouse.button != 0) return;
        if (!dragActive) {
            clearDrag();
            return;
        }

        String selector = dragKind == DragKind.TEAM ? "[data-team-index]" : "[data-member-index]";
        Element releasedElement = targetElement == null ? null : targetElement.closest(selector);
        boolean releasedOnSource = releasedElement != null && releasedElement == dragSourceElement;
        updateDragTarget(targetElement);
        pendingDrop = createPendingDrop();
        suppressClickTicks = releasedOnSource ? 2 : 0;
        pendingDropTicks = 2;
        addClass(dragGhostElement, "dropping");
        updateDragGhostPosition(true);
        mouse.preventDefault();
    }

    private PendingDrop createPendingDrop() {
        if (dragOver < 0) return new PendingDrop(dragKind, dragFrom, dragFrom, List.of(), null);
        if (dragKind == DragKind.TEAM) {
            return new PendingDrop(dragKind, dragFrom, dragOver, List.of(), null);
        }
        List<UUID> members = new ArrayList<>(ClientCompanionTeamState.members(target(), selectedTeam));
        if (dragFrom < 0 || dragFrom >= members.size() || dragOver < 0 || dragOver >= members.size()) {
            return new PendingDrop(dragKind, dragFrom, dragFrom, List.of(), null);
        }
        UUID moved = members.remove(dragFrom);
        members.add(dragOver, moved);
        return new PendingDrop(dragKind, dragFrom, dragOver, List.copyOf(members), moved);
    }

    private void completePendingDrop() {
        PendingDrop drop = pendingDrop;
        CompanionTeamTarget currentTarget = target();
        int currentTeam = selectedTeam;
        clearDrag();
        if (drop == null || drop.from == drop.to) return;
        if (drop.kind == DragKind.TEAM) {
            int newSelectedTeam = remapMovedIndex(selectedTeam, drop.from, drop.to);
            ClientCompanionTeamState.moveTeam(currentTarget, drop.from, drop.to);
            selectedTeam = newSelectedTeam;
            ClientCompanionTeamState.selectTeam(currentTarget, selectedTeam);
            ModNetwork.sendToServer(new CompanionTeamCommandPacket(CompanionTeamAction.REORDER, currentTarget, drop.from, drop.to, "", List.of()));
        } else if (drop.kind == DragKind.MEMBER && !drop.members.isEmpty()) {
            ClientCompanionTeamState.replaceMembers(currentTarget, currentTeam, drop.members);
            selectedUuid = drop.moved;
            ModNetwork.sendToServer(new CompanionTeamCommandPacket(CompanionTeamAction.SET, currentTarget, currentTeam, -1, "", drop.members));
        }
        refreshDocument();
    }

    private void updateDragGhostPosition(boolean dropping) {
        if (dragGhostElement == null) return;
        double ghostWidth = dragKind == DragKind.MEMBER ? scaled(52) : Math.max(scaled(48), Size.of(dragSourceElement).width());
        double ghostHeight = dragKind == DragKind.MEMBER ? scaled(141) : Math.max(1.0, Size.of(dragSourceElement).height());
        double left;
        double top;
        if (dropping && dragOverElement != null) {
            Position targetPosition = Position.of(dragOverElement);
            double targetWidth = Math.max(1.0, Size.of(dragOverElement).width());
            double sourceWidth = Math.max(1.0, Size.of(dragSourceElement).width());
            double finalSourceLeft = targetPosition.x;
            if (dragKind == DragKind.MEMBER && dragFrom < dragOver) {
                finalSourceLeft = targetPosition.x + targetWidth - sourceWidth;
            }
            left = finalSourceLeft + (sourceWidth - ghostWidth) * 0.5;
            top = targetPosition.y - scaled(4);
        } else {
            left = dragMouseX - ghostWidth * dragGrabRatioX;
            top = dragMouseY - ghostHeight * dragGrabRatioY - scaled(4);
        }
        left = Math.max(0.0, Math.min(Math.max(0.0, width - ghostWidth), left));
        top = Math.max(0.0, Math.min(Math.max(0.0, height - ghostHeight), top));
        dragGhostElement.setAttribute("style", "left:" + Math.round(left) + "px;top:" + Math.round(top)
                + "px;width:" + Math.round(ghostWidth) + "px;height:" + Math.round(ghostHeight) + "px;"
                + dragGhostAppearanceStyle);
    }

    private void applyLiveDragLayout() {
        restoreDragInlineStyles();
        if (!dragActive || dragOver < 0 || dragOver == dragFrom || dragSourceElement == null) return;
        Document document = getLinkedDocument();
        if (document == null) return;
        String selector = dragKind == DragKind.TEAM ? "[data-team-index]" : "[data-member-index]";
        double distance = dragKind == DragKind.TEAM
                ? Math.max(1.0, Size.of(dragSourceElement).height())
                : Math.max(1.0, Size.of(dragSourceElement).width()) + scaled(3);
        for (Element element : document.querySelectorAll(selector)) {
            if (element == dragSourceElement) continue;
            int index = parseInt(element.getAttribute(dragKind == DragKind.TEAM ? "data-team-index" : "data-member-index"), -1);
            boolean shiftBack = dragFrom < dragOver && index > dragFrom && index <= dragOver;
            boolean shiftForward = dragFrom > dragOver && index >= dragOver && index < dragFrom;
            if (!shiftBack && !shiftForward) continue;
            double offset = shiftBack ? -distance : distance;
            setTemporaryDragStyle(element, dragKind == DragKind.TEAM
                    ? "transform:translateY(" + Math.round(offset) + "px)"
                    : "transform:translateX(" + Math.round(offset) + "px)");
        }
    }

    private void setTemporaryDragStyle(Element element, String temporary) {
        if (element == null) return;
        String base = dragInlineStyles.computeIfAbsent(element, ignored -> {
            String style = element.getAttribute("style");
            return style == null ? "" : style;
        });
        element.setAttribute("style", base + (base.isBlank() || base.endsWith(";") ? "" : ";") + temporary);
    }

    private void restoreDragInlineStyles() {
        for (Map.Entry<Element, String> entry : dragInlineStyles.entrySet()) {
            if (entry.getValue().isBlank()) entry.getKey().removeAttribute("style");
            else entry.getKey().setAttribute("style", entry.getValue());
        }
        dragInlineStyles.clear();
    }

    private void clearDrag() {
        restoreDragInlineStyles();
        removeClass(dragSourceElement, "dragging");
        removeClass(dragOverElement, "drag-over");
        if (dragGhostElement != null) {
            dragGhostElement.setClassName("drag-ghost");
            dragGhostElement.removeAttribute("style");
            dragGhostElement.setInnerHTML("");
        }
        dragKind = DragKind.NONE;
        dragFrom = -1;
        dragOver = -1;
        dragPressedAtNanos = 0L;
        dragSourceElement = null;
        dragOverElement = null;
        dragGhostElement = null;
        dragGhostAppearanceStyle = "";
        dragActive = false;
        dragMouseX = 0.0;
        dragMouseY = 0.0;
        dragGrabRatioX = 0.5;
        dragGrabRatioY = 0.5;
        pendingDrop = null;
        pendingDropTicks = 0;
        lastTeamSelectionMarkerStyle = "";
        updateTeamScrollThumb();
    }

    private static int remapMovedIndex(int selected, int from, int to) {
        if (selected == from) return to;
        if (from < to && selected > from && selected <= to) return selected - 1;
        if (from > to && selected >= to && selected < from) return selected + 1;
        return selected;
    }

    private static void addClass(Element element, String className) {
        if (element == null || className == null || className.isBlank()) return;
        String classes = element.getAttribute("class");
        classes = classes == null ? "" : classes.trim();
        if ((" " + classes + " ").contains(" " + className + " ")) return;
        element.setAttribute("class", classes.isBlank() ? className : classes + " " + className);
    }

    private static boolean hasClass(Element element, String className) {
        if (element == null || className == null || className.isBlank()) return false;
        String classes = element.getAttribute("class");
        return classes != null && (" " + classes.trim() + " ").contains(" " + className + " ");
    }

    private static void removeClass(Element element, String className) {
        if (element == null || className == null || className.isBlank()) return;
        String classes = element.getAttribute("class");
        if (classes == null || classes.isBlank()) return;
        String cleaned = (" " + classes.trim() + " ").replace(" " + className + " ", " ").trim();
        element.setAttribute("class", cleaned);
    }

    private void updateLocalMotions() {
        long now = System.nanoTime();
        if (cardExitAction != null && now - cardExitStartedAtNanos >= CARD_EXIT_NANOS) {
            Runnable action = cardExitAction;
            cardExitAction = null;
            action.run();
        }
        updateCardToggle(now);
    }

    private void startCardToggle(UUID uuid, boolean expanding) {
        if (uuid == null || view != View.TEAM || cardTogglePhase != CardTogglePhase.IDLE
                || cardExitAction != null || isTransitionRunning()) return;
        if (!settings.uiAnimations()) {
            selectedUuid = uuid;
            expandedUuid = expanding ? uuid : null;
            refreshRosterCards();
            return;
        }
        Element card = findRenderedCard(uuid);
        if (card == null) {
            selectedUuid = uuid;
            expandedUuid = expanding ? uuid : null;
            refreshRosterCards();
            return;
        }
        clearDrag();
        contextOpen = false;
        clearOverlayMarkup();
        cardToggleUuid = uuid;
        cardToggleExpanding = expanding;
        selectedUuid = uuid;
        expandedUuid = expanding ? uuid : null;
        cardTogglePhase = CardTogglePhase.IN;
        cardToggleStartedAtNanos = System.nanoTime();
        refreshRosterCards();
        applyCardToggleInClasses();
    }

    private void updateCardToggle(long now) {
        if (cardTogglePhase == CardTogglePhase.IN && now - cardToggleStartedAtNanos >= CARD_TOGGLE_IN_NANOS) {
            removeCardToggleClasses();
            cardTogglePhase = CardTogglePhase.IDLE;
            cardToggleUuid = null;
        }
    }

    private void refreshRosterCards() {
        long startedAt = FindMeAuiPerformanceMonitor.start();
        Document document = getLinkedDocument();
        if (document == null || view != View.TEAM) {
            refreshDocument();
            return;
        }
        Element roster = document.getElementById("findme-roster-strip");
        if (roster == null) {
            refreshDocument();
            return;
        }
        int compactWidth = scaled(36);
        int cardGap = scaled(3);
        int ordinal = 0;
        for (Element element : roster.querySelectorAll(".member-card[data-uuid]")) {
            UUID uuid = parseUuid(element.getAttribute("data-uuid"));
            Card card = uuid == null ? null : findCard(uuid);
            if (card == null) continue;
            int index = parseInt(element.getAttribute("data-member-index"), ordinal);
            boolean selected = uuid.equals(expandedUuid);
            String state = card.active ? "active" : card.alive ? "stored" : "dead";
            String insertion = settings.uiAnimations() && view == View.TEAM
                    && uuid.equals(pendingInsertedMemberUuid) ? " fm-member-insert" : "";
            int accentIndex = index == 0 ? 3 : Math.floorMod(index - 1, 5);
            element.setClassName("member-card " + (selected ? "selected-card " : "compact ")
                    + state + (selected ? "" : " accent-" + accentIndex) + insertion);
            String offset = ordinal++ == 0 ? "" : "margin-left:" + cardGap + "px;";
            String size = selected ? "" : "width:" + compactWidth + "px;min-width:" + compactWidth
                    + "px;flex:0 0 " + compactWidth + "px;";
            element.setAttribute("style", size + offset + cardThemeStyle(card));
        }
        renderedMarkupKey = markupKey(markup());
        FindMeAuiPerformanceMonitor.record(this, "refresh.card_toggle", startedAt, 8.0);
    }

    private void applyCardToggleInClasses() {
        Document document = getLinkedDocument();
        if (document == null || cardToggleUuid == null) return;
        for (Element card : document.querySelectorAll(".member-card")) {
            if (cardToggleUuid.toString().equals(card.getAttribute("data-uuid"))) {
                addClass(card, cardToggleExpanding ? "fm-card-expand-in" : "fm-card-collapse-in");
            } else {
                addClass(card, cardToggleExpanding ? "fm-card-neighbor-expand-in" : "fm-card-neighbor-collapse-in");
            }
        }
    }

    private void removeCardToggleClasses() {
        Document document = getLinkedDocument();
        if (document == null) return;
        for (Element card : document.querySelectorAll(".member-card")) {
            removeClass(card, "fm-card-expand-in");
            removeClass(card, "fm-card-collapse-in");
            removeClass(card, "fm-card-neighbor-expand-in");
            removeClass(card, "fm-card-neighbor-collapse-in");
        }
    }

    private void startCardExit(UUID uuid, Runnable action) {
        if (uuid == null || action == null || cardExitAction != null || cardExitUuid != null
                || cardTogglePhase != CardTogglePhase.IDLE || isTransitionRunning()) return;
        cardExitUuid = uuid;
        if (!settings.uiAnimations()) {
            action.run();
            return;
        }
        contextOpen = false;
        settingsChoiceMenu = SettingsChoiceMenu.NONE;
        pendingDanger = "";
        clearOverlayMarkup();
        Element card = findRenderedCard(uuid);
        if (card == null) {
            action.run();
            return;
        }
        addClass(card, "fm-card-exit");
        cardExitAction = action;
        cardExitStartedAtNanos = System.nanoTime();
    }

    private void finishCardExitResult(boolean success, UUID uuid) {
        if (!uuid.equals(cardExitUuid)) return;
        if (!success) {
            Element card = findRenderedCard(uuid);
            if (card != null) removeClass(card, "fm-card-exit");
        }
        cardExitAction = null;
        cardExitUuid = null;
    }

    private Element findRenderedCard(UUID uuid) {
        Document document = getLinkedDocument();
        if (document == null || uuid == null) return null;
        for (Element element : document.querySelectorAll("[data-uuid]")) {
            if (!uuid.toString().equals(element.getAttribute("data-uuid"))) continue;
            String classes = element.getAttribute("class");
            if (classes != null && (classes.contains("member-card") || classes.contains("warehouse-card"))) return element;
        }
        return null;
    }

    private double[] transitionAnchorFor(UUID uuid) {
        Element card = findRenderedCard(uuid);
        if (card == null) return new double[]{width * 0.42, height * 0.58};
        Position position = Position.of(card);
        Size size = Size.of(card);
        return new double[]{position.x + Math.max(1.0, size.width()) * 0.5,
                position.y + Math.max(1.0, size.height()) * 0.5};
    }

    private void changeView(View targetView) {
        com.kuzhi.findme.FindMeMod.LOGGER.info("[FindMe backup-ui] change view {} -> {} doctor={} doctorView={}",
                view, targetView, doctor != null, doctorView);
        if (view == View.SETTINGS && targetView != View.SETTINGS) persistSettingsDraft();
        view = targetView;
        if (view != View.WAREHOUSE) {
            backupWarehouse = null;
            backupWarehousePreview = false;
        }
        warehouseSelectionMode = false;
        if (view == View.WAREHOUSE) {
            warehouseFilter = WarehouseFilter.ALL;
            warehouseScrollTop = 0.0;
        }
        selectedUuid = null;
        expandedUuid = null;
        contextOpen = false;
        pendingDanger = "";
        clearSpellPicker();
        if (view == View.TEAM) search = "";
        if (view == View.SETTINGS) {
            ModNetwork.sendToServer(new com.kuzhi.findme.network.FindMeSettingsPacket(FindMeSettingsAction.SYNC, settings, true, ""));
        }
        if (view == View.DOCTOR) {
            com.kuzhi.findme.FindMeMod.LOGGER.info("[FindMe backup-ui] requesting doctor sync from changeView");
            ModNetwork.sendToServer(new DoctorCommandPacket(DoctorCommandAction.SYNC, null, 0, 0L, 0));
        }
        refreshDocument();
        com.kuzhi.findme.FindMeMod.LOGGER.info("[FindMe backup-ui] change view complete view={} doctor={} renderedKeyLength={}",
                view, doctor != null, renderedMarkupKey.length());
    }

    private void handleAction(String action, String value, Element sourceElement) {
        if (action == null) {
            com.kuzhi.findme.FindMeMod.LOGGER.warn("[FindMe backup-ui] handle action ignored: null action");
            return;
        }
        if (cardExitAction != null || cardTogglePhase != CardTogglePhase.IDLE || isTransitionRunning()) {
            com.kuzhi.findme.FindMeMod.LOGGER.warn("[FindMe backup-ui] handle action blocked action={} view={} cardExit={} cardToggle={} transition={}",
                    action, view, cardExitAction != null, cardTogglePhase, isTransitionRunning());
            return;
        }
        com.kuzhi.findme.FindMeMod.LOGGER.info("[FindMe backup-ui] handle action={} value={} view={} contextOpen={}",
                action, value, view, contextOpen);
        if (action.startsWith("category:")) {
            Category targetCategory = Category.valueOf(action.substring("category:".length()).toUpperCase(Locale.ROOT));
            if (targetCategory == category) return;
            View categoryView = view == View.WAREHOUSE ? View.WAREHOUSE
                    : view == View.DEAD ? View.DEAD
                    : view == View.RECOVERY ? View.RECOVERY : View.TEAM;
            boolean forward = targetCategory.ordinal() > category.ordinal();
            transitionSibling(() -> {
                category = targetCategory;
                selectedTeam = 0;
                ClientCompanionTeamState.selectTeam(target(), selectedTeam);
                selectedUuid = null;
                expandedUuid = null;
                view = categoryView;
                warehouseSelectionMode = false;
                warehouseFilter = WarehouseFilter.ALL;
                warehouseScrollTop = 0.0;
                search = "";
                refreshDocument();
            }, forward);
            return;
        }
        if (action.startsWith("view:")) {
            View targetView = View.valueOf(action.substring("view:".length()).toUpperCase(Locale.ROOT));
            if (targetView == view) return;
            transitionPage(() -> changeView(targetView));
            return;
        }
        if (action.startsWith("warehouse-filter:")) {
            warehouseFilter = WarehouseFilter.valueOf(action.substring("warehouse-filter:".length()).toUpperCase(Locale.ROOT));
            warehouseScrollTop = 0.0;
            selectedUuid = null;
            expandedUuid = null;
            contextOpen = false;
            refreshDocument();
            return;
        }
        if (action.startsWith("dead-filter:")) {
            deadFilter = DeadFilter.valueOf(action.substring("dead-filter:".length()).toUpperCase(Locale.ROOT));
            selectedUuid = null;
            expandedUuid = null;
            contextOpen = false;
            refreshDocument();
            return;
        }
        if (action.startsWith("detail-page:")) {
            int targetSection = Math.max(0, Math.min(1, parseInt(action.substring("detail-page:".length()), 0)));
            if (targetSection == detailSection) return;
            boolean forward = targetSection > detailSection;
            transitionSibling(() -> {
                detailSection = targetSection;
                contextOpen = false;
                refreshDocument();
            }, forward);
            return;
        }
        if (action.startsWith("settings-page:")) {
            int targetSection = Math.max(0, Math.min(2, parseInt(action.substring("settings-page:".length()), 0)));
            if (targetSection == settingsSection) return;
            boolean forward = targetSection > settingsSection;
            transitionSibling(() -> {
                settingsSection = targetSection;
                settingsScrollTop = 0.0;
                resetSettingsScrollOnRefresh = true;
                refreshDocument();
            }, forward);
            return;
        }
        if (action.equals("back")) {
            if (view == View.DETAIL || view == View.SETTINGS || view == View.DOCTOR) {
                if (view == View.SETTINGS) persistSettingsDraft();
                View targetView = view == View.DETAIL ? returnView : View.TEAM;
                Runnable backAction = () -> {
                    view = targetView;
                    contextOpen = false;
                    pendingDanger = "";
                    refreshDocument();
                };
                transitionBack(backAction);
            } else if (view == View.WAREHOUSE && warehouseSelectionMode) {
                transitionBack(() -> {
                    view = View.TEAM;
                    warehouseSelectionMode = false;
                    selectedUuid = null;
                    search = "";
                    warehouseScrollTop = 0.0;
                    refreshDocument();
                });
            } else if (view == View.WAREHOUSE && backupWarehousePreview) {
                transitionBack(() -> {
                    view = View.DOCTOR;
                    backupWarehousePreview = false;
                    backupWarehouse = null;
                    doctorView = DoctorView.BACKUPS;
                    refreshDocument();
                });
            } else {
                transitionClose();
            }
            return;
        }
        if (action.equals("open-detail")) {
            UUID uuid = parseUuid(value);
            if (uuid != null) {
                double[] anchor = transitionAnchorFor(uuid);
                transitionDeeper(() -> {
                    selectedUuid = uuid;
                    returnView = view;
                    view = View.DETAIL;
                    detailSection = 0;
                    contextOpen = false;
                    refreshDocument();
                }, anchor[0], anchor[1]);
            }
            return;
        }
        if (action.equals("activate-card")) {
            UUID uuid = parseUuid(value);
            if (uuid != null) activateCard(uuid);
            return;
        }
        if (action.equals("spell-slot-bind")) {
            String[] parts = value == null ? new String[0] : value.split("\\|", 2);
            UUID uuid = parts.length > 0 ? parseUuid(parts[0]) : null;
            int companionSlot = parts.length > 1 ? parseInt(parts[1], -1) : -1;
            if (uuid != null && companionSlot >= 0 && companionSlot < 3) {
                spellPickerUuid = uuid;
                spellPickerRequestId = UUID.randomUUID();
                spellPickerCompanionSlot = companionSlot;
                spellPickerCandidates = List.of();
                spellPickerSelectedSlot = -1;
                contextTeam = -1;
                contextBackupIndex = -1;
                contextBackupSavedAt = 0L;
                settingsChoiceMenu = SettingsChoiceMenu.NONE;
                warehouseMenuPage = WarehouseMenuPage.MAIN;
                teamMenuPage = TeamMenuPage.MAIN;
                warehouseAnimationPurpose = null;
                warehouseEffectPurpose = null;
                pendingDanger = "";
                contextOpen = true;
                contextMotionClass = "fm-context-open";
                refreshContextOverlay();
                ModNetwork.sendToServer(new CompanionSpellSlotPacket(uuid,
                        CompanionSpellSlotPacket.Action.REQUEST_CANDIDATES, companionSlot, -1,
                        spellPickerRequestId));
                setStatus(tr("screen.find_me.spell_slot.scanning"), 50);
            }
            return;
        }
        if (action.equals("spell-picker-select")) {
            spellPickerSelectedSlot = parseInt(value, -1);
            contextMotionClass = "";
            refreshContextOverlay();
            return;
        }
        if (action.equals("spell-picker-confirm")) {
            if (spellPickerUuid != null && spellPickerSelectedSlot >= 0) {
                ModNetwork.sendToServer(new CompanionSpellSlotPacket(spellPickerUuid,
                        CompanionSpellSlotPacket.Action.BIND_INVENTORY_SLOT,
                        spellPickerCompanionSlot, spellPickerSelectedSlot));
                setStatus(tr("screen.find_me.spell_slot.binding"), 50);
                contextOpen = false;
                clearSpellPicker();
                clearOverlayMarkup();
            }
            return;
        }
        if (action.equals("spell-picker-refresh")) {
            if (spellPickerUuid != null) {
                spellPickerRequestId = UUID.randomUUID();
                ModNetwork.sendToServer(new CompanionSpellSlotPacket(spellPickerUuid,
                        CompanionSpellSlotPacket.Action.REQUEST_CANDIDATES, spellPickerCompanionSlot, -1,
                        spellPickerRequestId));
                setStatus(tr("screen.find_me.spell_slot.scanning"), 50);
            }
            return;
        }
        if (action.equals("spell-slot-clear")) {
            String[] parts = value == null ? new String[0] : value.split("\\|", 2);
            UUID uuid = parts.length > 0 ? parseUuid(parts[0]) : null;
            int companionSlot = parts.length > 1 ? parseInt(parts[1], -1) : -1;
            if (uuid != null && companionSlot >= 0 && companionSlot < 3) {
                ModNetwork.sendToServer(new CompanionSpellSlotPacket(uuid,
                        CompanionSpellSlotPacket.Action.CLEAR, companionSlot));
                setStatus(tr("screen.find_me.spell_slot.clearing"), 50);
                contextOpen = false;
                clearSpellPicker();
                clearOverlayMarkup();
            }
            return;
        }
        if (action.equals("add-current-team")) {
            UUID uuid = parseUuid(value);
            if (uuid != null) addToCurrentTeam(uuid);
            return;
        }
        if (action.equals("open-team-warehouse")) {
            transitionPage(() -> {
                view = View.WAREHOUSE;
                warehouseFilter = WarehouseFilter.UNASSIGNED;
                warehouseSelectionMode = true;
                warehouseScrollTop = 0.0;
                search = "";
                selectedUuid = null;
                expandedUuid = null;
                contextOpen = false;
                pendingDanger = "";
                refreshDocument();
            });
            return;
        }
        if (action.equals("apply-search")) {
            Element input = getLinkedDocument().getElementById("findme-search");
            search = input == null ? "" : input.getValue();
            selectedUuid = null;
            expandedUuid = null;
            warehouseScrollTop = 0.0;
            contextOpen = false;
            refreshDocument();
            return;
        }
        if (action.equals("settings-toggle")) {
            String[] parts = value == null ? new String[0] : value.split(":", 2);
            if (parts.length == 2) {
                settings = settings.changed(parseInt(parts[0], -1), parseInt(parts[1], -1));
                markSettingsDirty();
                updateSettingToggleCard(sourceElement);
            }
            return;
        }
        if (action.equals("settings-ui-animations")) {
            settings = settings.withUiAnimations(!settings.uiAnimations());
            markSettingsDirty();
            updateSettingToggleCard(sourceElement, settings.uiAnimations());
            return;
        }
        if (action.equals("settings-hud-toggle")) {
            boolean visible = ClientFindMeHudLayout.toggleVisible();
            FindMeHudRenderer.refresh();
            com.kuzhi.findme.FindMeMod.LOGGER.info("[FindMe settings-ui] HUD visibility changed visible={}", visible);
            updateSettingToggleCard(sourceElement, visible);
            return;
        }
        if (action.equals("settings-reset-binding-history")) {
            ModNetwork.sendToServer(new com.kuzhi.findme.network.FindMeSettingsPacket(
                    FindMeSettingsAction.RESET_BINDING_HISTORY, settings, true, ""));
            transitionSibling(this::refreshDocument, true);
            return;
        }
        if (action.equals("settings-export-defaults")) {
            if (!ClientFindMeModuleState.canManage()) return;
            ModNetwork.sendToServer(new com.kuzhi.findme.network.FindMeSettingsPacket(
                    FindMeSettingsAction.EXPORT_DEFAULTS, settings, true, ""));
            transitionSibling(this::refreshDocument, true);
            return;
        }
        if (action.equals("settings-choice")) {
            try {
                if (settingsChoiceMenu == SettingsChoiceMenu.COMPANION_LIMIT) {
                    ModNetwork.sendToServer(new com.kuzhi.findme.network.FindMeServerSettingsCommandPacket(
                            Integer.parseInt(value)));
                    contextOpen = false;
                    settingsChoiceMenu = SettingsChoiceMenu.NONE;
                    clearOverlayMarkup();
                    return;
                }
                SettingsChoiceMenu selectedChoice = settingsChoiceMenu;
                settings = switch (selectedChoice) {
                    case WHEEL_STYLE -> settings.withWheelStyle(FindMeWheelStyle.valueOf(value));
                    case DRAG_HOLD -> settings.withDragHoldMillis(Integer.parseInt(value));
                    case NAME_LENGTH -> settings.withNameMaxLength(Integer.parseInt(value));
                    case TEXT_MODE -> settings.withTextMode(FindMeTextMode.valueOf(value));
                    case FONT_FAMILY -> settings.withFontFamily(FindMeFontFamily.valueOf(value));
                    case FONT_SIZE -> settings.withFontSize(FindMeFontSize.valueOf(value));
                    case DEFAULT_TEAM -> settings.withDefaultTeamIndex(Integer.parseInt(value));
                    case RIDING_CAMERA -> settings.withRidingCameraMode(FindMeRidingCameraMode.valueOf(value));
                    case BINDING_ANIMATION -> settings.withBindingAnimationPolicy(BindingAnimationPolicy.valueOf(value));
                    case SUMMONED_OUTLINE -> settings.withSummonedOutlineMode(SummonedOutlineMode.valueOf(value));
                    case COMPANION_LIMIT, NONE -> settings;
                };
                markSettingsDirty();
                contextOpen = false;
                settingsChoiceMenu = SettingsChoiceMenu.NONE;
                clearOverlayMarkup();
                updateSettingChoiceCard(selectedChoice);
            } catch (IllegalArgumentException ignored) {
            }
            return;
        }
        if (action.equals("settings-module") && ClientFindMeModuleState.canManage()) {
            try {
                FindMeModule module = FindMeModule.valueOf(value);
                ModNetwork.sendToServer(new com.kuzhi.findme.network.FindMeModuleCommandPacket(
                        module, !ClientFindMeModuleState.configured(module)));
            } catch (IllegalArgumentException ignored) {
            }
            return;
        }
        if (action.equals("settings-reset")) {
            transitionSibling(() -> {
                resetVisibleSettingsSection();
                markSettingsDirty();
                refreshDocument();
            }, true);
            return;
        }
        if (action.equals("settings-modules-reset") && ClientFindMeModuleState.canManage()) {
            transitionSibling(() -> {
                ModNetwork.sendToServer(new com.kuzhi.findme.network.FindMeModuleCommandPacket(null, false, true));
                scheduleRefresh(3);
            }, true);
            return;
        }
        if (action.equals("doctor-backup")) {
            ModNetwork.sendToServer(new DoctorCommandPacket(DoctorCommandAction.CREATE_BACKUP, null, 0, 0L, 0));
            return;
        }
        if (action.equals("doctor-refresh")) {
            ModNetwork.sendToServer(new DoctorCommandPacket(DoctorCommandAction.SYNC, null, 0, 0L, 0));
            return;
        }
        if (action.equals("doctor-view-backups")) {
            backupWarehousePreview = false;
            backupWarehouse = null;
            view = View.DOCTOR;
            doctorView = DoctorView.BACKUPS;
            doctorRestoreConfirm = false;
            refreshDocument();
            return;
        }
        if (action.equals("doctor-backup-open-warehouse")) {
            int index = parseInt(value, -1);
            long savedAt = doctor == null ? 0L : doctor.backups().stream()
                    .filter(backup -> backup.index() == index)
                    .findFirst().map(DoctorPagePacket.Backup::savedAt).orElse(0L);
            if (index >= 0 && savedAt > 0L) {
                setStatus(tr("screen.find_me.doctor.message.review_backup"), 40);
                com.kuzhi.findme.FindMeMod.LOGGER.info("[FindMe backup-ui] request snapshot index={} savedAt={}", index, savedAt);
                ModNetwork.sendToServer(new DoctorCommandPacket(DoctorCommandAction.OPEN_BACKUP_WAREHOUSE,
                        null, index, savedAt, 0));
            }
            return;
        }
        if (action.equals("doctor-backup-detail")) {
            pendingBackup = parseInt(value, -1);
            pendingBackupSavedAt = doctor == null ? 0L : doctor.backups().stream().filter(backup -> backup.index() == pendingBackup).findFirst().map(DoctorPagePacket.Backup::savedAt).orElse(0L);
            doctorRestoreConfirm = false;
            doctorView = DoctorView.BACKUP_DETAIL;
            refreshDocument();
            return;
        }
        if (action.equals("doctor-backup-switch")) {
            String[] parts = value == null ? new String[0] : value.split("\\|", 2);
            int index = parts.length > 0 ? parseInt(parts[0], -1) : -1;
            long savedAt = parts.length > 1 ? parseLong(parts[1], 0L) : 0L;
            boolean valid = doctor != null && doctor.backups().stream()
                    .anyMatch(backup -> backup.index() == index && backup.savedAt() == savedAt && backup.checksumValid());
            if (valid) {
                pendingBackup = index;
                pendingBackupSavedAt = savedAt;
                doctorRestoreConfirm = true;
                contextMotionClass = "fm-context-alert";
                refreshContextOverlay();
            }
            return;
        }
        if (action.equals("ask-delete-backup")) {
            pendingDanger = "backup";
            contextMotionClass = "fm-context-alert";
            refreshContextOverlay();
            return;
        }
        if (action.equals("doctor-backup-delete")) {
            if (contextBackupIndex >= 0 && contextBackupSavedAt > 0L) {
                ModNetwork.sendToServer(new DoctorCommandPacket(DoctorCommandAction.DELETE_BACKUP, null,
                        contextBackupIndex, contextBackupSavedAt, 0));
            }
            dismissContextMenu();
            return;
        }
        if (action.equals("doctor-backup-rename")) {
            Element input = contextOrMainElement("doctor-backup-rename-input");
            String name = input == null ? "" : input.getValue();
            if (contextBackupIndex >= 0 && contextBackupSavedAt > 0L) {
                ModNetwork.sendToServer(new DoctorCommandPacket(DoctorCommandAction.RENAME_BACKUP, null,
                        contextBackupIndex, contextBackupSavedAt, 0, name));
            }
            dismissContextMenu();
            return;
        }
        if (action.equals("doctor-restore-arm")) {
            doctorRestoreConfirm = true;
            refreshDocument();
            return;
        }
        if (action.equals("doctor-restore-cancel")) {
            doctorRestoreConfirm = false;
            pendingDanger = "";
            if (contextOpen) refreshContextOverlay();
            else refreshDocument();
            return;
        }
        if (action.equals("doctor-restore")) {
            doctorRestoreConfirm = false;
            if (pendingBackup >= 0 && pendingBackupSavedAt > 0L) {
                ModNetwork.sendToServer(new DoctorCommandPacket(DoctorCommandAction.RESTORE_BACKUP,
                        null, pendingBackup, pendingBackupSavedAt, 0));
            }
            if (contextOpen) dismissContextMenu();
            return;
        }
        if (action.startsWith("team:")) {
            int targetTeam = Integer.parseInt(action.substring("team:".length()));
            if (targetTeam == selectedTeam) return;
            transitionPage(() -> {
                selectedTeam = targetTeam;
                ClientCompanionTeamState.selectTeam(target(), selectedTeam);
                selectedUuid = null;
                expandedUuid = null;
                refreshDocument();
            });
            return;
        }
        if (action.equals("create-team")) {
            CompanionTeamTarget currentTarget = target();
            int newTeam = ClientCompanionTeamState.appendTeam(currentTarget);
            selectedTeam = newTeam;
            pendingInsertedTeamIndex = newTeam;
            pendingTeamScrollIndex = newTeam;
            selectedUuid = null;
            expandedUuid = null;
            ModNetwork.sendToServer(new CompanionTeamCommandPacket(CompanionTeamAction.CREATE, currentTarget, -1, -1, "", List.of()));
            refreshDocument();
            return;
        }
        if (action.equals("toggle-selected-auto-join")) {
            toggleAutoJoin(selectedTeam);
            return;
        }
        if (action.equals("open-team-menu")) {
            contextTeam = selectedTeam;
            selectedUuid = null;
            settingsChoiceMenu = SettingsChoiceMenu.NONE;
            contextOpen = true;
            warehouseMenuPage = WarehouseMenuPage.MAIN;
            teamMenuPage = TeamMenuPage.MAIN;
            warehouseAnimationPurpose = null;
            pendingDanger = "";
            contextMotionClass = "fm-context-open";
            refreshDocument();
            return;
        }
        if (action.equals("close-context")) {
            dismissContextMenu();
            return;
        }
        if (action.equals("open-settings-choice")) {
            contextTeam = -1;
            selectedUuid = null;
            try {
                settingsChoiceMenu = SettingsChoiceMenu.valueOf(value);
            } catch (IllegalArgumentException exception) {
                settingsChoiceMenu = SettingsChoiceMenu.NONE;
                return;
            }
            contextOpen = true;
            pendingDanger = "";
            contextMotionClass = "fm-context-open";
            refreshContextOverlay();
            return;
        }
        if (action.equals("team-menu:rename")) {
            teamMenuPage = TeamMenuPage.RENAME;
            contextMotionClass = "fm-context-morph";
            refreshContextOverlay();
            return;
        }
        if (action.equals("team-menu:main")) {
            teamMenuPage = TeamMenuPage.MAIN;
            contextMotionClass = "fm-context-back";
            refreshContextOverlay();
            return;
        }
        if (action.equals("save-team-name")) {
            Element input = contextOrMainElement("team-rename-input");
            String name = input == null ? "" : input.getValue();
            ModNetwork.sendToServer(new CompanionTeamCommandPacket(CompanionTeamAction.RENAME, target(), contextTeam, -1, name, List.of()));
            contextOpen = false;
            teamMenuPage = TeamMenuPage.MAIN;
            clearOverlayMarkup();
            return;
        }
        if (action.equals("confirm-delete-team")) {
            ModNetwork.sendToServer(new CompanionTeamCommandPacket(CompanionTeamAction.DELETE, target(), contextTeam, -1, "", List.of()));
            contextOpen = false;
            pendingDanger = "";
            clearOverlayMarkup();
            return;
        }
        if (action.equals("ask-delete-team")) {
            pendingDanger = "team";
            contextMotionClass = "fm-context-alert";
            refreshContextOverlay();
            return;
        }
        if (action.equals("select-warehouse-card")) {
            UUID uuid = parseUuid(value);
            if (uuid == null) return;
            selectedUuid = uuid;
            expandedUuid = null;
            contextOpen = false;
            refreshDocument();
            return;
        }
        if (action.equals("confirm-warehouse-selection")) {
            if (selectedUuid != null) assignToTeam(selectedUuid, selectedTeam, true);
            return;
        }
        if (action.startsWith("warehouse-menu:")) {
            WarehouseMenuPage previousPage = warehouseMenuPage;
            WarehouseMenuPage targetPage = WarehouseMenuPage.valueOf(action.substring("warehouse-menu:".length()).toUpperCase(Locale.ROOT));
            warehouseMenuPage = targetPage;
            warehouseAnimationPurpose = null;
            warehouseEffectPurpose = null;
            contextOpen = true;
            contextMotionClass = targetPage == WarehouseMenuPage.MAIN
                    || (previousPage == WarehouseMenuPage.ANIMATION_STYLE && targetPage == WarehouseMenuPage.ANIMATION)
                    ? "fm-context-back" : targetPage == WarehouseMenuPage.RENAME
                    ? "fm-context-morph" : "fm-context-forward";
            refreshContextOverlay();
            return;
        }
        if (action.startsWith("warehouse-team:")) {
            int teamIndex = parseInt(action.substring("warehouse-team:".length()), -1);
            if (selectedUuid != null && teamIndex >= 0) assignToTeam(selectedUuid, teamIndex, false);
            return;
        }
        if (action.equals("warehouse-leave-team")) {
            UUID leaving = selectedUuid;
            if (leaving != null) startCardExit(leaving, () -> leaveTeam(leaving));
            return;
        }
        if (action.startsWith("warehouse-animation-purpose:")) {
            try {
                warehouseAnimationPurpose = CompanionAnimationPurpose.valueOf(action.substring("warehouse-animation-purpose:".length()).toUpperCase(Locale.ROOT));
                if (category == Category.COMPANION && warehouseAnimationPurpose == CompanionAnimationPurpose.SUMMON) {
                    warehouseAnimationPurpose = null;
                    return;
                }
                warehouseMenuPage = WarehouseMenuPage.ANIMATION_STYLE;
                contextMotionClass = "fm-context-forward";
                refreshContextOverlay();
            } catch (IllegalArgumentException ignored) {
            }
            return;
        }
        if (action.startsWith("warehouse-animation-style:")) {
            if (selectedUuid == null || warehouseAnimationPurpose == null) return;
            try {
                CompanionAnimationStyle style = CompanionAnimationStyle.valueOf(action.substring("warehouse-animation-style:".length()).toUpperCase(Locale.ROOT));
                ModNetwork.sendToServer(new CompanionAnimationStylePacket(selectedUuid, warehouseAnimationPurpose, style));
                warehouseMenuPage = WarehouseMenuPage.ANIMATION;
                warehouseAnimationPurpose = null;
                contextMotionClass = "fm-context-back";
                refreshContextOverlay();
            } catch (IllegalArgumentException ignored) {
            }
            return;
        }
        if (action.startsWith("warehouse-effect-purpose:")) {
            try {
                warehouseEffectPurpose = CompanionEffectPurpose.valueOf(action.substring("warehouse-effect-purpose:".length()).toUpperCase(Locale.ROOT));
                warehouseMenuPage = WarehouseMenuPage.EFFECT_STYLE;
                contextMotionClass = "fm-context-forward";
                refreshContextOverlay();
            } catch (IllegalArgumentException ignored) {
            }
            return;
        }
        if (action.startsWith("warehouse-effect-style:")) {
            if (selectedUuid == null || warehouseEffectPurpose == null) return;
            try {
                CompanionEffectStyle style = CompanionEffectStyle.valueOf(action.substring("warehouse-effect-style:".length()).toUpperCase(Locale.ROOT));
                ModNetwork.sendToServer(new CompanionEffectStylePacket(selectedUuid, warehouseEffectPurpose, style));
                warehouseMenuPage = WarehouseMenuPage.EFFECT;
                warehouseEffectPurpose = null;
                contextMotionClass = "fm-context-back";
                refreshContextOverlay();
            } catch (IllegalArgumentException ignored) {
            }
            return;
        }
        if (action.equals("select-card")) {
            UUID uuid = parseUuid(value);
            if (uuid == null) return;
            if (view == View.TEAM) {
                startCardToggle(uuid, !uuid.equals(expandedUuid));
            } else {
                selectedUuid = uuid;
                expandedUuid = view == View.DEAD || view == View.RECOVERY || uuid.equals(expandedUuid) ? null : uuid;
                contextOpen = false;
                refreshDocument();
            }
            return;
        }
        if (action.equals("save-entity-name")) {
            Element input = contextOrMainElement("entity-rename-input");
            String name = input == null ? "" : input.getValue();
            if (selectedUuid != null) {
                ModNetwork.sendToServer(new WarehouseEntityCommandPacket(WarehouseEntityAction.RENAME, selectedUuid, name));
            }
            contextOpen = false;
            warehouseMenuPage = WarehouseMenuPage.MAIN;
            clearOverlayMarkup();
            return;
        }
        if (action.equals("ask-release")) {
            pendingDanger = "release";
            contextOpen = true;
            contextMotionClass = "fm-context-alert";
            refreshContextOverlay();
            return;
        }
        if (action.equals("confirm-release")) {
            UUID releasing = selectedUuid;
            pendingDanger = "";
            contextOpen = false;
            if (releasing != null) startCardExit(releasing, () -> warehouse(WarehouseEntityAction.RELEASE, releasing));
            return;
        }
        if (action.equals("ask-delete-dead")) {
            UUID uuid = parseUuid(value);
            if (uuid == null) return;
            selectedUuid = uuid;
            contextOpen = true;
            pendingDanger = "dead";
            contextMotionClass = "fm-context-alert";
            refreshContextOverlay();
            return;
        }
        if (action.equals("confirm-delete-dead")) {
            if (selectedUuid != null) warehouse(WarehouseEntityAction.DELETE_DEAD, selectedUuid);
            pendingDanger = "";
            contextOpen = false;
            return;
        }
        if (action.equals("retry-recovery")) {
            UUID uuid = parseUuid(value);
            if (uuid != null) warehouse(WarehouseEntityAction.RETRY_RECOVERY, uuid);
            contextOpen = false;
            return;
        }
        if (action.equals("move-recovery-to-dead")) {
            UUID uuid = parseUuid(value);
            if (uuid != null) warehouse(WarehouseEntityAction.MOVE_RECOVERY_TO_DEAD, uuid);
            contextOpen = false;
            return;
        }
        if (action.equals("ask-delete-recovery")) {
            UUID uuid = parseUuid(value);
            if (uuid == null) return;
            selectedUuid = uuid;
            contextOpen = true;
            pendingDanger = "recovery";
            contextMotionClass = "fm-context-alert";
            refreshContextOverlay();
            return;
        }
        if (action.equals("confirm-delete-recovery")) {
            if (selectedUuid != null) warehouse(WarehouseEntityAction.DELETE_RECOVERY, selectedUuid);
            pendingDanger = "";
            contextOpen = false;
            return;
        }
        UUID uuid = parseUuid(value);
        if (uuid == null) return;
        selectedUuid = uuid;
        switch (action) {
            case "rename" -> {
                contextOpen = true;
                warehouseMenuPage = WarehouseMenuPage.RENAME;
                contextMotionClass = "fm-context-morph";
                refreshContextOverlay();
            }
            case "move-companion" -> startCardExit(uuid,
                    () -> warehouse(WarehouseEntityAction.MOVE_TO_COMPANION, uuid));
            case "move-mount" -> startCardExit(uuid,
                    () -> warehouse(WarehouseEntityAction.MOVE_TO_MOUNT, uuid));
            case "remove-team" -> startCardExit(uuid, () -> {
                removeFromClientTeams(target(), uuid);
                warehouse(WarehouseEntityAction.REMOVE_FROM_TEAM, uuid);
                selectedUuid = null;
                expandedUuid = null;
                refreshDocument();
            });
            case "release" -> startCardExit(uuid, () -> warehouse(WarehouseEntityAction.RELEASE, uuid));
            case "delete-dead" -> warehouse(WarehouseEntityAction.DELETE_DEAD, uuid);
            default -> refreshDocument();
        }
    }

    private void warehouse(WarehouseEntityAction action, UUID uuid) {
        ModNetwork.sendToServer(new WarehouseEntityCommandPacket(action, uuid, ""));
    }

    private void setStatus(String message, int ticks) {
        status = message == null ? "" : message;
        statusTicks = status.isBlank() ? 0 : Math.max(1, ticks);
    }

    private Element contextOrMainElement(String id) {
        Document overlay = getOverlayDocument();
        Element element = contextOpen && overlay != null ? overlay.getElementById(id) : null;
        return element != null || getLinkedDocument() == null ? element : getLinkedDocument().getElementById(id);
    }

    private void activateCard(UUID uuid) {
        Card card = findCard(uuid);
        if (card == null || !card.alive || view == View.DEAD || view == View.RECOVERY) return;
        if (view == View.WAREHOUSE) return;
        int memberIndex = ClientCompanionTeamState.members(target(), selectedTeam).indexOf(uuid);
        if (memberIndex < 0) return;
        if (category == Category.VEHICLE) {
            ClientMountRosterState.select(MountRosterSource.VEHICLE, card.uuid);
            ClientMountRosterTransactionState.send(card.active ? MountRosterAction.RECALL
                            : MountRosterAction.ACTIVATE,
                    MountRosterSource.VEHICLE, card.uuid, memberIndex, selectedTeam);
        } else {
            CompanionKind kind = category == Category.MOUNT ? CompanionKind.MOUNT : CompanionKind.COMPANION;
            if (kind == CompanionKind.MOUNT) {
                ClientMountRosterState.select(MountRosterSource.FIND_ME, card.uuid);
                ClientMountRosterTransactionState.send(card.active ? MountRosterAction.RECALL
                                : MountRosterAction.ACTIVATE,
                        MountRosterSource.FIND_ME, card.uuid, memberIndex, selectedTeam);
            } else if (card.active) {
                ModNetwork.sendToServer(new CompanionTeamCommandPacket(CompanionTeamAction.APPLY,
                        target(), selectedTeam, -1, "", List.of()));
                ModNetwork.sendToServer(new CompanionCommandPacket(kind, CompanionAction.SELECT, memberIndex));
                ModNetwork.sendToServer(new CompanionCommandPacket(kind, CompanionAction.RETURN_HOME, -1));
            } else {
                ModNetwork.sendToServer(new CompanionTeamCommandPacket(CompanionTeamAction.APPLY,
                        target(), selectedTeam, -1, "", List.of()));
                ModNetwork.sendToServer(new CompanionCommandPacket(kind, CompanionAction.SELECT_SUMMON, memberIndex));
            }
        }
    }

    private void addToCurrentTeam(UUID uuid) {
        assignToTeam(uuid, selectedTeam, true);
    }

    private void toggleAutoJoin(int teamIndex) {
        CompanionTeamTarget currentTarget = target();
        if (!ClientCompanionTeamState.toggleAutoJoin(currentTarget, teamIndex)) return;
        pendingAutoJoinMotionTeam = teamIndex;
        ModNetwork.sendToServer(new CompanionTeamCommandPacket(CompanionTeamAction.TOGGLE_AUTO_JOIN,
                currentTarget, teamIndex, -1, "", List.of()));
        refreshDocument();
    }

    private void assignToTeam(UUID uuid, int teamIndex, boolean returnToTeam) {
        Card card = findCard(uuid);
        CompanionTeamTarget currentTarget = target();
        List<UUID> members = new ArrayList<>(ClientCompanionTeamState.members(currentTarget, teamIndex));
        if (members.contains(uuid) || members.size() >= com.kuzhi.findme.common.FindMeUiSettings.TEAM_CAPACITY) return;
        removeFromClientTeams(currentTarget, uuid);
        members.add(uuid);
        ClientCompanionTeamState.replaceMembers(currentTarget, teamIndex, members);
        ModNetwork.sendToServer(new CompanionTeamCommandPacket(CompanionTeamAction.SET, currentTarget, teamIndex, -1, "", members));
        selectedUuid = uuid;
        expandedUuid = null;
        pendingInsertedMemberUuid = returnToTeam ? uuid : null;
        contextOpen = false;
        warehouseMenuPage = WarehouseMenuPage.MAIN;
        teamMenuPage = TeamMenuPage.MAIN;
        warehouseAnimationPurpose = null;
        pendingDanger = "";
        if (returnToTeam) {
            selectedTeam = teamIndex;
            ClientCompanionTeamState.selectTeam(currentTarget, selectedTeam);
            view = View.TEAM;
            warehouseSelectionMode = false;
            search = "";
            warehouseScrollTop = 0.0;
        }
        refreshDocument();
    }

    private void leaveTeam(UUID uuid) {
        CompanionTeamTarget currentTarget = target();
        removeFromClientTeams(currentTarget, uuid);
        warehouse(WarehouseEntityAction.REMOVE_FROM_TEAM, uuid);
        contextOpen = false;
        warehouseMenuPage = WarehouseMenuPage.MAIN;
        teamMenuPage = TeamMenuPage.MAIN;
        warehouseAnimationPurpose = null;
        selectedUuid = null;
        refreshDocument();
    }

    private void removeFromClientTeams(CompanionTeamTarget currentTarget, UUID uuid) {
        for (var team : ClientCompanionTeamState.entries(currentTarget)) {
            if (!team.uuids().contains(uuid)) continue;
            List<UUID> members = new ArrayList<>(team.uuids());
            members.remove(uuid);
            ClientCompanionTeamState.replaceMembers(currentTarget, team.index(), members);
        }
    }

    private void refreshDocument() {
        long refreshStartedAt = FindMeAuiPerformanceMonitor.start();
        Document document = getLinkedDocument();
        if (document == null || document.body == null) {
            com.kuzhi.findme.FindMeMod.LOGGER.error("[FindMe backup-ui] refresh skipped document={} body={} view={}",
                    document != null, document != null && document.body != null, view);
            return;
        }
        Element root = document.getElementById("findme-manager-root");
        if (root == null) {
            com.kuzhi.findme.FindMeMod.LOGGER.error("[FindMe backup-ui] refresh skipped: manager root missing view={} bodyElements={}",
                    view, document.getElements().size());
            return;
        }
        Element oldWarehouseGrid = document.getElementById("findme-warehouse-grid");
        if (oldWarehouseGrid != null) warehouseScrollTop = oldWarehouseGrid.getScrollTop();
        Element oldTeamList = document.getElementById("findme-team-list");
        if (oldTeamList != null) teamScrollTop = oldTeamList.getTargetScrollTop();
        Element oldSettingsGrid = document.getElementById("findme-settings-grid");
        if (oldSettingsGrid != null && !resetSettingsScrollOnRefresh) {
            settingsScrollTop = oldSettingsGrid.getTargetScrollTop();
        }
        Element oldBackupRecords = document.getElementById("findme-doctor-backup-records");
        if (oldBackupRecords != null && view == View.DOCTOR && doctorView == DoctorView.BACKUPS) {
            doctorBackupScrollTop = oldBackupRecords.getTargetScrollTop();
        }
        if (resetSettingsScrollOnRefresh) settingsScrollTop = 0.0;
        clearDrag();
        long markupStartedAt = FindMeAuiPerformanceMonitor.start();
        String nextLayoutStyle = layoutStyle();
        String nextMarkup = markup();
        FindMeAuiPerformanceMonitor.record(this, "refresh.markup", markupStartedAt, 8.0);
        boolean memberInsertionPending = pendingInsertedMemberUuid != null && view == View.TEAM;
        boolean teamInsertionPending = pendingInsertedTeamIndex >= 0 && view == View.TEAM;
        boolean autoJoinMotionPending = pendingAutoJoinMotionTeam >= 0 && view == View.TEAM;
        String nextMarkupKey = markupKey(nextMarkup);
        boolean layoutChanged = !nextLayoutStyle.equals(renderedLayoutStyle);
        boolean markupChanged = !nextMarkupKey.equals(renderedMarkupKey);
        if (view == View.DOCTOR) {
            com.kuzhi.findme.FindMeMod.LOGGER.info("[FindMe backup-ui] doctor refresh begin doctor={} doctorView={} markupChanged={} layoutChanged={} markupLength={} transition={}",
                    doctor != null, doctorView, markupChanged, layoutChanged, nextMarkup.length(), isTransitionRunning());
        }
        if (layoutChanged) {
            root.setAttribute("style", nextLayoutStyle);
            renderedLayoutStyle = nextLayoutStyle;
        }
        if (markupChanged) {
            long domStartedAt = FindMeAuiPerformanceMonitor.start();
            root.setInnerHTML(nextMarkup);
            FindMeAuiPerformanceMonitor.record(this, "refresh.dom_replace", domStartedAt, 8.0);
            long selectorsStartedAt = FindMeAuiPerformanceMonitor.start();
            document.rebuildSelectorIndex();
            FindMeAuiPerformanceMonitor.record(this, "refresh.selector_index", selectorsStartedAt, 5.0);
            if (view == View.DOCTOR && doctor != null && doctorView == DoctorView.BACKUPS) {
                Element records = document.getElementById("findme-doctor-backup-records");
                int directRecords = countDoctorBackupRecords(records);
                int selectorRecords = document.querySelectorAll(".doctor-backup-record").size();
                int dataRecords = document.querySelectorAll("[data-backup-index]").size();
                com.kuzhi.findme.FindMeMod.LOGGER.info(
                        "[FindMe backup-ui] dom after setInnerHTML elements={} records={} directRecords={} classRecords={} dataRecords={} innerHtmlLength={}",
                        document.getElements().size(), records != null, directRecords, selectorRecords, dataRecords,
                        records == null ? 0 : records.getInnerHTML().length());
                if (ensureDoctorBackupRecordsDom(document)) {
                    document.rebuildSelectorIndex();
                    com.kuzhi.findme.FindMeMod.LOGGER.info(
                            "[FindMe backup-ui] rebuilt backup records with legacy DOM API directRecords={} classRecords={} dataRecords={}",
                            countDoctorBackupRecords(records), document.querySelectorAll(".doctor-backup-record").size(),
                            document.querySelectorAll("[data-backup-index]").size());
                }
            }
            long stylesStartedAt = FindMeAuiPerformanceMonitor.start();
            document.reapplyStylesFromCache();
            // Old AUI does not always commit styles for nodes inserted by setInnerHTML.
            // The doctor page is loaded through this path, so force the same synchronous
            // style pass used by the legacy screen refresh flow.
            document.commitStyleRecalc();
            FindMeAuiPerformanceMonitor.record(this, "refresh.styles", stylesStartedAt, 8.0);
            long bindStartedAt = FindMeAuiPerformanceMonitor.start();
            bindDocument();
            FindMeAuiPerformanceMonitor.record(this, "refresh.bind", bindStartedAt, 8.0);
            renderedMarkupKey = nextMarkupKey;
            if (memberInsertionPending) pendingInsertedMemberUuid = null;
            if (teamInsertionPending) pendingInsertedTeamIndex = -1;
            if (autoJoinMotionPending) pendingAutoJoinMotionTeam = -1;
        } else if (isPageRevealing()) {
            addClass(document.querySelector(".fm-page"), "fm-page-reveal");
        }
        long postworkStartedAt = FindMeAuiPerformanceMonitor.start();
        long postworkPhaseStartedAt = FindMeAuiPerformanceMonitor.start();
        if (layoutChanged && !markupChanged) {
            document.reapplyStylesFromCache();
            document.commitStyleRecalc();
        }
        FindMeAuiPerformanceMonitor.record(this, "refresh.postwork.relayout", postworkPhaseStartedAt, 4.0);
        lastTeamScrollThumbTop = -1;
        lastTeamSelectionMarkerStyle = "";
        lastWarehouseScrollThumbStyle = "";
        lastSettingsScrollThumbStyle = "";
        postworkPhaseStartedAt = FindMeAuiPerformanceMonitor.start();
        Element warehouseGrid = document.getElementById("findme-warehouse-grid");
        if (warehouseGrid != null) warehouseGrid.setScrollTop(warehouseScrollTop);
        Element teamList = document.getElementById("findme-team-list");
        if (teamList != null) teamList.setScrollTop(teamScrollTop);
        Element settingsGrid = document.getElementById("findme-settings-grid");
        if (settingsGrid != null) settingsGrid.setScrollTop(settingsScrollTop);
        Element backupRecords = document.getElementById("findme-doctor-backup-records");
        if (backupRecords != null && view == View.DOCTOR && doctorView == DoctorView.BACKUPS) {
            backupRecords.setScrollTop(doctorBackupScrollTop);
        }
        if (view == View.DOCTOR && doctor != null && markupChanged) {
            Element renderedBackupRecords = document.getElementById("findme-doctor-backup-records");
            int domRecords = document.querySelectorAll(".doctor-backup-record").size();
            com.kuzhi.findme.FindMeMod.LOGGER.info(
                    "[FindMe backup-ui] refresh view={} dataRecords={} domRecords={} recordsBox={}x{}",
                    doctorView, doctor.backups().size(), domRecords,
                    renderedBackupRecords == null ? 0 : Math.round((float) Box.of(renderedBackupRecords).innerSize().width()),
                    renderedBackupRecords == null ? 0 : Math.round((float) Box.of(renderedBackupRecords).innerSize().height()));
        }
        if (view == View.DOCTOR) {
            Element page = document.querySelector(".fm-page");
            Element sidebar = document.querySelector(".fm-sidebar");
            Element content = document.querySelector(".doctor-content");
            com.kuzhi.findme.FindMeMod.LOGGER.info("[FindMe backup-ui] doctor refresh end page={} pageBox={}x{} sidebar={} content={}x{} records={}",
                    page == null ? "missing" : page.getAttribute("class"),
                    page == null ? 0 : Math.round((float) Box.of(page).innerSize().width()),
                    page == null ? 0 : Math.round((float) Box.of(page).innerSize().height()),
                    sidebar == null ? "missing" : "present",
                    content == null ? 0 : Math.round((float) Box.of(content).innerSize().width()),
                    content == null ? 0 : Math.round((float) Box.of(content).innerSize().height()),
                    document.querySelectorAll(".doctor-backup-record").size());
        }
        resetSettingsScrollOnRefresh = false;
        FindMeAuiPerformanceMonitor.record(this, "refresh.postwork.scroll_restore", postworkPhaseStartedAt, 4.0);
        if (pendingTeamScrollIndex >= 0) {
            scrollTeamIntoView(pendingTeamScrollIndex);
            pendingTeamScrollIndex = -1;
        }
        postworkPhaseStartedAt = FindMeAuiPerformanceMonitor.start();
        refreshContextOverlay();
        FindMeAuiPerformanceMonitor.record(this, "refresh.postwork.context", postworkPhaseStartedAt, 4.0);
        postworkPhaseStartedAt = FindMeAuiPerformanceMonitor.start();
        updateTeamScrollThumb();
        FindMeAuiPerformanceMonitor.record(this, "refresh.postwork.team_scroll", postworkPhaseStartedAt, 4.0);
        postworkPhaseStartedAt = FindMeAuiPerformanceMonitor.start();
        updateWarehouseScrollThumb();
        FindMeAuiPerformanceMonitor.record(this, "refresh.postwork.warehouse_scroll", postworkPhaseStartedAt, 4.0);
        postworkPhaseStartedAt = FindMeAuiPerformanceMonitor.start();
        updateSettingsScrollThumb();
        FindMeAuiPerformanceMonitor.record(this, "refresh.postwork.settings_scroll", postworkPhaseStartedAt, 4.0);
        postworkPhaseStartedAt = FindMeAuiPerformanceMonitor.start();
        updateDoctorBackupScrollThumb();
        FindMeAuiPerformanceMonitor.record(this, "refresh.postwork.backup_scroll", postworkPhaseStartedAt, 4.0);
        lastSignature = stateSignature();
        pendingSignature = Long.MIN_VALUE;
        refreshDebounceTicks = -1;
        forcedRefreshTicks = -1;
        FindMeAuiPerformanceMonitor.record(this, "refresh.postwork", postworkStartedAt, 6.0);
        FindMeAuiPerformanceMonitor.record(this, "refresh.total", refreshStartedAt, 20.0);
    }

    private void scrollTeamIntoView(int teamIndex) {
        Document document = getLinkedDocument();
        if (document == null || teamIndex < 0) return;
        Element list = document.getElementById("findme-team-list");
        if (list == null) return;
        double rowHeight = 27.0;
        for (Element row : document.querySelectorAll("[data-team-index]")) {
            int index = parseInt(row.getAttribute("data-team-index"), -1);
            if (index == teamIndex) {
                rowHeight = Math.max(1.0, Size.of(row).height());
                break;
            }
        }
        double viewport = Math.max(rowHeight, Box.of(list).innerSize().height());
        double targetTop = Math.max(0.0, teamIndex * rowHeight - Math.max(0.0, viewport - rowHeight));
        list.setScrollTop(targetTop);
        teamScrollTop = list.getTargetScrollTop();
        updateTeamScrollThumb();
    }

    private void rememberContextAnchor(double x, double y) {
        contextAnchorX = x;
        contextAnchorY = y;
    }

    private void refreshContextOverlay() {
        if (!contextOpen) {
            contextBounds = FindMeAuiContextMenuPlacement.EMPTY;
            clearOverlayMarkup();
            return;
        }
        String nextMarkup = "<div class='findme-context-dismiss' data-context-dismiss='1' data-action='close-context'></div>" + contextMarkup();
        // ApricityUI 1.1.6 can retain descendants when a menu changes shape (for example,
        // the rename input surviving into the restore-confirm page). Clear the old tree first.
        if (!overlayMarkupEquals(nextMarkup)) {
            clearOverlayMarkup();
            setOverlayMarkup(nextMarkup);
        }
        contextMotionClass = "";
        double fallbackWidth = spellPickerUuid != null ? 100.0 : view == View.WAREHOUSE ? 100.0 : 91.0;
        contextBounds = FindMeAuiContextMenuPlacement.place(getOverlayDocument(), "findme-context-menu", ".findme-context-overlay-root",
                contextAnchorX, contextAnchorY, width - fallbackWidth - 8.0, 4.0, fallbackWidth, 24.0);
    }

    private void updateTeamScrollThumb() {
        if (view != View.TEAM) return;
        Document document = getLinkedDocument();
        if (document == null) return;
        Element list = document.getElementById("findme-team-list");
        if (list == null) return;

        double contentHeight = Math.max(TEAM_SCROLL_VIEWPORT_HEIGHT, teams().size() * TEAM_ROW_HEIGHT);
        double scrollLimit = Math.max(0.0, contentHeight - TEAM_SCROLL_VIEWPORT_HEIGHT);
        teamScrollTop = Math.max(0.0, Math.min(scrollLimit, list.getTargetScrollTop()));
        double ratio = scrollLimit <= 0.0 ? 0.0 : teamScrollTop / scrollLimit;

        Element rail = document.getElementById("findme-team-scroll-rail");
        Element thumb = document.getElementById("findme-team-scroll-thumb");
        if (rail != null && thumb != null) {
            double trackHeight = TEAM_SCROLL_VIEWPORT_HEIGHT;
            double thumbHeight = Math.min(trackHeight, TEAM_SCROLL_THUMB_HEIGHT);
            double travel = Math.max(0.0, trackHeight - thumbHeight);
            int top = (int) Math.round(travel * ratio);
            if (top != lastTeamScrollThumbTop) {
                lastTeamScrollThumbTop = top;
                thumb.setAttribute("style", "top:" + top + "px");
            }
        }

        Element marker = document.getElementById("findme-team-selection-marker");
        if (marker != null) {
            int markerTop = 37 + (int) Math.round(selectedTeam * TEAM_ROW_HEIGHT - teamScrollTop);
            boolean visible = markerTop >= 37 && markerTop + TEAM_ROW_HEIGHT <= 163;
            String markerStyle = "top:" + markerTop + "px;visibility:" + (visible ? "visible" : "hidden");
            if (!markerStyle.equals(lastTeamSelectionMarkerStyle)) {
                lastTeamSelectionMarkerStyle = markerStyle;
                marker.setAttribute("style", markerStyle);
            }
        }
    }

    private void updateWarehouseScrollThumb() {
        if (view != View.WAREHOUSE && view != View.DEAD) return;
        Document document = getLinkedDocument();
        if (document == null) return;
        Element grid = document.getElementById("findme-warehouse-grid");
        Element rail = document.getElementById("findme-warehouse-scroll-rail");
        Element thumb = document.getElementById("findme-warehouse-scroll-thumb");
        if (grid == null || rail == null || thumb == null) return;

        double viewportHeight = Math.max(1.0, Box.of(grid).innerSize().height());
        double contentHeight = Math.max(viewportHeight, Size.getContentSize(grid).height());
        double scrollLimit = Math.max(0.0, contentHeight - viewportHeight);
        double ratio = scrollLimit <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, grid.getScrollTop() / scrollLimit));
        warehouseScrollTop = grid.getScrollTop();

        double trackHeight = Math.max(1.0, Box.of(rail).innerSize().height());
        double thumbHeight = scrollLimit <= 0.0 ? trackHeight : Math.max(scaled(16), Math.min(trackHeight, trackHeight * viewportHeight / contentHeight));
        double travel = Math.max(0.0, trackHeight - thumbHeight);
        String style = "top:" + (int) Math.round(travel * ratio) + "px;height:" + (int) Math.round(thumbHeight)
                + "px;visibility:" + (scrollLimit > 0.0 ? "visible" : "hidden");
        if (!style.equals(lastWarehouseScrollThumbStyle)) {
            lastWarehouseScrollThumbStyle = style;
            thumb.setAttribute("style", style);
        }
    }

    private boolean beginDoctorBackupScroll(MouseEvent mouse, Element targetElement) {
        if (view != View.DOCTOR || doctorView != DoctorView.BACKUPS || mouse.button != 0) return false;
        Element rail = targetElement.closest("#findme-doctor-backup-scroll-rail");
        if (rail == null) return false;
        updateDoctorBackupScrollThumb();
        Element thumb = targetElement.closest("#findme-doctor-backup-scroll-thumb");
        if (thumb != null) {
            doctorBackupScrollDragging = true;
            doctorBackupScrollDragOffset = Math.max(0.0, mouse.clientY - Position.of(thumb).y);
        } else {
            setDoctorBackupScrollFromPointer(mouse.clientY, Double.NaN);
        }
        return true;
    }

    private void dragDoctorBackupScroll(MouseEvent mouse) {
        setDoctorBackupScrollFromPointer(mouse.clientY, doctorBackupScrollDragOffset);
    }

    private void setDoctorBackupScrollFromPointer(double mouseY, double grabOffset) {
        Document document = getLinkedDocument();
        if (document == null) return;
        Element records = document.getElementById("findme-doctor-backup-records");
        Element rail = document.getElementById("findme-doctor-backup-scroll-rail");
        if (records == null || rail == null) return;

        double viewportHeight = Math.max(1.0, Box.of(records).innerSize().height());
        double contentHeight = Math.max(viewportHeight, Size.getContentSize(records).height());
        double scrollLimit = Math.max(0.0, contentHeight - viewportHeight);
        double trackHeight = Math.max(1.0, Box.of(rail).innerSize().height());
        double thumbHeight = scrollLimit <= 0.0 ? trackHeight
                : Math.max(scaled(12), Math.min(trackHeight, trackHeight * viewportHeight / contentHeight));
        double travel = Math.max(0.0, trackHeight - thumbHeight);
        double effectiveOffset = Double.isNaN(grabOffset) ? thumbHeight * 0.5 : grabOffset;
        double localTop = Math.max(0.0, Math.min(travel, mouseY - Position.of(rail).y - effectiveOffset));
        double next = travel <= 0.0 ? 0.0 : scrollLimit * localTop / travel;
        records.setScrollTop(next);
        doctorBackupScrollTop = records.getTargetScrollTop();
        updateDoctorBackupScrollThumb();
    }

    private void updateDoctorBackupScrollThumb() {
        if (view != View.DOCTOR || doctorView != DoctorView.BACKUPS) return;
        Document document = getLinkedDocument();
        if (document == null) return;
        Element records = document.getElementById("findme-doctor-backup-records");
        Element rail = document.getElementById("findme-doctor-backup-scroll-rail");
        Element thumb = document.getElementById("findme-doctor-backup-scroll-thumb");
        if (records == null || rail == null || thumb == null) return;

        double viewportHeight = Math.max(1.0, Box.of(records).innerSize().height());
        double contentHeight = Math.max(viewportHeight, Size.getContentSize(records).height());
        double scrollLimit = Math.max(0.0, contentHeight - viewportHeight);
        double current = Math.max(0.0, Math.min(scrollLimit, records.getScrollTop()));
        if (current != records.getScrollTop()) records.setScrollTop(current);
        doctorBackupScrollTop = current;

        double trackHeight = Math.max(1.0, Box.of(rail).innerSize().height());
        double thumbHeight = scrollLimit <= 0.0 ? trackHeight
                : Math.max(scaled(12), Math.min(trackHeight, trackHeight * viewportHeight / contentHeight));
        double travel = Math.max(0.0, trackHeight - thumbHeight);
        double ratio = scrollLimit <= 0.0 ? 0.0 : current / scrollLimit;
        String style = "top:" + (int) Math.round(travel * ratio) + "px;height:" + (int) Math.round(thumbHeight)
                + "px;visibility:" + (scrollLimit > 0.0 ? "visible" : "hidden");
        if (!style.equals(lastDoctorBackupScrollThumbStyle)) {
            lastDoctorBackupScrollThumbStyle = style;
            thumb.setAttribute("style", style);
        }
    }

    private boolean beginSettingsScroll(MouseEvent mouse, Element targetElement) {
        if (view != View.SETTINGS || mouse.button != 0) return false;
        Element rail = targetElement.closest("#findme-settings-scroll-rail");
        if (rail == null) return false;
        updateSettingsScrollThumb();
        Element thumb = targetElement.closest("#findme-settings-scroll-thumb");
        if (thumb != null) {
            settingsScrollDragging = true;
            settingsScrollDragOffset = Math.max(0.0, mouse.clientY - Position.of(thumb).y);
        } else {
            setSettingsScrollFromPointer(mouse.clientY, Double.NaN);
        }
        return true;
    }

    private void dragSettingsScroll(MouseEvent mouse) {
        setSettingsScrollFromPointer(mouse.clientY, settingsScrollDragOffset);
    }

    private void setSettingsScrollFromPointer(double mouseY, double grabOffset) {
        Document document = getLinkedDocument();
        if (document == null) return;
        Element grid = document.getElementById("findme-settings-grid");
        Element viewport = document.getElementById("findme-settings-grid-viewport");
        Element rail = document.getElementById("findme-settings-scroll-rail");
        if (grid == null || viewport == null || rail == null) return;

        double viewportHeight = Math.max(1.0, Box.of(viewport).innerSize().height());
        double contentHeight = Math.max(viewportHeight, Size.getContentSize(grid).height());
        double scrollLimit = Math.max(0.0, contentHeight - viewportHeight);
        double trackHeight = Math.max(1.0, Box.of(rail).innerSize().height());
        double thumbHeight = scrollLimit <= 0.0 ? trackHeight
                : Math.max(scaled(12), Math.min(trackHeight, trackHeight * viewportHeight / contentHeight));
        double travel = Math.max(0.0, trackHeight - thumbHeight);
        double effectiveOffset = Double.isNaN(grabOffset) ? thumbHeight * 0.5 : grabOffset;
        double localTop = Math.max(0.0, Math.min(travel, mouseY - Position.of(rail).y - effectiveOffset));
        double next = travel <= 0.0 ? 0.0 : scrollLimit * localTop / travel;
        grid.setScrollTop(next);
        settingsScrollTop = grid.getTargetScrollTop();
        updateSettingsScrollThumb();
    }

    private void updateSettingsScrollThumb() {
        if (view != View.SETTINGS) return;
        Document document = getLinkedDocument();
        if (document == null) return;
        Element grid = document.getElementById("findme-settings-grid");
        Element viewport = document.getElementById("findme-settings-grid-viewport");
        Element rail = document.getElementById("findme-settings-scroll-rail");
        Element thumb = document.getElementById("findme-settings-scroll-thumb");
        if (grid == null || viewport == null || rail == null || thumb == null) return;

        double viewportHeight = Math.max(1.0, Box.of(viewport).innerSize().height());
        double contentHeight = Math.max(viewportHeight, Size.getContentSize(grid).height());
        double scrollLimit = Math.max(0.0, contentHeight - viewportHeight);
        double target = grid.getTargetScrollTop();
        double current = Math.max(0.0, Math.min(scrollLimit, target));
        if (current != target) grid.setScrollTop(current);
        settingsScrollTop = current;

        double trackHeight = Math.max(1.0, Box.of(rail).innerSize().height());
        double thumbHeight = scrollLimit <= 0.0 ? trackHeight
                : Math.max(scaled(12), Math.min(trackHeight, trackHeight * viewportHeight / contentHeight));
        double travel = Math.max(0.0, trackHeight - thumbHeight);
        double ratio = scrollLimit <= 0.0 ? 0.0 : current / scrollLimit;
        String style = "top:" + (int) Math.round(travel * ratio) + "px;height:" + (int) Math.round(thumbHeight)
                + "px;visibility:" + (scrollLimit > 0.0 ? "visible" : "hidden");
        if (!style.equals(lastSettingsScrollThumbStyle)) {
            lastSettingsScrollThumbStyle = style;
            thumb.setAttribute("style", style);
        }
    }

    private String markup() {
        List<ClientCompanionTeamState.TeamEntry> teams = teams();
        selectedTeam = teams.isEmpty() ? 0 : Math.max(0, Math.min(selectedTeam, teams.size() - 1));
        if (view == View.SETTINGS) return pageShell("settings", tr("screen.find_me.aui.settings"),
                tr("screen.find_me.aui.settings.subtitle"), settingsMarkup());
        if (view == View.DOCTOR) return pageShell("doctor", tr("screen.find_me.aui.backups"),
                tr("screen.find_me.doctor.caption.auto_manual"), doctorMarkup());
        if (view == View.DETAIL) return pageShell("detail", tr("screen.find_me.aui.profile.title"), "COMPANION PROFILE", detailPageMarkup());

        List<Card> visibleCards = cards();
        if (view == View.DEAD || view == View.RECOVERY) {
            expandedUuid = null;
            if (selectedUuid == null || visibleCards.stream().noneMatch(card -> card.uuid.equals(selectedUuid))) {
                selectedUuid = visibleCards.isEmpty() ? null : visibleCards.get(0).uuid;
            }
        } else {
            if (expandedUuid != null && visibleCards.stream().noneMatch(card -> card.uuid.equals(expandedUuid))) {
                expandedUuid = null;
            }
            if (selectedUuid != null && visibleCards.stream().noneMatch(card -> card.uuid.equals(selectedUuid))) {
                selectedUuid = null;
            }
        }
        String pageClass = view == View.DEAD || view == View.RECOVERY ? "death" : view == View.WAREHOUSE ? "warehouse" : "team-view";
        String title = view == View.DEAD ? tr("screen.find_me.aui.deaths")
                : view == View.RECOVERY ? tr("screen.find_me.aui.recovery")
                : view == View.WAREHOUSE ? tr("screen.find_me.warehouse") : tr("screen.find_me.aui.manage_title");
        String subtitle = view == View.DEAD ? "MEMORIAL ARCHIVE"
                : view == View.RECOVERY ? "DATA RECOVERY"
                : view == View.WAREHOUSE ? "COMPANION STORAGE" : tr("screen.find_me.aui.manage_title.secondary");
        StringBuilder html = new StringBuilder("<div class='fm-page ").append(pageClass).append(" ").append(typographyClasses())
                .append(isPageRevealing() ? " fm-page-reveal" : "")
                .append("' style='height:").append(Math.max(1, height)).append("px'>")
                .append(headerMarkup(title, subtitle, true));
        html.append(sidebarMarkup(teams));
        html.append("<div class='fm-workspace'>").append(workspaceHeader(teams, visibleCards.size()));
        if (view == View.DEAD || view == View.RECOVERY) html.append(deathGridMarkup(visibleCards));
        else if (view == View.WAREHOUSE) html.append(warehouseMarkup(visibleCards));
        else html.append(rosterMarkup(visibleCards));
        html.append("</div>");
        if (view == View.TEAM) html.append("<div id='findme-drag-ghost' class='drag-ghost'></div>");
        if (!status.isBlank()) html.append("<div class='status'>").append(escape(status)).append("</div>");
        return html.append("</div>").toString();
    }

    private String headerMarkup(String title, String subtitle, boolean categories) {
        StringBuilder html = new StringBuilder("<div class='topbar'>").append(button("back", "‹", "back-button"))
                .append("<div class='page-title ").append(font.width(title) > 100 ? "long-title" : "")
                .append("'><strong>").append(escape(title)).append("</strong><small>").append(subtitle).append("</small></div>");
        if (categories) {
            html.append("<div class='category-tabs'>");
            for (Category value : Category.values()) {
                html.append("<div class='ui-button category-button ").append(value == category ? "active" : "").append("' data-action='category:").append(value.name().toLowerCase(Locale.ROOT)).append("'><span>").append(escape(tr(value.key))).append("</span>");
                if (value == category) html.append("<div class='category-indicator'></div>");
                html.append("</div>");
            }
            html.append("</div>");
            int count = allCards().size();
            String action = view == View.WAREHOUSE
                    ? (backupWarehousePreview ? "doctor-view-backups" : "view:team") : "view:warehouse";
            String label = view == View.WAREHOUSE
                    ? (backupWarehousePreview ? tr("screen.find_me.aui.backups") : tr("screen.find_me.teams"))
                    : tr("screen.find_me.warehouse");
            html.append("<div class='ui-button top-link' data-action='").append(action).append("'>")
                    .append("<span>").append(escape(label)).append("</span><b>").append(count).append("</b></div>");
        }
        return html.append("</div>").toString();
    }

    private String pageShell(String pageClass, String title, String subtitle, String content) {
        StringBuilder html = new StringBuilder("<div class='fm-page ").append(pageClass).append(" ").append(typographyClasses())
                .append(isPageRevealing() ? " fm-page-reveal" : "")
                .append("' style='height:").append(Math.max(1, height)).append("px'>")
                .append(headerMarkup(title, subtitle, false)).append(content);
        if (!status.isBlank()) html.append("<div class='status'>").append(escape(status)).append("</div>");
        return html.append("</div>").toString();
    }

    private String sidebarMarkup(List<ClientCompanionTeamState.TeamEntry> teams) {
        String sideTitle = view == View.TEAM ? tr("screen.find_me.aui.teams_heading")
                : view == View.DEAD ? tr("screen.find_me.aui.records")
                : view == View.RECOVERY ? tr("screen.find_me.aui.recovery") : tr(view.key);
        StringBuilder html = new StringBuilder("<div class='fm-sidebar'><div class='fm-sidebar-sheet'></div><div class='fm-sidebar-content'><div class='side-heading fm-side-stage-0'><small>").append(view == View.TEAM ? escape(tr("screen.find_me.aui.teams_heading.secondary")) : view == View.DEAD ? "ARCHIVE" : view == View.RECOVERY ? "RECOVERY" : "STORAGE").append("</small><strong>").append(escape(sideTitle)).append("</strong></div>");
        if (view == View.TEAM) {
            html.append("<div id='findme-team-list' class='team-list'>");
            for (var team : teams) {
                String name = team.name().isBlank() ? tr("screen.find_me.team_number", team.number()) : team.name();
                html.append("<div class='side-row fm-side-stage-").append(Math.min(3, team.index() + 1)).append(" ").append(team.index() == selectedTeam ? "active" : "")
                        .append(settings.uiAnimations() && team.index() == pendingInsertedTeamIndex ? " fm-team-insert" : "")
                        .append(settings.uiAnimations() && team.index() == pendingAutoJoinMotionTeam ? " fm-auto-join-change" : "")
                        .append("' data-action='team:").append(team.index()).append("' data-team-index='").append(team.index())
                        .append("'><b>").append(twoDigits(team.number())).append("</b><span>").append(teamNameMarkup(name))
                        .append("<small>").append(team.uuids().size()).append(" / 6</small></span></div>");
            }
            html.append("</div>");
            if (!teams.isEmpty()) {
                html.append("<div id='findme-team-selection-marker' class='team-selection-marker fm-side-stage-2'></div>");
            }
            if (teams.size() * 27 > 126) {
                html.append("<div id='findme-team-scroll-rail' class='team-scroll-rail fm-side-stage-3'><div id='findme-team-scroll-thumb' class='team-scroll-thumb'></div></div>");
            }
            html.append(button("create-team", "+", "new-team fm-side-stage-3"));
        } else if (view == View.WAREHOUSE) {
            int all = allCards().size();
            if (backupWarehousePreview) {
                html.append(sideCount("fm-side-stage-1 active", all, tr("screen.find_me.aui.backups"),
                        tr("screen.find_me.doctor.caption.snapshot"), "doctor-view-backups"));
            } else {
                Set<UUID> assigned = assignedUuids();
                int assignedCount = (int) allCards().stream().filter(card -> assigned.contains(card.uuid())).count();
                int unassignedCount = all - assignedCount;
                html.append(sideCount("fm-side-stage-1 " + (warehouseFilter == WarehouseFilter.ALL ? "active" : ""), all, tr("screen.find_me.all"), "ALL", "warehouse-filter:all"));
                html.append(sideCount("fm-side-stage-2 " + (warehouseFilter == WarehouseFilter.ASSIGNED ? "active" : ""), assignedCount, tr("screen.find_me.aui.assigned"), "ASSIGNED", "warehouse-filter:assigned"));
                html.append(sideCount("fm-side-stage-3 " + (warehouseFilter == WarehouseFilter.UNASSIGNED ? "active" : ""), unassignedCount, tr("screen.find_me.aui.unassigned"), "UNASSIGNED", "warehouse-filter:unassigned"));
            }
            html.append(filterSelectionMarker(warehouseFilter.ordinal()));
        } else if (view == View.DEAD) {
            int all = deadCards(DeadFilter.ALL).size();
            deadFilter = DeadFilter.ALL;
            html.append(sideCount("fm-side-stage-1 active", all, tr("screen.find_me.aui.deaths"), "ALL RECORDS", "dead-filter:all"));
            html.append(filterSelectionMarker(0));
        } else {
            int all = recoveryCards().size();
            html.append(sideCount("fm-side-stage-1 active", all, tr("screen.find_me.aui.recovery"), "MISSING DATA", "view:recovery"));
            html.append(filterSelectionMarker(0));
        }
        return html.append("</div></div>").toString();
    }

    private String sideCount(String classes, int count, String label, String subtitle, String action) {
        return "<div class='side-row " + classes + "' data-action='" + action + "'><b class='" + (count >= 100 ? "wide-count" : "") + "'>" + twoDigits(count) + "</b><span><strong>" + escape(label) + "</strong><small>" + subtitle + "</small></span></div>";
    }

    private String filterSelectionMarker(int index) {
        return "<div class='side-filter-selection-marker fm-side-stage-3' style='top:" + (37 + Math.max(0, index) * 27) + "px'></div>";
    }

    private String teamNameMarkup(String name) {
        int availableWidth = Math.max(1, scaled(75) - 29);
        int textWidth = Math.max(1, Math.round(font.width(name) * (12.0f / 9.0f)));
        int shift = Math.max(0, textWidth - availableWidth);
        if (shift <= 0) return "<strong>" + escape(name) + "</strong>";
        int duration = Math.min(2200, 550 + shift * 18);
        String base = "width:" + textWidth + "px;overflow:visible;transform:translateX(0);transition:transform " + duration + "ms ease-in-out";
        return "<strong class='team-name-scroll' data-fm-scroll-base='" + base + "' data-fm-scroll-shift='-" + shift
                + "px' style='" + base + "'>"
                + escape(name) + "</strong>";
    }

    private String workspaceHeader(List<ClientCompanionTeamState.TeamEntry> teams, int count) {
        String label = view == View.TEAM ? teamName(teams, selectedTeam)
                : view == View.WAREHOUSE ? (backupWarehousePreview ? tr("screen.find_me.aui.backups") : tr(warehouseFilter.key))
                : view == View.RECOVERY ? tr("screen.find_me.aui.recovery") : tr(deadFilter.key);
        int number = view == View.TEAM && selectedTeam >= 0 && selectedTeam < teams.size()
                ? teams.get(selectedTeam).number() : count;
        String subtitle = view == View.TEAM ? tr("screen.find_me.aui.team_workspace.secondary")
                : view == View.WAREHOUSE ? (backupWarehousePreview
                        ? tr("screen.find_me.doctor.caption.snapshot") : warehouseFilter.subtitle)
                : view == View.RECOVERY ? "QUARANTINED RECORDS" : deadFilter.subtitle;
        StringBuilder html = new StringBuilder("<div class='workspace-head'><b><span class='workspace-number ").append(number >= 100 ? "wide-count" : "").append("'>").append(twoDigits(number)).append("</span></b><span><strong>").append(escape(label)).append("</strong><small>").append(subtitle).append("</small></span>");
        if (view == View.TEAM && !teams.isEmpty()) {
            int innerWidth = Math.max(1, width - scaled(75) - scaled(10) - scaled(9));
            int actionLeft = Math.max(0, innerWidth - scaled(57));
            boolean autoJoin = teams.get(selectedTeam).autoJoin();
            html.append("<div class='head-actions' style='left:").append(actionLeft).append("px;top:").append(scaled(4)).append("px'><div class='ui-button auto-join-button ")
                    .append(autoJoin ? "active" : "manual")
                    .append(settings.uiAnimations() && selectedTeam == pendingAutoJoinMotionTeam ? " fm-auto-join-change" : "")
                    .append("' data-action='toggle-selected-auto-join'>")
                    .append(escape(tr(autoJoin ? "screen.find_me.aui.auto_join" : "screen.find_me.aui.manual_join")))
                    .append("</div><div class='ui-button more-button' data-action='open-team-menu'>•••</div></div>");
        } else if (view != View.WAREHOUSE) {
            html.append("<input id='findme-search' class='search' type='text' placeholder='").append(escape(tr("screen.find_me.search"))).append("' value='").append(escape(search)).append("'>").append(button("apply-search", tr("screen.find_me.aui.go"), "search-button"));
        }
        return html.append("</div>").toString();
    }

    private String warehouseMarkup(List<Card> visibleCards) {
        int innerWidth = Math.max(1, width - scaled(75) - scaled(10) - scaled(9));
        int cardWidth = scaled(78);
        int cardHeight = scaled(72);
        int columnGap = Math.max(scaled(3), (innerWidth - cardWidth * 4) / 3);
        int rowCount = (visibleCards.size() + 3) / 4;
        StringBuilder html = new StringBuilder("<div class='warehouse-grid-shell'><div id='findme-warehouse-grid' class='warehouse-grid'>");
        for (int i = 0; i < visibleCards.size(); i++) {
            int column = i % 4;
            int row = i / 4;
            if (column == 0) {
                html.append("<div class='warehouse-grid-row' style='width:").append(innerWidth)
                        .append("px;height:").append(cardHeight).append("px")
                        .append(row < rowCount - 1 ? ";margin-bottom:" + scaled(4) + "px" : "")
                        .append("'>");
            }
            Card card = visibleCards.get(i);
            String team = teamContaining(card.uuid());
            boolean snapshot = backupWarehousePreview;
            html.append("<div class='warehouse-card").append(card.uuid().equals(selectedUuid) ? " selected" : "")
                    .append(snapshot ? " backup-snapshot-card" : "")
                    .append(snapshot && !card.alive ? " backup-dead-card" : "")
                    .append("'");
            if (!snapshot) {
                html.append(" data-action='select-warehouse-card' data-value='").append(card.uuid()).append("'");
            }
            html.append(" data-uuid='").append(card.uuid()).append("' style='")
                    .append(column < 3 && i < visibleCards.size() - 1 ? "margin-right:" + columnGap + "px" : "")
                    .append("'>")
                    .append("<div class='warehouse-preview-clip'><findme-preview data-uuid='").append(card.uuid())
                    .append("' data-entity-type='").append(escape(card.type)).append("' data-preview-type='").append(escape(card.type)).append("'")
                    .append(" data-preview-name='").append(escape(card.name)).append("'");
            if (snapshot && card.previewEntry != null && card.previewEntry.previewTag() != null) {
                html.append(" data-preview-nbt='").append(escape(card.previewEntry.previewTag().toString())).append("'");
            }
            html.append(" data-preview-scale='0.68' data-preview-overlay='warehouse' data-preview-team='")
                    .append(escape(team));
            if (snapshot) {
                html.append("' data-preview-number='").append(twoDigits(i + 1));
                if (!card.alive) {
                    html.append("' data-preview-status-tone='death' data-preview-status='")
                            .append(escape(tr("screen.find_me.aui.death_record")));
                }
            }
            html.append("'></findme-preview></div>");
            if (card.active()) {
                html.append("<em>").append(escape(tr("screen.find_me.manage.state_deployed"))).append("</em>");
            }
            html.append("</div>");
            if (column == 3 || i == visibleCards.size() - 1) {
                html.append("</div>");
            }
        }
        html.append("</div>");
        if (visibleCards.isEmpty()) {
            html.append("<div class='warehouse-empty'><b>00</b><span>")
                    .append(escape(tr(backupWarehousePreview
                            ? "screen.find_me.doctor.message.backup_no_entries"
                            : "screen.find_me.manage.no_creature")))
                    .append("</span></div>");
        }
        if (visibleCards.size() > 8) {
            html.append("<div id='findme-warehouse-scroll-rail' class='warehouse-scroll-rail'><div id='findme-warehouse-scroll-thumb' class='warehouse-scroll-thumb'></div></div>");
        }
        html.append("</div><div class='warehouse-footer'><input id='findme-search' class='warehouse-search' type='text' placeholder='")
                .append(escape(tr("screen.find_me.search"))).append("' value='").append(escape(search)).append("'>")
                .append(button("apply-search", tr("screen.find_me.aui.go"), "warehouse-search-button"));
        if (warehouseSelectionMode) {
            boolean canConfirm = selectedUuid != null && canAssignToTeam(selectedUuid, selectedTeam);
            if (canConfirm) {
                html.append(button("confirm-warehouse-selection", tr("screen.find_me.aui.confirm_add"), "warehouse-confirm primary"));
            } else {
                html.append("<div class='warehouse-confirm disabled'>").append(escape(tr("screen.find_me.aui.confirm_add"))).append("</div>");
            }
        }
        return html.append("</div>").toString();
    }

    private String rosterMarkup(List<Card> visibleCards) {
        int rosterHeight = scaled(141);
        StringBuilder html = new StringBuilder("<div id='findme-roster-strip' class='roster-strip' style='height:").append(rosterHeight)
                .append("px;flex:0 0 ").append(rosterHeight).append("px;gap:0'>")
                .append(rosterCardsMarkup(visibleCards));
        int hintInnerWidth = Math.max(1, width - scaled(75) - scaled(10) - scaled(9));
        int countLeft = Math.max(0, hintInnerWidth - scaled(28));
        html.append("</div><div class='hintbar'><div class='hint-actions'><div class='hint-item'>").append(mouseIconMarkup("left-click")).append("<span>").append(escape(tr("screen.find_me.details"))).append("</span></div><div class='hint-item'>").append(mouseIconMarkup("right-click")).append("<span>").append(escape(tr("screen.find_me.aui.more_actions"))).append("</span></div></div><div class='hint-count' style='left:").append(countLeft).append("px'><span>").append(twoDigits(visibleCards.size())).append("</span><span class='hint-count-total'>/ ").append(view == View.TEAM ? "06" : twoDigits(allCards().size())).append("</span></div></div>");
        return html.toString();
    }

    private String mouseIconMarkup(String clickClass) {
        return "<div class='mouse-icon " + clickClass + "'><i class='mouse-button mouse-left'></i>"
                + "<i class='mouse-button mouse-right'></i><i class='mouse-divider'></i>"
                + "<i class='mouse-wheel'></i></div>";
    }

    private String rosterCardsMarkup(List<Card> visibleCards) {
        int compactWidth = scaled(36);
        int cardGap = scaled(3);
        String compactBaseStyle = "width:" + compactWidth + "px;min-width:" + compactWidth + "px;flex:0 0 " + compactWidth + "px;";
        StringBuilder html = new StringBuilder();
        List<UUID> teamMembers = view == View.TEAM ? ClientCompanionTeamState.members(target(), selectedTeam) : List.of();
        for (int i = 0; i < visibleCards.size(); i++) {
            Card card = visibleCards.get(i);
            int memberIndex = view == View.TEAM ? teamMembers.indexOf(card.uuid) : i;
            String offsetStyle = i == 0 ? "" : "margin-left:" + cardGap + "px;";
            html.append(cardMarkup(card, memberIndex >= 0 ? memberIndex : i, compactBaseStyle + offsetStyle, offsetStyle));
        }
        if (view == View.TEAM) {
            for (int i = visibleCards.size(); i < com.kuzhi.findme.common.FindMeUiSettings.TEAM_CAPACITY; i++) {
                String offsetStyle = i == 0 ? "" : "margin-left:" + cardGap + "px;";
                html.append("<div class='member-card compact empty-slot' style='").append(compactBaseStyle).append(offsetStyle)
                        .append("' data-action='open-team-warehouse'><b>+</b><span>").append(escape(tr("screen.find_me.aui.add_to_team")))
                        .append("</span><small>").append(twoDigits(i + 1)).append(" / EMPTY</small></div>");
            }
        }
        if (visibleCards.isEmpty() && view != View.TEAM) {
            html.append("<div class='empty-page'><b>00</b><span>").append(escape(tr("screen.find_me.aui.no_records")))
                    .append("<small>").append(escape(tr(category.key))).append(" / ").append(view == View.WAREHOUSE ? "STORAGE" : "ARCHIVE").append("</small></span></div>");
        }
        return html.toString();
    }

    private String layoutStyle() {
        int systemLeft = scaled(88);
        int systemWidth = Math.max(1, width - systemLeft - scaled(12));
        int settingsGap = scaled(5);
        int settingsScrollGutter = scaled(8);
        int settingCardWidth = Math.max(1,
                (systemWidth - settingsGap - settingsScrollGutter) / 2);
        int doctorLeft = scaled(84);
        int doctorWidth = Math.max(1, width - doctorLeft - scaled(10));
        int profileLeft = scaled(84);
        int profileRight = scaled(10);
        int profileGap = scaled(8);
        int profileAvailable = Math.max(2, width - profileLeft - profileRight - profileGap);
        int profileModelWidth = Math.max(1, (int)Math.round(profileAvailable * 0.55));
        int profileDataLeft = profileLeft + profileModelWidth + profileGap;
        int profileDataWidth = Math.max(1, width - profileDataLeft - profileRight);
        int profileModelHeight = Math.max(1, height - scaled(43) - scaled(12));
        int profileFactLabelWidth = Math.max(1, (int)Math.round(profileDataWidth * 0.42));
        int profileFactValueLeft = Math.min(profileDataWidth - 1,
                profileFactLabelWidth + Math.max(4, scaled(3)));
        int profileFactValueWidth = Math.max(1, profileDataWidth - profileFactValueLeft);
        return "height:" + Math.max(1, height) + "px"
                + ";--fm-top:" + scaled(35) + "px"
                + ";--fm-body:" + Math.max(1, height - scaled(35)) + "px"
                + ";--fm-side:" + scaled(75) + "px"
                + ";--fm-fold-left:" + Math.max(0, scaled(75) - 10) + "px"
                + ";--fm-fold-dark-top:" + Math.max(0, scaled(35) - 5) + "px"
                + ";--fm-fold-light-top:" + scaled(39) + "px"
                + ";--fm-team-scroll-left:" + Math.max(1, scaled(75) - 5) + "px"
                + ";--fm-work-pad-top:" + scaled(8) + "px"
                + ";--fm-work-pad-right:" + scaled(9) + "px"
                + ";--fm-work-pad-left:" + scaled(10) + "px"
                + ";--fm-head:" + scaled(28) + "px"
                + ";--fm-selected:" + scaled(136) + "px"
                + ";--fm-card:" + scaled(141) + "px"
                + ";--fm-preview:" + scaled(121) + "px"
                + ";--fm-selected-copy-left:" + scaled(8) + "px"
                + ";--fm-selected-copy-right:" + scaled(5) + "px"
                + ";--fm-selected-copy-bottom:" + scaled(25) + "px"
                + ";--fm-selected-actions-bottom:" + scaled(6) + "px"
                + ";--fm-selected-spell-bottom:" + scaled(24) + "px"
                + ";--fm-selected-spell-width:" + scaled(122) + "px"
                + ";--fm-compact:" + scaled(36) + "px"
                + ";--fm-gap:" + scaled(3) + "px"
                + ";--fm-stripe-top:" + scaled(48) + "px"
                + ";--fm-stripe-height:" + scaled(15) + "px"
                + ";--fm-hint:" + scaled(29) + "px"
                + ";--fm-warehouse-card-width:" + scaled(78) + "px"
                + ";--fm-warehouse-card-height:" + scaled(72) + "px"
                + ";--fm-warehouse-grid:" + scaled(148) + "px"
                + ";--fm-warehouse-gap:" + scaled(4) + "px"
                + ";--fm-warehouse-footer:" + scaled(21) + "px"
                + ";--fm-system-left:" + systemLeft + "px"
                + ";--fm-system-width:" + systemWidth + "px"
                + ";--fm-setting-card:" + settingCardWidth + "px"
                + ";--fm-content-top:" + scaled(43) + "px"
                + ";--fm-content-height:" + Math.max(1, height - scaled(43) - scaled(8)) + "px"
                + ";--fm-doctor-left:" + doctorLeft + "px"
                + ";--fm-doctor-width:" + doctorWidth + "px"
                + ";--fm-doctor-backup-list-height:" + Math.max(1, height - scaled(43) - scaled(8) - scaled(39)) + "px"
                + ";--fm-profile-model-left:" + profileLeft + "px"
                + ";--fm-profile-model-top:" + scaled(43) + "px"
                + ";--fm-profile-model-width:" + profileModelWidth + "px"
                + ";--fm-profile-model-height:" + profileModelHeight + "px"
                + ";--fm-profile-data-left:" + profileDataLeft + "px"
                + ";--fm-profile-data-top:" + scaled(43) + "px"
                + ";--fm-profile-data-width:" + profileDataWidth + "px"
                + ";--fm-profile-body-height:" + Math.max(1, profileModelHeight - 52) + "px"
                + ";--fm-profile-actions-top:" + Math.max(0, profileModelHeight - 24) + "px"
                + ";--fm-profile-fact-label-width:" + profileFactLabelWidth + "px"
                + ";--fm-profile-fact-value-left:" + profileFactValueLeft + "px"
                + ";--fm-profile-fact-value-width:" + profileFactValueWidth + "px"
                + ";--fm-death-list:" + scaled(60) + "px"
                + ";--fm-memorial-preview:" + scaled(112) + "px"
                + ";--fm-memorial-copy-left:" + scaled(94) + "px";
    }

    private int scaled(int base) {
        double scale = Math.min(Math.max(1, width) / 427.0, Math.max(1, height) / 240.0);
        return Math.max(1, (int) Math.round(base * scale));
    }

    private String cardMarkup(Card card, int index, String compactStyle, String offsetStyle) {
        String state = card.active ? "active" : card.alive ? "stored" : "dead";
        boolean selected = card.uuid.equals(expandedUuid);
        String insertion = settings.uiAnimations() && view == View.TEAM && card.uuid.equals(pendingInsertedMemberUuid) ? " fm-member-insert" : "";
        int accentIndex = index == 0 ? 3 : Math.floorMod(index - 1, 5);
        String accent = " accent-" + accentIndex;
        String themeStyle = cardThemeStyle(card);
        String primary = view == View.WAREHOUSE
                ? button("add-current-team", tr("screen.find_me.aui.add_to_team"), "primary selected-action").replace("data-action='add-current-team'", "data-action='add-current-team' data-value='" + card.uuid + "'")
                : button("activate-card", tr(card.active ? "screen.find_me.store" : "screen.find_me.summon"), "primary selected-action").replace("data-action='activate-card'", "data-action='activate-card' data-value='" + card.uuid + "'");
        String deployed = card.active ? tr("screen.find_me.manage.state_deployed") : "";
        String spellSlot = card.alive && category != Category.VEHICLE && spellUiAvailable(card)
                ? spellSlotsMarkup(card, card.uuid, "selected-spell-slots") : "";
        return "<div class='member-card " + (selected ? "selected-card " : "compact ") + state
                + (selected ? "" : accent) + insertion + "' style='" + (selected ? offsetStyle : compactStyle)
                + themeStyle + "' data-uuid='" + card.uuid + "' data-member-index='" + index
                + "' data-action='select-card' data-value='" + card.uuid + "'>"
                + "<div class='compact-pane'><span class='member-number'>" + twoDigits(index + 1)
                + "</span><div class='compact-info'><span class='compact-name'>" + escape(card.name)
                + "</span><span class='compact-state'>" + escape(stateLabel(card)) + "</span></div></div>"
                + "<findme-preview data-uuid='" + card.uuid + "' data-preview-overlay='expanded' data-preview-type='" + escape(card.type)
                + "' data-preview-team='" + escape(stateLabel(card)) + " &#183; " + escape(tr(category.key))
                + "' data-preview-number='" + twoDigits(index + 1) + "' data-preview-status='" + escape(deployed) + "'></findme-preview>"
                + spellSlot
                + "<div class='selected-actions'>" + primary + button("open-detail", tr("screen.find_me.details"), "selected-action detail").replace("data-action='open-detail'", "data-action='open-detail' data-value='" + card.uuid + "'") + "</div></div>";
    }

    private String detailPageMarkup() {
        Card card = selectedUuid == null ? null : findCard(selectedUuid);
        if (card == null) return "<div class='detail-empty'>" + escape(tr("screen.find_me.aui.no_records")) + "</div>";
        CompanionListPacket.Entry entry = card.previewEntry;
        float health = entry == null ? 0.0f : entry.health();
        float maxHealth = entry == null ? 0.0f : entry.maxHealth();
        float armor = entry == null ? 0.0f : entry.armor();
        String movement = category == Category.VEHICLE ? "VEHICLE" : card.moveType.name();
        String sourceMod = card.type.contains(":") ? card.type.substring(0, card.type.indexOf(':')) : "minecraft";
        String home = entry != null && entry.hasHome() ? (entry.homeResident() ? "RESIDENT" : "ASSIGNED") : "NONE";
        String tactical = entry == null || entry.tacticalAction() == null ? "NONE" : entry.tacticalAction().name();
        String team = teamContaining(card.uuid);
        detailSection = Math.max(0, Math.min(1, detailSection));
        String[] tabKeys = {"screen.find_me.aui.profile.overview", "screen.find_me.aui.profile.status"};
        String[] tabLabels = {"OVERVIEW", "STATUS"};
        StringBuilder html = new StringBuilder("<div class='profile-sidebar'><div class='profile-sidebar-sheet'></div><div class='profile-sidebar-content'>")
                .append("<div class='profile-side-heading profile-side-stage-0'><small>DOSSIER</small><strong>")
                .append(escape(tr("screen.find_me.details"))).append("</strong></div><div class='profile-tabs'>");
        for (int i = 0; i < tabKeys.length; i++) {
            html.append("<div class='profile-side-stage-").append(Math.min(3, i + 1)).append(" ")
                    .append(i == detailSection ? "active" : "").append("' data-action='detail-page:").append(i)
                    .append("'><i></i><b>").append(twoDigits(i + 1)).append("</b><span><strong>")
                    .append(escape(tr(tabKeys[i]))).append("</strong><small>").append(tabLabels[i]).append("</small></span></div>");
        }
        html.append("</div></div></div>");
        html.append("<div class='profile-model'><findme-preview data-uuid='").append(card.uuid)
                .append("' data-interaction-id='manage-detail' data-preview-overlay='profile' data-preview-type='")
                .append(escape(card.type)).append("' data-preview-team='")
                .append(escape(tr(category.key))).append(" / ").append(escape(movement))
                .append("'></findme-preview></div>");
        html.append("<div class='profile-data'><div class='profile-state'><b>").append(escape(stateLabel(card)))
                .append("</b><span>").append(escape(tactical.equals("NONE") ? home : tactical)).append("</span></div><div class='profile-body'>");
        if (detailSection == 0) {
            if (maxHealth > 0.0f) html.append(metric("HEALTH", tr("screen.find_me.aui.profile_health"), Math.round(health) + " / " + Math.round(maxHealth), maxHealth <= 0.0f ? 0 : Math.round(health / maxHealth * 100.0f)));
            if (armor > 0.0f) html.append(metric("ARMOR", tr("screen.find_me.aui.profile_armor"), Integer.toString(Math.round(armor)), Math.min(100, Math.round(armor * 4.0f))));
            html.append(profileFact("MOVEMENT", tr("screen.find_me.pack_movement"), movement));
            html.append("<div class='profile-facts'><div><small>TEAM</small><b>").append(escape(team))
                    .append("</b></div><div><small>HOME</small><b>").append(escape(home))
                    .append("</b></div><div><small>ORDER</small><b>").append(escape(tactical))
                    .append("</b></div><div><small>TYPE</small><b>").append(escape(tr(category.key))).append("</b></div></div>");
        } else {
            html.append("<div class='profile-status-list'>")
                    .append(profileFact("SOURCE MOD", "Source", sourceMod))
                    .append(profileFact("TEAM", tr("screen.find_me.teams"), team))
                    .append(profileFact("ENTITY TYPE", tr("screen.find_me.aui.profile.entity_type"), card.type))
                    .append(profileFact("LIFECYCLE", tr("screen.find_me.aui.profile.status"), stateLabel(card)))
                    .append(profileFact("HOME", "Home assignment", home))
                    .append(profileFact("UUID", "UUID", card.uuid.toString())).append("</div>");
        }
        String contextual = returnView == View.WAREHOUSE && warehouseSelectionMode
                ? button("add-current-team", tr("screen.find_me.aui.add_to_team"), "primary")
                        .replace("data-action='add-current-team'", "data-action='add-current-team' data-value='" + card.uuid + "'")
                : "";
        html.append("</div><div class='profile-actions'>").append(contextual)
                .append(button("rename", tr("screen.find_me.aui.rename"), "").replace("data-action='rename'", "data-action='rename' data-value='" + card.uuid + "'"))
                .append(button("ask-release", tr("screen.find_me.aui.release_binding"), "danger"))
                .append("</div></div>");
        return html.toString();
    }

    private String metric(String english, String label, String value, int percent) {
        int dataWidth = profileDataWidth();
        int labelWidth = profileFactLabelWidth(dataWidth);
        int valueLeft = profileFactValueLeft(dataWidth, labelWidth);
        int valueWidth = Math.max(1, dataWidth - valueLeft);
        return "<div class='profile-metric " + english.toLowerCase(Locale.ROOT) + "' style='width:" + dataWidth + "px'>"
                + "<b class='profile-metric-label' style='left:0;top:3px;width:" + labelWidth + "px'>" + escape(label) + "</b>"
                + "<small class='profile-metric-english' style='left:0;top:15px;width:" + labelWidth + "px'>" + english + "</small>"
                + "<strong style='left:" + valueLeft + "px;top:7px;width:" + valueWidth + "px'>" + escape(value) + "</strong>"
                + "<i><u style='width:" + Math.max(0, Math.min(100, percent)) + "%'></u></i></div>";
    }

    private String profileFact(String english, String label, String value) {
        int dataWidth = profileDataWidth();
        int labelWidth = profileFactLabelWidth(dataWidth);
        int valueLeft = profileFactValueLeft(dataWidth, labelWidth);
        int valueWidth = Math.max(1, dataWidth - valueLeft);
        return "<div class='profile-fact-row' style='width:" + dataWidth + "px'>"
                + "<small class='profile-fact-english' style='left:0;top:4px;width:" + labelWidth + "px'>" + escape(english) + "</small>"
                + "<b class='profile-fact-label' style='left:0;top:15px;width:" + labelWidth + "px'>" + escape(label) + "</b>"
                + "<strong style='left:" + valueLeft + "px;top:15px;width:" + valueWidth + "px'>" + escape(value) + "</strong></div>";
    }

    private int profileDataWidth() {
        int profileLeft = scaled(84);
        int profileRight = scaled(10);
        int profileGap = scaled(8);
        int available = Math.max(2, width - profileLeft - profileRight - profileGap);
        int modelWidth = Math.max(1, (int)Math.round(available * 0.55));
        return Math.max(1, width - (profileLeft + modelWidth + profileGap) - profileRight);
    }

    private int profileFactLabelWidth(int dataWidth) {
        return Math.max(1, (int)Math.round(dataWidth * 0.42));
    }

    private int profileFactValueLeft(int dataWidth, int labelWidth) {
        return Math.min(dataWidth - 1, labelWidth + Math.max(4, scaled(3)));
    }

    private String deathGridMarkup(List<Card> visibleCards) {
        boolean recovery = view == View.RECOVERY;
        int innerWidth = Math.max(1, width - scaled(75) - scaled(10) - scaled(9));
        int cardWidth = scaled(78);
        int cardHeight = scaled(72);
        int columnGap = Math.max(scaled(3), (innerWidth - cardWidth * 4) / 3);
        int rowCount = Math.max(1, (visibleCards.size() + 3) / 4);
        StringBuilder html = new StringBuilder("<div class='death-grid-shell'><div id='findme-warehouse-grid' class='death-grid'>");
        for (int i = 0; i < visibleCards.size(); i++) {
            Card card = visibleCards.get(i);
            int column = i % 4;
            int row = i / 4;
            if (column == 0) {
                html.append("<div class='death-grid-row' style='width:").append(innerWidth).append("px;height:").append(cardHeight).append("px");
                if (row < rowCount - 1) html.append(";margin-bottom:").append(scaled(4)).append("px");
                html.append("'>");
            }
            html.append("<div class='death-card ").append(recovery ? "recovery-card " : "")
                    .append(card.uuid.equals(selectedUuid) ? "selected" : "")
                    .append("' data-action='select-card' data-value='").append(card.uuid).append("' data-uuid='").append(card.uuid)
                    .append("' style='");
            if (column < 3) html.append("margin-right:").append(columnGap).append("px");
            html.append("'><findme-preview data-uuid='").append(card.uuid)
                    .append("' data-preview-overlay='warehouse' data-preview-team='").append(escape(card.type))
                     .append("' data-preview-number='").append(twoDigits(i + 1)).append("' data-preview-status-tone='")
                     .append(recovery ? "recovery" : "death").append("' data-preview-status='")
                    .append(escape(tr(recovery ? "screen.find_me.aui.recovery_record" : "screen.find_me.aui.death_record")))
                    .append("'></findme-preview></div>");
            if (column == 3 || i == visibleCards.size() - 1) html.append("</div>");
        }
        if (visibleCards.isEmpty()) {
            html.append("<div class='death-empty'><b>00</b><span>")
                    .append(escape(tr("screen.find_me.aui.no_records"))).append("</span></div>");
        }
        html.append("</div>");
        if (visibleCards.size() > 8) {
            html.append("<div id='findme-warehouse-scroll-rail' class='warehouse-scroll-rail'><div id='findme-warehouse-scroll-thumb' class='warehouse-scroll-thumb'></div></div>");
        }
        int hintInnerWidth = Math.max(1, width - scaled(75) - scaled(10) - scaled(9));
        int countLeft = Math.max(0, hintInnerWidth - scaled(28));
        return html.append("</div><div class='hintbar'><div class='hint-actions'><div class='hint-item'><span>")
                .append(escape(tr("screen.find_me.details"))).append("</span></div><div class='hint-item'><span>")
                .append(escape(tr(recovery ? "screen.find_me.aui.recovery_hint" : "screen.find_me.aui.permanent_records"))).append("</span></div></div><div class='hint-count' style='left:")
                .append(countLeft).append("px'><span>").append(twoDigits(visibleCards.size()))
                .append("</span><span class='hint-count-total'>/ ").append(twoDigits(recovery ? recoveryCards().size() : deadCards(DeadFilter.ALL).size()))
                .append("</span></div></div>").toString();
    }

    private String deathMarkup(List<Card> visibleCards) {
        StringBuilder html = new StringBuilder("<div class='death-layout'><div class='death-list'>");
        for (int i = 0; i < visibleCards.size(); i++) {
            Card card = visibleCards.get(i);
            var dead = deadEntry(card.uuid);
            html.append("<div class='death-row ").append(card.uuid.equals(selectedUuid) ? "active" : "").append("' data-action='select-card' data-value='").append(card.uuid).append("' data-uuid='").append(card.uuid).append("'><b>").append(twoDigits(i + 1)).append("</b><span><strong>").append(escape(card.name)).append("</strong><small>").append(escape(dead == null ? card.type : dead.deathCause() + " · " + dead.dimension())).append("</small></span></div>");
        }
        html.append("</div>");
        Card selected = selectedUuid == null ? null : findCard(selectedUuid);
        if (selected != null) {
            var dead = deadEntry(selected.uuid);
            html.append("<div class='memorial'><findme-preview data-uuid='").append(selected.uuid).append("'></findme-preview><span class='memorial-tag'>").append(dead != null && dead.recoverable() ? "RECOVERABLE" : "ARCHIVED").append("</span><div class='memorial-copy'><small>").append(escape(selected.type)).append("</small><h1>").append(escape(selected.name)).append("</h1>");
            if (dead != null) html.append("<p>").append(escape(dead.deathCause())).append("</p><p>").append(escape(dead.dimension())).append(" · ").append(Math.round(dead.x())).append(" / ").append(Math.round(dead.y())).append(" / ").append(Math.round(dead.z())).append("</p>");
            html.append("</div><div class='recovery-note'><b>").append(escape(tr(dead != null && dead.recoverable() ? "screen.find_me.aui.recoverable_sleep" : "screen.find_me.aui.record_only"))).append("</b><span>").append(escape(dead == null ? "" : dead.recoveryRequirements())).append("</span></div>");
            html.append(button("ask-delete-dead", tr("screen.find_me.aui.delete_permanently"), "delete-record").replace("data-action='ask-delete-dead'", "data-action='ask-delete-dead' data-value='" + selected.uuid + "'")).append("</div>");
        } else {
            html.append("<div class='memorial empty-memorial'><span class='memorial-tag'>ARCHIVE / EMPTY</span><div class='empty-memorial-copy'><b>00</b><strong>").append(escape(tr("screen.find_me.aui.no_records"))).append("</strong><small>").append(escape(tr(deadFilter.key))).append("</small></div></div>");
        }
        int hintInnerWidth = Math.max(1, width - scaled(75) - scaled(10) - scaled(9));
        int countLeft = Math.max(0, hintInnerWidth - scaled(28));
        return html.append("</div><div class='hintbar'><div class='hint-actions'><div class='hint-item'>").append(mouseIconMarkup("left-click")).append("<span>")
                .append(escape(tr("screen.find_me.details"))).append("</span></div><div class='hint-item'><span>")
                .append(escape(tr("screen.find_me.aui.permanent_records"))).append("</span></div></div><div class='hint-count' style='left:")
                .append(countLeft).append("px'><span>").append(twoDigits(visibleCards.size()))
                .append("</span><span class='hint-count-total'>/ ").append(twoDigits(deadCards(DeadFilter.ALL).size()))
                .append("</span></div></div>").toString();
    }

    private com.kuzhi.findme.network.DeadCompanionListPacket.Entry deadEntry(UUID uuid) {
        return ClientCompanionState.deadEntries().stream().filter(entry -> entry.uuid().equals(uuid)).findFirst().orElse(null);
    }

    private String teamContaining(UUID uuid) {
        for (var team : teams()) if (team.uuids().contains(uuid)) return team.name().isBlank() ? tr("screen.find_me.team_number", team.number()) : team.name();
        return tr("screen.find_me.aui.unassigned");
    }

    private int teamIndexContaining(UUID uuid) {
        for (var team : teams()) if (team.uuids().contains(uuid)) return team.index();
        return -1;
    }

    private boolean canAssignToTeam(UUID uuid, int teamIndex) {
        if (uuid == null || teamIndex < 0) return false;
        Card card = findCard(uuid);
        List<UUID> members = ClientCompanionTeamState.members(target(), teamIndex);
        return !members.contains(uuid) && members.size() < com.kuzhi.findme.common.FindMeUiSettings.TEAM_CAPACITY;
    }

    private String stateLabel(Card card) {
        if (!card.alive) return tr("screen.find_me.state_dead");
        return card.active ? tr("screen.find_me.manage.state_deployed") : tr("screen.find_me.manage.state_idle");
    }

    private String cardThemeStyle(Card card) {
        if (card.previewEntry == null) return "";
        String fallback = card.moveType == CompanionMoveType.FLY ? "minecraft:parrot"
                : card.moveType == CompanionMoveType.SWIM ? "minecraft:salmon" : "minecraft:horse";
        var entity = new CompanionDetailPreviewRenderer().previewEntityForBounds(card.previewEntry, fallback);
        int color = CompanionCardColorResolver.resolve(card.uuid, entity);
        return String.format(Locale.ROOT, "background-color:#%06X;", color & 0xFFFFFF);
    }

    private String silhouetteMarkup(Card card) {
        String asset = switch (card.moveType) {
            case FLY -> "silhouette_bird.png";
            case SWIM -> "silhouette_fish.png";
            default -> "silhouette_horse.png";
        };
        float scale = 1.0f;
        if (card.previewEntry != null) {
            String fallback = card.moveType == CompanionMoveType.FLY ? "minecraft:parrot"
                    : card.moveType == CompanionMoveType.SWIM ? "minecraft:salmon" : "minecraft:horse";
            var entity = new CompanionDetailPreviewRenderer().previewEntityForBounds(card.previewEntry, fallback);
            if (entity != null) scale = CompanionPreviewScaler.silhouetteScale(card.previewEntry, entity);
        }
        return "<img class='entity-silhouette silhouette-" + asset.substring(11, asset.length() - 4)
                + " movement-" + card.moveType.name().toLowerCase(Locale.ROOT)
                + "' src='cards/" + asset + "' style='transform:scale("
                + String.format(Locale.ROOT, "%.3f", scale) + ")'>";
    }

    private String typographyClasses() {
        return settings.fontFamily().cssClass() + " " + settings.fontSize().cssClass()
                + (settings.reduceBackgroundAnimation() ? "" : " fm-reduce-background")
                + (settings.controlHints() ? "" : " fm-hide-hints");
    }

    private static String twoDigits(int value) {
        return String.format(Locale.ROOT, "%02d", Math.max(0, value));
    }

    private String settingsMarkup() {
        int systemWidth = Math.max(1, width - scaled(88) - scaled(12));
        int contentHeight = Math.max(1, height - scaled(43) - scaled(8));
        int settingsViewportWidth = Math.max(1, systemWidth - 8);
        int settingsViewportHeight = Math.max(1, contentHeight - 59);
        String[] sectionLabelKeys = {
                "screen.find_me.aui.settings.interface_short",
                "screen.find_me.aui.settings.teams_short",
                "screen.find_me.aui.settings.riding_short"};
        String[] sectionKeys = {
                "screen.find_me.aui.settings.interface",
                "screen.find_me.aui.settings.teams",
                "screen.find_me.aui.settings.riding"};
        String[] descriptionKeys = {
                "screen.find_me.aui.settings.interface_note",
                "screen.find_me.aui.settings.teams_note",
                "screen.find_me.aui.settings.riding_note"};
        settingsSection = Math.max(0, Math.min(sectionLabelKeys.length - 1, settingsSection));
        StringBuilder html = new StringBuilder("<div class='system-sidebar'><div class='system-sidebar-sheet'></div><div class='system-sidebar-content'>")
                .append("<div class='system-side-heading system-side-stage-0'><small>")
                .append(escape(tr("screen.find_me.aui.settings.short"))).append("</small><strong>")
                .append(escape(tr("screen.find_me.aui.settings"))).append("</strong></div>");
        for (int i = 0; i < sectionLabelKeys.length; i++) {
            html.append("<div class='system-row system-side-stage-").append(Math.min(3, i + 1)).append(" ")
                    .append(i == settingsSection ? "active" : "").append("' data-action='settings-page:")
                    .append(i).append("'><i></i><b>").append(twoDigits(i + 1)).append("</b><span><strong>")
                    .append(escape(tr(sectionKeys[i]))).append("</strong><small>")
                    .append(escape(tr(sectionLabelKeys[i]))).append("</small></span></div>");
        }
        html.append("</div></div><div class='settings-content'><div class='system-head'><b>").append(twoDigits(settingsSection + 1))
                .append("</b><span style='width:").append(Math.max(1, systemWidth - 96)).append("px'><small>")
                .append(escape(tr("screen.find_me.aui.settings.section_heading", tr(sectionLabelKeys[settingsSection]))))
                .append("</small><strong>")
                .append(escape(tr(sectionKeys[settingsSection]))).append("</strong></span>")
                .append(button("settings-reset", tr("screen.find_me.config_reset"), "settings-reset"));
        html.append("</div><div class='settings-head-note settings-description'>")
                .append(escape(tr(descriptionKeys[settingsSection]))).append("</div><div id='findme-settings-grid-viewport' class='settings-grid-viewport' style='width:")
                .append(settingsViewportWidth).append("px;height:").append(settingsViewportHeight).append("px'>")
                .append("<div id='findme-settings-grid' class='settings-grid' style='width:")
                .append(systemWidth).append("px;height:").append(settingsViewportHeight).append("px'>");
        if (settingsSection == 0) {
            html.append("<div class='settings-pair'>")
                    .append(animationSettingCardMarkup(0))
                    .append(wheelStyleSelectorMarkup(1))
                    .append("</div>");
            html.append("<div class='settings-pair'>")
                    .append(hudVisibilitySettingCardMarkup(2))
                    .append(settingCardMarkup(new String[]{"0", "2", "screen.find_me.aui.setting.operation_sounds", bool(settings.operationSounds())}, 3))
                    .append("</div>");
            html.append("<div class='settings-pair'>")
                    .append(settingCardMarkup(new String[]{"0", "3", "screen.find_me.aui.setting.control_hints", bool(settings.controlHints())}, 4))
                    .append(settingCardMarkup(new String[]{"0", "1", "screen.find_me.aui.setting.reduce_background", bool(settings.reduceBackgroundAnimation())}, 5))
                    .append("</div>");
            html.append("<div class='settings-pair'>")
                    .append(settingCardMarkup(new String[]{"0", "0", "screen.find_me.aui.setting.rotate_models", bool(settings.rotateModels())}, 6))
                    .append(settingCardMarkup(new String[]{"9", "0", "screen.find_me.aui.setting.drag_hold", tr("screen.find_me.aui.milliseconds", settings.dragHoldMillis())}, 7))
                    .append("</div>");
            return finishSettingsMarkup(html);
        }
        if (settingsSection == 1) {
            html.append("<div class='settings-pair'>")
                    .append(settingCardMarkup(new String[]{"1", "0", "screen.find_me.aui.setting.auto_join", bool(settings.autoJoinTeams())}, 0))
                    .append(settingCardMarkup(new String[]{"1", "1", "screen.find_me.aui.setting.auto_create", bool(settings.autoCreateTeams())}, 1))
                    .append("</div><div class='settings-pair'>")
                    .append(settingCardMarkup(new String[]{"1", "2", "screen.find_me.aui.setting.default_team", defaultTeamLabel()}, 2))
                    .append("<div class='setting-card-spacer'></div></div>");
            return finishSettingsMarkup(html);
        }
        html.append("<div class='settings-pair'>")
                .append(ridingCameraSelectorMarkup(0))
                .append(settingCardMarkup(new String[]{"0", "4", "screen.find_me.aui.setting.prefer_native_mount_interaction", bool(settings.preferNativeMountInteraction())}, 1))
                .append("</div><div class='settings-pair'>")
                .append(settingCardMarkup(new String[]{"0", "5", "screen.find_me.aui.setting.auto_promote_ridden_companions", bool(settings.autoPromoteRiddenCompanions())}, 2))
                .append(settingCardMarkup(new String[]{"0", "7", "screen.find_me.aui.setting.hide_ridden_mount_when_looking_down", bool(settings.hideRiddenMountWhenLookingDown()), "screen.find_me.aui.setting.note.hide_ridden_mount_when_looking_down"}, 3))
                .append("</div><div class='settings-pair'>")
                .append(settingCardMarkup(new String[]{"0", "6", "screen.find_me.aui.setting.mount_summon_animations", bool(settings.mountSummonAnimations()), "screen.find_me.aui.setting.note.mount_summon_animations"}, 4))
                .append(summonedOutlineSelectorMarkup(5))
                .append("</div><div class='settings-pair'>")
                .append(bindingAnimationSelectorMarkup(6))
                .append(bindingHistoryResetMarkup(7))
                .append("</div>");
        return finishSettingsMarkup(html);
    }

    private String defaultTeamLabel() {
        for (var team : ClientCompanionTeamState.entries(com.kuzhi.findme.common.CompanionTeamTarget.MOUNT)) {
            if (team.index() == settings.defaultTeamIndex()) {
                return team.name().isBlank() ? tr("screen.find_me.team_number", team.number()) : team.name();
            }
        }
        return tr("screen.find_me.team_number", settings.defaultTeamIndex() + 1);
    }

    private String finishSettingsMarkup(StringBuilder html) {
        return html.append("</div></div><div id='findme-settings-scroll-rail' class='settings-scroll-rail'>")
                .append("<i class='settings-scroll-track'></i><div id='findme-settings-scroll-thumb' class='settings-scroll-thumb'><i class='settings-scroll-thumb-mark'></i></div>")
                .append("</div></div>").toString();
    }

    private String moduleSettingCardMarkup(FindMeModule module, int visibleIndex) {
        boolean configured = ClientFindMeModuleState.configured(module);
        return "<div class='setting-card module-setting-card' data-action='settings-module' data-value='" + module.name()
                + "'><div class='setting-card-index'>" + twoDigits(visibleIndex + 1)
                + "</div><div class='setting-card-copy'><b>" + escape(tr("module.find_me." + module.id()))
                + "</b><small>" + escape(tr("module.find_me." + module.id() + ".note"))
                + "</small></div><div class='setting-card-control'><em>"
                + escape(bool(configured))
                + "</em><i class='setting-toggle " + (configured ? "on" : "") + "'><u></u></i></div></div>";
    }

    private String companionLimitSettingCardMarkup(int visibleIndex) {
        return "<div class='setting-card' data-action='open-settings-choice' data-value='COMPANION_LIMIT'>"
                + "<div class='setting-card-index'>" + twoDigits(visibleIndex + 1)
                + "</div><div class='setting-card-copy'><b>"
                + escape(tr("screen.find_me.aui.setting.companion_deployment_limit"))
                + "</b><small>" + escape(tr("screen.find_me.aui.setting.note.companion_deployment_limit"))
                + "</small></div><div class='setting-card-control choice'><em>"
                + ClientFindMeModuleState.companionDeploymentLimit() + "</em><i>></i></div></div>";
    }

    private String animationSettingCardMarkup(int visibleIndex) {
        return "<div class='setting-card' data-action='settings-ui-animations'><div class='setting-card-index'>"
                + twoDigits(visibleIndex + 1) + "</div><div class='setting-card-copy'><b>"
                + escape(tr("screen.find_me.aui.setting.ui_animations"))
                + "</b><small>" + escape(tr("screen.find_me.aui.setting.note.interface_motion"))
                + "</small></div><div class='setting-card-control'><em>"
                + escape(bool(settings.uiAnimations())) + "</em><i class='setting-toggle "
                + (settings.uiAnimations() ? "on" : "") + "'><u></u></i></div></div>";
    }

    private String hudVisibilitySettingCardMarkup(int visibleIndex) {
        boolean visible = ClientFindMeHudLayout.current().visible();
        return "<div class='setting-card' data-action='settings-hud-toggle'><div class='setting-card-index'>"
                + twoDigits(visibleIndex + 1) + "</div><div class='setting-card-copy'><b>"
                + escape(tr("screen.find_me.aui.setting.hud_visibility")) + "</b><small>"
                + escape(tr("screen.find_me.aui.setting.note.hud_visibility"))
                + "</small></div><div class='setting-card-control'><em>"
                + escape(bool(visible)) + "</em><i class='setting-toggle "
                + (visible ? "on" : "") + "'><u></u></i></div></div>";
    }

    private String wheelStyleSelectorMarkup(int visibleIndex) {
        FindMeWheelStyle style = settings.wheelStyle();
        return "<div class='setting-card wheel-style-card' data-action='open-settings-choice' data-value='WHEEL_STYLE'"
                + "><div class='setting-card-index'>" + twoDigits(visibleIndex + 1)
                + "</div><div class='setting-card-copy'><b>"
                + escape(tr("screen.find_me.aui.setting.wheel_style"))
                + "</b><small>" + escape(tr("screen.find_me.aui.setting.note.wheel_style"))
                + "</small></div><div class='setting-card-control choice'><em>"
                + escape(tr("screen.find_me.aui.wheel_style." + style.name().toLowerCase(Locale.ROOT)))
                + "</em><i>></i></div></div>";
    }

    private String ridingCameraSelectorMarkup(int visibleIndex) {
        FindMeRidingCameraMode mode = settings.ridingCameraMode();
        return "<div class='setting-card' data-action='open-settings-choice' data-value='RIDING_CAMERA'"
                + "><div class='setting-card-index'>" + twoDigits(visibleIndex + 1)
                + "</div><div class='setting-card-copy'><b>"
                + escape(tr("screen.find_me.aui.setting.riding_camera"))
                + "</b><small>" + escape(tr("screen.find_me.aui.setting.note.riding_camera"))
                + "</small></div><div class='setting-card-control choice'><em>"
                + escape(tr("screen.find_me.aui.riding_camera." + mode.name().toLowerCase(Locale.ROOT)))
                + "</em><i>></i></div></div>";
    }

    private String bindingAnimationSelectorMarkup(int visibleIndex) {
        BindingAnimationPolicy policy = settings.bindingAnimationPolicy();
        return "<div class='setting-card' data-action='open-settings-choice' data-value='BINDING_ANIMATION'"
                + "><div class='setting-card-index'>" + twoDigits(visibleIndex + 1)
                + "</div><div class='setting-card-copy'><b>"
                + escape(tr("screen.find_me.aui.setting.binding_animation"))
                + "</b><small>" + escape(tr("screen.find_me.aui.setting.note.binding_cinematic"))
                + "</small></div><div class='setting-card-control choice'><em>"
                + escape(tr("screen.find_me.aui.binding_animation." + policy.name().toLowerCase(Locale.ROOT)))
                + "</em><i>></i></div></div>";
    }

    private String bindingHistoryResetMarkup(int visibleIndex) {
        return "<div class='setting-card' data-action='settings-reset-binding-history'>"
                + "<div class='setting-card-index'>" + twoDigits(visibleIndex + 1)
                + "</div><div class='setting-card-copy'><b>"
                + escape(tr("screen.find_me.aui.setting.binding_history_reset"))
                + "</b><small>" + escape(tr("screen.find_me.aui.setting.note.binding_history"))
                + "</small></div><div class='setting-card-control choice'><em>"
                + escape(tr("screen.find_me.config_reset")) + "</em><i>></i></div></div>";
    }

    private String summonedOutlineSelectorMarkup(int visibleIndex) {
        SummonedOutlineMode mode = settings.summonedOutlineMode();
        return "<div class='setting-card' data-action='open-settings-choice' data-value='SUMMONED_OUTLINE'"
                + "><div class='setting-card-index'>" + twoDigits(visibleIndex + 1)
                + "</div><div class='setting-card-copy'><b>"
                + escape(tr("screen.find_me.aui.setting.summoned_outline"))
                + "</b><small>" + escape(tr("screen.find_me.aui.setting.note.summoned_outline"))
                + "</small></div><div class='setting-card-control choice'><em>"
                + escape(tr("screen.find_me.aui.summoned_outline."
                + mode.name().toLowerCase(Locale.ROOT)))
                + "</em><i>></i></div></div>";
    }

    private String settingCardMarkup(String[] row, int visibleIndex) {
        boolean binary = row[3].equals(bool(true)) || row[3].equals(bool(false));
        SettingsChoiceMenu choice = settingsChoiceFor(row);
        String action = binary ? "settings-toggle" : "open-settings-choice";
        String value = binary ? row[0] + ":" + row[1] : choice.name();
        StringBuilder html = new StringBuilder("<div class='setting-card' data-action='").append(action)
                .append("' data-value='").append(value).append("'><div class='setting-card-index'>")
                .append(twoDigits(visibleIndex + 1)).append("</div><div class='setting-card-copy'><b>")
                .append(escape(tr(row[2]))).append("</b><small>")
                .append(escape(tr(row.length > 4 ? row[4] : "screen.find_me.aui.setting.note.option")))
                .append("</small></div>");
            if (binary) {
            html.append("<div class='setting-card-control'><em>").append(escape(row[3])).append("</em><i class='setting-toggle ")
                    .append(row[3].equals(bool(true)) ? "on" : "").append("'><u></u></i></div>");
            } else {
            html.append("<div class='setting-card-control choice'><em>").append(escape(row[3])).append("</em><i>></i></div>");
            }
        return html.append("</div>").toString();
    }

    private static SettingsChoiceMenu settingsChoiceFor(String[] row) {
        String key = row[0] + ":" + row[1];
        return switch (key) {
            case "9:0" -> SettingsChoiceMenu.DRAG_HOLD;
            case "1:2" -> SettingsChoiceMenu.DEFAULT_TEAM;
            case "2:4" -> SettingsChoiceMenu.NAME_LENGTH;
            case "5:0" -> SettingsChoiceMenu.TEXT_MODE;
            case "6:0" -> SettingsChoiceMenu.FONT_FAMILY;
            case "6:1" -> SettingsChoiceMenu.FONT_SIZE;
            default -> SettingsChoiceMenu.NONE;
        };
    }

    private String doctorMarkup() {
        StringBuilder html = new StringBuilder(doctorSidebarMarkup());
        if (doctor == null) return html.append("<div class='doctor-loading'>")
                .append(button("doctor-refresh", tr("screen.find_me.aui.refresh"), "primary"))
                .append("</div>").toString();
        html.append("<div class='doctor-content'>");
        html.append(doctorView == DoctorView.BACKUP_DETAIL ? doctorBackupDetailMarkup() : doctorBackupsMarkup());
        return html.append("</div>").toString();
    }

    private String doctorSidebarMarkup() {
        return new StringBuilder("<div class='fm-sidebar'><div class='fm-sidebar-sheet'></div><div class='fm-sidebar-content'>")
                .append("<div class='side-heading fm-side-stage-0'><small>")
                .append(escape(tr("screen.find_me.doctor.caption.auto_manual"))).append("</small><strong>")
                .append(escape(tr("screen.find_me.aui.backups"))).append("</strong></div>")
                .append("<div class='side-row fm-side-stage-1 active' data-action='doctor-view-backups'><b>01</b><span><strong>")
                .append(escape(tr("screen.find_me.aui.backups"))).append("</strong><small>")
                .append(escape(tr("screen.find_me.doctor.caption.auto_manual"))).append("</small></span></div><div class='side-spacer'></div>")
                .append("</div></div>").toString();
    }

    private String doctorBackupsMarkup() {
        StringBuilder html = new StringBuilder(doctorHeader("01", "screen.find_me.aui.backups",
                tr("screen.find_me.doctor.caption.auto_manual"),
                button("doctor-backup", tr("screen.find_me.aui.create_backup"), "doctor-create-button")))
                .append("<div id='findme-doctor-backup-records' class='doctor-backup-records'>");
        for (var backup : doctor.backups()) {
            html.append("<div class='doctor-backup-record ").append(backup.checksumValid() ? "valid" : "invalid")
                    .append(backup.manual() ? " manual" : " automatic")
                    .append("' data-action='doctor-backup-open-warehouse' data-value='").append(backup.index())
                    .append("' data-backup-index='").append(backup.index()).append("' data-backup-saved-at='")
                    .append(backup.savedAt()).append("' data-backup-manual='").append(backup.manual()).append("'><b>#")
                    .append(twoDigits(backup.index())).append("</b><span><strong>").append(escape(backupDisplayName(backup)))
                    .append("</strong><small>").append(escape(backupDateLabel(backup)))
                    .append(" / ").append(escape(tr("screen.find_me.doctor.format", backup.formatVersion()))).append("</small></span><em>")
                    .append(escape(tr(backup.checksumValid() ? "screen.find_me.aui.valid" : "screen.find_me.aui.invalid")))
                    .append("</em><i>></i></div>");
        }
        if (doctor.backups().isEmpty()) html.append("<div class='doctor-empty-report'><b>00</b><span>")
                .append(escape(tr("message.find_me.backup_empty"))).append("</span></div>");
        return html.append("</div>").toString();
    }

    private int countDoctorBackupRecords(Element records) {
        if (records == null) return 0;
        int count = 0;
        for (Element child : records.getChildren()) {
            if (child != null && child.hasAttribute("data-backup-index")) count++;
        }
        return count;
    }

    /**
     * The legacy AUI parser can leave a dynamically inserted subtree visible at the parent level while
     * dropping some nested elements from the selector/render tree. Build this small, fixed-format list with
     * the old DOM API when that mismatch is detected.
     */
    private boolean ensureDoctorBackupRecordsDom(Document document) {
        if (document == null || doctor == null || doctorView != DoctorView.BACKUPS) return false;
        Element records = document.getElementById("findme-doctor-backup-records");
        if (records == null) return false;
        int expected = doctor.backups().size();
        int directRecords = countDoctorBackupRecords(records);
        int selectorRecords = document.querySelectorAll(".doctor-backup-record").size();
        int dataRecords = document.querySelectorAll("[data-backup-index]").size();
        if (directRecords == expected && selectorRecords == expected && dataRecords == expected) return false;

        for (Element child : new ArrayList<>(records.getChildren())) {
            records.removeChild(child);
        }
        for (var backup : doctor.backups()) {
            Element record = document.createElement("div");
            record.setClassName("doctor-backup-record " + (backup.checksumValid() ? "valid" : "invalid")
                    + (backup.manual() ? " manual" : " automatic"));
            record.setAttribute("data-action", "doctor-backup-open-warehouse");
            record.setAttribute("data-value", String.valueOf(backup.index()));
            record.setAttribute("data-backup-index", String.valueOf(backup.index()));
            record.setAttribute("data-backup-saved-at", String.valueOf(backup.savedAt()));
            record.setAttribute("data-backup-manual", String.valueOf(backup.manual()));

            Element number = document.createElement("b");
            number.setTextContent("#" + twoDigits(backup.index()));
            record.appendChild(number);

            Element copy = document.createElement("span");
            Element name = document.createElement("strong");
            name.setTextContent(backupDisplayName(backup));
            copy.appendChild(name);
            Element date = document.createElement("small");
            date.setTextContent(backupDateLabel(backup) + " / "
                    + tr("screen.find_me.doctor.format", backup.formatVersion()));
            copy.appendChild(date);
            record.appendChild(copy);

            Element validity = document.createElement("em");
            validity.setTextContent(tr(backup.checksumValid()
                    ? "screen.find_me.aui.valid" : "screen.find_me.aui.invalid"));
            record.appendChild(validity);
            Element arrow = document.createElement("i");
            arrow.setTextContent(">");
            record.appendChild(arrow);
            records.appendChild(record);
        }
        return true;
    }

    private String doctorBackupDetailMarkup() {
        DoctorPagePacket.Backup backup = doctor.backups().stream()
                .filter(value -> value.index() == pendingBackup)
                .findFirst()
                .orElse(null);
        if (backup == null) {
            return doctorHeader("01", "screen.find_me.aui.backups",
                    tr("screen.find_me.doctor.caption.auto_manual"), "")
                    + "<div class='doctor-detail-placeholder'><b>00</b><span>"
                    + escape(tr("message.find_me.backup_empty")) + "</span></div>";
        }
        StringBuilder html = new StringBuilder(doctorHeader("01", "screen.find_me.aui.backups",
                tr("screen.find_me.doctor.caption.auto_manual"),
                button("doctor-view-backups", tr("screen.find_me.aui.back"), "doctor-filter")))
                .append("<div class='doctor-backup-meta'><b>#").append(twoDigits(backup.index())).append("</b><span><strong>")
                .append(escape(backupDisplayName(backup))).append("</strong><small>")
                .append(escape(backup.manual() ? backupDisplayName(backup) : backupReason(backup.reason())))
                .append(" / ")
                .append(escape(backupDateLabel(backup)))
                .append(" / ").append(escape(tr("screen.find_me.doctor.format", backup.formatVersion())))
                .append("</small></span><em class='")
                .append(backup.checksumValid() ? "valid" : "invalid").append("'>")
                .append(escape(tr(backup.checksumValid() ? "screen.find_me.aui.valid" : "screen.find_me.aui.invalid"))).append("</em></div>");
        if (doctorRestoreConfirm) {
            html.append("<div class='doctor-restore-confirm'><span>").append(escape(tr("screen.find_me.doctor.restore_warning")))
                    .append("</span>").append(button("doctor-restore-cancel", tr("screen.find_me.cancel"), "doctor-cancel-button"))
                    .append(button("doctor-restore", tr("screen.find_me.confirm"), "doctor-confirm-button")).append("</div>");
        } else if (!backup.checksumValid()) {
            html.append("<div class='doctor-restore-unavailable'>")
                    .append(escape(tr("screen.find_me.doctor.message.backup_unavailable"))).append("</div>");
        } else {
            html.append(button("doctor-restore-arm", tr("screen.find_me.aui.confirm_restore", pendingBackup), "doctor-restore-open"));
        }
        return html.append("</div>").toString();
    }

    private String doctorHeader(String number, String titleKey, String caption, String action) {
        return "<div class='doctor-head'><b>" + number + "</b><span><small>" + caption + "</small><strong>"
                + escape(tr(titleKey)) + "</strong></span>" + action + "</div>";
    }
























    private String backupReason(String reason) {
        if (reason == null || reason.isBlank()) return tr("screen.find_me.doctor.backup_reason.unknown");
        String normalized = reason.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        String key = "screen.find_me.doctor.backup_reason." + normalized;
        String translated = tr(key);
        return translated.equals(key) ? reason : translated;
    }

    private String backupDisplayName(DoctorPagePacket.Backup backup) {
        if (backup.manual() && (backup.reason().isBlank() || "manual".equalsIgnoreCase(backup.reason())
                || "aui_manual".equalsIgnoreCase(backup.reason()))) {
            return tr("screen.find_me.doctor.manual_backup");
        }
        return backupReason(backup.reason());
    }

    private String backupDateLabel(DoctorPagePacket.Backup backup) {
        long timestamp = backup.createdAtEpochMillis();
        if (timestamp > 0L) {
            String date = BACKUP_DATE_FORMAT.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()));
            return tr("screen.find_me.doctor.saved_at", date);
        }
        return tr("screen.find_me.doctor.legacy_saved_tick", backup.savedAt());
    }

    private String cardActions(Card card) {
        if (!card.alive) return button("ask-delete-dead", tr("screen.find_me.delete"), "ui-button card-action danger").replace("data-action='ask-delete-dead'", "data-action='ask-delete-dead' data-value='" + card.uuid + "'");
        if (view == View.TEAM) return button("remove-team", tr("screen.find_me.remove"), "ui-button card-action").replace("data-action='remove-team'", "data-action='remove-team' data-value='" + card.uuid + "'") + button("rename", tr("screen.find_me.aui.rename"), "ui-button card-action").replace("data-action='rename'", "data-action='rename' data-value='" + card.uuid + "'");
        return button("rename", tr("screen.find_me.aui.rename"), "ui-button card-action").replace("data-action='rename'", "data-action='rename' data-value='" + card.uuid + "'");
    }

    private String detailMarkup(UUID uuid) {
        Card card = findCard(uuid);
        if (card != null && !card.alive) {
            var dead = ClientCompanionState.deadEntries().stream().filter(entry -> entry.uuid().equals(uuid)).findFirst().orElse(null);
            String summary = dead == null ? card.type : dead.deathCause() + " / " + dead.dimension() + " / "
                    + Math.round(dead.x()) + ", " + Math.round(dead.y()) + ", " + Math.round(dead.z());
            String recovery = tr(dead != null && dead.recoverable() ? "screen.find_me.aui.recoverable_sleep" : "screen.find_me.aui.record_only");
            return "<div class='detail death-detail'><div class='detail-name'><span class='menu-title'>" + escape(tr("screen.find_me.aui.death_record")) + "</span><span class='detail-sub'>" + escape(card.name) + "</span></div>"
                    + "<div><span>" + escape(summary) + "</span><span>" + escape(recovery) + "</span></div>"
                    + button("ask-delete-dead", tr("screen.find_me.aui.delete_record"), "ui-button detail-action danger").replace("data-action='ask-delete-dead'", "data-action='ask-delete-dead' data-value='" + uuid + "'") + "</div>";
        }
        String transferButton = "";
        if (card != null && category == Category.MOUNT) {
            transferButton = button("move-companion", tr("screen.find_me.aui.convert_to_companion"), "ui-button detail-action")
                    .replace("data-action='move-companion'", "data-action='move-companion' data-value='" + uuid + "'");
        } else if (card != null && category == Category.COMPANION) {
            transferButton = button("move-mount", tr("screen.find_me.aui.convert_to_mount"), "ui-button detail-action")
                    .replace("data-action='move-mount'", "data-action='move-mount' data-value='" + uuid + "'");
        }
        return "<div class='detail'><div class='detail-name'><span class='menu-title'>" + escape(tr("screen.find_me.select")) + "</span><span class='detail-sub'>" + escape(labelFor(uuid)) + "</span></div>"
                + "<input id='entity-rename-input' class='detail-input' type='text' value='" + escape(labelFor(uuid)) + "' maxlength='64'>"
                + (category == Category.VEHICLE || !spellUiAvailable(card)
                ? "" : spellSlotsMarkup(card, uuid, "detail-spell-slots"))
                + button("save-entity-name", tr("screen.find_me.config_save"), "ui-button detail-action")
                + transferButton + "</div>";
    }

    private String spellSlotsMarkup(Card card, UUID uuid, String extraClass) {
        List<com.kuzhi.findme.api.CompanionSpellBinding> bindings = card == null || card.previewEntry() == null
                ? List.of() : card.previewEntry().spellBindings();
        StringBuilder html = new StringBuilder("<div class='spell-loadout ").append(extraClass)
                .append("'><div class='spell-slot-strip'>");
        for (int slotIndex = 0; slotIndex < 3; slotIndex++) {
            var binding = slotIndex < bindings.size() ? bindings.get(slotIndex) : null;
            String icon = binding == null
                    ? "find_me:textures/particle/contract_glyph.png" : binding.iconResource().toString();
            String label = binding == null ? tr("screen.find_me.spell_slot.empty") : binding.displayName();
            html.append("<div class='spell-slot-frame")
                    .append(binding == null ? " empty" : " bound")
                    .append("' title='").append(escape(label))
                    .append("' data-action='spell-slot-bind' data-value='").append(uuid).append('|').append(slotIndex)
                    .append("'><span class='spell-slot-icon' data-spell-icon='").append(escape(icon)).append("'></span></div>");
        }
        html.append("</div></div>");
        return html.toString();
    }

    private boolean spellUiAvailable(Card card) {
        return com.kuzhi.findme.api.FindMeApi.hasCompanionSpellProviders()
                || card != null && card.previewEntry() != null
                && card.previewEntry().spellBindings().stream().anyMatch(java.util.Objects::nonNull);
    }

    private void appendSpellPicker(StringBuilder html) {
        Card card = findCard(spellPickerUuid);
        String subject = card == null ? "" : card.name;
        html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.spell_slot.choose"))).append("</span>");
        if (!subject.isBlank()) {
            html.append("<div class='spell-picker-subject'>").append(escape(subject)).append("</div>");
        }
        if (spellPickerCandidates.isEmpty()) {
            html.append("<div class='spell-picker-empty'>")
                    .append(escape(tr("screen.find_me.spell_slot.no_candidates"))).append("</div>")
                    .append(button("spell-picker-refresh", tr("screen.find_me.spell_slot.refresh"), "ui-button menu-button"));
        } else {
            html.append("<div class='spell-picker-list'>");
            for (CompanionSpellSlotCandidatesPacket.Entry entry : spellPickerCandidates) {
                boolean selected = entry.inventorySlot() == spellPickerSelectedSlot;
                String level = entry.spellLevel() > 0
                        ? tr("screen.find_me.spell_slot.level", entry.spellLevel()) : "";
                html.append("<div class='ui-button menu-button spell-picker-row")
                        .append(selected ? " active" : "")
                        .append("' data-action='spell-picker-select' data-value='").append(entry.inventorySlot())
                        .append("'><i class='spell-picker-icon' data-spell-icon='").append(escape(entry.iconResource().toString()))
                        .append("'></i><span><b>").append(escape(entry.displayName()))
                        .append("</b><small>").append(escape(level))
                        .append(" / ").append(escape(tr("screen.find_me.spell_slot.role."
                                + entry.role().name().toLowerCase(Locale.ROOT))))
                        .append(entry.count() > 1 ? " x" + entry.count() : "")
                        .append("</small></span></div>");
            }
            html.append("</div>");
            if (spellPickerSelectedSlot >= 0) {
                html.append(button("spell-picker-confirm", tr("screen.find_me.spell_slot.confirm"),
                        "ui-button menu-button active"));
            } else {
                html.append("<div class='ui-button menu-button disabled'>")
                        .append(escape(tr("screen.find_me.spell_slot.confirm"))).append("</div>");
            }
            html.append(button("spell-picker-refresh", tr("screen.find_me.spell_slot.refresh"), "ui-button menu-button"));
        }
        if (card != null && card.previewEntry() != null && spellPickerCompanionSlot >= 0
                && spellPickerCompanionSlot < card.previewEntry().spellBindings().size()
                && card.previewEntry().spellBindings().get(spellPickerCompanionSlot) != null) {
            html.append(button("spell-slot-clear", tr("screen.find_me.spell_slot.clear"), "ui-button menu-button danger")
                    .replace("data-action='spell-slot-clear'",
                            "data-action='spell-slot-clear' data-value='" + spellPickerUuid + "|"
                                    + spellPickerCompanionSlot + "'"));
        }
    }

    private String contextMarkup() {
        StringBuilder html = new StringBuilder("<div id='findme-context-menu' class='context-menu")
                .append(view == View.WAREHOUSE ? " warehouse-context" : "")
                .append(spellPickerUuid != null ? " spell-picker-context" : "")
                .append(" ").append(typographyClasses())
                .append(!settings.uiAnimations() || contextMotionClass.isBlank() ? "" : " " + contextMotionClass)
                .append("'>");
        if (spellPickerUuid != null) {
            appendSpellPicker(html);
        } else if (settingsChoiceMenu != SettingsChoiceMenu.NONE) {
            appendSettingsChoiceMenu(html);
        } else if (contextBackupIndex >= 0 && contextBackupSavedAt > 0L) {
            DoctorPagePacket.Backup backup = doctor == null ? null : doctor.backups().stream()
                    .filter(value -> value.index() == contextBackupIndex && value.savedAt() == contextBackupSavedAt
                            ).findFirst().orElse(null);
            if (pendingDanger.equals("backup")) {
                html.append("<div class='spell-picker-empty backup-warning'>")
                        .append(escape(tr("screen.find_me.doctor.delete_backup_confirm"))).append("</div>")
                        .append(button("doctor-restore-cancel", tr("screen.find_me.cancel"), "ui-button menu-button"))
                        .append(button("doctor-backup-delete", tr("screen.find_me.aui.delete_backup"), "ui-button menu-button danger"));
            } else if (doctorRestoreConfirm && pendingBackup == contextBackupIndex
                    && pendingBackupSavedAt == contextBackupSavedAt && backup != null && backup.checksumValid()) {
                html.append("<div class='spell-picker-empty backup-warning'>")
                        .append(escape(tr("screen.find_me.doctor.restore_warning"))).append("</div>")
                        .append(button("doctor-restore-cancel", tr("screen.find_me.cancel"), "ui-button menu-button"))
                        .append(button("doctor-restore", tr("screen.find_me.confirm"), "ui-button menu-button danger"));
            } else {
                html.append("<span class='menu-title'>")
                        .append(escape(tr("screen.find_me.aui.backup_actions"))).append("</span>");
                if (backup != null && backup.checksumValid()) {
                    html.append(button("doctor-backup-switch", tr("screen.find_me.aui.switch_to_backup"),
                            "ui-button menu-button danger").replace("data-action='doctor-backup-switch'",
                            "data-action='doctor-backup-switch' data-value='" + backup.index() + "|" + backup.savedAt() + "'"));
                }
                if (backup != null) {
                    html.append(button("ask-delete-backup", tr("screen.find_me.aui.delete_backup"),
                            "ui-button menu-button danger"));
                }
                if (backup != null && backup.manual()) {
                    html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.aui.rename"))).append("</span>")
                            .append("<input id='doctor-backup-rename-input' class='menu-input' type='text' value='")
                            .append(escape(backupDisplayName(backup))).append("' maxlength='48'>")
                            .append(button("doctor-backup-rename", tr("screen.find_me.config_save"), "ui-button menu-button"));
                }
            }
        } else if (contextTeam >= 0) {
            if (pendingDanger.equals("team")) {
                html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.aui.delete_team_confirm"))).append("</span>").append(button("confirm-delete-team", tr("screen.find_me.confirm"), "ui-button menu-button danger"));
            } else if (teamMenuPage == TeamMenuPage.RENAME) {
                String currentName = teamName(teams(), contextTeam);
                html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.aui.rename"))).append("</span>")
                        .append("<input id='team-rename-input' class='menu-input' type='text' value='").append(escape(currentName)).append("' maxlength='48'>")
                        .append(button("save-team-name", tr("screen.find_me.config_save"), "ui-button menu-button"))
                        .append(button("team-menu:main", tr("screen.find_me.aui.back"), "ui-button menu-button"));
            } else {
                html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.aui.more_actions"))).append("</span>");
                html.append(button("team-menu:rename", tr("screen.find_me.aui.rename"), "ui-button menu-button"));
                html.append(button("ask-delete-team", tr("screen.find_me.delete"), "ui-button menu-button danger"));
                html.append(button("view:dead", tr("screen.find_me.aui.deaths"), "ui-button menu-button"));
                html.append(button("view:recovery", tr("screen.find_me.aui.recovery"), "ui-button menu-button"));
                html.append(button("view:settings", tr("screen.find_me.aui.settings"), "ui-button menu-button"));
                html.append(button("view:doctor", tr("screen.find_me.aui.backups"), "ui-button menu-button"));
            }
        } else if (selectedUuid != null) {
            Card selected = findCard(selectedUuid);
            if (view == View.WAREHOUSE && selected != null) {
                html.append(warehouseContextMarkup(selected));
            } else if (pendingDanger.equals("recovery")) {
                html.append("<span class='menu-title'>")
                        .append(escape(tr("screen.find_me.aui.delete_recovery_confirm")))
                        .append("</span>")
                        .append(button("confirm-delete-recovery", tr("screen.find_me.aui.confirm_delete"),
                                "ui-button menu-button danger"));
            } else if (view == View.RECOVERY && selected != null) {
                html.append("<span class='menu-title'>")
                        .append(escape(tr("screen.find_me.aui.recovery_record"))).append("</span>");
                html.append(button("retry-recovery", tr("screen.find_me.aui.retry_recovery"),
                        "ui-button menu-button active").replace("data-action='retry-recovery'",
                        "data-action='retry-recovery' data-value='" + selectedUuid + "'"));
                html.append(button("move-recovery-to-dead", tr("screen.find_me.aui.move_recovery_to_dead"),
                        "ui-button menu-button").replace("data-action='move-recovery-to-dead'",
                        "data-action='move-recovery-to-dead' data-value='" + selectedUuid + "'"));
                html.append(button("ask-delete-recovery", tr("screen.find_me.aui.delete_permanently"),
                        "ui-button menu-button danger").replace("data-action='ask-delete-recovery'",
                        "data-action='ask-delete-recovery' data-value='" + selectedUuid + "'"));
            } else if (pendingDanger.equals("dead")) {
                html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.aui.delete_death_confirm"))).append("</span>").append(button("confirm-delete-dead", tr("screen.find_me.aui.confirm_delete"), "ui-button menu-button danger"));
            } else if (selected != null && !selected.alive) {
                html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.aui.death_record"))).append("</span>").append(button("ask-delete-dead", tr("screen.find_me.aui.delete_permanently"), "ui-button menu-button danger").replace("data-action='ask-delete-dead'", "data-action='ask-delete-dead' data-value='" + selectedUuid + "'"));
            } else if (pendingDanger.equals("release")) {
                html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.aui.release_confirm"))).append("</span>").append(button("confirm-release", tr("screen.find_me.confirm"), "ui-button menu-button danger"));
            } else if (warehouseMenuPage == WarehouseMenuPage.RENAME) {
                html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.aui.rename"))).append("</span>")
                        .append("<input id='entity-rename-input' class='menu-input' type='text' value='").append(escape(labelFor(selectedUuid))).append("' maxlength='64'>")
                        .append(button("save-entity-name", tr("screen.find_me.config_save"), "ui-button menu-button"))
                        .append(button("warehouse-menu:main", tr("screen.find_me.aui.back"), "ui-button menu-button"));
            } else {
                html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.aui.card_actions"))).append("</span>");
                html.append(button("warehouse-menu:rename", tr("screen.find_me.aui.rename"), "ui-button menu-button"));
                if (selected != null && category == Category.MOUNT) {
                    html.append(button("move-companion", tr("screen.find_me.aui.convert_to_companion"), "ui-button menu-button")
                            .replace("data-action='move-companion'", "data-action='move-companion' data-value='" + selectedUuid + "'"));
                } else if (selected != null && category == Category.COMPANION) {
                    html.append(button("move-mount", tr("screen.find_me.aui.convert_to_mount"), "ui-button menu-button")
                            .replace("data-action='move-mount'", "data-action='move-mount' data-value='" + selectedUuid + "'"));
                }
                html.append(button("ask-release", tr("screen.find_me.aui.release_binding"), "ui-button menu-button danger"));
                html.append(button("remove-team", tr("screen.find_me.team_remove_member"), "ui-button menu-button").replace("data-action='remove-team'", "data-action='remove-team' data-value='" + selectedUuid + "'"));
            }
        }
        html.append(button("close-context", tr("screen.find_me.cancel"), "ui-button menu-button")).append("</div>");
        return html.toString();
    }

    private void appendSettingsChoiceMenu(StringBuilder html) {
        String titleKey = switch (settingsChoiceMenu) {
            case WHEEL_STYLE -> "screen.find_me.aui.setting.wheel_style";
            case DRAG_HOLD -> "screen.find_me.aui.setting.drag_hold";
            case NAME_LENGTH -> "screen.find_me.aui.setting.name_length";
            case TEXT_MODE -> "screen.find_me.aui.setting.text_mode";
            case FONT_FAMILY -> "screen.find_me.aui.setting.font_family";
            case FONT_SIZE -> "screen.find_me.aui.setting.font_size";
            case RIDING_CAMERA -> "screen.find_me.aui.setting.riding_camera";
            case BINDING_ANIMATION -> "screen.find_me.aui.setting.binding_animation";
            case SUMMONED_OUTLINE -> "screen.find_me.aui.setting.summoned_outline";
            case DEFAULT_TEAM -> "screen.find_me.aui.setting.default_team";
            case COMPANION_LIMIT -> "screen.find_me.aui.setting.companion_deployment_limit";
            case NONE -> "";
        };
        html.append("<span class='menu-title'>").append(escape(tr(titleKey))).append("</span>");
        switch (settingsChoiceMenu) {
            case WHEEL_STYLE -> {
                for (FindMeWheelStyle style : FindMeWheelStyle.values()) {
                    appendSettingsChoiceOption(html,
                            tr("screen.find_me.aui.wheel_style." + style.name().toLowerCase(Locale.ROOT)),
                            style.name(), settings.wheelStyle() == style);
                }
            }
            case DRAG_HOLD -> {
                for (int millis = 150; millis <= 600; millis += 50) {
                    appendSettingsChoiceOption(html, tr("screen.find_me.aui.milliseconds", millis),
                            Integer.toString(millis), settings.dragHoldMillis() == millis);
                }
            }
            case NAME_LENGTH -> {
                for (int length = 8; length <= 64; length += 8) {
                    appendSettingsChoiceOption(html, Integer.toString(length), Integer.toString(length),
                            settings.nameMaxLength() == length);
                }
            }
            case TEXT_MODE -> {
                for (FindMeTextMode mode : FindMeTextMode.values()) {
                    appendSettingsChoiceOption(html,
                            tr("screen.find_me.aui.text_mode." + mode.name().toLowerCase(Locale.ROOT)),
                            mode.name(), settings.textMode() == mode);
                }
            }
            case FONT_FAMILY -> {
                for (FindMeFontFamily family : FindMeFontFamily.values()) {
                    appendSettingsChoiceOption(html,
                            tr("screen.find_me.aui.font_family." + family.name().toLowerCase(Locale.ROOT)),
                            family.name(), settings.fontFamily() == family);
                }
            }
            case FONT_SIZE -> {
                for (FindMeFontSize size : FindMeFontSize.values()) {
                    appendSettingsChoiceOption(html,
                            tr("screen.find_me.aui.font_size." + size.name().toLowerCase(Locale.ROOT)),
                            size.name(), settings.fontSize() == size);
                }
            }
            case RIDING_CAMERA -> {
                for (FindMeRidingCameraMode mode : FindMeRidingCameraMode.values()) {
                    appendSettingsChoiceOption(html,
                            tr("screen.find_me.aui.riding_camera." + mode.name().toLowerCase(Locale.ROOT)),
                            mode.name(), settings.ridingCameraMode() == mode);
                }
            }
            case BINDING_ANIMATION -> {
                for (BindingAnimationPolicy policy : BindingAnimationPolicy.values()) {
                    if (policy == BindingAnimationPolicy.INHERIT) continue;
                    appendSettingsChoiceOption(html,
                            tr("screen.find_me.aui.binding_animation." + policy.name().toLowerCase(Locale.ROOT)),
                            policy.name(), settings.bindingAnimationPolicy() == policy);
                }
            }
            case SUMMONED_OUTLINE -> {
                for (SummonedOutlineMode mode : SummonedOutlineMode.values()) {
                    appendSettingsChoiceOption(html,
                            tr("screen.find_me.aui.summoned_outline."
                                    + mode.name().toLowerCase(Locale.ROOT)),
                            mode.name(), settings.summonedOutlineMode() == mode);
                }
            }
            case DEFAULT_TEAM -> {
                for (var team : ClientCompanionTeamState.entries(com.kuzhi.findme.common.CompanionTeamTarget.MOUNT)) {
                    String label = team.name().isBlank() ? tr("screen.find_me.team_number", team.number()) : team.name();
                    appendSettingsChoiceOption(html, label, Integer.toString(team.index()),
                            settings.defaultTeamIndex() == team.index());
                }
            }
            case COMPANION_LIMIT -> {
                for (int limit = 1; limit <= 32; limit++) {
                    appendSettingsChoiceOption(html, Integer.toString(limit), Integer.toString(limit),
                            ClientFindMeModuleState.companionDeploymentLimit() == limit);
                }
            }
            case NONE -> { }
        }
    }

    private static void appendSettingsChoiceOption(StringBuilder html, String label, String value, boolean active) {
        html.append("<div class='ui-button menu-button ").append(active ? "active" : "")
                .append("' data-action='settings-choice' data-value='").append(escape(value)).append("'><span>")
                .append(escape(label)).append("</span><small>").append(active ? "|" : "").append("</small></div>");
    }

    private String warehouseContextMarkup(Card selected) {
        StringBuilder html = new StringBuilder();
        if (warehouseMenuPage == WarehouseMenuPage.ANIMATION_STYLE && warehouseAnimationPurpose == null) {
            warehouseMenuPage = WarehouseMenuPage.ANIMATION;
        }
        if (warehouseMenuPage == WarehouseMenuPage.EFFECT_STYLE && warehouseEffectPurpose == null) {
            warehouseMenuPage = WarehouseMenuPage.EFFECT;
        }
        int assignedTeam = teamIndexContaining(selected.uuid());
        switch (warehouseMenuPage) {
            case RENAME -> html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.aui.rename"))).append("</span>")
                    .append("<input id='entity-rename-input' class='menu-input' type='text' value='").append(escape(selected.name())).append("' maxlength='64'>")
                    .append(button("save-entity-name", tr("screen.find_me.config_save"), "ui-button menu-button"))
                    .append(button("warehouse-menu:main", tr("screen.find_me.aui.back"), "ui-button menu-button"));
            case TEAM -> {
                html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.aui.choose_team"))).append("</span><div class='warehouse-team-menu'>");
                for (var team : teams()) {
                    String name = team.name().isBlank() ? tr("screen.find_me.team_number", team.number()) : team.name();
                    boolean full = team.uuids().size() >= com.kuzhi.findme.common.FindMeUiSettings.TEAM_CAPACITY;
                    if (full) {
                        html.append("<div class='menu-button disabled'><span>").append(escape(name)).append("</span><small>").append(team.uuids().size()).append(" / 6</small></div>");
                    } else {
                        html.append("<div class='ui-button menu-button' data-action='warehouse-team:").append(team.index()).append("'><span>")
                                .append(escape(name)).append("</span><small>").append(team.uuids().size()).append(" / 6</small></div>");
                    }
                }
                html.append("</div>").append(button("warehouse-menu:main", tr("screen.find_me.aui.back"), "ui-button menu-button"));
            }
            case PRESENTATION -> html.append("<span class='menu-title'>")
                    .append(escape(tr("screen.find_me.presentation_settings"))).append("</span>")
                    .append(button("warehouse-menu:animation", tr("screen.find_me.animation_settings"), "ui-button menu-button"))
                    .append(button("warehouse-menu:effect", tr("screen.find_me.effect_settings"), "ui-button menu-button"))
                    .append(button("warehouse-menu:main", tr("screen.find_me.aui.back"), "ui-button menu-button"));
            case ANIMATION -> {
                html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.animation_settings"))).append("</span>");
                for (CompanionAnimationPurpose purpose : CompanionAnimationPurpose.values()) {
                    if (category == Category.COMPANION && purpose == CompanionAnimationPurpose.SUMMON) continue;
                    CompanionAnimationStyle currentStyle = animationStyle(selected.uuid(), purpose);
                    html.append("<div class='ui-button menu-button animation-purpose' data-action='warehouse-animation-purpose:").append(purpose.name()).append("'><span>")
                            .append(escape(tr("screen.find_me.animation_purpose_short." + purpose.name().toLowerCase(Locale.ROOT)))).append("</span><small>")
                            .append(escape(tr("screen.find_me.animation_style." + currentStyle.name().toLowerCase(Locale.ROOT)))).append("</small></div>");
                }
                html.append(button("warehouse-menu:presentation", tr("screen.find_me.aui.back"), "ui-button menu-button"));
            }
            case ANIMATION_STYLE -> {
                html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.animation_purpose_short." + warehouseAnimationPurpose.name().toLowerCase(Locale.ROOT)))).append("</span><div class='warehouse-style-viewport'><div class='warehouse-style-menu'>");
                CompanionAnimationStyle active = animationStyle(selected.uuid(), warehouseAnimationPurpose);
                for (CompanionAnimationStyle style : availableAnimations(warehouseAnimationPurpose)) {
                    html.append("<div class='ui-button menu-button ").append(style == active ? "active" : "")
                            .append("' data-action='warehouse-animation-style:").append(style.name()).append("'>")
                            .append(escape(tr("screen.find_me.animation_style." + style.name().toLowerCase(Locale.ROOT)))).append("</div>");
                }
                html.append("</div></div>").append(button("warehouse-menu:animation", tr("screen.find_me.aui.back"), "ui-button menu-button"));
            }
            case EFFECT -> {
                html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.effect_settings"))).append("</span>");
                for (CompanionEffectPurpose purpose : CompanionEffectPurpose.values()) {
                    CompanionEffectStyle currentStyle = effectStyle(selected.uuid(), purpose);
                    html.append("<div class='ui-button menu-button animation-purpose' data-action='warehouse-effect-purpose:").append(purpose.name()).append("'><span>")
                            .append(escape(tr("screen.find_me.effect_purpose_short." + purpose.name().toLowerCase(Locale.ROOT)))).append("</span><small>")
                            .append(escape(tr("screen.find_me.effect_style." + currentStyle.name().toLowerCase(Locale.ROOT)))).append("</small></div>");
                }
                html.append(button("warehouse-menu:presentation", tr("screen.find_me.aui.back"), "ui-button menu-button"));
            }
            case EFFECT_STYLE -> {
                html.append("<span class='menu-title'>").append(escape(tr("screen.find_me.effect_purpose_short." + warehouseEffectPurpose.name().toLowerCase(Locale.ROOT)))).append("</span><div class='warehouse-style-viewport'><div class='warehouse-style-menu'>");
                CompanionEffectStyle active = effectStyle(selected.uuid(), warehouseEffectPurpose);
                for (CompanionEffectStyle style : availableStyles(warehouseEffectPurpose)) {
                    html.append("<div class='ui-button menu-button ").append(style == active ? "active" : "")
                            .append("' data-action='warehouse-effect-style:").append(style.name()).append("'>")
                            .append(escape(tr("screen.find_me.effect_style." + style.name().toLowerCase(Locale.ROOT)))).append("</div>");
                }
                html.append("</div></div>").append(button("warehouse-menu:effect", tr("screen.find_me.aui.back"), "ui-button menu-button"));
            }
            case MAIN -> {
                html.append("<span class='menu-title'>").append(escape(selected.name())).append("</span>")
                        .append(button("warehouse-menu:rename", tr("screen.find_me.aui.rename"), "ui-button menu-button"));
                if (assignedTeam >= 0) {
                    html.append(button("warehouse-leave-team", tr("screen.find_me.aui.leave_team"), "ui-button menu-button"));
                } else {
                    html.append(button("warehouse-menu:team", tr("screen.find_me.aui.add_to_team"), "ui-button menu-button"));
                }
                html.append(button("open-detail", tr("screen.find_me.details"), "ui-button menu-button").replace("data-action='open-detail'", "data-action='open-detail' data-value='" + selected.uuid() + "'"))
                        .append(button("warehouse-menu:presentation", tr("screen.find_me.presentation_settings"), "ui-button menu-button"));
                if (category == Category.MOUNT) {
                    html.append(button("move-companion", tr("screen.find_me.aui.convert_to_companion"), "ui-button menu-button")
                            .replace("data-action='move-companion'", "data-action='move-companion' data-value='" + selected.uuid() + "'"));
                } else if (category == Category.COMPANION) {
                    html.append(button("move-mount", tr("screen.find_me.aui.convert_to_mount"), "ui-button menu-button")
                            .replace("data-action='move-mount'", "data-action='move-mount' data-value='" + selected.uuid() + "'"));
                }
            }
        }
        return html.toString();
    }

    private CompanionEffectStyle effectStyle(UUID uuid, CompanionEffectPurpose purpose) {
        if (category == Category.VEHICLE) {
            for (var entry : ClientVehicleState.allEntries()) {
                if (!entry.uuid().equals(uuid)) continue;
                return switch (purpose) {
                    case SUMMON -> entry.summonStyle();
                    case RESCUE -> entry.rescueStyle();
                    case STORAGE -> entry.storageStyle();
                };
            }
        } else {
            CompanionKind kind = category == Category.MOUNT ? CompanionKind.MOUNT : CompanionKind.COMPANION;
            for (var entry : ClientCompanionState.allEntries(kind)) {
                if (!entry.uuid().equals(uuid)) continue;
                return switch (purpose) {
                    case SUMMON -> entry.summonStyle();
                    case RESCUE -> entry.rescueStyle();
                    case STORAGE -> entry.storageStyle();
                };
            }
        }
        return CompanionEffectStyle.NONE;
    }

    private CompanionAnimationStyle animationStyle(UUID uuid, CompanionAnimationPurpose purpose) {
        if (category == Category.VEHICLE) {
            for (var entry : ClientVehicleState.allEntries()) {
                if (!entry.uuid().equals(uuid)) continue;
                return switch (purpose) {
                    case SUMMON -> entry.summonAnimation();
                    case RESCUE -> entry.rescueAnimation();
                    case STORAGE -> entry.storageAnimation();
                    case SWITCH -> entry.switchAnimation();
                };
            }
        } else {
            CompanionKind kind = category == Category.MOUNT ? CompanionKind.MOUNT : CompanionKind.COMPANION;
            for (var entry : ClientCompanionState.allEntries(kind)) {
                if (!entry.uuid().equals(uuid)) continue;
                return switch (purpose) {
                    case SUMMON -> entry.summonAnimation();
                    case RESCUE -> entry.rescueAnimation();
                    case STORAGE -> entry.storageAnimation();
                    case SWITCH -> entry.switchAnimation();
                };
            }
        }
        return CompanionAnimationStyle.STANDARD;
    }

    private List<CompanionAnimationStyle> availableAnimations(CompanionAnimationPurpose purpose) {
        return switch (purpose) {
            case SUMMON, RESCUE -> List.of(CompanionAnimationStyle.NONE, CompanionAnimationStyle.STANDARD,
                    CompanionAnimationStyle.GROUND_EMERGE);
            case STORAGE -> category == Category.VEHICLE
                    ? List.of(CompanionAnimationStyle.NONE, CompanionAnimationStyle.STANDARD)
                    : List.of(CompanionAnimationStyle.NONE, CompanionAnimationStyle.STANDARD,
                    CompanionAnimationStyle.GROUND_SINK);
            case SWITCH -> List.of(CompanionAnimationStyle.STANDARD);
        };
    }

    private List<CompanionEffectStyle> availableStyles(CompanionEffectPurpose purpose) {
        if (purpose == CompanionEffectPurpose.STORAGE) {
            return List.of(CompanionEffectStyle.NONE, CompanionEffectStyle.ENDER,
                    CompanionEffectStyle.MAGIC_CIRCLE, CompanionEffectStyle.CUSTOM_MAGIC_CIRCLE);
        }
        return List.of(CompanionEffectStyle.NONE, CompanionEffectStyle.ENDER, CompanionEffectStyle.MAGIC_CIRCLE,
                CompanionEffectStyle.CUSTOM_MAGIC_CIRCLE, CompanionEffectStyle.VELOCITY_BURST);
    }

    private String button(String action, String text, String classes) {
        return "<div class='ui-button " + classes + "' data-action='" + action + "'>" + escape(text) + "</div>";
    }

    private List<Card> cards() {
        if (view == View.DEAD) {
            return deadCards(deadFilter);
        }
        if (view == View.RECOVERY) {
            return recoveryCards();
        }
        if (view == View.WAREHOUSE) {
            return switch (warehouseFilter) {
                case ALL -> allCards().stream().filter(this::matches).toList();
                case ASSIGNED -> assignedCards();
                case UNASSIGNED -> unassignedCards();
            };
        }
        List<Card> result = new ArrayList<>();
        List<UUID> members;
        members = ClientCompanionTeamState.members(target(), selectedTeam);
        for (UUID uuid : members) {
            Card card = findCard(uuid);
            if (card != null && matches(card)) result.add(card);
        }
        return result;
    }

    private List<Card> unassignedCards() {
        Set<UUID> assigned = assignedUuids();
        return allCards().stream().filter(card -> !assigned.contains(card.uuid())).filter(this::matches).toList();
    }

    private List<Card> assignedCards() {
        Set<UUID> assigned = assignedUuids();
        return allCards().stream().filter(card -> assigned.contains(card.uuid())).filter(this::matches).toList();
    }

    private Set<UUID> assignedUuids() {
        Set<UUID> assigned = new HashSet<>();
        ClientCompanionTeamState.entries(target()).forEach(team -> assigned.addAll(team.uuids()));
        return assigned;
    }

    private List<Card> deadCards(DeadFilter filter) {
        List<Card> result = new ArrayList<>();
        for (var entry : ClientCompanionState.deadEntries()) {
            if ((category == Category.COMPANION && entry.kind() != CompanionKind.COMPANION)
                    || (category == Category.MOUNT && entry.kind() != CompanionKind.MOUNT)
                    || category == Category.VEHICLE) continue;
            if (filter == DeadFilter.RECOVERABLE && !entry.recoverable()) continue;
            if (filter == DeadFilter.ARCHIVED && entry.recoverable()) continue;
            Card card = new Card(entry.uuid(), entry.name(), entry.entityType(), false, false);
            if (matches(card)) result.add(card);
        }
        return result;
    }

    private List<Card> recoveryCards() {
        List<Card> result = new ArrayList<>();
        for (var entry : ClientCompanionState.recoveryEntries()) {
            if ((category == Category.COMPANION && entry.kind() != CompanionKind.COMPANION)
                    || (category == Category.MOUNT && entry.kind() != CompanionKind.MOUNT)
                    || category == Category.VEHICLE) continue;
            Card card = new Card(entry.uuid(), entry.name(), entry.entityType(), false, false,
                    entry.moveType(), null);
            if (matches(card)) result.add(card);
        }
        return result;
    }

    private List<Card> allCards() {
        List<Card> result = new ArrayList<>();
        if (backupWarehousePreview && backupWarehouse != null) {
            for (BackupWarehousePacket.Entry entry : backupWarehouse.entries()) {
                CompanionMoveType moveType = entry.vehicle() ? CompanionMoveType.WALK
                        : entry.kind() == CompanionKind.MOUNT ? CompanionMoveType.WALK : CompanionMoveType.COMPANION;
                CompanionListPacket.Entry previewEntry = new CompanionListPacket.Entry(entry.uuid(), -1,
                        entry.entityType(), entry.name(), false, entry.alive(), false, false, false, false, null,
                        0.0f, 0.0f, 0.0f, moveType,
                        CompanionAnimationStyle.STANDARD, CompanionAnimationStyle.STANDARD,
                        CompanionAnimationStyle.STANDARD, CompanionAnimationStyle.STANDARD,
                        CompanionEffectStyle.NONE, CompanionEffectStyle.NONE, CompanionEffectStyle.NONE,
                        entry.previewTag());
                result.add(new Card(entry.uuid(), entry.name(), entry.entityType(), false, entry.alive(), moveType, previewEntry));
            }
            return result;
        }
        if (category == Category.VEHICLE) ClientVehicleState.allEntries().forEach(entry -> result.add(new Card(entry.uuid(), entry.name(), entry.entityType(), entry.loaded() && entry.alive(), entry.alive(), CompanionMoveType.WALK, entry.asPreviewEntry())));
        else {
            ClientCompanionState.allEntries(category == Category.MOUNT ? CompanionKind.MOUNT : CompanionKind.COMPANION)
                    .forEach(entry -> result.add(new Card(entry.uuid(), entry.name(), entry.entityType(), entry.loaded() && entry.alive(), entry.alive(), entry.moveType(), entry)));
        }
        return result;
    }

    private boolean matches(Card card) {
        String query = search.toLowerCase(Locale.ROOT).trim();
        return query.isBlank() || card.name.toLowerCase(Locale.ROOT).contains(query) || card.type.toLowerCase(Locale.ROOT).contains(query);
    }

    private Card findCard(UUID uuid) {
        for (var entry : ClientCompanionState.recoveryEntries()) {
            if (entry.uuid().equals(uuid)) return new Card(uuid, entry.name(), entry.entityType(), false,
                    false, entry.moveType(), null);
        }
        for (var entry : ClientCompanionState.deadEntries()) {
            if (entry.uuid().equals(uuid)) return new Card(uuid, entry.name(), entry.entityType(), false, false);
        }
        if (category == Category.VEHICLE) {
            for (var entry : ClientVehicleState.allEntries()) if (entry.uuid().equals(uuid)) return new Card(uuid, entry.name(), entry.entityType(), entry.loaded() && entry.alive(), entry.alive(), CompanionMoveType.WALK, entry.asPreviewEntry());
        } else {
            CompanionKind kind = category == Category.MOUNT ? CompanionKind.MOUNT : CompanionKind.COMPANION;
            for (var entry : ClientCompanionState.allEntries(kind)) if (entry.uuid().equals(uuid)) return new Card(uuid, entry.name(), entry.entityType(), entry.loaded() && entry.alive(), entry.alive(), entry.moveType(), entry);
        }
        return null;
    }

    private List<ClientCompanionTeamState.TeamEntry> teams() {
        return ClientCompanionTeamState.entries(target());
    }

    private String teamName(List<ClientCompanionTeamState.TeamEntry> teams, int index) {
        if (index < 0 || index >= teams.size()) return tr("screen.find_me.team_number", 1);
        ClientCompanionTeamState.TeamEntry team = teams.get(index);
        return team.name().isBlank() ? tr("screen.find_me.team_number", team.number()) : team.name();
    }

    private CompanionTeamTarget target() {
        return category == Category.VEHICLE ? CompanionTeamTarget.VEHICLE : category == Category.MOUNT ? CompanionTeamTarget.MOUNT : CompanionTeamTarget.COMPANION;
    }

    private long stateSignature() {
        long signature = ClientCompanionState.revision();
        signature = signature * 31L + ClientVehicleState.revision();
        signature = signature * 31L + ClientCompanionTeamState.revision();
        return signature;
    }

    private String labelFor(UUID uuid) {
        Card card = uuid == null ? null : findCard(uuid);
        return card == null ? tr("screen.find_me.aui.unknown") : card.name;
    }

    private static UUID parseUuid(String value) {
        try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (RuntimeException ignored) { return fallback; }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String markupKey(String markup) {
        return markup.replace(" fm-page-reveal", "")
                .replace(" fm-member-insert", "")
                .replace(" fm-team-insert", "")
                .replace(" fm-auto-join-change", "");
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String bool(boolean value) {
        return tr(value ? "screen.find_me.aui.enabled" : "screen.find_me.aui.disabled");
    }

    private record Card(UUID uuid, String name, String type, boolean active, boolean alive,
                        CompanionMoveType moveType, CompanionListPacket.Entry previewEntry) {
        private Card(UUID uuid, String name, String type, boolean active, boolean alive) {
            this(uuid, name, type, active, alive, CompanionMoveType.WALK, null);
        }
    }
    private record PendingDrop(DragKind kind, int from, int to, List<UUID> members, UUID moved) {}
    private enum SettingsChoiceMenu { NONE, WHEEL_STYLE, DRAG_HOLD, NAME_LENGTH, TEXT_MODE, FONT_FAMILY, FONT_SIZE, RIDING_CAMERA, BINDING_ANIMATION, SUMMONED_OUTLINE, DEFAULT_TEAM, COMPANION_LIMIT }
    private enum DragKind { NONE, TEAM, MEMBER }
    private enum CardTogglePhase { IDLE, IN }
    private enum DoctorView { BACKUPS, BACKUP_DETAIL }
    private enum Category {
        MOUNT("screen.find_me.mounts"), VEHICLE("screen.find_me.vehicles"), COMPANION("screen.find_me.companions");
        final String key;
        Category(String key) { this.key = key; }
    }
    private enum View {
        TEAM("screen.find_me.teams"), WAREHOUSE("screen.find_me.warehouse"), DEAD("screen.find_me.aui.deaths"), RECOVERY("screen.find_me.aui.recovery"), DETAIL("screen.find_me.details"), SETTINGS("screen.find_me.aui.settings"), DOCTOR("screen.find_me.aui.doctor");
        final String key;
        View(String key) { this.key = key; }
    }
    private enum WarehouseFilter {
        ALL("screen.find_me.all", "ALL CREATURES"), ASSIGNED("screen.find_me.aui.assigned", "ASSIGNED CREATURES"), UNASSIGNED("screen.find_me.aui.unassigned", "UNASSIGNED CREATURES");
        final String key;
        final String subtitle;
        WarehouseFilter(String key, String subtitle) { this.key = key; this.subtitle = subtitle; }
    }
    private enum WarehouseMenuPage { MAIN, RENAME, TEAM, PRESENTATION, ANIMATION, ANIMATION_STYLE, EFFECT, EFFECT_STYLE }
    private enum TeamMenuPage { MAIN, RENAME }
    private enum DeadFilter {
        ALL("screen.find_me.aui.deaths", "MEMORIAL ARCHIVE"), RECOVERABLE("screen.find_me.aui.recoverable_sleep", "RECOVERABLE RECORDS"), ARCHIVED("screen.find_me.aui.record_only", "ARCHIVED RECORDS");
        final String key;
        final String subtitle;
        DeadFilter(String key, String subtitle) { this.key = key; this.subtitle = subtitle; }
    }

    private static final class MinecraftAccess {
        private MinecraftAccess() {}
        static void setScreen(FindMeAuiManageScreen screen) {
            net.minecraft.client.Minecraft.getInstance().setScreen(screen);
        }
        static void closeScreen() {
            net.minecraft.client.Minecraft.getInstance().setScreen(null);
        }
    }
}
