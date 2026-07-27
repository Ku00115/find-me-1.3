package com.kuzhi.findme.client;

/** Clears presentation-only state that must never cross a server/world session boundary. */
public final class ClientFindMeSessionState {
    private ClientFindMeSessionState() {
    }

    public static void reset() {
        ClientCompanionState.reset();
        ClientCompanionWheelController.reset();
        ClientVehicleState.reset();
        ClientCobblemonState.reset();
        ClientMountRosterState.reset();
        ClientMountRosterTransactionState.reset();
        ClientCompanionTeamState.reset();
        ClientWheelSelectionMemory.reset();
        ClientFindMeModuleState.reset();
        FindMeAuiManageScreen.resetSession();
        CompanionDetailPreviewRenderer.reset();
        ClientCameraLock.clear();
        ClientRidingCameraState.clear();
        ClientContractCamera.stop();
        ClientMountApproachPresentationState.clear();
        ClientContractRenderState.stop();
        ClientRideHomeReadyState.clear();
        ClientRideHomeOrientationState.clear();
        ClientRideHomeTransitionState.clear();
        ClientExternalRideHandoffState.clear();
        ClientRescueMagicRenderState.clear();
        ClientStorageEffectState.clear();
        ClientBurrowEffectState.clear();
        ClientVehicleSealEffectState.clear();
        ClientCompanionDialogueState.clear();
        ClientEvents.resetSessionInput();
    }
}
