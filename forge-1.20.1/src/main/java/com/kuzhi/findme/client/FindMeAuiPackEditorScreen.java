package com.kuzhi.findme.client;

import com.kuzhi.findme.api.client.FindMeClientIntegrationRegistry;
import com.kuzhi.findme.common.CompanionEffectPurpose;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.CompanionRescueMotion;
import com.kuzhi.findme.common.PackAnimationPresetCategory;
import com.kuzhi.findme.common.PackEditorAction;
import com.kuzhi.findme.common.PackEntityCategoryOverride;
import com.kuzhi.findme.common.PackEntityMovementOverride;
import com.kuzhi.findme.common.PackEntityBindingRequirement;
import com.kuzhi.findme.common.BindingAnimationPolicy;
import com.kuzhi.findme.common.PackEntityPresetField;
import com.kuzhi.findme.common.FindMeUiSettings;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.PackAnimationPresetListPacket;
import com.kuzhi.findme.network.PackAnimationPresetMotionPacket;
import com.kuzhi.findme.network.PackAnimationPresetStylePacket;
import com.kuzhi.findme.network.PackEditorActionPacket;
import com.kuzhi.findme.network.PackEntityPresetUpdatePacket;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

/** ApricityUI editor for server-authoritative integration-pack entity profiles. */
public final class FindMeAuiPackEditorScreen extends FindMeAuiOverlayScreen {
    private static final String PATH = "findme/pack/editor.html";
    private static final int ENTRY_ROW_HEIGHT = 33;
    private static final int SOUND_VISIBLE_ROWS = 6;
    private static FindMeAuiPackEditorScreen current;

    private final Set<String> selected = new LinkedHashSet<>();
    private final Map<String, PackAnimationPresetListPacket.Entry> drafts = new LinkedHashMap<>();
    private final Set<String> resetDrafts = new LinkedHashSet<>();
    private Category category = Category.ALL;
    private Page page = Page.TYPE;
    private String search = "";
    private int entryScrollIndex;
    private int soundScrollIndex;
    private int selectionAnchor = -1;
    private String soundSearch = "";
    private boolean confirmReset;
    private boolean filterOpen;
    private boolean soundPickerOpen;
    private ScrollbarDrag scrollbarDrag = ScrollbarDrag.NONE;
    private double scrollbarGrabOffset;
    private double fieldsScrollTop;
    private boolean shellBuilt;
    private boolean closeRequested;
    private String selectionDragStartType;
    private int selectionDragStartIndex = -1;
    private long selectionPressedAtNanos;
    private boolean selectionDragActive;
    private boolean suppressSelectionClick;
    private PreviewDrag previewDrag = PreviewDrag.NONE;
    private double previewLastX;
    private double previewLastY;

    private FindMeAuiPackEditorScreen() {
        super(PATH);
        current = this;
    }

    public static void open() {
        if (!ClientFindMeModuleState.enabled(FindMeModule.MANAGEMENT)) {
            return;
        }
        Minecraft.getInstance().setScreen(new FindMeAuiPackEditorScreen());
    }

    static void refreshCurrent() {
        if (current != null && Minecraft.getInstance().screen == current) {
            current.onPresetStateUpdated();
        }
    }

    @Override
    protected void init() {
        super.init();
        shellBuilt = false;
        bind();
        refreshAll();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        shellBuilt = false;
        bind();
        refreshAll();
    }

    @Override
    public void onClose() {
        requestClose();
    }

    @Override
    public void removed() {
        if (current == this) current = null;
        shellBuilt = false;
        super.removed();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Document document = getLinkedDocument();
        Element focused = document == null ? null : document.getFocusedElement();
        String focusedId = focused == null ? "" : focused.getAttribute("id");
        if (Screen.hasControlDown() && FindMeUiKeys.isSelectAll(keyCode) && focused != null && "input".equalsIgnoreCase(focused.tagName)) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (Screen.hasControlDown() && FindMeUiKeys.isSelectAll(keyCode)) {
            selected.clear();
            for (PackAnimationPresetListPacket.Entry entry : entries()) selected.add(entry.entityType());
            selectionAnchor = entries().isEmpty() ? -1 : 0;
            confirmReset = false;
            refreshParts(false, false, true, true, false);
            return true;
        }
        if (FindMeUiKeys.isConfirm(keyCode)) {
            if ("arrival-sound".equals(focusedId)) {
                sendField(PackEntityPresetField.ARRIVAL_SOUND, focused.getValue().trim());
                return true;
            }
            if ("sound-picker-search".equals(focusedId)) {
                applySoundSearch();
                return true;
            }
            if (focused == null || "pack-search".equals(focusedId)) {
                applySearch();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void bind() {
        Document document = getLinkedDocument();
        if (document == null || document.body == null || "1".equals(document.body.getAttribute("data-findme-bound"))) return;
        document.body.setAttribute("data-findme-bound", "1");
        document.body.addEventListener("click", event -> {
            Element target = event.target instanceof Element element ? element.closest("[data-action]") : null;
            if (target == null) return;
            FindMeAuiSound.click();
            action(target.getAttribute("data-action"), target.getAttribute("data-value"));
            event.preventDefault();
        });
        document.body.addEventListener("mousedown", event -> {
            if (event instanceof MouseEvent mouse && event.target instanceof Element element) {
                if (beginPreviewInteraction(mouse, element)) {
                    event.preventDefault();
                    return;
                }
                beginScrollbarInteraction(mouse, element);
                beginSelectionDrag(mouse, element);
            }
        });
        document.body.addEventListener("mousemove", event -> {
            if (event instanceof MouseEvent mouse) {
                if (updatePreviewInteraction(mouse)) {
                    event.preventDefault();
                    return;
                }
                updateScrollbarDrag(mouse);
                updateSelectionDrag(mouse, event.target instanceof Element element ? element : null);
            }
        });
        document.body.addEventListener("mouseup", event -> {
            if (event instanceof MouseEvent mouse) {
                if (previewDrag != PreviewDrag.NONE) {
                    previewDrag = PreviewDrag.NONE;
                    event.preventDefault();
                }
                if (mouse.button == 0) {
                    scrollbarDrag = ScrollbarDrag.NONE;
                    finishSelectionDrag(mouse);
                }
            }
        });
        document.body.addEventListener("wheel", event -> {
            if (!(event instanceof MouseEvent mouse) || !(event.target instanceof Element element)) return;
            double delta = mouse.scrollDelta != 0.0 ? mouse.scrollDelta : mouse.deltaY;
            if (delta == 0.0) return;
            if (element.closest(".preview-panel") != null) {
                FindMePreviewInteractionState.zoom("pack-editor-main", delta);
                event.preventDefault();
                return;
            }
            if (element.closest(".sound-picker-list") != null) {
                scrollSounds(delta > 0.0 ? 1 : -1);
                event.preventDefault();
                return;
            }
            Element fields = element.closest(".fields-panel");
            if (fields != null) {
                fields.setScrollTop(fields.getTargetScrollTop() + delta);
                updateFieldsScrollThumb();
                event.preventDefault();
                return;
            }
            if (element.closest(".entry-list") == null) return;
            int step = Math.max(1, visibleEntryRows() / 4);
            scrollEntries(delta > 0.0 ? step : -step);
            event.preventDefault();
        });
    }

    private void action(String action, String value) {
        if (action == null) return;
        if (isTransitionRunning()) return;
        if (action.equals("back")) {
            requestClose();
            return;
        }
        if (action.equals("save-drafts")) {
            stageTextInputs();
            saveDrafts();
            return;
        }
        if (action.equals("cancel-drafts")) {
            discardDrafts();
            return;
        }
        if (action.startsWith("category:")) {
            stageTextInputs();
            Category target = Category.valueOf(action.substring(9).toUpperCase(Locale.ROOT));
            if (target == category) {
                if (filterOpen) {
                    filterOpen = false;
                    refreshParts(false, false, true, false, false);
                }
                return;
            }
            boolean forward = target.ordinal() > category.ordinal();
            transitionSibling(() -> {
                category = target;
                filterOpen = false;
                entryScrollIndex = 0;
                selectionAnchor = -1;
                refreshParts(true, false, true, false, false);
            }, forward);
            return;
        }
        if (action.startsWith("page:")) {
            stageTextInputs();
            Page target = Page.valueOf(action.substring(5).toUpperCase(Locale.ROOT));
            if (target == page) return;
            boolean forward = target.ordinal() > page.ordinal();
            transitionSibling(() -> {
                page = target;
                confirmReset = false;
                soundPickerOpen = false;
                scrollbarDrag = ScrollbarDrag.NONE;
                resetFieldsScroll();
                refreshParts(false, true, false, true, false);
            }, forward);
            return;
        }
        if (action.equals("apply-search")) {
            applySearch();
            return;
        }
        if (action.equals("toggle-filter")) {
            filterOpen = !filterOpen;
            refreshParts(false, false, true, false, false);
            return;
        }
        if (action.equals("select")) {
            if (suppressSelectionClick) {
                suppressSelectionClick = false;
                return;
            }
            stageTextInputs();
            select(value);
            return;
        }
        if (action.equals("save-preview-nbt")) {
            if (stagePreviewNbtInput()) refreshDraftStateInPlace();
            return;
        }
        if (action.equals("clear-preview-nbt")) {
            sendField(PackEntityPresetField.PREVIEW_NBT, "");
            return;
        }
        if (action.startsWith("integration:")) {
            stageTextInputs();
            FindMeClientIntegrationRegistry.open(action.substring("integration:".length()),
                    new ArrayList<>(selected));
            return;
        }
        if (action.startsWith("field:")) {
            String[] parts = action.split(":", 3);
            if (parts.length == 3) sendField(PackEntityPresetField.valueOf(parts[1]), parts[2]);
            return;
        }
        if (action.startsWith("step:")) {
            String[] parts = action.split(":", 3);
            if (parts.length == 3) step(PackEntityPresetField.valueOf(parts[1]), Float.parseFloat(parts[2]));
            return;
        }
        if (action.startsWith("style:")) {
            String[] parts = action.split(":", 3);
            if (parts.length == 3) sendStyle(CompanionEffectPurpose.valueOf(parts[1]), CompanionEffectStyle.valueOf(parts[2]));
            return;
        }
        if (action.startsWith("animation:")) {
            String[] parts = action.split(":", 3);
            if (parts.length == 3) sendAnimation(CompanionAnimationPurpose.valueOf(parts[1]),
                    CompanionAnimationStyle.valueOf(parts[2]));
            return;
        }
        if (action.equals("save-sound")) {
            Element input = getLinkedDocument().getElementById("arrival-sound");
            sendField(PackEntityPresetField.ARRIVAL_SOUND, input == null ? "" : input.getValue().trim());
            return;
        }
        if (action.equals("clear-sound")) {
            soundPickerOpen = false;
            sendField(PackEntityPresetField.ARRIVAL_SOUND, "");
            return;
        }
        if (action.equals("open-sound-picker")) {
            soundPickerOpen = !soundPickerOpen;
            soundSearch = "";
            soundScrollIndex = soundPickerOpen ? selectedSoundIndex() : 0;
            refreshParts(false, false, false, true, false);
            return;
        }
        if (action.equals("apply-sound-search")) {
            applySoundSearch();
            return;
        }
        if (action.startsWith("choose-sound:")) {
            soundPickerOpen = false;
            sendField(PackEntityPresetField.ARRIVAL_SOUND, action.substring("choose-sound:".length()));
            return;
        }
        if (action.equals("ask-reset")) {
            confirmReset = true;
            refreshDraftStateInPlace();
            return;
        }
        if (action.equals("confirm-reset")) {
            stageReset();
            confirmReset = false;
            refreshDraftStateInPlace();
        }
    }

    private void requestClose() {
        if (isTransitionRunning()) return;
        drafts.clear();
        resetDrafts.clear();
        if (!closeRequested && ClientPackAnimationPresetState.editMode()) {
            closeRequested = true;
            ModNetwork.sendToServer(new PackEditorActionPacket(PackEditorAction.CLOSE));
        }
        if (!transitionClose()) Minecraft.getInstance().setScreen(null);
    }

    private void applySearch() {
        Document document = getLinkedDocument();
        Element input = document == null ? null : document.getElementById("pack-search");
        search = input == null ? search : input.getValue();
        filterOpen = false;
        entryScrollIndex = 0;
        selectionAnchor = -1;
        refreshParts(false, false, true, false, false);
    }

    private boolean beginPreviewInteraction(MouseEvent mouse, Element target) {
        if (target.closest(".preview-panel") == null || (mouse.button != 0 && mouse.button != 1)) return false;
        previewDrag = mouse.button == 0 ? PreviewDrag.ROTATE : PreviewDrag.PAN;
        previewLastX = mouse.clientX;
        previewLastY = mouse.clientY;
        return true;
    }

    private boolean updatePreviewInteraction(MouseEvent mouse) {
        if (previewDrag == PreviewDrag.NONE) return false;
        double deltaX = mouse.clientX - previewLastX;
        double deltaY = mouse.clientY - previewLastY;
        previewLastX = mouse.clientX;
        previewLastY = mouse.clientY;
        if (previewDrag == PreviewDrag.ROTATE) {
            FindMePreviewInteractionState.rotate("pack-editor-main", deltaX, deltaY);
        } else {
            FindMePreviewInteractionState.pan("pack-editor-main", deltaX, deltaY);
        }
        return true;
    }

    private boolean stagePreviewNbtInput() {
        if (page != Page.TYPE || selected.size() != 1) return false;
        Document document = getLinkedDocument();
        Element input = document == null ? null : document.getElementById("preview-nbt");
        PackAnimationPresetListPacket.Entry current = firstSelected();
        if (input == null || current == null) return false;
        String value = input.getValue() == null ? "" : input.getValue().trim();
        if (Objects.equals(value, current.previewNbt())) return false;
        putDraft(current.entityType(), withField(current, PackEntityPresetField.PREVIEW_NBT, value));
        return true;
    }

    private void stageTextInputs() {
        stagePreviewNbtInput();
    }

    private void select(String value) {
        if (value == null || value.isBlank()) return;
        List<PackAnimationPresetListPacket.Entry> visible = entries();
        int index = indexOf(visible, value);
        if (Screen.hasShiftDown() && selectionAnchor >= 0 && selectionAnchor < visible.size() && index >= 0) {
            if (!Screen.hasControlDown()) selected.clear();
            int from = Math.min(selectionAnchor, index);
            int to = Math.max(selectionAnchor, index);
            for (int i = from; i <= to; i++) selected.add(visible.get(i).entityType());
        } else if (Screen.hasControlDown()) {
            if (!selected.add(value)) selected.remove(value);
            selectionAnchor = index;
        } else {
            selected.clear();
            selected.add(value);
            selectionAnchor = index;
        }
        confirmReset = false;
        resetFieldsScroll();
        refreshParts(false, false, true, true, false);
    }

    private static int indexOf(List<PackAnimationPresetListPacket.Entry> entries, String entityType) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).entityType().equals(entityType)) return i;
        return -1;
    }

