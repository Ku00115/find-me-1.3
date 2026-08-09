package com.kuzhi.findme.client;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.ApricityScreen;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.style.Cursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Apricity screen with a second, truly top-most document for transient menus. */
abstract class FindMeAuiOverlayScreen extends ApricityScreen {
    private static final String OVERLAY_PATH = "findme/overlay/context-menu.html";
    private Document cachedMainDocument;
    private Document overlayDocument;
    private String contextOverlayMarkup;
    private String previewCopyMarkup;
    private final FindMeAuiTransitionController transitionController = new FindMeAuiTransitionController();
    private boolean closingImmediately;
    private boolean reusedMainDocument;

    protected FindMeAuiOverlayScreen(String templatePath) {
        super(templatePath);
    }

    @Override
    protected void init() {
        long totalStartedAt = FindMeAuiPerformanceMonitor.start();
        closingImmediately = false;
        reusedMainDocument = cachedMainDocument != null && cachedMainDocument.isActive();
        long mainStartedAt = FindMeAuiPerformanceMonitor.start();
        if (!reusedMainDocument) {
            super.init();
            cachedMainDocument = super.getLinkedDocument();
        } else {
            attachDocument(cachedMainDocument);
        }
        FindMeAuiPerformanceMonitor.record(this,
                reusedMainDocument ? "init.main_reattach" : "init.main_create", mainStartedAt, 20.0);
        long overlayStartedAt = FindMeAuiPerformanceMonitor.start();
        if (overlayDocument == null || !overlayDocument.isActive()) {
            overlayDocument = Document.create(OVERLAY_PATH);
        } else {
            attachDocument(overlayDocument);
        }
        contextOverlayMarkup = null;
        clearOverlayMarkup();
        FindMeAuiPerformanceMonitor.record(this, "init.overlay", overlayStartedAt, 12.0);
        long setupStartedAt = FindMeAuiPerformanceMonitor.start();
        previewCopyMarkup = null;
        clearPreviewCopyMarkup();
        syncOverlaySize();
        setTransitionBlocked(false);
        if (pageTransitionsEnabled() && ClientWheelPresentationState.uiAnimations()) transitionController.enter(transitionHost);
        else transitionController.forceSettle(transitionHost);
        FindMeAuiPerformanceMonitor.record(this, "init.layout_transition", setupStartedAt, 12.0);
        FindMeAuiPerformanceMonitor.record(this, "init.total", totalStartedAt, 30.0);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        if (this.width != width || this.height != height) {
            discardCachedDocuments();
        }
        super.resize(minecraft, width, height);
        syncOverlaySize();
        forceRelayout(cachedMainDocument);
        forceRelayout(overlayDocument);
    }

    @Override
    public Document getLinkedDocument() {
        return cachedMainDocument != null && cachedMainDocument.isActive()
                ? cachedMainDocument : super.getLinkedDocument();
    }

    protected final boolean reusedMainDocument() {
        return reusedMainDocument;
    }

    protected final Document getOverlayDocument() {
        return overlayDocument;
    }

    protected final Element getOverlayRoot() {
        return overlayDocument == null ? null : overlayDocument.getElementById("findme-context-layer");
    }

    private Element getPreviewCopyRoot() {
        return overlayDocument == null ? null : overlayDocument.getElementById("findme-preview-copy-layer");
    }

    protected final void setOverlayMarkup(String markup) {
        Element root = getOverlayRoot();
        if (root == null || overlayDocument == null) return;
        String next = markup == null ? "" : markup;
        if (next.equals(contextOverlayMarkup)) return;
        syncOverlaySize();
        root.setInnerHTML(next);
        overlayDocument.rebuildSelectorIndex();
        overlayDocument.reapplyStylesFromCache();
        overlayDocument.commitStyleRecalc();
        contextOverlayMarkup = next;
    }

    protected final boolean overlayMarkupEquals(String markup) {
        String next = markup == null ? "" : markup;
        return next.equals(contextOverlayMarkup);
    }

    protected final void clearOverlayMarkup() {
        setOverlayMarkup("");
    }

    protected final void setPreviewCopyMarkup(String markup) {
        Element root = getPreviewCopyRoot();
        if (root == null || overlayDocument == null) return;
        String next = markup == null ? "" : markup;
        if (next.equals(previewCopyMarkup)) return;
        syncOverlaySize();
        root.setInnerHTML(next);
        overlayDocument.rebuildSelectorIndex();
        overlayDocument.reapplyStylesFromCache();
        previewCopyMarkup = next;
    }

    protected final void clearPreviewCopyMarkup() {
        setPreviewCopyMarkup("");
    }

