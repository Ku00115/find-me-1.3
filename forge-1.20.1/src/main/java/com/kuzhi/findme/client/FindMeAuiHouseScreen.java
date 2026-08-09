package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.HouseCommandAction;
import com.kuzhi.findme.common.HouseResidentMode;
import com.kuzhi.findme.network.HouseCommandPacket;
import com.kuzhi.findme.network.HousePagePacket;
import com.kuzhi.findme.network.ModNetwork;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.chat.Component;

/** AUI house page. The server packet is the only source of resident data. */
public final class FindMeAuiHouseScreen extends FindMeAuiOverlayScreen {
    private static final String PATH = "findme/home/house.html";
    private static final String SIDE_HOME = "home";
    private static final String SIDE_OUTSIDE = "outside";
    private static final int VISIBLE_CARDS_PER_PANE = 4;
    private static final int VISIBLE_PARTNER_CARDS = 8;
    private static final int CARD_HEIGHT = 34;
    private static final int CARD_GAP = 4;
    private static FindMeAuiHouseScreen current;
    private static FindMeAuiHouseScreen cachedScreen;

    private HousePagePacket page;
    private HouseView view = HouseView.PARTNERS;
    private String search = "";
    private int homeFirstVisible;
    private int outsideFirstVisible;
    private int partnerFirstVisible;
    private String lastHomeThumbStyle = "";
    private String lastOutsideThumbStyle = "";
    private String lastPartnerThumbStyle = "";
    private int suppressClickTicks;
    private UUID dragUuid;
    private String dragFromSide;
    private Element dragSourceElement;
    private Element dragTargetElement;
    private Element dragGhostElement;
    private boolean dragActive;
    private double dragStartX;
    private double dragStartY;
    private double dragMouseX;
    private double dragMouseY;
    private boolean contextOpen;
    private UUID contextUuid;
    private String contextSide;
    private HouseContextPage contextPage = HouseContextPage.MAIN;
    private double contextAnchorX = -1.0;
    private double contextAnchorY = -1.0;
    private String contextMotionClass = "";
    private FindMeAuiContextMenuPlacement.Bounds contextBounds = FindMeAuiContextMenuPlacement.EMPTY;
    private String renderedMarkupKey = "";
    private String renderedLayoutStyle = "";

    private FindMeAuiHouseScreen(HousePagePacket page) {
        super(PATH);
        this.page = page;
        current = this;
    }

    public static void open(HousePagePacket page) {
        if (!ClientFindMeModuleState.enabled(com.kuzhi.findme.common.FindMeModule.HOUSES)) {
            return;
        }
        if (current != null && net.minecraft.client.Minecraft.getInstance().screen == current
                && current.page.houseId().equals(page.houseId())) {
            current.page = page;
            current.refresh();
            return;
        }
        if (cachedScreen == null || !cachedScreen.page.houseId().equals(page.houseId())) {
            if (cachedScreen != null) cachedScreen.discardCachedDocuments();
            cachedScreen = new FindMeAuiHouseScreen(page);
        } else {
            cachedScreen.page = page;
        }
        cachedScreen.prepareForOpen();
        current = cachedScreen;
        MinecraftAccess.setScreen(cachedScreen);
    }

    private void prepareForOpen() {
        clearDrag();
        view = HouseView.PARTNERS;
        search = "";
        homeFirstVisible = 0;
        outsideFirstVisible = 0;
        partnerFirstVisible = 0;
        suppressClickTicks = 0;
        contextOpen = false;
        contextUuid = null;
        contextSide = null;
        contextPage = HouseContextPage.MAIN;
        contextAnchorX = -1.0;
        contextAnchorY = -1.0;
        contextMotionClass = "";
        contextBounds = FindMeAuiContextMenuPlacement.EMPTY;
    }

    @Override
    protected void init() {
        super.init();
        bind();
        bindContextOverlay();
        refresh();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextOpen && !contextBounds.overlaps(mouseX, mouseY, 1.0, 1.0)) {
            closeContext();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        super.tick();
        if (suppressClickTicks > 0) suppressClickTicks--;
        updateScrollThumb(SIDE_HOME);
        updateScrollThumb(SIDE_OUTSIDE);
        updatePartnerScrollThumb();
    }

    @Override
    public void removed() {
        clearDrag();
        if (current == this) current = null;
        super.removed();
    }

