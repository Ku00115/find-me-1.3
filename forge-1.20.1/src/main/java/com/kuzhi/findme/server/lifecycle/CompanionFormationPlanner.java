package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.common.CompanionMoveType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.Mth;

/** Produces stable, collision-aware slots without taking ownership of entity movement. */
final class CompanionFormationPlanner {
    private static final double ARC_RADIANS = Math.toRadians(160.0);
    private static final double MIN_GAP = 1.5;

    private CompanionFormationPlanner() {
    }

    static Map<UUID, Offset> plan(List<Member> input, double anchorWidth) {
        return plan(input, anchorWidth, false);
    }

    static Map<UUID, Offset> planGuardPosts(List<Member> input) {
        return plan(input, 0.6, true);
    }

    private static Map<UUID, Offset> plan(List<Member> input, double anchorWidth, boolean fullRing) {
        if (input == null || input.isEmpty()) return Map.of();
        ArrayList<Member> members = new ArrayList<>(input);
        members.sort(Comparator.comparing(Member::uuid));
        double maxFootprint = members.stream()
                .mapToDouble(member -> Math.max(member.width(), member.depth()))
                .map(value -> Mth.clamp(value, 0.9, 24.0))
                .max().orElse(1.0);
        double maxHeight = members.stream().mapToDouble(Member::height)
                .map(value -> Mth.clamp(value, 0.9, 32.0)).max().orElse(1.8);
        int count = members.size();
        double step = count <= 1 ? ARC_RADIANS
                : fullRing ? Math.PI * 2.0 / count : ARC_RADIANS / (count - 1);
        double spacingRadius = count <= 1 ? 0.0
                : (maxFootprint + MIN_GAP) / (2.0 * Math.sin(step * 0.5));
        double radius = Math.max(Math.max(0.6, anchorWidth) * 0.5 + maxFootprint * 0.5 + 2.0,
                spacingRadius);
        LinkedHashMap<UUID, Offset> result = new LinkedHashMap<>();
        int flyingIndex = 0;
        for (int index = 0; index < count; index++) {
            Member member = members.get(index);
            double angle = count <= 1 ? 0.0
                    : fullRing ? step * index : -ARC_RADIANS * 0.5 + step * index;
            double lateral = Math.sin(angle) * radius;
            double rear = fullRing ? Math.cos(angle) * radius : Math.max(1.0, Math.cos(angle) * radius);
            double vertical = member.flying()
                    ? (flyingIndex++ % 3) * Math.max(1.75, maxHeight * 0.28) : 0.0;
            result.put(member.uuid(), new Offset(lateral, rear, vertical));
        }
        return Map.copyOf(result);
    }

    record Member(UUID uuid, double width, double depth, double height, CompanionMoveType moveType) {
        Member(UUID uuid, double width, double depth, double height, boolean flying) {
            this(uuid, width, depth, height, flying ? CompanionMoveType.FLY : CompanionMoveType.WALK);
        }

        boolean flying() {
            return moveType == CompanionMoveType.FLY;
        }
    }

    record Offset(double lateral, double rear, double vertical) {
    }
}
