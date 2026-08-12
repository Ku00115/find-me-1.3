package com.kuzhi.findme.api.client;

import com.kuzhi.findme.common.CompanionTeamTarget;

import java.util.List;
import java.util.UUID;

/**
 * Small client-side bridge for addons that need FindMe's manager shell while
 * keeping their own companion actions and persistence.
 */
public interface FindMeExternalCompanionEditor {
    default CompanionTeamTarget teamTarget() {
        return CompanionTeamTarget.COMPANION;
    }

    default List<UUID> editorSlots(int teamIndex) {
        return List.of();
    }

    String title();

    String primaryActionLabel(UUID companionUuid);

    default String primaryActionLabel(UUID companionUuid, int slotIndex) {
        return primaryActionLabel(companionUuid);
    }

    /** Returns the action id currently assigned to this exact editor slot. */
    default String currentActionId(UUID companionUuid, int slotIndex) {
        return null;
    }

    List<Action> actions(UUID companionUuid);

    default List<Action> actions(UUID companionUuid, int slotIndex) {
        return actions(companionUuid);
    }

    boolean acceptsWarehouseType(String entityType);

    void onPrimaryAction(UUID companionUuid);

    void onAction(UUID companionUuid, String actionId);

    default void onAction(UUID companionUuid, int slotIndex, String actionId) {
        onAction(companionUuid, actionId);
    }

    void onWarehouseSelection(UUID companionUuid);

    default void onClose() {
    }

    record Action(String id, String label) {
    }
}