    private void bind() {
        Document document = getLinkedDocument();
        if (document == null || document.body == null || "1".equals(document.body.getAttribute("data-findme-bound"))) return;
        Element root = document.body;
        root.setAttribute("data-findme-bound", "1");
        root.addEventListener("click", event -> {
            if (suppressClickTicks > 0) {
                suppressClickTicks = 0;
                event.preventDefault();
                return;
            }
            Element target = event.target instanceof Element element ? element.closest("[data-action]") : null;
            if (target == null) return;
            FindMeAuiSound.click();
            action(target.getAttribute("data-action"), target.getAttribute("data-value"));
            event.preventDefault();
        });
        root.addEventListener("mousedown", event -> {
            if (event instanceof MouseEvent mouse && event.target instanceof Element element) beginDrag(mouse, element);
        });
        root.addEventListener("mousemove", event -> {
            if (event instanceof MouseEvent mouse) updateDrag(mouse);
        });
        root.addEventListener("mouseup", event -> {
            if (!(event instanceof MouseEvent mouse)) return;
            finishDrag(mouse, event.target instanceof Element element ? element : null);
        });
        root.addEventListener("contextmenu", event -> {
            if (!(event instanceof MouseEvent mouse) || !(event.target instanceof Element element)) return;
            Element card = element.closest("[data-house-entry]");
            if (card == null) return;
            UUID uuid = parseUuid(card.getAttribute("data-uuid"));
            String side = card.getAttribute("data-house-source");
            if (uuid == null || (!SIDE_HOME.equals(side) && !SIDE_OUTSIDE.equals(side))) return;
            clearDrag();
            contextUuid = uuid;
            contextSide = side;
            contextPage = HouseContextPage.MAIN;
            contextAnchorX = mouse.clientX;
            contextAnchorY = mouse.clientY;
            contextOpen = true;
            contextMotionClass = "fm-context-open";
            refreshContextOverlay();
            event.preventDefault();
        });
        root.addEventListener("wheel", event -> {
            if (!(event instanceof MouseEvent mouse) || !(event.target instanceof Element element)) return;
            double delta = mouse.scrollDelta != 0.0 ? mouse.scrollDelta : mouse.deltaY;
            if (delta == 0.0) return;
            Element partnerGrid = element.closest(".house-partner-grid");
            if (partnerGrid != null) {
                partnerFirstVisible += delta > 0.0 ? 4 : -4;
                refresh();
                event.preventDefault();
                return;
            }
            Element list = element.closest(".house-list");
            if (list == null) return;
            String side = list.getAttribute("data-side");
            if (SIDE_HOME.equals(side)) homeFirstVisible += delta > 0.0 ? 1 : -1;
            else outsideFirstVisible += delta > 0.0 ? 1 : -1;
            refresh();
            event.preventDefault();
        });
    }

    private void bindContextOverlay() {
        Document document = getOverlayDocument();
        if (document == null || document.body == null || "1".equals(document.body.getAttribute("data-findme-bound"))) return;
        Element root = document.body;
        root.setAttribute("data-findme-bound", "1");
        root.addEventListener("click", event -> {
            if (!(event.target instanceof Element element)) return;
            Element actionElement = element.closest("[data-action]");
            if (actionElement != null) {
                FindMeAuiSound.click();
                action(actionElement.getAttribute("data-action"), actionElement.getAttribute("data-value"));
                event.preventDefault();
                return;
            }
            if (element.closest("[data-context-dismiss]") != null) {
                FindMeAuiSound.click();
                closeContext();
                event.preventDefault();
            }
        });
        root.addEventListener("contextmenu", event -> {
            if (!(event.target instanceof Element element) || element.closest("[data-context-dismiss]") == null) return;
            closeContext();
            event.preventDefault();
        });
    }

    private void action(String action, String value) {
        if (action == null) return;
        if (action.equals("back")) {
            transitionClose();
            return;
        }
        if (action.equals("refresh")) {
            ModNetwork.sendToServer(new HouseCommandPacket(HouseCommandAction.REFRESH, page.houseId(), null, ""));
            return;
        }
        if (action.startsWith("house-view:")) {
            HouseView targetView = HouseView.valueOf(action.substring("house-view:".length()).toUpperCase(Locale.ROOT));
            if (targetView == view) return;
            boolean forward = targetView.ordinal() > view.ordinal();
            transitionSibling(() -> {
                view = targetView;
                clearDrag();
                closeContext();
                refresh();
            }, forward);
            return;
        }
        if (action.equals("apply-search")) {
            Element input = getLinkedDocument().getElementById("house-search");
            search = input == null ? "" : input.getValue().trim();
            homeFirstVisible = 0;
            outsideFirstVisible = 0;
            partnerFirstVisible = 0;
            refresh();
            return;
        }
        if (action.equals("house-context:close")) {
            closeContext();
            return;
        }
        if (action.equals("house-context:main")) {
            contextPage = HouseContextPage.MAIN;
            contextMotionClass = "fm-context-back";
            refreshContextOverlay();
            return;
        }
        if (action.equals("house-context:detail")) {
            contextPage = HouseContextPage.DETAIL;
            contextMotionClass = "fm-context-forward";
            refreshContextOverlay();
            return;
        }
        if (action.equals("house-context:rename") && !page.readOnly()) {
            contextPage = HouseContextPage.RENAME;
            contextMotionClass = "fm-context-morph";
            refreshContextOverlay();
            return;
        }
        if (action.equals("house-context:save-rename") && !page.readOnly() && contextUuid != null) {
            Document overlay = getOverlayDocument();
            Element input = overlay == null ? null : overlay.getElementById("house-resident-rename-input");
            String name = input == null ? "" : input.getValue();
            ModNetwork.sendToServer(new HouseCommandPacket(HouseCommandAction.RENAME_RESIDENT,
                    page.houseId(), contextUuid, name));
            closeContext();
            refresh();
            return;
        }
        if (action.equals("house-context:move") && !page.readOnly() && contextUuid != null && contextSide != null) {
            UUID uuid = contextUuid;
            String side = contextSide;
            closeContext();
            move(uuid, side);
            return;
        }
        if (action.startsWith("house-context:mode:") && !page.readOnly() && contextUuid != null
                && SIDE_HOME.equals(contextSide)) {
            HouseResidentMode mode = HouseResidentMode.parse(action.substring("house-context:mode:".length()));
            ModNetwork.sendToServer(new HouseCommandPacket(HouseCommandAction.SET_RESIDENT_MODE,
                    page.houseId(), contextUuid, mode.name()));
            return;
        }
        if (action.equals("toggle-home") && !page.readOnly()) {
            moveFromValue(value);
        }
    }

