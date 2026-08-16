package dev.patina_pandemonium.network;

import dev.patina_pandemonium.PatinaPandemonium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Clientbound actions initiated from server commands. */
public class PatinaClientSync {

    public static final int DEFAULT_ISOMETRIC_SIZE = 512;
    public static final int MIN_ISOMETRIC_SIZE = 128;
    public static final int MAX_ISOMETRIC_SIZE = 2048;

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ExportIsometricPayload.TYPE, ExportIsometricPayload.STREAM_CODEC);
    }

    public static void sendIsometricExport(ServerPlayer player, int size) {
        PacketDistributor.sendToPlayer(player, new ExportIsometricPayload(Math.clamp(size, MIN_ISOMETRIC_SIZE, MAX_ISOMETRIC_SIZE)));
    }

    public record ExportIsometricPayload(int size) implements CustomPacketPayload {

        public static final Type<ExportIsometricPayload> TYPE = new Type<>(PatinaPandemonium.id("export_isometric"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ExportIsometricPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ExportIsometricPayload::size,
            ExportIsometricPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

}