    private void scrollEntries(int amount) {
        int maximum = Math.max(0, entries().size() - visibleEntryRows());
        int next = Math.max(0, Math.min(maximum, entryScrollIndex + amount));
        if (next == entryScrollIndex) return;
        entryScrollIndex = next;
        refreshParts(false, false, true, false, false);
    }

    private void beginSelectionDrag(MouseEvent mouse, Element target) {
        if (mouse.button != 0 || target.closest(".entry-scroll-rail") != null) return;
        Element row = target.closest(".entry-row");
        if (row == null) return;
        suppressSelectionClick = false;
        selectionDragStartType = row.getAttribute("data-value");
        selectionDragStartIndex = parseInt(row.getAttribute("data-entry-index"), -1);
        selectionPressedAtNanos = System.nanoTime();
        selectionDragActive = false;
    }

    private void updateSelectionDrag(MouseEvent mouse, Element target) {
        if (selectionDragStartType == null || selectionDragStartIndex < 0) return;
        long holdNanos = FindMeUiSettings.defaults().dragHoldMillis() * 1_000_000L;
        if (!selectionDragActive && System.nanoTime() - selectionPressedAtNanos >= holdNanos) {
            selectionDragActive = true;
            selected.clear();
            selected.add(selectionDragStartType);
            selectionAnchor = selectionDragStartIndex;
        }
        if (!selectionDragActive || target == null) return;
        Element row = target.closest(".entry-row");
        int targetIndex = row == null ? -1 : parseInt(row.getAttribute("data-entry-index"), -1);
        if (targetIndex < 0) return;
        List<PackAnimationPresetListPacket.Entry> visible = entries();
        int from = Math.max(0, Math.min(selectionDragStartIndex, targetIndex));
        int to = Math.min(visible.size() - 1, Math.max(selectionDragStartIndex, targetIndex));
        LinkedHashSet<String> range = new LinkedHashSet<>();
        for (int i = from; i <= to; i++) range.add(visible.get(i).entityType());
        if (!selected.equals(range)) {
            selected.clear();
            selected.addAll(range);
            confirmReset = false;
            refreshParts(false, false, true, true, false);
        }
        mouse.preventDefault();
    }

    private void finishSelectionDrag(MouseEvent mouse) {
        if (selectionDragStartType == null) return;
        if (selectionDragActive) {
            suppressSelectionClick = true;
            mouse.preventDefault();
        }
        selectionDragStartType = null;
        selectionDragStartIndex = -1;
        selectionDragActive = false;
    }

    private void applySoundSearch() {
        Document document = getLinkedDocument();
        Element input = document == null ? null : document.getElementById("sound-picker-search");
        soundSearch = input == null ? soundSearch : input.getValue();
        soundScrollIndex = 0;
        refreshParts(false, false, false, true, false);
    }

    private void scrollSounds(int amount) {
        int maximum = Math.max(0, soundIds().size() - SOUND_VISIBLE_ROWS);
        int next = Math.max(0, Math.min(maximum, soundScrollIndex + amount));
        if (next == soundScrollIndex) return;
        soundScrollIndex = next;
        refreshParts(false, false, false, true, false);
    }

    private void beginScrollbarInteraction(MouseEvent mouse, Element target) {
        if (mouse.button != 0) return;
        Element entryRail = target.closest(".entry-scroll-rail");
        Element soundRail = target.closest(".sound-scroll-rail");
        Element fieldsRail = target.closest(".fields-scroll-rail");
        Element rail = entryRail != null ? entryRail : soundRail != null ? soundRail : fieldsRail;
        if (rail == null) return;
        scrollbarDrag = entryRail != null ? ScrollbarDrag.ENTRY : soundRail != null ? ScrollbarDrag.SOUND : ScrollbarDrag.FIELDS;
        String thumbSelector = switch (scrollbarDrag) {
            case ENTRY -> ".entry-scroll-thumb";
            case SOUND -> ".sound-scroll-thumb";
            case FIELDS -> ".fields-scroll-thumb";
            default -> "";
        };
        Element thumb = rail.querySelector(thumbSelector);
        boolean pressedThumb = target.closest(thumbSelector) != null;
        double thumbHeight = switch (scrollbarDrag) {
            case ENTRY -> entryThumbHeight();
            case SOUND -> soundThumbHeight();
            case FIELDS -> fieldsThumbHeight();
            default -> 1.0;
        };
        scrollbarGrabOffset = pressedThumb && thumb != null
                ? Math.max(0.0, Math.min(thumbHeight, mouse.clientY - Position.of(thumb).y))
                : thumbHeight * 0.5;
        if (!pressedThumb) updateScrollbarFromPointer(mouse.clientY);
        mouse.preventDefault();
    }

    private void updateScrollbarDrag(MouseEvent mouse) {
        if (scrollbarDrag == ScrollbarDrag.NONE) return;
        updateScrollbarFromPointer(mouse.clientY);
        mouse.preventDefault();
    }