    private void moveFromValue(String value) {
        if (value == null) return;
        int separator = value.indexOf('|');
        if (separator <= 0 || separator >= value.length() - 1) return;
        move(parseUuid(value.substring(separator + 1)), value.substring(0, separator));
    }

    private void move(UUID uuid, String fromSide) {
        if (uuid == null || page.readOnly()) return;
        HouseCommandAction action = SIDE_HOME.equals(fromSide) ? HouseCommandAction.REMOVE : HouseCommandAction.ASSIGN;
        ModNetwork.sendToServer(new HouseCommandPacket(action, page.houseId(), uuid, ""));
    }

    private void beginDrag(MouseEvent mouse, Element target) {
        clearDrag();
        if (page.readOnly() || mouse.button != 0) return;
        Element card = target.closest("[data-house-entry]");
        if (card == null) return;
        UUID uuid = parseUuid(card.getAttribute("data-uuid"));
        String side = card.getAttribute("data-house-source");
        if (uuid == null || (!SIDE_HOME.equals(side) && !SIDE_OUTSIDE.equals(side))) return;
        dragUuid = uuid;
        dragFromSide = side;
        dragSourceElement = card;
        dragStartX = mouse.clientX;
        dragStartY = mouse.clientY;
        dragMouseX = mouse.clientX;
        dragMouseY = mouse.clientY;
    }

    private void updateDrag(MouseEvent mouse) {
        if (dragUuid == null) return;
        dragMouseX = mouse.clientX;
        dragMouseY = mouse.clientY;
        double threshold = scaled(4);
        double dx = dragMouseX - dragStartX;
        double dy = dragMouseY - dragStartY;
        if (!dragActive && dx * dx + dy * dy >= threshold * threshold) activateDrag();
        if (!dragActive) return;
        updateDragTarget();
        updateDragGhostPosition();
        mouse.preventDefault();
    }

    private void activateDrag() {
        if (dragActive || dragUuid == null) return;
        dragActive = true;
        addClass(dragSourceElement, "dragging");
        Document document = getLinkedDocument();
        dragGhostElement = document == null ? null : document.getElementById("findme-house-drag-ghost");
        HousePagePacket.Resident resident = resident(dragUuid);
        if (dragGhostElement != null && resident != null) {
            dragGhostElement.setClassName("house-drag-ghost active");
            dragGhostElement.setInnerHTML("<b>" + escape(resident.name()) + "</b><small>"
                    + escape(kindLabel(resident.kind())) + "</small>");
            document.rebuildSelectorIndex();
            document.reapplyStylesFromCache();
            updateDragGhostPosition();
        }
    }

    private void updateDragTarget() {
        removeClass(dragTargetElement, "drop-target");
        dragTargetElement = paneAt(dragMouseX, dragMouseY);
        if (dragTargetElement == null || dragFromSide.equals(dragTargetElement.getAttribute("data-house-side"))) {
            dragTargetElement = null;
            return;
        }
        addClass(dragTargetElement, "drop-target");
    }

    private Element paneAt(double mouseX, double mouseY) {
        Document document = getLinkedDocument();
        if (document == null) return null;
        Element dual = document.getElementById("findme-house-dual");
        if (dual != null) {
            Position position = Position.of(dual);
            Size size = Size.of(dual);
            if (mouseX >= position.x && mouseX <= position.x + size.width()
                    && mouseY >= position.y && mouseY <= position.y + size.height()) {
                String side = mouseX < position.x + size.width() / 2.0 ? SIDE_HOME : SIDE_OUTSIDE;
                return document.getElementById(paneId(side));
            }
        }
        for (String side : List.of(SIDE_HOME, SIDE_OUTSIDE)) {
            Element pane = document.getElementById(paneId(side));
            if (pane == null) continue;
            Position position = Position.of(pane);
            Size size = Size.of(pane);
            if (mouseX >= position.x && mouseX <= position.x + size.width()
                    && mouseY >= position.y && mouseY <= position.y + size.height()) {
                return pane;
            }
        }
        return null;
    }

    private void updateDragGhostPosition() {
        if (dragGhostElement == null || dragSourceElement == null) return;
        double width = Math.max(scaled(92), Size.of(dragSourceElement).width());
        double height = Math.max(scaled(34), Size.of(dragSourceElement).height());
        long left = Math.round(dragMouseX - width / 2.0);
        long top = Math.round(dragMouseY - height / 2.0 - scaled(3));
        dragGhostElement.setAttribute("style", "left:" + left + "px;top:" + top + "px;width:"
                + Math.round(width) + "px;height:" + Math.round(height) + "px");
    }

