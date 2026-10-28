package com.davidblackcn.config;

import com.davidblackcn.BuildupGroundscape;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** Bounded, server-authoritative request/reply protocol. Revisions reject stale editor writes. */
public final class ConfigNetworking {
    public static final int MAX_JSON = 16_384;
    public record Request(long requestId, long revision, boolean save, String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(BuildupGroundscape.id("config_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (b, p) -> { b.writeLong(p.requestId); b.writeLong(p.revision); b.writeBoolean(p.save); b.writeUtf(p.json, MAX_JSON); },
                b -> new Request(b.readLong(), b.readLong(), b.readBoolean(), b.readUtf(MAX_JSON)));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Reply(long requestId, long revision, boolean editable, String json, String error) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(BuildupGroundscape.id("config_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.of(
                (b, p) -> { b.writeLong(p.requestId); b.writeLong(p.revision); b.writeBoolean(p.editable); b.writeUtf(p.json, MAX_JSON); b.writeUtf(p.error, 1024); },
                b -> new Reply(b.readLong(), b.readLong(), b.readBoolean(), b.readUtf(MAX_JSON), b.readUtf(1024)));
        @Override public Type<Reply> type() { return TYPE; }
    }
    private ConfigNetworking() {}
    public static boolean canEdit(ServerPlayer player) {
        return player.level().getServer().isSingleplayerOwner(player.nameAndId())
                || player.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
    }
    public static Reply handle(ServerPlayer player, Request request) {
        String error = "";
        boolean editable = canEdit(player);
        if (request.save()) {
            if (!editable) error = "permission";
            else {
                try { GroundscapeConfigManager.apply(request.revision(), request.json()); }
                catch (java.io.IOException e) {
                    BuildupGroundscape.LOGGER.error("Cannot save Groundscape configuration", e);
                    error = "io";
                } catch (IllegalArgumentException | com.google.gson.JsonParseException e) {
                    error = request.revision() != GroundscapeConfigManager.snapshot().revision() ? "conflict" : "invalid";
                }
            }
        }
        var snapshot = GroundscapeConfigManager.snapshot();
        return new Reply(request.requestId(), snapshot.revision(), editable, snapshot.config().json(), error);
    }
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Reply.TYPE, Reply.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (request, context) ->
                ServerPlayNetworking.send(context.player(), handle(context.player(), request)));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("groundscape").then(Commands.literal("reload")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .executes(context -> {
                            try {
                                GroundscapeConfigManager.reload();
                                context.getSource().sendSuccess(() -> Component.translatable("groundscape.config.reloaded"), true);
                                return 1;
                            } catch (java.io.IOException | IllegalArgumentException | com.google.gson.JsonParseException e) {
                                context.getSource().sendFailure(Component.translatable("groundscape.config.reload_failed"));
                                BuildupGroundscape.LOGGER.warn("Groundscape reload rejected: {}", e.getMessage());
                                return 0;
                            }
                        }))));
    }
}