    private void updateScrollbarFromPointer(double mouseY) {
        Document document = getLinkedDocument();
        if (document == null) return;
        boolean entry = scrollbarDrag == ScrollbarDrag.ENTRY;
        boolean sound = scrollbarDrag == ScrollbarDrag.SOUND;
        Element rail = document.getElementById(entry ? "pack-entry-scroll-rail" : sound ? "pack-sound-scroll-rail" : "pack-fields-scroll-rail");
        if (rail == null) return;
        double trackHeight = entry ? editorListHeight() : sound ? soundTrackHeight() : fieldsTrackHeight();
        double thumbHeight = entry ? entryThumbHeight() : sound ? soundThumbHeight() : fieldsThumbHeight();
        double travel = Math.max(1.0, trackHeight - thumbHeight);
        double ratio = Math.max(0.0, Math.min(1.0, (mouseY - Position.of(rail).y - scrollbarGrabOffset) / travel));
        if (entry) {
            int maximum = Math.max(0, entries().size() - visibleEntryRows());
            int next = (int)Math.round(maximum * ratio);
            if (next != entryScrollIndex) {
                entryScrollIndex = next;
                refreshParts(false, false, true, false, false);
            }
        } else if (sound) {
            int maximum = Math.max(0, soundIds().size() - SOUND_VISIBLE_ROWS);
            int next = (int)Math.round(maximum * ratio);
            if (next != soundScrollIndex) {
                soundScrollIndex = next;
                refreshParts(false, false, false, true, false);
            }
        } else {
            Element fields = document.getElementById("pack-fields-panel");
            if (fields == null) return;
            double maximum = fieldsScrollLimit(fields);
            fields.setScrollTop(maximum * ratio);
            updateFieldsScrollThumb();
        }
    }

    private int entryThumbHeight() {
        int total = entries().size();
        if (total <= 0) return editorListHeight();
        return Math.max(12, (int)Math.round(editorListHeight() * Math.min(1.0, visibleEntryRows() / (double)total)));
    }

    private int soundTrackHeight() {
        return SOUND_VISIBLE_ROWS * 18;
    }

    private int soundThumbHeight() {
        int total = soundIds().size();
        if (total <= 0) return soundTrackHeight();
        return Math.max(12, (int)Math.round(soundTrackHeight() * Math.min(1.0, SOUND_VISIBLE_ROWS / (double)total)));
    }

    private double fieldsTrackHeight() {
        return Math.max(1.0, height - scaled(43) - scaled(8));
    }

    private double fieldsThumbHeight() {
        Document document = getLinkedDocument();
        Element fields = document == null ? null : document.getElementById("pack-fields-panel");
        if (fields == null) return fieldsTrackHeight();
        double viewport = Math.max(1.0, Box.of(fields).innerSize().height());
        double content = Math.max(viewport, Size.getContentSize(fields).height());
        return Math.max(12.0, Math.min(fieldsTrackHeight(), fieldsTrackHeight() * viewport / content));
    }

    private static double fieldsScrollLimit(Element fields) {
        double viewport = Math.max(1.0, Box.of(fields).innerSize().height());
        double content = Math.max(viewport, Size.getContentSize(fields).height());
        return Math.max(0.0, content - viewport);
    }