    protected final boolean transitionPage(Runnable pageChange) {
        if (!pageTransitionsEnabled() || !ClientWheelPresentationState.uiAnimations()) {
            pageChange.run();
            return true;
        }
        return transitionController.start(transitionHost, pageChange, true);
    }

    protected final boolean transitionSibling(Runnable pageChange, boolean forward) {
        return transitionPage(pageChange);
    }

    protected final boolean transitionDeeper(Runnable pageChange) {
        return transitionPage(pageChange);
    }

    protected final boolean transitionDeeper(Runnable pageChange, double anchorX, double anchorY) {
        return transitionPage(pageChange);
    }

    protected final boolean transitionBack(Runnable pageChange) {
        return transitionPage(pageChange);
    }

    protected final boolean transitionClose() {
        if (!pageTransitionsEnabled() || !ClientWheelPresentationState.uiAnimations()) {
            closeImmediately();
            return true;
        }
        return transitionController.start(transitionHost, this::closeImmediately, false);
    }

    protected final boolean isPageRevealing() {
        return pageTransitionsEnabled() && ClientWheelPresentationState.uiAnimations() && transitionController.isRevealing();
    }

    protected final boolean isTransitionRunning() {
        return pageTransitionsEnabled() && ClientWheelPresentationState.uiAnimations() && transitionController.isRunning();
    }

    /** Full-page transforms are intentionally disabled; local control animations remain available. */
    protected boolean pageTransitionsEnabled() {
        return false;
    }

    /** Network-driven page updates must not leave the old fold animation active. */
    protected final void settlePageTransition() {
        if (transitionController.isRunning()) {
            transitionController.forceSettle(transitionHost);
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateTransitionPreference();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long totalStartedAt = FindMeAuiPerformanceMonitor.start();
        updateTransitionPreference();
        FindMePreviewElement.beginOverlayPass();
        if (getLinkedDocument() != null) {
            long mainStartedAt = FindMeAuiPerformanceMonitor.start();
            Base.drawScreenDocument(graphics.pose(), getLinkedDocument());
            FindMeAuiPerformanceMonitor.record(this, "render.main_document", mainStartedAt, 10.0);
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
            long previewsStartedAt = FindMeAuiPerformanceMonitor.start();
            FindMePreviewElement.renderQueuedOverlays(graphics);
            FindMeAuiPerformanceMonitor.record(this, "render.previews", previewsStartedAt, 8.0);
            renderMainDocumentOverlay(graphics);
        }
        boolean needsOverlay = contextOverlayMarkup != null && !contextOverlayMarkup.isBlank()
                || previewCopyMarkup != null && !previewCopyMarkup.isBlank()
                || transitionController.isRunning();
        if (overlayDocument != null && needsOverlay) {
            long overlayStartedAt = FindMeAuiPerformanceMonitor.start();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0f, 0.0f, 500.0f);
            Base.drawScreenDocument(graphics.pose(), overlayDocument);
            renderContextDocumentOverlay(graphics);
            graphics.pose().popPose();
            FindMeAuiPerformanceMonitor.record(this, "render.overlay_document", overlayStartedAt, 5.0);
        }
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        Cursor.drawPseudoCursor(graphics.pose());
        FindMeAuiPerformanceMonitor.record(this, "render.total", totalStartedAt, 16.0);
    }

    protected void renderMainDocumentOverlay(GuiGraphics graphics) {
    }

    protected void renderContextDocumentOverlay(GuiGraphics graphics) {
    }

    @Override
    public void onClose() {
        if (!closingImmediately && transitionClose()) return;
        closeImmediately();
    }

    @Override
    public void removed() {
        transitionController.forceSettle(transitionHost);
        detachDocument(overlayDocument);
        detachDocument(cachedMainDocument);
        Cursor.resetToDefault();
    }

    protected final void discardCachedDocuments() {
        if (overlayDocument != null && overlayDocument.isActive()) {
            attachDocument(overlayDocument);
            overlayDocument.remove();
        }
        if (cachedMainDocument != null && cachedMainDocument.isActive()) {
            attachDocument(cachedMainDocument);
            cachedMainDocument.remove();
        }
        overlayDocument = null;
        cachedMainDocument = null;
        previewCopyMarkup = null;
        contextOverlayMarkup = null;
    }

    private static void attachDocument(Document document) {
        if (document != null && document.isActive() && !Document.getAll().contains(document)) {
            Document.getAll().add(document);
        }
    }

    private static void detachDocument(Document document) {
        if (document != null) Document.getAll().remove(document);
    }