    private void finishDrag(MouseEvent mouse, Element target) {
        if (dragUuid == null || mouse.button != 0) return;
        if (!dragActive) {
            clearDrag();
            return;
        }
        dragMouseX = mouse.clientX;
        dragMouseY = mouse.clientY;
        UUID moved = dragUuid;
        String source = dragFromSide;
        updateDragTarget();
        if (dragTargetElement == null && target != null) {
            Element fallback = target.closest("[data-house-side]");
            if (fallback != null && !source.equals(fallback.getAttribute("data-house-side"))) {
                dragTargetElement = fallback;
            }
        }
        String destination = dragTargetElement == null ? null : dragTargetElement.getAttribute("data-house-side");
        suppressClickTicks = 2;
        clearDrag();
        if (destination != null && !destination.equals(source)) move(moved, source);
        mouse.preventDefault();
    }

    private void clearDrag() {
        removeClass(dragSourceElement, "dragging");
        removeClass(dragTargetElement, "drop-target");
        if (dragGhostElement != null) {
            dragGhostElement.setClassName("house-drag-ghost");
            dragGhostElement.removeAttribute("style");
            dragGhostElement.setInnerHTML("");
        }
        dragUuid = null;
        dragFromSide = null;
        dragSourceElement = null;
        dragTargetElement = null;
        dragGhostElement = null;
        dragActive = false;
    }

    private void refresh() {
        Document document = getLinkedDocument();
        if (document == null || document.body == null) return;
        Element root = document.getElementById("findme-house-root");
        if (root == null) return;
        clearDrag();

        String nextLayoutStyle = layoutStyle();
        String nextMarkup = markup();
        String nextMarkupKey = nextMarkup.replace(" fm-page-reveal", "");
        boolean layoutChanged = !nextLayoutStyle.equals(renderedLayoutStyle);
        boolean markupChanged = !nextMarkupKey.equals(renderedMarkupKey);
        if (layoutChanged) {
            root.setAttribute("style", nextLayoutStyle);
            renderedLayoutStyle = nextLayoutStyle;
        }
        if (markupChanged) {
            root.setInnerHTML(nextMarkup);
            document.rebuildSelectorIndex();
            document.reapplyStylesFromCache();
            bind();
            renderedMarkupKey = nextMarkupKey;
        } else if (isPageRevealing()) {
            addClass(document.querySelector(".house-page"), "fm-page-reveal");
        }
        if (layoutChanged && !markupChanged) {
            document.reapplyStylesFromCache();
            document.commitStyleRecalc();
        }

        if (contextOpen && resident(contextUuid) == null) closeContext();
        refreshContextOverlay();

        lastHomeThumbStyle = "";
        lastOutsideThumbStyle = "";
        lastPartnerThumbStyle = "";
        updateScrollThumb(SIDE_HOME);
        updateScrollThumb(SIDE_OUTSIDE);
        updatePartnerScrollThumb();
    }

    private void closeContext() {
        contextOpen = false;
        contextUuid = null;
        contextSide = null;
        contextPage = HouseContextPage.MAIN;
        contextMotionClass = "";
        clearOverlayMarkup();
    }

    private void refreshContextOverlay() {
        if (!contextOpen) {
            contextBounds = FindMeAuiContextMenuPlacement.EMPTY;
            clearOverlayMarkup();
            return;
        }
        setOverlayMarkup("<div class='findme-context-dismiss' data-context-dismiss='1' data-action='house-context:close'></div>" + contextMarkup());
        contextMotionClass = "";
        contextBounds = FindMeAuiContextMenuPlacement.place(getOverlayDocument(), "findme-house-context-menu",
                ".findme-context-overlay-root", contextAnchorX, contextAnchorY, width - 99.0, 4.0, 91.0, 24.0);
    }

    private void updateScrollThumb(String side) {
        Document document = getLinkedDocument();
        if (document == null) return;
        Element rail = document.getElementById(railId(side));
        Element thumb = document.getElementById(thumbId(side));
        if (rail == null || thumb == null) return;
        int size = filtered(SIDE_HOME.equals(side) ? page.residents() : page.available(), SIDE_OUTSIDE.equals(side)).size();
        int maximum = Math.max(0, size - VISIBLE_CARDS_PER_PANE);
        int first = normalizedFirstVisible(side, size);
        double ratio = maximum <= 0 ? 0.0 : first / (double) maximum;
        double trackHeight = Math.max(1.0, Box.of(rail).innerSize().height());
        double thumbHeight = maximum <= 0 ? trackHeight
                : Math.max(scaled(16), Math.min(trackHeight, trackHeight * VISIBLE_CARDS_PER_PANE / size));
        double travel = Math.max(0.0, trackHeight - thumbHeight);
        String style = "top:" + (int) Math.round(travel * ratio) + "px;height:" + (int) Math.round(thumbHeight)
                + "px;visibility:" + (maximum > 0 ? "visible" : "hidden");
        String previous = SIDE_HOME.equals(side) ? lastHomeThumbStyle : lastOutsideThumbStyle;
        if (style.equals(previous)) return;
        if (SIDE_HOME.equals(side)) lastHomeThumbStyle = style;
        else lastOutsideThumbStyle = style;
        thumb.setAttribute("style", style);
    }

