package dev.patina_pandemonium.network;

import dev.patina_pandemonium.PatinaPandemonium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Sends only the currently inspected Patina HUD text outside Jade's 16 KiB server-data packet. */
public class PatinaHudSync {

    private static final int BLOCK_TARGET = 0;
    private static final int ENTITY_TARGET = 1;
    private static final Map<ServerPlayer, SentHud> LAST_SENT = new WeakHashMap<>();
    private static BlockHudPayload clientBlockPayload;
    private static EntityHudPayload clientEntityPayload;

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(BlockHudPayload.TYPE, BlockHudPayload.STREAM_CODEC);
        registrar.playToClient(EntityHudPayload.TYPE, EntityHudPayload.STREAM_CODEC);
    }

    public static void sendBlock(ServerPlayer player, long targetId, long revision, Supplier<HudText> textFactory) {
        if (!shouldSend(player, BLOCK_TARGET, targetId, revision)) return;
        PacketDistributor.sendToPlayer(player, new BlockHudPayload(targetId, revision, textFactory.get()));
        LAST_SENT.put(player, new SentHud(BLOCK_TARGET, targetId, revision));
    }

    public static void sendEntity(ServerPlayer player, int targetId, long revision, Supplier<HudText> textFactory) {
        if (!shouldSend(player, ENTITY_TARGET, targetId, revision)) return;
        PacketDistributor.sendToPlayer(player, new EntityHudPayload(targetId, revision, textFactory.get()));
        LAST_SENT.put(player, new SentHud(ENTITY_TARGET, targetId, revision));
    }

    public static void receive(BlockHudPayload payload) {
        clientBlockPayload = payload;
    }

    public static void receive(EntityHudPayload payload) {
        clientEntityPayload = payload;
    }

    @Nullable
    public static HudText block(long targetId, long revision) {
        BlockHudPayload payload = clientBlockPayload;
        return payload != null && payload.targetId() == targetId && payload.revision() == revision ? payload.text() : null;
    }

    @Nullable
    public static HudText entity(int targetId, long revision) {
        EntityHudPayload payload = clientEntityPayload;
        return payload != null && payload.targetId() == targetId && payload.revision() == revision ? payload.text() : null;
    }

    private static boolean shouldSend(ServerPlayer player, int targetType, long targetId, long revision) {
        SentHud sent = LAST_SENT.get(player);
        return sent == null || sent.targetType() != targetType || sent.targetId() != targetId || sent.revision() != revision;
    }

    public record HudText(Component title, Component sourceName, Component geneticsName) {

        // Clientbound HUD components can legitimately exceed vanilla's 2 MiB NBT allocation budget when names recurse deeply.
        private static final StreamCodec<RegistryFriendlyByteBuf, HudText> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, HudText::title,
            ComponentSerialization.TRUSTED_STREAM_CODEC, HudText::sourceName,
            ComponentSerialization.TRUSTED_STREAM_CODEC, HudText::geneticsName,
            HudText::new);
    }

    public record BlockHudPayload(long targetId, long revision, HudText text) implements CustomPacketPayload {

        public static final Type<BlockHudPayload> TYPE = new Type<>(PatinaPandemonium.id("jade_block_hud"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BlockHudPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BlockHudPayload::targetId,
            ByteBufCodecs.VAR_LONG, BlockHudPayload::revision,
            HudText.STREAM_CODEC, BlockHudPayload::text,
            BlockHudPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record EntityHudPayload(int targetId, long revision, HudText text) implements CustomPacketPayload {

        public static final Type<EntityHudPayload> TYPE = new Type<>(PatinaPandemonium.id("jade_entity_hud"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EntityHudPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EntityHudPayload::targetId,
            ByteBufCodecs.VAR_LONG, EntityHudPayload::revision,
            HudText.STREAM_CODEC, EntityHudPayload::text,
            EntityHudPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record SentHud(int targetType, long targetId, long revision) {
    }
}