    private void updateFieldsScrollThumb() {
        Document document = getLinkedDocument();
        if (document == null) return;
        Element fields = document.getElementById("pack-fields-panel");
        Element thumb = document.getElementById("pack-fields-scroll-thumb");
        if (fields == null || thumb == null) return;
        double maximum = fieldsScrollLimit(fields);
        double thumbHeight = fieldsThumbHeight();
        double travel = Math.max(0.0, fieldsTrackHeight() - thumbHeight);
        double ratio = maximum <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, fields.getTargetScrollTop() / maximum));
        thumb.setAttribute("style", "top:" + Math.round(travel * ratio) + "px;height:" + Math.round(thumbHeight)
                + "px;visibility:" + (maximum > 0.0 ? "visible" : "hidden"));
    }

    private void resetFieldsScroll() {
        fieldsScrollTop = 0.0;
        Document document = getLinkedDocument();
        Element fields = document == null ? null : document.getElementById("pack-fields-panel");
        if (fields != null) fields.setScrollTop(0.0);
    }

    private void captureFieldsScroll(Document document) {
        Element fields = document == null ? null : document.getElementById("pack-fields-panel");
        if (fields != null) fieldsScrollTop = fields.getTargetScrollTop();
    }

    private void restoreFieldsScroll(Document document) {
        Element fields = document == null ? null : document.getElementById("pack-fields-panel");
        if (fields == null) return;
        fields.setScrollTop(Math.max(0.0, fieldsScrollTop));
    }

    private void refreshDraftStateInPlace() {
        Document document = getLinkedDocument();
        if (!shellBuilt || document == null || document.body == null) {
            refreshAll();
            return;
        }
        PackAnimationPresetListPacket.Entry entry = firstSelected();
        if (entry == null) {
            refreshParts(true, false, true, true, false);
            return;
        }

        boolean dirty = !drafts.isEmpty() || !resetDrafts.isEmpty();
        Element save = document.querySelector("[data-action='save-drafts']");
        Element cancel = document.querySelector("[data-action='cancel-drafts']");
        if (save != null) save.setClassName("ui-button toolbar-button primary " + (dirty ? "dirty" : ""));
        if (cancel != null) cancel.setClassName("ui-button toolbar-button " + (dirty ? "dirty" : ""));

        for (Element row : document.querySelectorAll(".entry-row")) {
            String type = row.getAttribute("data-value");
            String classes = "entry-row ";
            if (selected.contains(type)) classes += "selected ";
            if (drafts.containsKey(type) || resetDrafts.contains(type)) classes += "dirty";
            row.setClassName(classes.trim());
        }
        for (Element option : document.querySelectorAll(".option")) {
            option.setClassName("ui-button option " + (isOptionActive(option.getAttribute("data-action")) ? "active" : ""));
        }
        for (Element value : document.querySelectorAll("[data-step-field]")) {
            PackEntityPresetField field;
            try {
                field = PackEntityPresetField.valueOf(value.getAttribute("data-step-field"));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            value.setTextContent(stepValue(field));
        }

        Element preview = document.querySelector("findme-preview[data-interaction-id='pack-editor-main']");
        if (preview != null) {
            preview.setAttribute("data-entity-type", entry.entityType());
            preview.setAttribute("data-preview-nbt", entry.previewNbt());
            preview.setAttribute("data-bounds-scale", String.format(Locale.ROOT, "%.2f", entry.boundsScale()));
            preview.setAttribute("data-effect-scale", String.format(Locale.ROOT, "%.2f", entry.circleScale()));
        }
        Element selectedCopy = document.querySelector(".preview-copy p");
        if (selectedCopy != null) selectedCopy.setTextContent(tr("screen.find_me.pack_selected", selected.size()));
        Element nbtInput = document.getElementById("preview-nbt");
        if (nbtInput != null && !nbtInput.isFocus) nbtInput.setValue(entry.previewNbt());

        Element reset = document.querySelector(".reset");
        if (reset != null) {
            reset.setAttribute("data-action", confirmReset ? "confirm-reset" : "ask-reset");
            reset.setTextContent(tr(confirmReset ? "screen.find_me.aui.pack.confirm_reset" : "screen.find_me.aui.pack.reset_selected"));
        }
        updateFieldsScrollThumb();
    }

    private boolean isOptionActive(String action) {
        if (action == null || action.isBlank()) return false;
        String[] parts = action.split(":", 3);
        try {
            if (parts.length == 3 && "field".equals(parts[0])) {
                return switch (PackEntityPresetField.valueOf(parts[1])) {
                    case CATEGORY -> allMatch(entry -> entry.categoryOverride().name().equals(parts[2]));
                    case MOVEMENT -> allMatch(entry -> entry.movementOverride().name().equals(parts[2]));
                    case BINDING_REQUIREMENT -> allMatch(entry -> entry.bindingRequirement().name().equals(parts[2]));
                    case BINDING_ANIMATION -> allMatch(entry -> entry.bindingAnimationPolicy().name().equals(parts[2]));
                    case RESCUE_MOTION -> allMatch(entry -> entry.rescueMotion().name().equals(parts[2]));
                    default -> false;
                };
            }
            if (parts.length == 3 && "style".equals(parts[0])) {
                CompanionEffectPurpose purpose = CompanionEffectPurpose.valueOf(parts[1]);
                CompanionEffectStyle style = CompanionEffectStyle.valueOf(parts[2]);
                return allMatch(entry -> switch (purpose) {
                    case SUMMON -> entry.summonStyle() == style;
                    case RESCUE -> entry.rescueStyle() == style;
                    case STORAGE -> entry.storageStyle() == style;
                });
            }
            if (parts.length == 3 && "animation".equals(parts[0])) {
                CompanionAnimationPurpose purpose = CompanionAnimationPurpose.valueOf(parts[1]);
                CompanionAnimationStyle style = CompanionAnimationStyle.valueOf(parts[2]);
                return allMatch(entry -> animation(entry, purpose) == style);
            }
        } catch (IllegalArgumentException ignored) {
        }
        return false;
    }

    private String stepValue(PackEntityPresetField field) {
        Function<PackAnimationPresetListPacket.Entry, Float> getter = switch (field) {
            case BOUNDS_SCALE -> PackAnimationPresetListPacket.Entry::boundsScale;
            case CIRCLE_SCALE -> PackAnimationPresetListPacket.Entry::circleScale;
            case ARRIVAL_VOLUME -> PackAnimationPresetListPacket.Entry::arrivalVolume;
            case ARRIVAL_PITCH -> PackAnimationPresetListPacket.Entry::arrivalPitch;
            default -> null;
        };
        if (getter == null || firstSelected() == null || mixed(getter)) return "--";
        return String.format(Locale.ROOT, "%.2f", getter.apply(firstSelected()));
    }

    private void step(PackEntityPresetField field, float delta) {
        PackAnimationPresetListPacket.Entry entry = firstSelected();
        if (entry == null) return;
        float currentValue = switch (field) {
            case BOUNDS_SCALE -> entry.boundsScale();
            case CIRCLE_SCALE -> entry.circleScale();
            case ARRIVAL_VOLUME -> entry.arrivalVolume();
            case ARRIVAL_PITCH -> entry.arrivalPitch();
            default -> 1.0f;
        };
        boolean visualScale = field == PackEntityPresetField.BOUNDS_SCALE || field == PackEntityPresetField.CIRCLE_SCALE;
        float minimum = visualScale ? 0.25f : 0.0f;
        float maximum = visualScale ? 4.0f : 2.0f;
        float next = Math.max(minimum, Math.min(maximum, currentValue + delta));
        sendField(field, String.format(Locale.ROOT, "%.2f", next));
    }

    private void sendField(PackEntityPresetField field, String value) {
        List<String> types = selected.stream().toList();
        if (types.isEmpty()) return;
        for (String type : types) {
            PackAnimationPresetListPacket.Entry current = effectiveEntry(type);
            if (current != null) putDraft(type, withField(current, field, value));
        }
        refreshDraftStateInPlace();
    }

    private void sendStyle(CompanionEffectPurpose purpose, CompanionEffectStyle style) {
        List<String> types = selected.stream().toList();
        if (types.isEmpty()) return;
        for (String type : types) {
            PackAnimationPresetListPacket.Entry current = effectiveEntry(type);
            if (current != null) putDraft(type, withStyle(current, purpose, style));
        }
        refreshDraftStateInPlace();
    }

    private void sendAnimation(CompanionAnimationPurpose purpose, CompanionAnimationStyle style) {
        List<String> types = selected.stream().toList();
        if (types.isEmpty()) return;
        for (String type : types) {
            PackAnimationPresetListPacket.Entry current = effectiveEntry(type);
            if (current != null) putDraft(type, withAnimation(current, purpose, style));
        }
        refreshDraftStateInPlace();
    }

    private void stageReset() {
        for (String type : selected) {
            PackAnimationPresetListPacket.Entry authoritative = authoritativeEntry(type);
            if (authoritative == null) continue;
            resetDrafts.add(type);
            drafts.put(type, resetEntry(authoritative));
        }
    }

    private void putDraft(String type, PackAnimationPresetListPacket.Entry draft) {
        PackAnimationPresetListPacket.Entry authoritative = authoritativeEntry(type);
        if (!resetDrafts.contains(type) && Objects.equals(authoritative, draft)) {
            drafts.remove(type);
        } else {
            drafts.put(type, draft);
        }
    }

    private void saveDrafts() {
        if (drafts.isEmpty() && resetDrafts.isEmpty()) return;
        List<String> resets = resetDrafts.stream().toList();
        if (!resets.isEmpty()) {
            ModNetwork.sendToServer(new PackEntityPresetUpdatePacket(resets, PackEntityPresetField.RESET, ""));
        }

        Map<FieldChange, List<String>> fields = new LinkedHashMap<>();
        Map<StyleChange, List<String>> styles = new LinkedHashMap<>();
        Map<AnimationChange, List<String>> animations = new LinkedHashMap<>();
        for (Map.Entry<String, PackAnimationPresetListPacket.Entry> draft : drafts.entrySet()) {
            PackAnimationPresetListPacket.Entry authoritative = authoritativeEntry(draft.getKey());
            if (authoritative == null) continue;
            PackAnimationPresetListPacket.Entry baseline = resetDrafts.contains(draft.getKey())
                    ? resetEntry(authoritative) : authoritative;
            collectChanges(draft.getKey(), baseline, draft.getValue(), fields, styles, animations);
        }
        for (Map.Entry<FieldChange, List<String>> change : fields.entrySet()) {
            ModNetwork.sendToServer(new PackEntityPresetUpdatePacket(change.getValue(), change.getKey().field(), change.getKey().value()));
        }
        for (Map.Entry<StyleChange, List<String>> change : styles.entrySet()) {
            ModNetwork.sendToServer(new PackAnimationPresetStylePacket(change.getValue(), change.getKey().purpose(), change.getKey().style()));
        }
        for (Map.Entry<AnimationChange, List<String>> change : animations.entrySet()) {
            ModNetwork.sendToServer(new PackAnimationPresetMotionPacket(change.getValue(), change.getKey().purpose(),
                    change.getKey().style()));
        }

        ClientPackAnimationPresetState.applyLocal(new ArrayList<>(drafts.values()));
        drafts.clear();
        resetDrafts.clear();
        refreshDraftStateInPlace();
    }

    private void discardDrafts() {
        if (drafts.isEmpty() && resetDrafts.isEmpty()) return;
        drafts.clear();
        resetDrafts.clear();
        confirmReset = false;
        refreshDraftStateInPlace();
    }

    private static void collectChanges(String type, PackAnimationPresetListPacket.Entry baseline,
                                       PackAnimationPresetListPacket.Entry draft,
                                       Map<FieldChange, List<String>> fields,
                                       Map<StyleChange, List<String>> styles,
                                       Map<AnimationChange, List<String>> animations) {
        addFieldChange(type, fields, PackEntityPresetField.CATEGORY, baseline.categoryOverride(), draft.categoryOverride());
        addFieldChange(type, fields, PackEntityPresetField.MOVEMENT, baseline.movementOverride(), draft.movementOverride());
        addFieldChange(type, fields, PackEntityPresetField.BINDING_REQUIREMENT,
                baseline.bindingRequirement(), draft.bindingRequirement());
        addFieldChange(type, fields, PackEntityPresetField.BINDING_ANIMATION,
                baseline.bindingAnimationPolicy(), draft.bindingAnimationPolicy());
        addFieldChange(type, fields, PackEntityPresetField.RESCUE_MOTION, baseline.rescueMotion(), draft.rescueMotion());
        addFloatChange(type, fields, PackEntityPresetField.BOUNDS_SCALE, baseline.boundsScale(), draft.boundsScale());
        addFloatChange(type, fields, PackEntityPresetField.CIRCLE_SCALE, baseline.circleScale(), draft.circleScale());
        if (!Objects.equals(baseline.arrivalSound(), draft.arrivalSound())) {
            fields.computeIfAbsent(new FieldChange(PackEntityPresetField.ARRIVAL_SOUND, draft.arrivalSound()), ignored -> new ArrayList<>()).add(type);
        }
        addFloatChange(type, fields, PackEntityPresetField.ARRIVAL_VOLUME, baseline.arrivalVolume(), draft.arrivalVolume());
        addFloatChange(type, fields, PackEntityPresetField.ARRIVAL_PITCH, baseline.arrivalPitch(), draft.arrivalPitch());
        if (!Objects.equals(baseline.previewNbt(), draft.previewNbt())) {
            fields.computeIfAbsent(new FieldChange(PackEntityPresetField.PREVIEW_NBT, draft.previewNbt()), ignored -> new ArrayList<>()).add(type);
        }
        addStyleChange(type, styles, CompanionEffectPurpose.SUMMON, baseline.summonStyle(), draft.summonStyle());
        addStyleChange(type, styles, CompanionEffectPurpose.RESCUE, baseline.rescueStyle(), draft.rescueStyle());
        addStyleChange(type, styles, CompanionEffectPurpose.STORAGE, baseline.storageStyle(), draft.storageStyle());
        addAnimationChange(type, animations, CompanionAnimationPurpose.SUMMON, baseline.summonAnimation(), draft.summonAnimation());
        addAnimationChange(type, animations, CompanionAnimationPurpose.RESCUE, baseline.rescueAnimation(), draft.rescueAnimation());
        addAnimationChange(type, animations, CompanionAnimationPurpose.STORAGE, baseline.storageAnimation(), draft.storageAnimation());
        addAnimationChange(type, animations, CompanionAnimationPurpose.SWITCH, baseline.switchAnimation(), draft.switchAnimation());
    }

    private static void addFieldChange(String type, Map<FieldChange, List<String>> changes, PackEntityPresetField field,
                                       Enum<?> before, Enum<?> after) {
        if (before == after) return;
        changes.computeIfAbsent(new FieldChange(field, after.name()), ignored -> new ArrayList<>()).add(type);
    }

    private static void addFloatChange(String type, Map<FieldChange, List<String>> changes, PackEntityPresetField field,
                                       float before, float after) {
        if (Float.compare(before, after) == 0) return;
        String value = String.format(Locale.ROOT, "%.2f", after);
        changes.computeIfAbsent(new FieldChange(field, value), ignored -> new ArrayList<>()).add(type);
    }

    private static void addStringChange(String type, Map<FieldChange, List<String>> changes,
                                        PackEntityPresetField field, String before, String after) {
        if (Objects.equals(before, after)) return;
        changes.computeIfAbsent(new FieldChange(field, after == null ? "" : after), ignored -> new ArrayList<>()).add(type);
    }

    private static void addStyleChange(String type, Map<StyleChange, List<String>> changes, CompanionEffectPurpose purpose,
                                       CompanionEffectStyle before, CompanionEffectStyle after) {
        if (before == after) return;
        changes.computeIfAbsent(new StyleChange(purpose, after), ignored -> new ArrayList<>()).add(type);
    }

    private static void addAnimationChange(String type, Map<AnimationChange, List<String>> changes,
                                           CompanionAnimationPurpose purpose, CompanionAnimationStyle before,
                                           CompanionAnimationStyle after) {
        if (before == after) return;
        changes.computeIfAbsent(new AnimationChange(purpose, after), ignored -> new ArrayList<>()).add(type);
    }

    private PackAnimationPresetListPacket.Entry effectiveEntry(String type) {
        PackAnimationPresetListPacket.Entry draft = drafts.get(type);
        return draft == null ? authoritativeEntry(type) : draft;
    }

    private static PackAnimationPresetListPacket.Entry authoritativeEntry(String type) {
        for (PackAnimationPresetListPacket.Entry entry : ClientPackAnimationPresetState.entries()) {
            if (entry.entityType().equals(type)) return entry;
        }
        return null;
    }

    private static PackAnimationPresetListPacket.Entry withField(PackAnimationPresetListPacket.Entry entry,
                                                                  PackEntityPresetField field, String value) {
        PackEntityCategoryOverride categoryOverride = entry.categoryOverride();
        PackAnimationPresetCategory category = entry.category();
        PackEntityMovementOverride movement = entry.movementOverride();
        PackEntityBindingRequirement bindingRequirement = entry.bindingRequirement();
        BindingAnimationPolicy bindingAnimationPolicy = entry.bindingAnimationPolicy();
        CompanionRescueMotion rescueMotion = entry.rescueMotion();
        float boundsScale = entry.boundsScale();
        float circleScale = entry.circleScale();
        String arrivalSound = entry.arrivalSound();
        float arrivalVolume = entry.arrivalVolume();
        float arrivalPitch = entry.arrivalPitch();
        String previewNbt = entry.previewNbt();
        try {
            switch (field) {
                case CATEGORY -> {
                    categoryOverride = PackEntityCategoryOverride.valueOf(value);
                    category = switch (categoryOverride) {
                        case MOUNT -> PackAnimationPresetCategory.MOUNT;
                        case COMPANION -> PackAnimationPresetCategory.COMPANION;
                        case VEHICLE -> PackAnimationPresetCategory.VEHICLE;
                        case DISABLED -> PackAnimationPresetCategory.DISABLED;
                        case AUTO -> category;
                    };
                }
                case MOVEMENT -> {
                    movement = PackEntityMovementOverride.valueOf(value);
                    if (categoryOverride == PackEntityCategoryOverride.AUTO && movement != PackEntityMovementOverride.AUTO) {
                        category = PackAnimationPresetCategory.MOUNT;
                    }
                }
                case BINDING_REQUIREMENT -> bindingRequirement = PackEntityBindingRequirement.valueOf(value);
                case BINDING_ANIMATION -> bindingAnimationPolicy = BindingAnimationPolicy.valueOf(value);
                case RESCUE_MOTION -> rescueMotion = CompanionRescueMotion.valueOf(value);
                case BOUNDS_SCALE -> boundsScale = Float.parseFloat(value);
                case CIRCLE_SCALE -> circleScale = Float.parseFloat(value);
                case ARRIVAL_SOUND -> arrivalSound = value == null ? "" : value.trim();
                case ARRIVAL_VOLUME -> arrivalVolume = Float.parseFloat(value);
                case ARRIVAL_PITCH -> arrivalPitch = Float.parseFloat(value);
                case PREVIEW_NBT -> previewNbt = value == null ? "" : value.trim();
                case RESET -> { return resetEntry(entry); }
            }
        } catch (IllegalArgumentException ignored) {
            return entry;
        }
        return new PackAnimationPresetListPacket.Entry(entry.entityType(), entry.name(), category, categoryOverride, movement,
                bindingRequirement, bindingAnimationPolicy, rescueMotion,
                entry.summonAnimation(), entry.rescueAnimation(), entry.storageAnimation(),
                entry.switchAnimation(), boundsScale, circleScale, arrivalSound, arrivalVolume, arrivalPitch,
                entry.summonStyle(), entry.rescueStyle(), entry.storageStyle(), previewNbt);
    }

    private static PackAnimationPresetListPacket.Entry withStyle(PackAnimationPresetListPacket.Entry entry,
                                                                  CompanionEffectPurpose purpose, CompanionEffectStyle style) {
        return new PackAnimationPresetListPacket.Entry(entry.entityType(), entry.name(), entry.category(), entry.categoryOverride(),
                entry.movementOverride(), entry.bindingRequirement(), entry.bindingAnimationPolicy(),
                entry.rescueMotion(), entry.summonAnimation(), entry.rescueAnimation(),
                entry.storageAnimation(), entry.switchAnimation(), entry.boundsScale(), entry.circleScale(), entry.arrivalSound(),
                entry.arrivalVolume(), entry.arrivalPitch(),
                purpose == CompanionEffectPurpose.SUMMON ? style : entry.summonStyle(),
                purpose == CompanionEffectPurpose.RESCUE ? style : entry.rescueStyle(),
                purpose == CompanionEffectPurpose.STORAGE ? style : entry.storageStyle(), entry.previewNbt());
    }

    private static PackAnimationPresetListPacket.Entry withAnimation(PackAnimationPresetListPacket.Entry entry,
                                                                      CompanionAnimationPurpose purpose,
                                                                      CompanionAnimationStyle style) {
        return new PackAnimationPresetListPacket.Entry(entry.entityType(), entry.name(), entry.category(),
                entry.categoryOverride(), entry.movementOverride(), entry.bindingRequirement(),
                entry.bindingAnimationPolicy(), entry.rescueMotion(),
                purpose == CompanionAnimationPurpose.SUMMON ? style : entry.summonAnimation(),
                purpose == CompanionAnimationPurpose.RESCUE ? style : entry.rescueAnimation(),
                purpose == CompanionAnimationPurpose.STORAGE ? style : entry.storageAnimation(),
                purpose == CompanionAnimationPurpose.SWITCH ? style : entry.switchAnimation(),
                entry.boundsScale(), entry.circleScale(), entry.arrivalSound(), entry.arrivalVolume(), entry.arrivalPitch(),
                entry.summonStyle(), entry.rescueStyle(), entry.storageStyle(), entry.previewNbt());
    }

    private static PackAnimationPresetListPacket.Entry resetEntry(PackAnimationPresetListPacket.Entry entry) {
        return new PackAnimationPresetListPacket.Entry(entry.entityType(), entry.name(), entry.category(),
                PackEntityCategoryOverride.AUTO, PackEntityMovementOverride.AUTO,
                PackEntityBindingRequirement.AUTO, BindingAnimationPolicy.INHERIT, CompanionRescueMotion.STANDARD,
                CompanionAnimationStyle.STANDARD, CompanionAnimationStyle.STANDARD,
                CompanionAnimationStyle.STANDARD, CompanionAnimationStyle.STANDARD,
                1.0f, 1.0f, "", 1.0f, 1.0f, CompanionEffectStyle.DEFAULT,
                CompanionEffectStyle.DEFAULT, CompanionEffectStyle.DEFAULT, "");
    }

    private void onPresetStateUpdated() {
        reconcileSelection();
        if (!shellBuilt) {
            refreshAll();
        } else {
            refreshDraftStateInPlace();
        }
    }

    private void refreshAll() {
        Document document = getLinkedDocument();
        if (document == null || document.body == null) return;
        Element root = document.getElementById("findme-pack-editor-root");
        if (root == null) return;
        clampEntryScroll();
        root.setAttribute("style", layoutStyle());
        root.setInnerHTML("<div class='editor-page fm-page " + ClientWheelPresentationState.typographyClasses()
                + (isPageRevealing() ? " fm-page-reveal" : "") + "'>"
                + foldDecoration() + "<div id='pack-topbar' class='topbar'>" + topbarMarkup()
                + "</div><div id='pack-sidebar' class='editor-sidebar'><div class='editor-sidebar-sheet'></div>"
                + "<div id='pack-sidebar-content' class='editor-sidebar-content'>" + sidebarMarkup()
                + "</div></div><div class='editor-content'><div id='pack-entry-panel' class='entry-panel'>" + entryPanelMarkup()
                + "</div><div id='pack-inspector' class='inspector'>" + inspectorMarkup()
                + "</div></div></div>");
        document.rebuildSelectorIndex();
        document.reapplyStylesFromCache();
        shellBuilt = true;
        bind();
        updateFieldsScrollThumb();
    }

    private void refreshParts(boolean topbar, boolean sidebar, boolean entryPanel, boolean inspector, boolean overlay) {
        Document document = getLinkedDocument();
        if (!shellBuilt || document == null || document.body == null) {
            refreshAll();
            return;
        }
        clampEntryScroll();
        if (inspector) captureFieldsScroll(document);
        boolean changed = false;
        changed |= replaceContents(document, topbar ? "pack-topbar" : null, this::topbarMarkup);
        changed |= replaceContents(document, sidebar ? "pack-sidebar-content" : null, this::sidebarMarkup);
        changed |= replaceContents(document, entryPanel ? "pack-entry-panel" : null, this::entryPanelMarkup);
        changed |= replaceContents(document, inspector ? "pack-inspector" : null, this::inspectorMarkup);
        if (changed) {
            document.rebuildSelectorIndex();
            document.reapplyStylesFromCache();
            bind();
            if (inspector) restoreFieldsScroll(document);
            updateFieldsScrollThumb();
        }
    }

    private static boolean replaceContents(Document document, String id, java.util.function.Supplier<String> markup) {
        if (id == null) return false;
        Element element = document.getElementById(id);
        if (element == null) return false;
        element.setInnerHTML(markup.get());
        return true;
    }

    private String topbarMarkup() {
        StringBuilder html = new StringBuilder(button("back", tr("screen.find_me.aui.back"), "back-button"))
                .append("<div class='page-title'><strong>").append(escape(tr("screen.find_me.aui.pack.editor_title")))
                .append("</strong><small>INTEGRATION PROFILE EDITOR</small></div><div class='topbar-spacer'></div>");
        boolean dirty = !drafts.isEmpty() || !resetDrafts.isEmpty();
        html.append("<div class='pack-toolbar'>")
                .append(button("save-drafts", tr("screen.find_me.config_save"), "toolbar-button primary " + (dirty ? "dirty" : "")))
                .append(button("cancel-drafts", tr("screen.find_me.cancel"), "toolbar-button " + (dirty ? "dirty" : "")))
                .append("</div>");
        return html.toString();
    }

    private String sidebarMarkup() {
        StringBuilder html = new StringBuilder("<div class='side-heading editor-side-stage-0'><small>PROFILE</small><strong>")
                .append(escape(tr("screen.find_me.aui.pack.entity_profiles"))).append("</strong></div>");
        int pageIndex = 1;
        for (Page value : Page.values()) {
            html.append("<div class='page-row editor-side-stage-").append(Math.min(3, pageIndex)).append(" ")
                    .append(value == page ? "active" : "").append("' data-action='page:")
                    .append(value.name().toLowerCase(Locale.ROOT)).append("'><div class='page-marker'></div><b>").append(twoDigits(pageIndex++))
                    .append("</b><span><strong>").append(escape(tr(value.key))).append("</strong><small>")
                    .append(value.name()).append("</small></span></div>");
        }
        return html.append("<div class='side-spacer'></div>").toString();
    }

    private String entryPanelMarkup() {
        List<PackAnimationPresetListPacket.Entry> entries = entries();
        int visibleRows = visibleEntryRows();
        int end = Math.min(entries.size(), entryScrollIndex + visibleRows);
        StringBuilder html = new StringBuilder("<div class='selection-bar'><span><small>SELECTED TYPES</small><strong>")
                .append(escape(tr("screen.find_me.pack_selected", selected.size()))).append("</strong></span><b>")
                .append(twoDigits(selected.size())).append("</b></div><div class='search-row' style='width:")
                .append(entryContentWidth()).append("px'>")
                .append("<div class='ui-button search-button filter-toggle ").append(filterOpen ? "active" : "")
                .append("' data-action='toggle-filter' style='width:").append(entryFilterWidth()).append("px;min-width:")
                .append(entryFilterWidth()).append("px;flex:0 0 ").append(entryFilterWidth()).append("px'>")
                .append(escape(tr(category.key))).append("</div>")
                .append("<input id='pack-search' class='search' type='text' maxlength='96' style='width:")
                .append(entrySearchWidth()).append("px;min-width:").append(entrySearchWidth()).append("px;flex:0 0 ")
                .append(entrySearchWidth()).append("px' placeholder='")
                .append(escape(tr("screen.find_me.aui.pack.search_entity"))).append("' value='").append(escape(search)).append("'></div>");
        if (filterOpen) {
            html.append("<div class='filter-popover'>");
            for (Category value : Category.values()) {
                html.append(button("category:" + value.name().toLowerCase(Locale.ROOT), tr(value.key),
                        "filter-option " + (value == category ? "active" : "")));
            }
            html.append("</div>");
        }
        html.append("<div class='entry-list' style='width:").append(entryContentWidth()).append("px;height:")
                .append(editorListHeight()).append("px'>");
        for (int i = entryScrollIndex; i < end; i++) {
            PackAnimationPresetListPacket.Entry entry = entries.get(i);
            html.append("<div class='entry-row ").append(selected.contains(entry.entityType()) ? "selected " : "")
                    .append(drafts.containsKey(entry.entityType()) || resetDrafts.contains(entry.entityType()) ? "dirty" : "")
                    .append("' data-action='select' data-entry-index='").append(i).append("' data-value='").append(escape(entry.entityType())).append("'><b>")
                    .append(twoDigits(i + 1)).append("</b><span><strong>").append(escape(entry.name())).append("</strong><small>")
                    .append(escape(entry.entityType())).append("</small></span></div>");
        }
        if (entries.isEmpty()) html.append("<div class='empty'>").append(escape(tr("screen.find_me.aui.pack.no_matches"))).append("</div>");
        html.append("</div>");
        if (entries.size() > visibleRows) {
            double ratio = entryScrollIndex / (double)Math.max(1, entries.size() - visibleRows);
            int trackHeight = Math.max(1, editorListHeight());
            int thumbHeight = entryThumbHeight();
            int thumbTop = (int)Math.round((trackHeight - thumbHeight) * ratio);
            html.append("<div id='pack-entry-scroll-rail' class='entry-scroll-rail' style='left:")
                    .append(entryRailLeft()).append("px;height:").append(editorListHeight())
                    .append("px'><div class='entry-scroll-track'></div><div class='entry-scroll-thumb' style='top:").append(thumbTop)
                    .append("px;height:").append(thumbHeight).append("px'><div class='entry-scroll-thumb-line'></div></div></div>");
        }
        return html.toString();
    }

    private String inspectorMarkup() {
        PackAnimationPresetListPacket.Entry entry = firstSelected();
        if (entry == null) return "<div class='empty'>" + escape(tr("screen.find_me.aui.pack.select_types")) + "</div>";
        String boundsAttributes = page == Page.SCALE
                ? " data-show-bounds='true' data-bounds-scale='" + String.format(Locale.ROOT, "%.2f", entry.boundsScale())
                + "' data-effect-scale='" + String.format(Locale.ROOT, "%.2f", entry.circleScale()) + "'"
                : "";
        StringBuilder html = new StringBuilder("<div class='preview-panel'><findme-preview data-entity-type='")
                .append(escape(entry.entityType())).append("' data-preview-nbt='").append(escape(entry.previewNbt()))
                .append("' data-preview-scale='1.15' data-interaction-id='pack-editor-main'").append(boundsAttributes)
                .append("></findme-preview><span class='preview-number'>01</span><div class='preview-copy'><small>")
                .append(escape(entry.entityType())).append("</small><h1>").append(escape(entry.name())).append("</h1><p>")
                .append(escape(tr("screen.find_me.pack_selected", selected.size()))).append("</p></div></div><div class='fields-viewport'><div id='pack-fields-panel' class='fields-panel'><div class='field-head'><small>")
                .append(page.name()).append("</small><strong>").append(escape(tr(page.key))).append("</strong></div>");
        if (selected.size() > 1) {
            html.append("<div class='batch-banner'>").append(escape(tr("screen.find_me.aui.pack.batch_edit", selected.size()))).append("</div>");
        }
        switch (page) {
            case TYPE -> typeFields(html);
            case ANIMATIONS -> animationFields(html, entry.category());
            case EFFECTS -> effectFields(html, entry.category());
            case SCALE -> scaleFields(html);
        }
        html.append(button(confirmReset ? "confirm-reset" : "ask-reset", tr(confirmReset ? "screen.find_me.aui.pack.confirm_reset" : "screen.find_me.aui.pack.reset_selected"), "reset"));
        return html.append("</div><div id='pack-fields-scroll-rail' class='fields-scroll-rail'><div class='fields-scroll-track'></div><div id='pack-fields-scroll-thumb' class='fields-scroll-thumb'><div class='fields-scroll-thumb-line'></div></div></div></div>").toString();
    }

    private void typeFields(StringBuilder html) {
        appendFieldTitle(html, tr("screen.find_me.pack_category"), mixed(PackAnimationPresetListPacket.Entry::categoryOverride));
        List<String> categoryOptions = new ArrayList<>();
        for (PackEntityCategoryOverride value : PackEntityCategoryOverride.values()) {
            categoryOptions.add(option("field:CATEGORY:" + value.name(), tr("screen.find_me.pack_category." + value.name().toLowerCase(Locale.ROOT)), allMatch(entry -> entry.categoryOverride() == value)));
        }
        appendOptionRows(html, categoryOptions);
        appendFieldTitle(html, tr("screen.find_me.pack_movement"), mixed(PackAnimationPresetListPacket.Entry::movementOverride));
        List<String> movementOptions = new ArrayList<>();
        for (PackEntityMovementOverride value : PackEntityMovementOverride.values()) {
            movementOptions.add(option("field:MOVEMENT:" + value.name(), tr("screen.find_me.pack_movement." + value.name().toLowerCase(Locale.ROOT)), allMatch(entry -> entry.movementOverride() == value)));
        }
        appendOptionRows(html, movementOptions);
        appendFieldTitle(html, tr("screen.find_me.pack_binding_requirement"),
                mixed(PackAnimationPresetListPacket.Entry::bindingRequirement));
        List<String> bindingOptions = new ArrayList<>();
        for (PackEntityBindingRequirement value : PackEntityBindingRequirement.values()) {
            bindingOptions.add(option("field:BINDING_REQUIREMENT:" + value.name(),
                    tr("screen.find_me.pack_binding_requirement." + value.name().toLowerCase(Locale.ROOT)),
                    allMatch(entry -> entry.bindingRequirement() == value)));
        }
        appendOptionRows(html, bindingOptions);
        appendFieldTitle(html, tr("screen.find_me.pack_binding_animation"),
                mixed(PackAnimationPresetListPacket.Entry::bindingAnimationPolicy));
        List<String> bindingAnimationOptions = new ArrayList<>();
        for (BindingAnimationPolicy value : BindingAnimationPolicy.values()) {
            bindingAnimationOptions.add(option("field:BINDING_ANIMATION:" + value.name(),
                    tr("screen.find_me.pack_binding_animation." + value.name().toLowerCase(Locale.ROOT)),
                    allMatch(entry -> entry.bindingAnimationPolicy() == value)));
        }
        appendOptionRows(html, bindingAnimationOptions);
        List<FindMeClientIntegrationRegistry.Entry> integrations = FindMeClientIntegrationRegistry.entries();
        if (!integrations.isEmpty()) {
            appendFieldTitle(html, tr("screen.find_me.pack_addon_profiles"), false);
            html.append("<div class='field-group integration-links'>");
            for (FindMeClientIntegrationRegistry.Entry integration : integrations) {
                html.append(button("integration:" + integration.id(), integration.label().getString(),
                        "integration-link"));
            }
            html.append("</div>");
        }
        if (CompanionRescueMotion.values().length > 1) {
            appendFieldTitle(html, tr("screen.find_me.pack_rescue_motion"), mixed(PackAnimationPresetListPacket.Entry::rescueMotion));
            List<String> rescueMotionOptions = new ArrayList<>();
            for (CompanionRescueMotion value : CompanionRescueMotion.values()) {
                rescueMotionOptions.add(option("field:RESCUE_MOTION:" + value.name(), tr("screen.find_me.pack_rescue_motion." + value.name().toLowerCase(Locale.ROOT)), allMatch(entry -> entry.rescueMotion() == value)));
            }
            appendOptionRows(html, rescueMotionOptions);
        }
        boolean mixedNbt = mixed(PackAnimationPresetListPacket.Entry::previewNbt);
        String previewNbt = mixedNbt || firstSelected() == null ? "" : firstSelected().previewNbt();
        appendFieldTitle(html, tr("screen.find_me.aui.pack.preview_nbt"), mixedNbt);
        if (selected.size() == 1) {
            html.append("<div class='field-group nbt-group'><textarea id='preview-nbt' class='nbt-input' maxlength='32767' placeholder='")
                    .append(escape(tr("screen.find_me.aui.pack.nbt_placeholder"))).append("'>")
                    .append(escape(previewNbt)).append("</textarea><div class='nbt-actions'>")
                    .append(button("save-preview-nbt", tr("screen.find_me.team_apply"), "nbt-action primary"))
                    .append(button("clear-preview-nbt", tr("screen.find_me.clear"), "nbt-action"))
                    .append("</div></div>");
        } else {
            html.append("<div class='batch-banner'>").append(escape(tr("screen.find_me.aui.pack.nbt_single_only"))).append("</div>");
        }
    }

    private void effectFields(StringBuilder html, PackAnimationPresetCategory category) {
        effectGroup(html, CompanionEffectPurpose.SUMMON, PackAnimationPresetListPacket.Entry::summonStyle, category);
        effectGroup(html, CompanionEffectPurpose.RESCUE, PackAnimationPresetListPacket.Entry::rescueStyle, category);
        effectGroup(html, CompanionEffectPurpose.STORAGE, PackAnimationPresetListPacket.Entry::storageStyle, category);
    }

    private void animationFields(StringBuilder html, PackAnimationPresetCategory category) {
        animationGroup(html, CompanionAnimationPurpose.SUMMON, PackAnimationPresetListPacket.Entry::summonAnimation, category);
        animationGroup(html, CompanionAnimationPurpose.RESCUE, PackAnimationPresetListPacket.Entry::rescueAnimation, category);
        animationGroup(html, CompanionAnimationPurpose.STORAGE, PackAnimationPresetListPacket.Entry::storageAnimation, category);
        animationGroup(html, CompanionAnimationPurpose.SWITCH, PackAnimationPresetListPacket.Entry::switchAnimation, category);
    }

    private void animationGroup(StringBuilder html, CompanionAnimationPurpose purpose,
                                Function<PackAnimationPresetListPacket.Entry, CompanionAnimationStyle> getter,
                                PackAnimationPresetCategory category) {
        appendFieldTitle(html, tr("screen.find_me.animation_purpose_short." + purpose.name().toLowerCase(Locale.ROOT)), mixed(getter));
        List<String> options = new ArrayList<>();
        for (CompanionAnimationStyle style : availableAnimations(purpose, category)) {
            options.add(option("animation:" + purpose.name() + ":" + style.name(), animationStyleLabel(style),
                    allMatch(entry -> getter.apply(entry) == style)));
        }
        appendOptionRows(html, options);
    }

    private void effectGroup(StringBuilder html, CompanionEffectPurpose purpose,
                             Function<PackAnimationPresetListPacket.Entry, CompanionEffectStyle> getter,
                             PackAnimationPresetCategory category) {
        appendFieldTitle(html, tr("screen.find_me.effect_purpose_short." + purpose.name().toLowerCase(Locale.ROOT)), mixed(getter));
        List<String> options = new ArrayList<>();
        for (CompanionEffectStyle style : availableStyles(purpose, category)) {
            options.add(option("style:" + purpose.name() + ":" + style.name(), styleLabel(style), allMatch(entry -> getter.apply(entry) == style)));
        }
        appendOptionRows(html, options);
    }

    private void appendOptionRows(StringBuilder html, List<String> options) {
        html.append("<div class='option-stack'>");
        for (int i = 0; i < options.size(); i += 2) {
            html.append("<div class='option-row'>").append(options.get(i));
            if (i + 1 < options.size()) html.append(options.get(i + 1));
            html.append("</div>");
        }
        html.append("</div>");
    }

    private void scaleFields(StringBuilder html) {
        stepper(html, tr("screen.find_me.pack_bounds_scale"), PackEntityPresetField.BOUNDS_SCALE,
                PackAnimationPresetListPacket.Entry::boundsScale);
        stepper(html, tr("screen.find_me.pack_circle_scale"), PackEntityPresetField.CIRCLE_SCALE,
                PackAnimationPresetListPacket.Entry::circleScale);
    }

    private void soundFields(StringBuilder html) {
        boolean mixedSound = mixed(PackAnimationPresetListPacket.Entry::arrivalSound);
        String sound = mixedSound || firstSelected() == null ? "" : firstSelected().arrivalSound();
        appendFieldTitle(html, tr("screen.find_me.pack_arrival_sound"), mixedSound);
        html.append("<div class='field-group sound-group'><div class='sound-entry-row'><input id='arrival-sound' class='sound-input' type='text' maxlength='128' value='")
                .append(escape(sound)).append("' placeholder='").append(escape(mixedSound ? tr("screen.find_me.aui.pack.mixed_values") : tr("screen.find_me.aui.pack.sound_placeholder")))
                .append("'>").append(button("open-sound-picker", tr("screen.find_me.aui.pack.sound_list"), "sound-list-button " + (soundPickerOpen ? "active" : "")))
                .append("</div>");
        if (soundPickerOpen) appendSoundPicker(html);
        html.append("<div class='sound-actions'>").append(button("save-sound", tr("screen.find_me.team_apply"), "sound-action primary"))
                .append(button("clear-sound", tr("screen.find_me.aui.pack.use_entity_voice"), "sound-action")).append("</div></div>");
        stepper(html, tr("screen.find_me.pack_arrival_volume"), PackEntityPresetField.ARRIVAL_VOLUME,
                PackAnimationPresetListPacket.Entry::arrivalVolume);
        stepper(html, tr("screen.find_me.pack_arrival_pitch"), PackEntityPresetField.ARRIVAL_PITCH,
                PackAnimationPresetListPacket.Entry::arrivalPitch);
    }

    private void appendSoundPicker(StringBuilder html) {
        List<String> sounds = soundIds();
        int maximum = Math.max(0, sounds.size() - SOUND_VISIBLE_ROWS);
        soundScrollIndex = Math.max(0, Math.min(soundScrollIndex, maximum));
        int end = Math.min(sounds.size(), soundScrollIndex + SOUND_VISIBLE_ROWS);
        html.append("<div class='sound-picker'><div class='sound-picker-search-row'><input id='sound-picker-search' class='sound-picker-search' type='text' maxlength='96' value='")
                .append(escape(soundSearch)).append("' placeholder='").append(escape(tr("screen.find_me.aui.pack.search_sound"))).append("'>")
                .append(button("apply-sound-search", tr("screen.find_me.aui.go"), "sound-picker-search-button")).append("</div><div class='sound-picker-list'>");
        for (int i = soundScrollIndex; i < end; i++) {
            String sound = sounds.get(i);
            html.append(button("choose-sound:" + sound, sound, "sound-picker-row"));
        }
        if (sounds.isEmpty()) html.append("<div class='sound-picker-empty'>").append(escape(tr("screen.find_me.aui.pack.no_sounds"))).append("</div>");
        html.append("</div>");
        if (sounds.size() > SOUND_VISIBLE_ROWS) {
            int thumbHeight = soundThumbHeight();
            int thumbTop = (int)Math.round((soundTrackHeight() - thumbHeight) * (soundScrollIndex / (double)Math.max(1, maximum)));
            html.append("<div id='pack-sound-scroll-rail' class='sound-scroll-rail'><div class='sound-scroll-track'></div><div class='sound-scroll-thumb' style='top:")
                    .append(thumbTop).append("px;height:").append(thumbHeight).append("px'><div class='sound-scroll-thumb-line'></div></div></div>");
        }
        html.append("</div>");
    }

    private void stepper(StringBuilder html, String label, PackEntityPresetField field,
                         Function<PackAnimationPresetListPacket.Entry, Float> getter) {
        boolean mixed = mixed(getter);
        appendFieldTitle(html, label, mixed);
        String value = mixed || firstSelected() == null ? "--" : String.format(Locale.ROOT, "%.2f", getter.apply(firstSelected()));
        html.append("<div class='field-group'><div class='stepper'>")
                .append(button("step:" + field.name() + ":-0.1", "-", "step-button"))
                .append("<div class='step-value' data-step-field='").append(field.name()).append("'>").append(value).append("</div>")
                .append(button("step:" + field.name() + ":0.1", "+", "step-button"))
                .append("</div></div>");
    }

    private void appendFieldTitle(StringBuilder html, String label, boolean mixed) {
        html.append("<div class='field-title'><span>").append(escape(label)).append("</span>");
        if (mixed) html.append("<small>").append(escape(tr("screen.find_me.aui.pack.mixed_values"))).append("</small>");
        html.append("</div>");
    }

    private String option(String action, String text, boolean active) {
        return button(action, text, "option " + (active ? "active" : ""));
    }

    private String button(String action, String text, String classes) {
        return "<div class='ui-button " + classes + "' data-action='" + action + "'>" + escape(text) + "</div>";
    }

    private String layoutStyle() {
        int editorTop = scaled(43);
        int editorHeight = Math.max(1, height - editorTop - scaled(8));
        int entryWidth = Math.min(160, scaled(103));
        int previewWidth = Math.min(190, scaled(103));
        int gap = scaled(4);
        int editorWidth = Math.max(1, width - scaled(84) - scaled(9));
        int fieldsWidth = Math.max(1, editorWidth - entryWidth - previewWidth - gap * 2);
        int fieldsInnerWidth = Math.max(1, fieldsWidth - 16);
        int optionWidth = Math.max(32, (fieldsInnerWidth - 3) / 2);
        int stepValueWidth = Math.max(32, fieldsInnerWidth - 56);
        int soundInputWidth = Math.max(32, fieldsInnerWidth - 38);
        return "height:" + Math.max(1, height) + "px"
                + ";--fm-top:" + scaled(39) + "px"
                + ";--fm-body:" + Math.max(1, height - scaled(39)) + "px"
                + ";--fm-side:" + scaled(75) + "px"
                + ";--fm-editor-left:" + scaled(84) + "px"
                + ";--fm-editor-top:" + editorTop + "px"
                + ";--fm-editor-width:" + editorWidth + "px"
                + ";--fm-editor-height:" + editorHeight + "px"
                + ";--fm-editor-list:" + editorListHeight() + "px"
                + ";--fm-editor-entry:" + entryWidth + "px"
                + ";--fm-editor-entry-list:" + entryContentWidth() + "px"
                + ";--fm-editor-entry-rail-left:" + entryRailLeft() + "px"
                + ";--fm-editor-preview:" + previewWidth + "px"
                + ";--fm-editor-fields:" + fieldsWidth + "px"
                + ";--fm-editor-fields-scroller:" + (fieldsWidth + 8) + "px"
                + ";--fm-editor-option:" + optionWidth + "px"
                + ";--fm-editor-step-value:" + stepValueWidth + "px"
                + ";--fm-editor-sound-input:" + soundInputWidth + "px"
                + ";--fm-editor-gap:" + gap + "px"
                + ";--fm-fold-left:" + Math.max(0, scaled(75) - 10) + "px"
                + ";--fm-fold-dark-top:" + Math.max(0, scaled(39) - 5) + "px"
                + ";--fm-fold-light-top:" + scaled(43) + "px";
    }

    private int entryPanelWidth() {
        return Math.min(160, scaled(103));
    }

    private int entryContentWidth() {
        return Math.max(1, entryPanelWidth() - 16);
    }

    private int entryFilterWidth() {
        return Math.min(34, Math.max(24, entryContentWidth() / 3));
    }

    private int entrySearchWidth() {
        return Math.max(1, entryContentWidth() - entryFilterWidth() - 2);
    }

    private int entryRailLeft() {
        return Math.max(1, 5 + entryContentWidth() + 3);
    }

    private String foldDecoration() {
        return "<i class='fm-fold-crease'></i><i class='fm-fold-shard fm-fold-shard-dark'></i>"
                + "<i class='fm-fold-shard fm-fold-shard-light'></i>";
    }

    private int editorListHeight() {
        int editorHeight = Math.max(1, height - scaled(43) - scaled(8));
        return Math.max(54, editorHeight - 56);
    }

    private int visibleEntryRows() {
        return Math.max(2, editorListHeight() / ENTRY_ROW_HEIGHT);
    }

    private int scaled(int base) {
        double scale = Math.min(Math.max(1, width) / 427.0, Math.max(1, height) / 240.0);
        return Math.max(1, (int)Math.round(base * scale));
    }

    private void clampEntryScroll() {
        entryScrollIndex = Math.max(0, Math.min(entryScrollIndex, Math.max(0, entries().size() - visibleEntryRows())));
    }

    private List<PackAnimationPresetListPacket.Entry> entries() {
        String query = search.toLowerCase(Locale.ROOT).trim();
        return ClientPackAnimationPresetState.entries().stream()
                .map(entry -> drafts.getOrDefault(entry.entityType(), entry))
                .filter(category::matches)
                .filter(entry -> query.isBlank() || entry.name().toLowerCase(Locale.ROOT).contains(query) || entry.entityType().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private List<String> soundIds() {
        String query = soundSearch.toLowerCase(Locale.ROOT).trim();
        return BuiltInRegistries.SOUND_EVENT.keySet().stream()
                .map(Object::toString)
                .filter(id -> query.isBlank() || id.toLowerCase(Locale.ROOT).contains(query))
                .sorted()
                .toList();
    }

    private int selectedSoundIndex() {
        PackAnimationPresetListPacket.Entry entry = firstSelected();
        if (entry == null || entry.arrivalSound().isBlank()) return 0;
        List<String> sounds = soundIds();
        int index = sounds.indexOf(entry.arrivalSound());
        return index < 0 ? 0 : Math.max(0, index - SOUND_VISIBLE_ROWS / 2);
    }

    private List<PackAnimationPresetListPacket.Entry> selectedEntries() {
        if (selected.isEmpty()) return List.of();
        ArrayList<PackAnimationPresetListPacket.Entry> ordered = new ArrayList<>();
        for (String selectedType : selected) {
            PackAnimationPresetListPacket.Entry entry = effectiveEntry(selectedType);
            if (entry != null) ordered.add(entry);
        }
        return List.copyOf(ordered);
    }

    private PackAnimationPresetListPacket.Entry firstSelected() {
        List<PackAnimationPresetListPacket.Entry> entries = selectedEntries();
        return entries.isEmpty() ? null : entries.get(0);
    }

    private boolean allMatch(java.util.function.Predicate<PackAnimationPresetListPacket.Entry> predicate) {
        List<PackAnimationPresetListPacket.Entry> entries = selectedEntries();
        return !entries.isEmpty() && entries.stream().allMatch(predicate);
    }

    private <T> boolean mixed(Function<PackAnimationPresetListPacket.Entry, T> getter) {
        List<PackAnimationPresetListPacket.Entry> entries = selectedEntries();
        if (entries.size() < 2) return false;
        T first = getter.apply(entries.get(0));
        return entries.stream().skip(1).anyMatch(entry -> !Objects.equals(first, getter.apply(entry)));
    }

    private void reconcileSelection() {
        Set<String> available = ClientPackAnimationPresetState.entries().stream()
                .map(PackAnimationPresetListPacket.Entry::entityType)
                .collect(java.util.stream.Collectors.toSet());
        selected.removeIf(type -> !available.contains(type));
    }

    private static String styleLabel(CompanionEffectStyle style) {
        return tr("screen.find_me.effect_style." + style.name().toLowerCase(Locale.ROOT));
    }

    private static String animationStyleLabel(CompanionAnimationStyle style) {
        return tr("screen.find_me.animation_style." + style.name().toLowerCase(Locale.ROOT));
    }

    private static CompanionAnimationStyle animation(PackAnimationPresetListPacket.Entry entry,
                                                       CompanionAnimationPurpose purpose) {
        return switch (purpose) {
            case SUMMON -> entry.summonAnimation();
            case RESCUE -> entry.rescueAnimation();
            case STORAGE -> entry.storageAnimation();
            case SWITCH -> entry.switchAnimation();
        };
    }

    private static List<CompanionAnimationStyle> availableAnimations(CompanionAnimationPurpose purpose,
                                                                      PackAnimationPresetCategory category) {
        return switch (purpose) {
            case SUMMON, RESCUE -> List.of(CompanionAnimationStyle.NONE, CompanionAnimationStyle.STANDARD,
                    CompanionAnimationStyle.GROUND_EMERGE);
            case STORAGE -> category == PackAnimationPresetCategory.VEHICLE
                    ? List.of(CompanionAnimationStyle.NONE, CompanionAnimationStyle.STANDARD)
                    : List.of(CompanionAnimationStyle.NONE, CompanionAnimationStyle.STANDARD,
                    CompanionAnimationStyle.GROUND_SINK);
            case SWITCH -> List.of(CompanionAnimationStyle.STANDARD);
        };
    }

    private static List<CompanionEffectStyle> availableStyles(CompanionEffectPurpose purpose, PackAnimationPresetCategory category) {
        if (purpose == CompanionEffectPurpose.STORAGE) {
            return List.of(CompanionEffectStyle.NONE, CompanionEffectStyle.ENDER,
                    CompanionEffectStyle.MAGIC_CIRCLE, CompanionEffectStyle.CUSTOM_MAGIC_CIRCLE);
        }
        return List.of(CompanionEffectStyle.NONE, CompanionEffectStyle.ENDER, CompanionEffectStyle.MAGIC_CIRCLE,
                CompanionEffectStyle.CUSTOM_MAGIC_CIRCLE, CompanionEffectStyle.VELOCITY_BURST);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String twoDigits(int value) {
        return String.format(Locale.ROOT, "%02d", Math.max(0, value));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record FieldChange(PackEntityPresetField field, String value) {
    }

    private record StyleChange(CompanionEffectPurpose purpose, CompanionEffectStyle style) {
    }

    private record AnimationChange(CompanionAnimationPurpose purpose, CompanionAnimationStyle style) {
    }

    private enum Page {
        TYPE("screen.find_me.pack_page_type"), ANIMATIONS("screen.find_me.pack_page_animation"),
        EFFECTS("screen.find_me.aui.pack.effects"),
        SCALE("screen.find_me.pack_page_size");
        final String key;
        Page(String key) { this.key = key; }
    }

    private enum Category {
        ALL("screen.find_me.all", null, true), MOUNT("screen.find_me.mounts", PackAnimationPresetCategory.MOUNT, true),
        COMPANION("screen.find_me.companions", PackAnimationPresetCategory.COMPANION, true),
        VEHICLE("screen.find_me.vehicles", PackAnimationPresetCategory.VEHICLE, true),
        DISABLED("screen.find_me.disabled", PackAnimationPresetCategory.DISABLED, true),
        NBT("screen.find_me.aui.pack.nbt_creatures", null, false);
        final String key;
        final PackAnimationPresetCategory value;
        final boolean topLevel;
        Category(String key, PackAnimationPresetCategory value, boolean topLevel) {
            this.key = key;
            this.value = value;
            this.topLevel = topLevel;
        }
        boolean matches(PackAnimationPresetListPacket.Entry entry) {
            if (this == NBT) return entry.previewNbt() != null && !entry.previewNbt().isBlank();
            return value == null || value == entry.category();
        }
    }

    private enum ScrollbarDrag { NONE, ENTRY, SOUND, FIELDS }
    private enum PreviewDrag { NONE, ROTATE, PAN }
}