    private void updatePartnerScrollThumb() {
        Document document = getLinkedDocument();
        if (document == null) return;
        Element rail = document.getElementById("findme-house-partner-rail");
        Element thumb = document.getElementById("findme-house-partner-thumb");
        if (rail == null || thumb == null) return;
        int size = filtered(page.residents(), true).size();
        int maximum = maximumPartnerFirst(size);
        partnerFirstVisible = normalizePartnerFirst(size);
        double ratio = maximum <= 0 ? 0.0 : partnerFirstVisible / (double) maximum;
        double trackHeight = Math.max(1.0, Box.of(rail).innerSize().height());
        double thumbHeight = maximum <= 0 ? trackHeight
                : Math.max(scaled(16), Math.min(trackHeight, trackHeight * VISIBLE_PARTNER_CARDS / size));
        double travel = Math.max(0.0, trackHeight - thumbHeight);
        String style = "top:" + (int) Math.round(travel * ratio) + "px;height:" + (int) Math.round(thumbHeight)
                + "px;visibility:" + (maximum > 0 ? "visible" : "hidden");
        if (style.equals(lastPartnerThumbStyle)) return;
        lastPartnerThumbStyle = style;
        thumb.setAttribute("style", style);
    }

    private String markup() {
        List<HousePagePacket.Resident> home = filtered(page.residents(), false);
        List<HousePagePacket.Resident> outside = filtered(page.available(), true);
        int totalVisible = home.size() + outside.size();
        int total = page.residents().size() + page.available().size();

        StringBuilder html = new StringBuilder("<div class='house-page ")
                .append(ClientWheelPresentationState.typographyClasses())
                .append(isPageRevealing() ? " fm-page-reveal" : "")
                .append("' style='height:")
                .append(Math.max(1, height)).append("px'><i class='fm-fold-crease'></i>")
                .append("<i class='fm-fold-shard fm-fold-shard-dark'></i>")
                .append("<i class='fm-fold-shard fm-fold-shard-light'></i><div class='topbar'>")
                .append(button("back", "\u2039", "back-button"))
                .append("<div class='page-title'><strong>")
                .append(escape(tr("screen.find_me.aui.house.house_label")))
                .append("</strong><small>").append(escape(tr("screen.find_me.aui.house.page_subtitle")))
                .append("</small></div>");
        if (page.readOnly()) {
            html.append("<span class='readonly'>").append(escape(tr("screen.find_me.aui.house.read_only"))).append("</span>");
        }
        int activeResidents = (int) page.residents().stream().filter(HousePagePacket.Resident::active).count();
        html.append("</div><div class='house-sidebar'><div class='house-sidebar-sheet'></div><div class='house-sidebar-content'><div class='side-heading house-side-stage-0'><small>")
                .append(escape(tr("screen.find_me.aui.house.capacity", page.residents().size(), page.capacity())))
                .append("</small><strong>")
                .append(escape(page.houseName())).append("</strong></div>")
                .append(sideSummary(activeResidents, tr("screen.find_me.aui.house.home_creatures"),
                        tr("screen.find_me.aui.house.home_creatures_subtitle"), "assignment", view == HouseView.ASSIGNMENT))
                .append(sideSummary(page.residents().size(), tr("screen.find_me.aui.house.home_partners"),
                        tr("screen.find_me.aui.house.home_partners_subtitle"), "partners", view == HouseView.PARTNERS))
                .append("<div class='side-spacer'></div></div></div>");

        int innerWidth = Math.max(1, width - scaled(75) - scaled(10) - scaled(9));
        html.append(view == HouseView.PARTNERS
                ? partnerMarkup(filtered(page.residents(), true), innerWidth)
                : assignmentMarkup(home, outside, innerWidth, totalVisible, total));
        html.append("<div id='findme-house-drag-ghost' class='house-drag-ghost'></div>");
        html.append("</div>");
        return html.toString();
    }

    private String contextMarkup() {
        HousePagePacket.Resident resident = resident(contextUuid);
        if (resident == null) return "";
        StringBuilder html = new StringBuilder("<div id='findme-house-context-menu' class='house-context-menu")
                .append(" ").append(ClientWheelPresentationState.typographyClasses())
                .append(!ClientWheelPresentationState.uiAnimations() || contextMotionClass.isBlank() ? "" : " " + contextMotionClass)
                .append("'>");
        if (contextPage == HouseContextPage.DETAIL) {
            html.append("<span class='house-menu-title'>").append(escape(resident.name())).append("</span>")
                    .append("<div class='house-menu-detail'><small>").append(escape(tr("screen.find_me.aui.house.detail_type")))
                    .append("</small><b>").append(escape(resident.entityType())).append("</b></div>")
                    .append("<div class='house-menu-detail'><small>").append(escape(tr("screen.find_me.aui.house.detail_state")))
                    .append("</small><b>").append(escape(cardStatus(resident, contextSide))).append("</b></div>")
                    .append("<div class='house-menu-detail'><small>")
                    .append(escape(tr("screen.find_me.aui.house.detail_mode")))
                    .append("</small><b>").append(escape(modeLabel(resident.mode()))).append("</b></div>")
                    .append(button("house-context:main", tr("screen.find_me.aui.back"), "house-menu-button"));
        } else if (contextPage == HouseContextPage.RENAME && !page.readOnly()) {
            html.append("<span class='house-menu-title'>").append(escape(tr("screen.find_me.aui.rename"))).append("</span>")
                    .append("<input id='house-resident-rename-input' class='house-menu-input' type='text' value='")
                    .append(escape(resident.name())).append("' maxlength='64'>")
                    .append(button("house-context:save-rename", tr("screen.find_me.config_save"), "house-menu-button"))
                    .append(button("house-context:main", tr("screen.find_me.aui.back"), "house-menu-button"));
        } else {
            html.append("<span class='house-menu-title'>").append(escape(resident.name())).append("</span>")
                    .append(button("house-context:detail", tr("screen.find_me.details"), "house-menu-button"));
            if (!page.readOnly()) {
                if (SIDE_HOME.equals(contextSide)) {
                    html.append("<span class='house-mode-label'>")
                            .append(escape(tr("screen.find_me.aui.house.behavior")))
                            .append("</span><div class='house-mode-segments'>");
                    for (HouseResidentMode mode : HouseResidentMode.values()) {
                        html.append(button("house-context:mode:" + mode.name().toLowerCase(Locale.ROOT),
                                modeLabel(mode), "house-mode-button" + (resident.mode() == mode ? " active" : "")));
                    }
                    html.append("</div>");
                }
                html.append(button("house-context:rename", tr("screen.find_me.aui.rename"), "house-menu-button"));
                String moveKey = SIDE_HOME.equals(contextSide)
                        ? "screen.find_me.aui.house.leave_current_home"
                        : resident.otherHouse() ? "screen.find_me.aui.house.transfer_current_home"
                        : "screen.find_me.aui.house.join_current_home";
                html.append(button("house-context:move", tr(moveKey), "house-menu-button"));
            }
        }
        return html.append(button("house-context:close", tr("screen.find_me.cancel"), "house-menu-button muted"))
                .append("</div>").toString();
    }

