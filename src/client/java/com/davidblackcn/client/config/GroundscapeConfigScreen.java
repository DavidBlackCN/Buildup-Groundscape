package com.davidblackcn.client.config;

import com.davidblackcn.config.ConfigNetworking;
import com.davidblackcn.config.GroundscapeConfigManager;
import com.davidblackcn.worldgen.GroundscapeWorldgenConfig;
import com.google.gson.*;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;
import java.util.concurrent.atomic.AtomicLong;

/** UI drafts never mutate the live config. Every remote save awaits authoritative acknowledgement. */
public final class GroundscapeConfigScreen {
    private static final AtomicLong IDS = new AtomicLong();
    private static boolean openNextTick;
    private GroundscapeConfigScreen() {}
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigNetworking.Reply.TYPE, (reply, context) -> {
            if (context.client().gui.screen() instanceof RequestScreen waiting && waiting.id == reply.requestId()) waiting.receive(reply);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                ClientCommands.literal("groundscape-config").executes(context -> { openNextTick = true; return 1; })));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openNextTick) { openNextTick = false; client.gui.setScreen(open(null)); }
        });
    }
    public static Screen open(Screen parent) {
        if (Minecraft.getInstance().getConnection() != null) return new RequestScreen(parent, null, -1);
        var snapshot = GroundscapeConfigManager.snapshot();
        return editor(parent, snapshot.config(), snapshot.revision(), true, false);
    }
    private static Component text(String key) { return Component.translatable("groundscape.config." + key); }
    private static Screen editor(Screen parent, GroundscapeWorldgenConfig config, long revision, boolean editable, boolean remote) {
        JsonObject draft = JsonParser.parseString(config.json()).getAsJsonObject();
        JsonObject defaults = JsonParser.parseString(GroundscapeWorldgenConfig.defaults().json()).getAsJsonObject();
        var builder = YetAnotherConfigLib.createBuilder().title(text("title"));
        var terrain = ConfigCategory.createBuilder().name(text("terrain"));
        terrain.option(LabelOption.create(text(remote ? (editable ? "source_server" : "source_readonly") : "source_local")));
        terrain.option(LabelOption.create(text("new_chunks")));
        add(terrain, draft, defaults, "enabled", 0, 1, editable);
        add(terrain, draft, defaults, "density", 0, .35, editable);
        add(terrain, draft, defaults, "maxLayers", 1, 7, editable);
        for (String key : new String[]{"slopeInfluence", "flatCoverage", "layerScale", "heightVariation"})
            add(terrain, draft, defaults, key, key.equals("layerScale") ? .5 : 0, key.equals("layerScale") ? 1.5 : 1, editable);
        builder.category(terrain.build());
        var materials = ConfigCategory.createBuilder().name(text("materials"));
        for (String material : draft.getAsJsonObject("materials").keySet()) {
            add(materials, draft, defaults, "materials." + material + ".enabled", 0, 1, editable);
            add(materials, draft, defaults, "materials." + material + ".thicknessScale", .5, 1.5, editable);
        }
        builder.category(materials.build());
        var leaves = ConfigCategory.createBuilder().name(text("leaves"));
        leaves.option(LabelOption.create(text(!config.enabled() ? "leaves_global_off" : !config.leafPatches().enabled() ? "leaves_off" : "leaves_on")));
        leaves.option(LabelOption.create(text("leaf_density")));
        add(leaves, draft, defaults, "leafPatches.enabled", 0, 1, editable);
        add(leaves, draft, defaults, "leafPatches.coverage", 0, .04, editable);
        add(leaves, draft, defaults, "leafPatches.maxLayers", 1, 2, editable);
        add(leaves, draft, defaults, "leafPatches.searchHeight", 1, 32, editable);
        add(leaves, draft, defaults, "leafPatches.maxPerChunk", 0, 32, editable);
        builder.category(leaves.build());
        var types = ConfigCategory.createBuilder().name(text("types"));
        for (String type : draft.getAsJsonObject("leafPatches").getAsJsonObject("types").keySet())
            add(types, draft, defaults, "leafPatches.types." + type, 0, 1, editable);
        builder.category(types.build());
        builder.save(() -> {
            if (!editable) return;
            Minecraft client = Minecraft.getInstance();
            if (remote) client.gui.setScreen(new RequestScreen(parent, draft.toString(), revision));
            else {
                try {
                    var saved = GroundscapeConfigManager.apply(revision, draft.toString());
                    client.gui.setScreen(new AlertScreen(() -> client.gui.setScreen(editor(parent, saved.config(), saved.revision(), true, false)), text("saved"), text("new_chunks")));
                } catch (java.io.IOException | IllegalArgumentException | JsonParseException e) {
                    client.gui.setScreen(new AlertScreen(() -> client.gui.setScreen(open(parent)), text("error"), text("local_failed")));
                }
            }
        });
        return builder.build().generateScreen(parent);
    }
    private static JsonObject owner(JsonObject root, String path) {
        String[] parts = path.split("\\.");
        for (int i = 0; i < parts.length - 1; i++) root = root.getAsJsonObject(parts[i]);
        return root;
    }
    private static void add(ConfigCategory.Builder category, JsonObject draft, JsonObject defaults, String path, double min, double max, boolean editable) {
        JsonObject target = owner(draft, path), def = owner(defaults, path);
        String key = path.substring(path.lastIndexOf('.') + 1);
        var value = target.getAsJsonPrimitive(key);
        Component name = text(path);
        String description = path.startsWith("materials.") ? "material_" + key : path.startsWith("leafPatches.types.") ? "type_description" : path + ".description";
        OptionDescription desc = OptionDescription.of(text(description));
        if (value.isBoolean()) category.option(Option.<Boolean>createBuilder().name(name).description(desc).available(editable)
                .binding(def.get(key).getAsBoolean(), () -> target.get(key).getAsBoolean(), v -> target.addProperty(key, v))
                .controller(TickBoxControllerBuilder::create).build());
        else if (key.equals("maxLayers") || key.equals("maxPerChunk") || key.equals("searchHeight"))
            category.option(Option.<Integer>createBuilder().name(name).description(desc).available(editable)
                    .binding(def.get(key).getAsInt(), () -> target.get(key).getAsInt(), v -> target.addProperty(key, v))
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range((int) min, (int) max).step(1)).build());
        else category.option(Option.<Double>createBuilder().name(name).description(desc).available(editable)
                    .binding(def.get(key).getAsDouble(), () -> target.get(key).getAsDouble(), v -> target.addProperty(key, v))
                    .controller(o -> DoubleSliderControllerBuilder.create(o).range(min, max).step(.001)).build());
    }
    private static final class RequestScreen extends net.minecraft.client.gui.screens.GenericMessageScreen {
        private final Screen parent;
        private final String json;
        private final long revision, id = IDS.incrementAndGet();
        private boolean sent;
        private int ticks;
        RequestScreen(Screen parent, String json, long revision) { super(text("waiting")); this.parent = parent; this.json = json; this.revision = revision; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
        @Override public void tick() {
            var client = Minecraft.getInstance();
            if (client.getConnection() == null || !ClientPlayNetworking.canSend(ConfigNetworking.Request.TYPE)) {
                client.gui.setScreen(new AlertScreen(this::onClose, text("error"), text("unavailable"))); return;
            }
            if (!sent) { sent = true; ClientPlayNetworking.send(new ConfigNetworking.Request(id, revision, json != null, json == null ? "" : json)); }
            if (++ticks > 200) client.gui.setScreen(new AlertScreen(this::onClose, text("error"), text("timeout")));
        }
        void receive(ConfigNetworking.Reply reply) {
            var client = Minecraft.getInstance();
            GroundscapeWorldgenConfig config = GroundscapeWorldgenConfig.parse(reply.json());
            if (!reply.error().isEmpty()) client.gui.setScreen(new AlertScreen(() -> client.gui.setScreen(open(parent)), text("error"), text("error_" + reply.error())));
            else if (json != null) client.gui.setScreen(new AlertScreen(() -> client.gui.setScreen(editor(parent, config, reply.revision(), reply.editable(), true)), text("saved"), text("new_chunks")));
            else client.gui.setScreen(editor(parent, config, reply.revision(), reply.editable(), true));
        }
    }
}