    private void syncOverlaySize() {
        if (overlayDocument == null) return;
        Element frame = overlayDocument.getElementById("findme-context-overlay-root");
        if (frame != null) {
            frame.setAttribute("style", "width:" + Math.max(1, width) + "px;height:" + Math.max(1, height)
                    + "px;--fm-context-max-height:" + Math.max(40, height - 8) + "px");
        }
    }

    private static void forceRelayout(Document document) {
        if (document == null || !document.isActive() || document.body == null) return;
        document.reapplyStylesFromCache();
        document.commitStyleRecalc();
    }

    private void setTransitionBlocked(boolean blocked) {
        if (overlayDocument == null) return;
        syncOverlaySize();
        Element blocker = overlayDocument.getElementById("findme-transition-input-blocker");
        if (blocker == null) return;
        String nextClass = blocked ? "fm-transition-input-blocker active" : "fm-transition-input-blocker";
        if (nextClass.equals(blocker.getAttribute("class"))) return;
        blocker.setClassName(nextClass);
        blocker.invalidateStyle();
        overlayDocument.commitStyleRecalc();
    }

    private void closeImmediately() {
        if (closingImmediately) return;
        closingImmediately = true;
        detachDocument(overlayDocument);
        detachDocument(cachedMainDocument);
        Cursor.resetToDefault();
        Minecraft.getInstance().setScreen(null);
    }

    private void updateTransitionPreference() {
        if (pageTransitionsEnabled() && ClientWheelPresentationState.uiAnimations()) transitionController.update(transitionHost);
        else if (transitionController.isRunning()) transitionController.forceSettle(transitionHost);
    }

    private void applyTransitionClass(String requestedClass) {
        Document document = getLinkedDocument();
        if (document == null) return;
        Element page = findPage(document);
        if (page == null) return;

        String classes = page.getAttribute("class");
        String padded = " " + (classes == null ? "" : classes.trim()) + " ";
        String clean = padded.replace(" fm-page-reveal ", " ")
                .replace(" fm-page-fold-in ", " ").trim();
        String next = requestedClass == null || requestedClass.isBlank()
                ? clean : (clean.isBlank() ? requestedClass : clean + " " + requestedClass);
        if (next.equals(classes == null ? "" : classes.trim())) return;

        List<Element> animated = new ArrayList<>();
        animated.add(page);
        for (Element element : document.getElements()) {
            if (element != page && page.contains(element) && Animation.hasAnimationSpec(element.getRawComputedStyle())) {
                animated.add(element);
            }
        }
        // Clear the previous frame before installing the next motion class. This avoids carrying a
        // folded matrix into the new page while keeping the style work to one synchronous commit.
        for (Element element : animated) clearTransitionRenderCaches(element);
        page.setClassName(next);
        page.invalidateStyle();
        document.commitStyleRecalc();
        for (Element element : animated) {
            clearInactiveAnimationState(element);
            if (!Animation.hasAnimationSpec(element.getRawComputedStyle())) clearTransitionRenderCaches(element);
        }
    }

    private static void clearInactiveAnimationState(Element element) {
        if (element == null) return;
        var style = element.getRawComputedStyle();
        if (!Animation.hasAnimationSpec(style)) Animation.updateStyle(element, style);
    }

    private static void clearTransitionRenderCaches(Element page) {
        if (page == null) return;
        // AUI's animation frame only changes the frame cache, so the raw CSS transform remains
        // "none" before and after the transition. Explicitly clear the render caches that can
        // otherwise retain the final folded matrix even after the animation spec is gone.
        page.getRenderer().transform.clear();
        page.getRenderer().opacity.clear();
        clearSurfaceRenderCaches(page);
    }

    private static void clearSurfaceRenderCaches(Element element) {
        if (element == null) return;
        element.getRenderer().filter.clear();
        element.getRenderer().backdropFilter.clear();
        element.getRenderer().box.clear();
        element.getRenderer().background.clear();
    }

    private static Element findPage(Document document) {
        if (document == null) return null;
        Element page = document.querySelector(".fm-page");
        return page != null ? page : document.querySelector(".house-page");
    }

    private final FindMeAuiTransitionController.Host transitionHost = new FindMeAuiTransitionController.Host() {
        @Override
        public void beginFoldIn() {
            applyTransitionClass("fm-page-fold-in");
        }

        @Override
        public void beginFoldOut() {
            applyTransitionClass("fm-page-reveal");
        }

        @Override
        public void finishFoldOut() {
            applyTransitionClass(null);
        }

        @Override
        public void setInputBlocked(boolean blocked) {
            setTransitionBlocked(blocked);
        }
    };
}