    private String assignmentMarkup(List<HousePagePacket.Resident> home, List<HousePagePacket.Resident> outside,
                                    int innerWidth, int totalVisible, int total) {
        int paneGap = scaled(6);
        int paneWidth = Math.max(1, (innerWidth - paneGap) / 2);
        return new StringBuilder("<div class='house-workspace'><div id='findme-house-dual' class='house-dual' style='width:")
                .append(innerWidth).append("px'>")
                .append(paneMarkup(SIDE_HOME, home, paneWidth, page.residents().size()))
                .append("<div class='pane-divider' style='width:").append(paneGap).append("px'></div>")
                .append(paneMarkup(SIDE_OUTSIDE, outside, paneWidth, page.available().size()))
                .append("</div>").append(footerMarkup(totalVisible, total, innerWidth)).append("</div>").toString();
    }

    private String paneMarkup(String side, List<HousePagePacket.Resident> residents, int paneWidth, int sourceCount) {
        boolean home = SIDE_HOME.equals(side);
        String title = tr(home ? "screen.find_me.aui.house.home_creatures" : "screen.find_me.aui.house.outside_creatures");
        String subtitle = tr(home ? "screen.find_me.aui.house.home_list_subtitle" : "screen.find_me.aui.house.outside_list_subtitle");
        int firstVisible = normalizedFirstVisible(side, residents.size());
        int lastVisible = Math.min(residents.size(), firstVisible + VISIBLE_CARDS_PER_PANE);
        StringBuilder html = new StringBuilder("<div id='").append(paneId(side)).append("' class='house-pane ").append(side)
                .append("' data-house-side='").append(side).append("' style='width:").append(paneWidth).append("px'>")
                .append("<div class='pane-head'><b>").append(twoDigits(sourceCount)).append("</b><span><strong>")
                .append(escape(title)).append("</strong><small>").append(escape(subtitle)).append("</small></span></div>")
                .append("<div class='house-list-shell'><div id='").append(listId(side))
                .append("' class='house-list' data-side='").append(side).append("'>");
        for (int i = firstVisible; i < lastVisible; i++) {
            html.append(cardMarkup(residents.get(i), i, side, paneWidth, i < lastVisible - 1));
        }
        if (residents.isEmpty()) {
            String emptyKey = page.readOnly() && !home
                    ? "screen.find_me.aui.house.read_only_hint"
                    : home ? "screen.find_me.aui.house.no_residents" : "screen.find_me.aui.house.no_home_candidates";
            html.append("<div class='house-empty'>").append(escape(tr(emptyKey))).append("</div>");
        }
        html.append("</div>");
        if (residents.size() > 4) {
            html.append("<div id='").append(railId(side)).append("' class='house-scroll-rail'><div id='")
                    .append(thumbId(side)).append("' class='house-scroll-thumb'></div></div>");
        }
        return html.append("</div></div>").toString();
    }

    private String cardMarkup(HousePagePacket.Resident resident, int index, String side, int paneWidth, boolean gapAfter) {
        int cardHeight = scaled(CARD_HEIGHT);
        String value = side + "|" + resident.uuid();
        String action = page.readOnly() ? "" : " data-action='toggle-home' data-value='" + value + "'";
        return "<div class='house-entry" + (resident.otherHouse() ? " foreign-house" : "")
                + "' style='width:" + paneWidth + "px;height:" + cardHeight + "px"
                + (gapAfter ? ";margin-bottom:" + scaled(CARD_GAP) + "px" : "")
                + "' data-house-entry='1' data-house-source='" + side + "' data-uuid='" + resident.uuid() + "'" + action + ">"
                + "<div class='house-entry-marker'></div><div class='house-entry-preview' style='width:" + cardHeight
                + "px;height:" + cardHeight + "px'><findme-preview data-uuid='" + resident.uuid()
                + "' data-preview-scale='0.78'></findme-preview></div><div class='house-entry-copy' style='left:"
                + (cardHeight + scaled(5)) + "px'><b>" + escape(resident.name()) + "</b><small>"
                + escape(cardStatus(resident, side)) + "</small></div>"
                + (resident.otherHouse() ? "<em class='house-entry-other-home'>"
                + escape(tr("screen.find_me.aui.house.other_home")) + "</em>" : "")
                + "<span class='house-entry-number'>"
                + twoDigits(index + 1) + "</span></div>";
    }

