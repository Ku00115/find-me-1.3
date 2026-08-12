package com.kuzhi.findme.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class CompanionTeamSelectionDraft {
    private final int capacity;
    private List<UUID> original = List.of();
    private final List<UUID> selected = new ArrayList<>();
    private List<UUID> displayOrder = List.of();

    CompanionTeamSelectionDraft(int capacity) {
        this.capacity = Math.max(0, capacity);
    }

    void begin(List<UUID> members, List<UUID> availableMembers) {
        original = members == null ? List.of() : members.stream().filter(java.util.Objects::nonNull)
                .distinct().limit(capacity).toList();
        selected.clear();
        selected.addAll(original);
        ArrayList<UUID> order = new ArrayList<>(original);
        if (availableMembers != null) {
            availableMembers.stream().filter(java.util.Objects::nonNull).filter(uuid -> !order.contains(uuid))
                    .forEach(order::add);
        }
        displayOrder = List.copyOf(order);
    }

    void clear() {
        original = List.of();
        selected.clear();
        displayOrder = List.of();
    }

    boolean toggle(UUID uuid) {
        if (uuid == null) return false;
        if (selected.remove(uuid)) return true;
        if (selected.size() >= capacity) return false;
        selected.add(uuid);
        return true;
    }

    boolean contains(UUID uuid) {
        return selected.contains(uuid);
    }

    boolean full() {
        return selected.size() >= capacity;
    }

    boolean changed() {
        return !original.equals(selected);
    }

    int size() {
        return selected.size();
    }

    List<UUID> selected() {
        return List.copyOf(selected);
    }

    List<UUID> original() {
        return original;
    }

    List<UUID> displayOrder() {
        return displayOrder;
    }
}
