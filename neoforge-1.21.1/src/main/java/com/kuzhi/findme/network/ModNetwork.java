package com.kuzhi.findme.network;

import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import com.kuzhi.findme.network.FindMeNetworkContext;

public final class ModNetwork {
    private static final String PROTOCOL = "85";
    public static final Object channel = new Object();

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToServer(CompanionCommandPacket.TYPE, CompanionCommandPacket.STREAM_CODEC, (packet, context) -> CompanionCommandPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(CompanionWheelIntentPacket.TYPE, CompanionWheelIntentPacket.STREAM_CODEC, (packet, context) -> CompanionWheelIntentPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(CompanionWheelIntentResultPacket.TYPE, CompanionWheelIntentResultPacket.STREAM_CODEC, (packet, context) -> CompanionWheelIntentResultPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(MountRosterIntentPacket.TYPE, MountRosterIntentPacket.STREAM_CODEC, (packet, context) -> MountRosterIntentPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(MountRosterIntentResultPacket.TYPE, MountRosterIntentResultPacket.STREAM_CODEC, (packet, context) -> MountRosterIntentResultPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(CompanionTacticalCommandPacket.TYPE, CompanionTacticalCommandPacket.STREAM_CODEC, (packet, context) -> CompanionTacticalCommandPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(CompanionTeamTacticalCommandPacket.TYPE, CompanionTeamTacticalCommandPacket.STREAM_CODEC, (packet, context) -> CompanionTeamTacticalCommandPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(CompanionListPacket.TYPE, CompanionListPacket.STREAM_CODEC, (packet, context) -> CompanionListPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(CobblemonCommandPacket.TYPE, CobblemonCommandPacket.STREAM_CODEC, (packet, context) -> CobblemonCommandPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(CobblemonPartyPacket.TYPE, CobblemonPartyPacket.STREAM_CODEC, (packet, context) -> CobblemonPartyPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(ExternalRideHandoffPacket.TYPE, ExternalRideHandoffPacket.STREAM_CODEC, (packet, context) -> ExternalRideHandoffPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(DeadCompanionListPacket.TYPE, DeadCompanionListPacket.STREAM_CODEC, (packet, context) -> DeadCompanionListPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(RecoveryCompanionListPacket.TYPE, RecoveryCompanionListPacket.STREAM_CODEC, (packet, context) -> RecoveryCompanionListPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(BackupWarehousePacket.TYPE, BackupWarehousePacket.STREAM_CODEC, (packet, context) -> BackupWarehousePacket.handle(packet, legacyContext(context)));
        registrar.playToClient(ContractCameraPacket.TYPE, ContractCameraPacket.STREAM_CODEC, (packet, context) -> ContractCameraPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(RideHomeCameraPacket.TYPE, RideHomeCameraPacket.STREAM_CODEC, (packet, context) -> RideHomeCameraPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(RideHomeDestinationPacket.TYPE, RideHomeDestinationPacket.STREAM_CODEC, (packet, context) -> RideHomeDestinationPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(RideHomeReadyPacket.TYPE, RideHomeReadyPacket.STREAM_CODEC, (packet, context) -> RideHomeReadyPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(WaystoneDestinationRequestPacket.TYPE, WaystoneDestinationRequestPacket.STREAM_CODEC, (packet, context) -> WaystoneDestinationRequestPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(WaystoneDestinationListPacket.TYPE, WaystoneDestinationListPacket.STREAM_CODEC, (packet, context) -> WaystoneDestinationListPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(WaystoneJourneyRequestPacket.TYPE, WaystoneJourneyRequestPacket.STREAM_CODEC, (packet, context) -> WaystoneJourneyRequestPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(RescueMagicPacket.TYPE, RescueMagicPacket.STREAM_CODEC, (packet, context) -> RescueMagicPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(StorageEffectPacket.TYPE, StorageEffectPacket.STREAM_CODEC, (packet, context) -> StorageEffectPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(CompanionTacticalFormationPacket.TYPE, CompanionTacticalFormationPacket.STREAM_CODEC, (packet, context) -> CompanionTacticalFormationPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(CompanionTacticalTargetPacket.TYPE, CompanionTacticalTargetPacket.STREAM_CODEC, (packet, context) -> CompanionTacticalTargetPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(CompanionDialoguePacket.TYPE, CompanionDialoguePacket.STREAM_CODEC, (packet, context) -> CompanionDialoguePacket.handle(packet, legacyContext(context)));
        registrar.playToServer(VehicleCommandPacket.TYPE, VehicleCommandPacket.STREAM_CODEC, (packet, context) -> VehicleCommandPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(VehicleListPacket.TYPE, VehicleListPacket.STREAM_CODEC, (packet, context) -> VehicleListPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(VehicleSealEffectPacket.TYPE, VehicleSealEffectPacket.STREAM_CODEC, (packet, context) -> VehicleSealEffectPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(SableVehiclePreviewCapturePacket.TYPE, SableVehiclePreviewCapturePacket.STREAM_CODEC, (packet, context) -> SableVehiclePreviewCapturePacket.handle(packet, legacyContext(context)));
        registrar.playToClient(SableVehiclePreviewDeletePacket.TYPE, SableVehiclePreviewDeletePacket.STREAM_CODEC, (packet, context) -> SableVehiclePreviewDeletePacket.handle(packet, legacyContext(context)));
        registrar.playToClient(SableVehiclePreviewTransferPacket.TYPE, SableVehiclePreviewTransferPacket.STREAM_CODEC, (packet, context) -> SableVehiclePreviewTransferPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(CompanionEffectStylePacket.TYPE, CompanionEffectStylePacket.STREAM_CODEC, (packet, context) -> CompanionEffectStylePacket.handle(packet, legacyContext(context)));
        registrar.playToServer(CompanionAnimationStylePacket.TYPE, CompanionAnimationStylePacket.STREAM_CODEC, (packet, context) -> CompanionAnimationStylePacket.handle(packet, legacyContext(context)));
        registrar.playToServer(CompanionSpellSlotPacket.TYPE, CompanionSpellSlotPacket.STREAM_CODEC, (packet, context) -> CompanionSpellSlotPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(CompanionSpellSlotCandidatesPacket.TYPE, CompanionSpellSlotCandidatesPacket.STREAM_CODEC, (packet, context) -> CompanionSpellSlotCandidatesPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(PackAnimationPresetModePacket.TYPE, PackAnimationPresetModePacket.STREAM_CODEC, (packet, context) -> PackAnimationPresetModePacket.handle(packet, legacyContext(context)));
        registrar.playToClient(PackAnimationPresetListPacket.TYPE, PackAnimationPresetListPacket.STREAM_CODEC, (packet, context) -> PackAnimationPresetListPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(PackAnimationPresetStylePacket.TYPE, PackAnimationPresetStylePacket.STREAM_CODEC, (packet, context) -> PackAnimationPresetStylePacket.handle(packet, legacyContext(context)));
        registrar.playToServer(PackAnimationPresetMotionPacket.TYPE, PackAnimationPresetMotionPacket.STREAM_CODEC, (packet, context) -> PackAnimationPresetMotionPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(PackEntityPresetUpdatePacket.TYPE, PackEntityPresetUpdatePacket.STREAM_CODEC, (packet, context) -> PackEntityPresetUpdatePacket.handle(packet, legacyContext(context)));
        registrar.playToServer(PackEditorActionPacket.TYPE, PackEditorActionPacket.STREAM_CODEC, (packet, context) -> PackEditorActionPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(CompanionTeamCommandPacket.TYPE, CompanionTeamCommandPacket.STREAM_CODEC, (packet, context) -> CompanionTeamCommandPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(CompanionTeamListPacket.TYPE, CompanionTeamListPacket.STREAM_CODEC, (packet, context) -> CompanionTeamListPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(HousePagePacket.TYPE, HousePagePacket.STREAM_CODEC, (packet, context) -> HousePagePacket.handle(packet, legacyContext(context)));
        registrar.playToServer(HouseCommandPacket.TYPE, HouseCommandPacket.STREAM_CODEC, (packet, context) -> HouseCommandPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(DoctorCommandPacket.TYPE, DoctorCommandPacket.STREAM_CODEC, (packet, context) -> DoctorCommandPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(DoctorPagePacket.TYPE, DoctorPagePacket.STREAM_CODEC, (packet, context) -> DoctorPagePacket.handle(packet, legacyContext(context)));
        registrar.playBidirectional(FindMeSettingsPacket.TYPE, FindMeSettingsPacket.STREAM_CODEC, (packet, context) -> FindMeSettingsPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(WarehouseEntityCommandPacket.TYPE, WarehouseEntityCommandPacket.STREAM_CODEC, (packet, context) -> WarehouseEntityCommandPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(WarehouseOperationResultPacket.TYPE, WarehouseOperationResultPacket.STREAM_CODEC, (packet, context) -> WarehouseOperationResultPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(FindMeModuleCommandPacket.TYPE, FindMeModuleCommandPacket.STREAM_CODEC, (packet, context) -> FindMeModuleCommandPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(FindMeServerSettingsCommandPacket.TYPE, FindMeServerSettingsCommandPacket.STREAM_CODEC, (packet, context) -> FindMeServerSettingsCommandPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(FindMeModuleStatePacket.TYPE, FindMeModuleStatePacket.STREAM_CODEC, (packet, context) -> FindMeModuleStatePacket.handle(packet, legacyContext(context)));
        registrar.playToClient(OpenFindMeManageScreenPacket.TYPE, OpenFindMeManageScreenPacket.STREAM_CODEC, (packet, context) -> OpenFindMeManageScreenPacket.handle(packet, legacyContext(context)));
        registrar.playToClient(OpenFindMeHudEditorPacket.TYPE, OpenFindMeHudEditorPacket.STREAM_CODEC, (packet, context) -> OpenFindMeHudEditorPacket.handle(packet, legacyContext(context)));
        registrar.playToServer(CompanionForwardTravelTeleportPacket.TYPE, CompanionForwardTravelTeleportPacket.STREAM_CODEC, (packet, context) -> CompanionForwardTravelTeleportPacket.handle(packet, legacyContext(context)));
    }

    private static Supplier<FindMeNetworkContext.Context> legacyContext(IPayloadContext context) {
        return () -> FindMeNetworkContext.Context.of(context);
    }

    public static void sendToServer(CompanionCommandPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToServer(CompanionTacticalCommandPacket packet) {
        PacketDistributor.sendToServer(packet);
    }
    public static void sendToServer(CompanionTeamTacticalCommandPacket packet) { PacketDistributor.sendToServer(packet); }

    public static void sendToServer(CompanionWheelIntentPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToServer(MountRosterIntentPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToServer(CobblemonCommandPacket packet) { PacketDistributor.sendToServer(packet); }

    public static void sendToServer(CompanionEffectStylePacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(CompanionAnimationStylePacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(CompanionSpellSlotPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(CompanionTeamCommandPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(FindMeSettingsPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(WarehouseEntityCommandPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(HouseCommandPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(DoctorCommandPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(PackAnimationPresetStylePacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(PackAnimationPresetMotionPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(PackEntityPresetUpdatePacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(PackEditorActionPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(FindMeModuleCommandPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(FindMeServerSettingsCommandPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(RideHomeReadyPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(WaystoneDestinationRequestPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(WaystoneJourneyRequestPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendToServer(CompanionForwardTravelTeleportPacket packet) { PacketDistributor.sendToServer(packet); }

    public static void sendToServer(VehicleCommandPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, CompanionListPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, CompanionWheelIntentResultPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, MountRosterIntentResultPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, CobblemonPartyPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, ExternalRideHandoffPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, PackAnimationPresetModePacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, PackAnimationPresetListPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, VehicleListPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, CompanionTeamListPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, HousePagePacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, DoctorPagePacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, FindMeSettingsPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, WarehouseOperationResultPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, FindMeModuleStatePacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, OpenFindMeManageScreenPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, OpenFindMeHudEditorPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, DeadCompanionListPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, RecoveryCompanionListPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, BackupWarehousePacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, ContractCameraPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, RideHomeCameraPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, RideHomeDestinationPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, WaystoneDestinationListPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, RescueMagicPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, StorageEffectPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, CompanionTacticalTargetPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, VehicleSealEffectPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, SableVehiclePreviewCapturePacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, SableVehiclePreviewDeletePacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, SableVehiclePreviewTransferPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayersNear(ServerLevel level, Vec3 center, double radius, RescueMagicPacket packet) {
        if (level != null && center != null) {
            PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, radius, packet);
        }
    }

    public static void sendToPlayersNear(ServerLevel level, Vec3 center, double radius, StorageEffectPacket packet) {
        if (level != null && center != null) {
            PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, radius, packet);
        }
    }

    public static void sendToPlayersNear(ServerLevel level, Vec3 center, double radius,
                                         CompanionTacticalFormationPacket packet) {
        if (level != null && center != null)
            PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, radius, packet);
    }

    public static void sendToPlayersNear(ServerLevel level, Vec3 center, double radius, VehicleSealEffectPacket packet) {
        if (level != null && center != null) {
            PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, radius, packet);
        }
    }

    public static void sendToPlayer(ServerPlayer player, CompanionDialoguePacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayer(ServerPlayer player, CompanionSpellSlotCandidatesPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

}