    private String partnerMarkup(List<HousePagePacket.Resident> residents, int innerWidth) {
        int firstVisible = normalizePartnerFirst(residents.size());
        int lastVisible = Math.min(residents.size(), firstVisible + VISIBLE_PARTNER_CARDS);
        int gap = scaled(7);
        int cardWidth = Math.max(1, (innerWidth - gap * 3) / 4);
        int cardHeight = scaled(72);
        StringBuilder html = new StringBuilder("<div class='house-workspace'><div class='partner-head'><b>")
                .append(twoDigits(page.residents().size())).append("</b><span><strong>")
                .append(escape(tr("screen.find_me.aui.house.home_partners"))).append("</strong><small>")
                .append(escape(tr("screen.find_me.aui.house.home_partners_grid_subtitle")))
                .append("</small></span>");
        if (!page.readOnly()) {
            html.append(button("house-view:assignment", tr("screen.find_me.aui.house.manage_assignment"), "partner-manage"));
        }
        html.append("</div><div class='house-partner-grid-shell'><div class='house-partner-grid'>");
        for (int i = firstVisible; i < lastVisible; i++) {
            int column = (i - firstVisible) % 4;
            if (column == 0) {
                html.append("<div class='house-partner-row' style='width:").append(innerWidth).append("px")
                        .append(i + 4 < lastVisible ? ";margin-bottom:" + scaled(4) + "px" : "").append("'>");
            }
            HousePagePacket.Resident resident = residents.get(i);
            html.append("<div class='house-partner-card' data-house-entry='1' data-house-source='").append(SIDE_HOME)
                    .append("' data-uuid='").append(resident.uuid()).append("' style='width:").append(cardWidth).append("px;height:")
                    .append(cardHeight).append("px")
                    .append(column < 3 && i < lastVisible - 1 ? ";margin-right:" + gap + "px" : "")
                    .append("'><div class='house-partner-preview'><findme-preview data-uuid='")
                    .append(resident.uuid()).append("' data-preview-scale='0.68'></findme-preview></div><span class='house-partner-number'>")
                    .append(twoDigits(i + 1)).append("</span><div class='house-partner-copy'><strong>")
                    .append(escape(resident.name())).append("</strong><small>")
                    .append(escape(cardStatus(resident, SIDE_HOME))).append("</small></div></div>");
            if (column == 3 || i == lastVisible - 1) html.append("</div>");
        }
        if (residents.isEmpty()) {
            html.append("<div class='house-partner-empty'>").append(escape(tr("screen.find_me.aui.house.no_residents"))).append("</div>");
        }
        html.append("</div>");
        if (residents.size() > VISIBLE_PARTNER_CARDS) {
            html.append("<div id='findme-house-partner-rail' class='house-partner-scroll-rail'><div id='findme-house-partner-thumb' class='house-partner-scroll-thumb'></div></div>");
        }
        return html.append("</div>").append(footerMarkup(residents.size(), page.residents().size(), innerWidth)).append("</div>").toString();
    }

    private String footerMarkup(int visible, int total, int innerWidth) {
        int countLeft = Math.max(0, innerWidth - scaled(28));
        return new StringBuilder("<div class='house-footer'><input id='house-search' class='house-search' type='text' placeholder='")
                .append(escape(tr("screen.find_me.search"))).append("' value='").append(escape(search)).append("'>")
                .append(button("apply-search", tr("screen.find_me.aui.go"), "house-search-button"))
                .append("<div class='house-count' style='left:").append(countLeft).append("px'><b>")
                .append(twoDigits(visible)).append("</b><span>/ ")
                .append(twoDigits(total)).append("</span></div></div>").toString();
    }

    private String cardStatus(HousePagePacket.Resident resident, String side) {
        String state = resident.dead()
                ? tr("screen.find_me.state_dead")
                : resident.active() ? tr("screen.find_me.manage.state_deployed")
                : resident.otherHouse() ? tr("screen.find_me.aui.house.other_home")
                : tr(SIDE_HOME.equals(side) ? "screen.find_me.aui.house.at_home" : "screen.find_me.aui.house.not_home");
        return kindLabel(resident.kind()) + " / " + state;
    }

    private String kindLabel(CompanionKind kind) {
        return tr(kind == CompanionKind.MOUNT ? "screen.find_me.mounts" : "screen.find_me.companions");
    }

    private String modeLabel(HouseResidentMode mode) {
        HouseResidentMode value = mode == null ? HouseResidentMode.WANDER : mode;
        return tr("screen.find_me.aui.house.mode." + value.name().toLowerCase(Locale.ROOT));
    }

    private String sideSummary(int count, String label, String subtitle, String targetView, boolean active) {
        String stage = "assignment".equals(targetView) ? " house-side-stage-1" : " house-side-stage-2";
        return "<div class='side-row" + stage + (active ? " active" : "") + "' data-action='house-view:" + targetView
                + "'><b>" + twoDigits(count) + "</b><span><strong>"
                + escape(label) + "</strong><small>" + escape(subtitle) + "</small></span></div>";
    }

