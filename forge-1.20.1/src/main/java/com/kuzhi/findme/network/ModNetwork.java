package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "71";
    public static SimpleChannel channel;
    private static int nextId;

    private ModNetwork() {
    }

    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(new ResourceLocation(FindMeMod.MODID, "main"),
                () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
        nextId = 0;
        toServer(CompanionCommandPacket.class, CompanionCommandPacket::encode, CompanionCommandPacket::decode, CompanionCommandPacket::handle);
        toServer(CompanionWheelIntentPacket.class, CompanionWheelIntentPacket::encode, CompanionWheelIntentPacket::decode, CompanionWheelIntentPacket::handle);
        toClient(CompanionWheelIntentResultPacket.class, CompanionWheelIntentResultPacket::encode, CompanionWheelIntentResultPacket::decode, CompanionWheelIntentResultPacket::handle);
        toServer(MountRosterIntentPacket.class, MountRosterIntentPacket::encode, MountRosterIntentPacket::decode, MountRosterIntentPacket::handle);
        toClient(MountRosterIntentResultPacket.class, MountRosterIntentResultPacket::encode, MountRosterIntentResultPacket::decode, MountRosterIntentResultPacket::handle);
        toServer(CompanionTacticalCommandPacket.class, CompanionTacticalCommandPacket::encode, CompanionTacticalCommandPacket::decode, CompanionTacticalCommandPacket::handle);
        toClient(CompanionListPacket.class, CompanionListPacket::encode, CompanionListPacket::decode, CompanionListPacket::handle);
        toClient(ExternalRideHandoffPacket.class, ExternalRideHandoffPacket::encode, ExternalRideHandoffPacket::decode, ExternalRideHandoffPacket::handle);
        toClient(DeadCompanionListPacket.class, DeadCompanionListPacket::encode, DeadCompanionListPacket::decode, DeadCompanionListPacket::handle);
        toClient(ContractCameraPacket.class, ContractCameraPacket::encode, ContractCameraPacket::decode, ContractCameraPacket::handle);
        toClient(RideHomeCameraPacket.class, RideHomeCameraPacket::encode, RideHomeCameraPacket::decode, RideHomeCameraPacket::handle);
        toClient(RideHomeDestinationPacket.class, RideHomeDestinationPacket::encode, RideHomeDestinationPacket::decode, RideHomeDestinationPacket::handle);
        toServer(RideHomeReadyPacket.class, RideHomeReadyPacket::encode, RideHomeReadyPacket::decode, RideHomeReadyPacket::handle);
        toServer(WaystoneDestinationRequestPacket.class, WaystoneDestinationRequestPacket::encode, WaystoneDestinationRequestPacket::decode, WaystoneDestinationRequestPacket::handle);
        toClient(WaystoneDestinationListPacket.class, WaystoneDestinationListPacket::encode, WaystoneDestinationListPacket::decode, WaystoneDestinationListPacket::handle);
        toServer(WaystoneJourneyRequestPacket.class, WaystoneJourneyRequestPacket::encode, WaystoneJourneyRequestPacket::decode, WaystoneJourneyRequestPacket::handle);
        toClient(RescueMagicPacket.class, RescueMagicPacket::encode, RescueMagicPacket::decode, RescueMagicPacket::handle);
        toClient(StorageEffectPacket.class, StorageEffectPacket::encode, StorageEffectPacket::decode, StorageEffectPacket::handle);
        toClient(CompanionDialoguePacket.class, CompanionDialoguePacket::encode, CompanionDialoguePacket::decode, CompanionDialoguePacket::handle);
        toServer(VehicleCommandPacket.class, VehicleCommandPacket::encode, VehicleCommandPacket::decode, VehicleCommandPacket::handle);
        toClient(VehicleListPacket.class, VehicleListPacket::encode, VehicleListPacket::decode, VehicleListPacket::handle);
        toClient(VehicleSealEffectPacket.class, VehicleSealEffectPacket::encode, VehicleSealEffectPacket::decode, VehicleSealEffectPacket::handle);
        toServer(CompanionEffectStylePacket.class, CompanionEffectStylePacket::encode, CompanionEffectStylePacket::decode, CompanionEffectStylePacket::handle);
        toServer(CompanionAnimationStylePacket.class, CompanionAnimationStylePacket::encode, CompanionAnimationStylePacket::decode, CompanionAnimationStylePacket::handle);
        toClient(PackAnimationPresetModePacket.class, PackAnimationPresetModePacket::encode, PackAnimationPresetModePacket::decode, PackAnimationPresetModePacket::handle);
        toClient(PackAnimationPresetListPacket.class, PackAnimationPresetListPacket::encode, PackAnimationPresetListPacket::decode, PackAnimationPresetListPacket::handle);
        toServer(PackAnimationPresetStylePacket.class, PackAnimationPresetStylePacket::encode, PackAnimationPresetStylePacket::decode, PackAnimationPresetStylePacket::handle);
        toServer(PackAnimationPresetMotionPacket.class, PackAnimationPresetMotionPacket::encode, PackAnimationPresetMotionPacket::decode, PackAnimationPresetMotionPacket::handle);
        toServer(PackEntityPresetUpdatePacket.class, PackEntityPresetUpdatePacket::encode, PackEntityPresetUpdatePacket::decode, PackEntityPresetUpdatePacket::handle);
        toServer(PackEditorActionPacket.class, PackEditorActionPacket::encode, PackEditorActionPacket::decode, PackEditorActionPacket::handle);
        toServer(CompanionTeamCommandPacket.class, CompanionTeamCommandPacket::encode, CompanionTeamCommandPacket::decode, CompanionTeamCommandPacket::handle);
        toClient(CompanionTeamListPacket.class, CompanionTeamListPacket::encode, CompanionTeamListPacket::decode, CompanionTeamListPacket::handle);
        toClient(HousePagePacket.class, HousePagePacket::encode, HousePagePacket::decode, HousePagePacket::handle);
        toServer(HouseCommandPacket.class, HouseCommandPacket::encode, HouseCommandPacket::decode, HouseCommandPacket::handle);
        toServer(DoctorCommandPacket.class, DoctorCommandPacket::encode, DoctorCommandPacket::decode, DoctorCommandPacket::handle);
        toClient(DoctorPagePacket.class, DoctorPagePacket::encode, DoctorPagePacket::decode, DoctorPagePacket::handle);
        bidirectional(FindMeSettingsPacket.class, FindMeSettingsPacket::encode, FindMeSettingsPacket::decode, FindMeSettingsPacket::handle);
        toServer(WarehouseEntityCommandPacket.class, WarehouseEntityCommandPacket::encode, WarehouseEntityCommandPacket::decode, WarehouseEntityCommandPacket::handle);
        toClient(WarehouseOperationResultPacket.class, WarehouseOperationResultPacket::encode, WarehouseOperationResultPacket::decode, WarehouseOperationResultPacket::handle);
        toServer(FindMeModuleCommandPacket.class, FindMeModuleCommandPacket::encode, FindMeModuleCommandPacket::decode, FindMeModuleCommandPacket::handle);
        toClient(FindMeModuleStatePacket.class, FindMeModuleStatePacket::encode, FindMeModuleStatePacket::decode, FindMeModuleStatePacket::handle);
        toClient(OpenFindMeManageScreenPacket.class, OpenFindMeManageScreenPacket::encode, OpenFindMeManageScreenPacket::decode, OpenFindMeManageScreenPacket::handle);
    }

    private static <T> void toServer(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                     Function<FriendlyByteBuf, T> decoder, PacketHandler<T> handler) {
        register(type, encoder, decoder, handler, NetworkDirection.PLAY_TO_SERVER);
    }

    private static <T> void toClient(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                     Function<FriendlyByteBuf, T> decoder, PacketHandler<T> handler) {
        register(type, encoder, decoder, handler, NetworkDirection.PLAY_TO_CLIENT);
    }

    private static <T> void bidirectional(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                          Function<FriendlyByteBuf, T> decoder, PacketHandler<T> handler) {
        channel.messageBuilder(type, nextId++)
                .encoder(encoder).decoder(decoder)
                .consumerMainThread((packet, context) -> handler.handle(packet, adapt(context)))
                .add();
    }

    private static <T> void register(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                     Function<FriendlyByteBuf, T> decoder, PacketHandler<T> handler,
                                     NetworkDirection direction) {
        channel.messageBuilder(type, nextId++, direction)
                .encoder(encoder).decoder(decoder)
                .consumerMainThread((packet, context) -> handler.handle(packet, adapt(context)))
                .add();
    }

    private static Supplier<FindMeNetworkContext.Context> adapt(Supplier<net.minecraftforge.network.NetworkEvent.Context> context) {
        return () -> FindMeNetworkContext.Context.of(context.get());
    }

    @FunctionalInterface
    private interface PacketHandler<T> {
        void handle(T packet, Supplier<FindMeNetworkContext.Context> context);
    }

    public static void sendToServer(Object packet) {
        channel.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToPlayersNear(ServerLevel level, Vec3 center, double radius, Object packet) {
        if (level == null || center == null) return;
        double radiusSqr = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center.x, center.y, center.z) <= radiusSqr) {
                sendToPlayer(player, packet);
            }
        }
    }
}
