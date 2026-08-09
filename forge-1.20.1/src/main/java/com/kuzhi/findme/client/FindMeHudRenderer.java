package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.CompanionListPacket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Lightweight vanilla HUD for deployed FindMe companions. */
public final class FindMeHudRenderer {
    private static final int CARD_HEIGHT = ClientFindMeHudLayout.HUD_CARD_HEIGHT;
    private static final int GAP = ClientFindMeHudLayout.HUD_GAP;
    private static final int SAMPLE_ENTRIES = ClientFindMeHudLayout.HUD_MAX_ENTRIES;

    private FindMeHudRenderer() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null
                || !ClientFindMeHudLayout.current().visible()) {
            return;
        }
        List<HudEntry> entries = deployedEntries(minecraft);
        if (entries.isEmpty()) return;
        ClientFindMeHudLayout.Layout layout = ClientFindMeHudLayout.current();
        drawEntries(graphics, minecraft.font, entries, layout,
                minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), false,
                (float) layout.scale());
    }

    static void renderEditor(GuiGraphics graphics, ClientFindMeHudLayout.Layout layout,
                             int width, int height, float zoom) {
        Minecraft minecraft = Minecraft.getInstance();
        ArrayList<HudEntry> samples = new ArrayList<>();
        samples.add(HudEntry.sample("screen.find_me.hud.sample_companion",
                "screen.find_me.wheel_state_deployed", 1.0f, "20 / 20", "deployed"));
        samples.add(HudEntry.sample("screen.find_me.hud.sample_critical",
                "screen.find_me.wheel_state_critical", 0.12f, "2 / 20", "critical"));
        samples.add(HudEntry.sample("screen.find_me.hud.sample_companion",
                "screen.find_me.wheel_state_deployed", 0.8f, "16 / 20", "deployed"));
        samples.add(HudEntry.sample("screen.find_me.hud.sample_critical",
                "screen.find_me.wheel_state_critical", 0.5f, "10 / 20", "critical"));
        samples.add(HudEntry.sample("screen.find_me.hud.sample_companion",
                "screen.find_me.wheel_state_deployed", 1.0f, "20 / 20", "deployed"));
        samples.add(HudEntry.sample("screen.find_me.hud.sample_critical",
                "screen.find_me.wheel_state_critical", 0.25f, "5 / 20", "critical"));
        drawEntries(graphics, minecraft.font, samples, layout, width, height, true, zoom);
    }

    public static void refresh() {
        // Vanilla rendering reads the current client state every frame.
    }

    public static void reset() {
        // There is no retained document or render-thread state to dispose.
    }

    static int editorHeight() {
        return editorHeight(1.0f);
    }

    static int editorWidth(float zoom) {
        return Math.max(1, Math.round(ClientFindMeHudLayout.displayWidth() * zoom));
    }

    static int editorWidth(double zoom) {
        return Math.max(1, (int) Math.round(ClientFindMeHudLayout.displayWidth() * zoom));
    }

    static int editorHeight(float zoom) {
        return Math.max(1, Math.round((SAMPLE_ENTRIES * ClientFindMeHudLayout.displayCardHeight()
                + (SAMPLE_ENTRIES - 1) * ClientFindMeHudLayout.displayGap()) * zoom));
    }

    static int editorHeight(double zoom) {
        return Math.max(1, (int) Math.round((SAMPLE_ENTRIES * ClientFindMeHudLayout.displayCardHeight()
                + (SAMPLE_ENTRIES - 1) * ClientFindMeHudLayout.displayGap()) * zoom));
    }

    private static void drawEntries(GuiGraphics graphics, Font font, List<HudEntry> entries,
                                    ClientFindMeHudLayout.Layout layout, int width, int height,
                                    boolean editor, float zoom) {
        int contentWidth = editor ? editorWidth(zoom) : ClientFindMeHudLayout.displayWidth(layout.scale());
        int contentHeight = editor ? editorHeight(zoom) : runtimeHeight(entries.size(), layout.scale());
        int left = ClientFindMeHudLayout.left(layout, width, contentWidth);
        int top = ClientFindMeHudLayout.top(layout, height, contentHeight);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0f);
        float scale = ClientFindMeHudLayout.HUD_SCALE * zoom;
        graphics.pose().scale(scale, scale, 1.0f);
        try {
            int index = 0;
            for (HudEntry hudEntry : entries) {
                if (index++ >= ClientFindMeHudLayout.HUD_MAX_ENTRIES) break;
                drawCard(graphics, font, hudEntry, 0, (index - 1) * (CARD_HEIGHT + GAP), editor);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private static int runtimeHeight(int entryCount, double layoutScale) {
        int visibleCount = Math.min(ClientFindMeHudLayout.HUD_MAX_ENTRIES, Math.max(0, entryCount));
        if (visibleCount <= 0) return 0;
        return ClientFindMeHudLayout.displayCardHeight(layoutScale) * visibleCount
                + ClientFindMeHudLayout.displayGap(layoutScale) * (visibleCount - 1);
    }

    private static void drawCard(GuiGraphics graphics, Font font, HudEntry hudEntry,
                                 int x, int y, boolean editor) {
        int stateColor = stateColor(hudEntry.stateClass());
        graphics.fill(x, y, x + ClientFindMeHudLayout.HUD_WIDTH, y + CARD_HEIGHT, 0xED151D22);
        graphics.fill(x, y, x + 3, y + CARD_HEIGHT, stateColor);
        graphics.fill(x + 3, y, x + 8, y + CARD_HEIGHT, withAlpha(stateColor, 0xCC));

        int textX = x + 14;
        int stateRight = x + ClientFindMeHudLayout.HUD_WIDTH - 14;
        int stateX = stateRight - font.width(hudEntry.state());
        int nameWidth = Math.max(1, stateX - textX - 5);
        String line = fit(font, hudEntry.name(), nameWidth);
        graphics.drawString(font, line, textX, y + 4, 0xFFF5FAFB);
        graphics.drawString(font, hudEntry.state(), stateX, y + 5, 0xFFAEBCC1);

        int barLeft = x + 14;
        int barRight = x + ClientFindMeHudLayout.HUD_WIDTH - 14;
        int hpTop = y + 17;
        int barTop = y + 28;
        graphics.drawString(font, hudEntry.hp(), barRight - font.width(hudEntry.hp()),
                hpTop, 0xFFD8E2E4);
        graphics.fill(barLeft, barTop, barRight, barTop + 5, 0xFF39464B);
        int filled = Math.round((barRight - barLeft) * hudEntry.healthRatio());
        if (filled > 0) graphics.fill(barLeft, barTop, barLeft + filled, barTop + 5, stateColor);

        if (editor) {
            graphics.fill(x, y + CARD_HEIGHT - 1, x + ClientFindMeHudLayout.HUD_WIDTH,
                    y + CARD_HEIGHT, 0xFF5ED4EE);
        }
    }

    private static List<HudEntry> deployedEntries(Minecraft minecraft) {
        ArrayList<HudEntry> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        appendDeployed(result, seen, CompanionKind.MOUNT, minecraft);
        appendDeployed(result, seen, CompanionKind.COMPANION, minecraft);
        return result;
    }

    private static void appendDeployed(List<HudEntry> result, Set<UUID> seen, CompanionKind kind,
                                       Minecraft minecraft) {
        for (CompanionListPacket.Entry entry : ClientCompanionState.allEntries(kind)) {
            if (entry.uuid() == null || seen.contains(entry.uuid())) continue;
            Entity entity = null;
            if (minecraft.level != null && entry.entityId() >= 0) {
                entity = minecraft.level.getEntity(entry.entityId());
            }
            boolean liveCompanion = entity instanceof LivingEntity living
                    && entry.uuid().equals(entity.getUUID()) && !living.isRemoved() && living.isAlive();
            boolean shouldShow = entry.deployed() || entry.ridden()
                    || liveCompanion && !entry.homeResident();
            if (shouldShow && seen.add(entry.uuid())) {
                float health = entry.health();
                float maxHealth = entry.maxHealth();
                if (liveCompanion) {
                    LivingEntity living = (LivingEntity) entity;
                    health = living.getHealth();
                    maxHealth = living.getMaxHealth();
                }
                CompanionWheelVisualState state = ClientCompanionWheelController.state(kind,
                        entry.uuid(), entry.alive(), entry.critical(), entry.deployed(), entry.ridden(), null);
                result.add(new HudEntry(displayName(entry), stateLabel(state), stateClass(state),
                        health, maxHealth, null, false));
            }
        }
    }

    private static String displayName(CompanionListPacket.Entry entry) {
        String custom = ClientWheelPresentationState.showCustomNames() ? entry.name() : "";
        String original = ClientWheelPresentationState.showOriginalNames() ? entry.entityType() : "";
        if (custom.isBlank()) return original;
        if (original.isBlank() || custom.equals(original)) return custom;
        return custom + " / " + original;
    }

    private static String stateLabel(CompanionWheelVisualState state) {
        return switch (state) {
            case PENDING -> translated("screen.find_me.wheel_state_pending");
            case DEPLOYED -> translated("screen.find_me.wheel_state_deployed");
            case SWITCHING -> translated("screen.find_me.wheel_state_switching");
            case CRITICAL -> translated("screen.find_me.wheel_state_critical");
            case DEAD -> translated("screen.find_me.wheel_state_dead");
            case AVAILABLE -> translated("screen.find_me.wheel_state_ready");
        };
    }

    private static String stateClass(CompanionWheelVisualState state) {
        return state.name().toLowerCase(Locale.ROOT);
    }

    private static String translated(String key) {
        return Component.translatable(key).getString();
    }

    private static int stateColor(String stateClass) {
        return switch (stateClass) {
            case "critical" -> 0xFFFF754F;
            case "dead" -> 0xFF9D3C43;
            case "pending", "switching" -> 0xFFF1C94D;
            default -> 0xFF57D183;
        };
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        return font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    record HudEntry(String name, String state, String stateClass, float health, float maxHealth,
                    String hpOverride, boolean sample) {
        static HudEntry sample(String nameKey, String stateKey, float health, String hp, String stateClass) {
            return new HudEntry(translated(nameKey), translated(stateKey), stateClass,
                    health * 20.0f, 20.0f, hp, true);
        }

        float healthRatio() {
            return maxHealth <= 0.0f ? 0.0f : Mth.clamp(health / maxHealth, 0.0f, 1.0f);
        }

        String hp() {
            if (sample) return hpOverride;
            return maxHealth > 0.0f
                    ? String.format(Locale.ROOT, "%.0f / %.0f", health, maxHealth) : "--";
        }
    }
}