    private List<HousePagePacket.Resident> filtered(List<HousePagePacket.Resident> source, boolean newestFirst) {
        List<HousePagePacket.Resident> result;
        if (search.isBlank()) {
            result = new ArrayList<>(source);
        } else {
            String needle = search.toLowerCase(Locale.ROOT);
            result = source.stream()
                    .filter(resident -> resident.name().toLowerCase(Locale.ROOT).contains(needle)
                            || resident.entityType().toLowerCase(Locale.ROOT).contains(needle))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        if (newestFirst) {
            Collections.reverse(result);
            result.sort((left, right) -> Boolean.compare(left.otherHouse(), right.otherHouse()));
        }
        return List.copyOf(result);
    }

    private int normalizedFirstVisible(String side, int size) {
        int maximum = Math.max(0, size - VISIBLE_CARDS_PER_PANE);
        int requested = SIDE_HOME.equals(side) ? homeFirstVisible : outsideFirstVisible;
        int first = Math.max(0, Math.min(maximum, requested));
        if (SIDE_HOME.equals(side)) homeFirstVisible = first;
        else outsideFirstVisible = first;
        return first;
    }

    private int normalizePartnerFirst(int size) {
        int maximum = maximumPartnerFirst(size);
        partnerFirstVisible = Math.max(0, Math.min(maximum, (partnerFirstVisible / 4) * 4));
        return partnerFirstVisible;
    }

    private static int maximumPartnerFirst(int size) {
        int overflow = Math.max(0, size - VISIBLE_PARTNER_CARDS);
        return ((overflow + 3) / 4) * 4;
    }

    private HousePagePacket.Resident resident(UUID uuid) {
        if (uuid == null) return null;
        for (HousePagePacket.Resident resident : page.residents()) if (uuid.equals(resident.uuid())) return resident;
        for (HousePagePacket.Resident resident : page.available()) if (uuid.equals(resident.uuid())) return resident;
        return null;
    }

    private String layoutStyle() {
        int innerWidth = Math.max(1, width - scaled(75) - scaled(10) - scaled(9));
        int partnerGap = scaled(7);
        int partnerCardWidth = Math.max(1, (innerWidth - partnerGap * 3) / 4);
        return "height:" + Math.max(1, height) + "px"
                + ";--fm-top:" + scaled(35) + "px"
                + ";--fm-body:" + Math.max(1, height - scaled(35)) + "px"
                + ";--fm-side:" + scaled(75) + "px"
                + ";--fm-house-left:" + scaled(75) + "px"
                + ";--fm-house-top:" + scaled(35) + "px"
                + ";--fm-house-width:" + Math.max(1, width - scaled(75)) + "px"
                + ";--fm-house-list:" + scaled(148) + "px"
                + ";--fm-house-main:" + (28 + scaled(148)) + "px"
                + ";--fm-house-entry:" + scaled(34) + "px"
                + ";--fm-house-partner-card-width:" + partnerCardWidth + "px"
                + ";--fm-house-partner-card-height:" + scaled(72) + "px"
                + ";--fm-house-footer:" + scaled(21) + "px"
                + ";--fm-fold-left:" + Math.max(0, scaled(75) - 10) + "px"
                + ";--fm-fold-dark-top:" + Math.max(0, scaled(35) - 5) + "px"
                + ";--fm-fold-light-top:" + scaled(39) + "px";
    }

    private int scaled(int base) {
        double scale = Math.min(Math.max(1, width) / 427.0, Math.max(1, height) / 240.0);
        return Math.max(1, (int) Math.round(base * scale));
    }

    private static String listId(String side) {
        return "findme-house-" + side + "-list";
    }

    private static String paneId(String side) {
        return "findme-house-" + side + "-pane";
    }

    private static String railId(String side) {
        return "findme-house-" + side + "-rail";
    }

    private static String thumbId(String side) {
        return "findme-house-" + side + "-thumb";
    }

    private static void addClass(Element element, String className) {
        if (element == null || className == null || className.isBlank()) return;
        String classes = element.getAttribute("class");
        classes = classes == null ? "" : classes.trim();
        if ((" " + classes + " ").contains(" " + className + " ")) return;
        element.setAttribute("class", classes.isBlank() ? className : classes + " " + className);
    }

    private static void removeClass(Element element, String className) {
        if (element == null || className == null || className.isBlank()) return;
        String classes = element.getAttribute("class");
        if (classes == null || classes.isBlank()) return;
        element.setAttribute("class", (" " + classes.trim() + " ").replace(" " + className + " ", " ").trim());
    }

    private static String twoDigits(int value) {
        return String.format(Locale.ROOT, "%02d", Math.max(0, value));
    }

    private static String button(String action, String text, String classes) {
        return "<div class='" + classes + "' data-action='" + action + "'>" + escape(text) + "</div>";
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private enum HouseView {
        ASSIGNMENT,
        PARTNERS
    }

    private enum HouseContextPage {
        MAIN,
        RENAME,
        DETAIL
    }

    private static final class MinecraftAccess {
        private MinecraftAccess() {
        }

        static void setScreen(FindMeAuiHouseScreen screen) {
            net.minecraft.client.Minecraft.getInstance().setScreen(screen);
        }
    }
}
