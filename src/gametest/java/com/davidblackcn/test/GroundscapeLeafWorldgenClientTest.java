package com.davidblackcn.test;

import com.davidblackcn.block.LeafPatchBlock;
import com.davidblackcn.registry.LeafFamily;
import com.davidblackcn.registry.GroundscapeLeaves;
import com.davidblackcn.worldgen.*;
import com.google.gson.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.*;
import java.io.*;
import java.security.*;
import java.util.*;

/** Fixed-seed actual forest controls. All evidence lives outside the version repository. */
public final class GroundscapeLeafWorldgenClientTest implements FabricClientGameTest {
    private static final long SEED = 20261026L;
    private static final String[] BIOMES = {"forest", "birch_forest", "cherry_grove", "dappled_forest", "taiga", "jungle"};
    @Override public void runTest(ClientGameTestContext context) {
        String mode = System.getProperty("groundscape.leafTest");
        if (mode == null) return;
        Path evidence = evidence();
        JsonObject result = new JsonObject(); result.addProperty("mode", mode); result.addProperty("seed", SEED);
        JsonObject scenes = new JsonObject(); result.add("scenes", scenes);
        try {
            JsonObject baseline = mode.equals("off") ? null : JsonParser.parseString(Files.readString(evidence.resolve("leaves-off.json"))).getAsJsonObject();
            JsonObject previous = mode.equals("repeat") ? JsonParser.parseString(Files.readString(evidence.resolve("leaves-on.json"))).getAsJsonObject() : null;
            context.runOnClient(client -> {
                client.options.renderDistance().set(6);
                client.getWindow().setWidth(1280); client.getWindow().setHeight(720);
                client.gameRenderer.resize(1280, 720);
            });
            var builder = context.worldBuilder().adjustSettings(creator -> {
                var normal = creator.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL);
                creator.setWorldType(new WorldCreationUiState.WorldTypeEntry(normal));
                creator.setSeed(Long.toString(SEED)); creator.setGenerateStructures(true);
                creator.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, null);
            });
            int total = 0;
            try (var world = builder.create()) {
                var server = world.getServer(); var connection = world.getConnection();
                server.runCommand("tick freeze"); server.runCommand("gamemode spectator @a");
                server.runCommand("time set noon"); server.runCommand("weather clear");
                for (int i = 0; i < BIOMES.length; i++) {
                    String biome = BIOMES[i]; int index = i;
                    JsonObject scene = server.computeOnServer(s -> sample(s.overworld(), biome, index));
                    scenes.add(biome, scene); total += scene.get("count").getAsInt();
                    if (baseline != null) require(scene.get("normalizedHash").equals(baseline.getAsJsonObject("scenes").getAsJsonObject(biome).get("normalizedHash")), "Only air changes into leaves: " + biome);
                    else require(scene.get("count").getAsInt() == 0, "Disabled leaves stay absent");
                    if (previous != null) require(scene.get("hash").equals(previous.getAsJsonObject("scenes").getAsJsonObject(biome).get("hash")), "Repeat determinism " + biome);
                    server.runCommand("tp @a " + scene.get("cameraX") + " " + scene.get("cameraY") + " " + scene.get("cameraZ") + " 0 45");
                    context.waitTick(); connection.waitForClientboundPackets();
                    int centerX = scene.get("cameraX").getAsInt() >> 4, centerZ = scene.get("cameraZ").getAsInt() >> 4;
                    context.waitFor(client -> {
                        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                            if (!client.level.hasChunk(centerX + dx, centerZ + dz)) return false;
                        return true;
                    }, 2400);
                    context.waitTicks(5); connection.waitForChunksRender(false, 2400);
                    context.runOnClient(client -> { if (!client.gui.hud.isHidden()) client.gui.hud.toggle(); });
                    context.takeScreenshot(TestScreenshotOptions.of("leaves-" + mode + "-" + biome).withSize(1280, 720).withDestinationDir(evidence).disableCounterPrefix());
                    if (scene.get("count").getAsInt() > 0) {
                        server.runCommand("tp @a " + scene.get("closeX") + " " + scene.get("closeY") + " " + scene.get("closeZ") + " 0 30");
                        context.waitTicks(5); connection.waitForClientboundPackets(); connection.waitForChunksRender(false, 2400);
                        context.takeScreenshot(TestScreenshotOptions.of("leaves-" + mode + "-" + biome + "-close").withSize(1280, 720).withDestinationDir(evidence).disableCounterPrefix());
                    }
                    write(evidence.resolve("leaves-" + mode + ".json"), result);
                }
            }
            require(mode.equals("off") || total > 0, "Actual natural leaf patches exist");
            result.addProperty("total", total); result.addProperty("result", "PASS");
            write(evidence.resolve("leaves-" + mode + ".json"), result);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static JsonObject sample(ServerLevel level, String biome, int index) {
        var found = level.findClosestBiome3d(b -> b.unwrapKey().orElseThrow().identifier().getPath().equals(biome), new BlockPos(20000 + index * 20000, 64, 20000), 6400, 32, 64);
        require(found != null, "Locate " + biome);
        var anchor = found.getFirst(); int cx = anchor.getX() >> 4, cz = anchor.getZ() >> 4;
        long start = System.nanoTime();
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) level.getChunk(cx + dx, cz + dz);
        long generationMillis = (System.nanoTime() - start) / 1000000;
        MessageDigest exact = digest(), normalized = digest();
        int count = 0, doubles = 0, canopyColumns = 0; BlockPos first = null;
        JsonObject types = new JsonObject(); var config = GroundscapeWorldgenConfig.current().leafPatches();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            var chunk = level.getChunk(cx + dx, cz + dz); int chunkCount = 0;
            for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                boolean hasCanopy = false;
                for (int y = level.getMinY(); y <= level.getMaxY(); y++) {
                    var pos = new BlockPos(x, y, z); var state = chunk.getBlockState(pos);
                    update(exact, Block.getId(state));
                    boolean leaf = state.getBlock() instanceof LeafPatchBlock;
                    update(normalized, Block.getId(leaf ? Blocks.AIR.defaultBlockState() : state));
                    if (LeafFamily.from(state) != null) hasCanopy = true;
                    if (!leaf) continue;
                    count++; chunkCount++;
                    if (state.getValue(LeafPatchBlock.LAYERS) == 2) doubles++;
                    if (first == null) first = pos;
                    var family = LeafPatchPlanner.canopy(level, pos, p -> level.hasChunkAt(p), config);
                    require(family != null && state.is(GroundscapeLeaves.get(family)), "Leaf species matches nearest natural canopy");
                    String name = family.id(); types.addProperty(name, (types.has(name) ? types.get(name).getAsInt() : 0) + 1);
                    require(state.getValue(LeafPatchBlock.LAYERS) <= config.maxLayers(), "Natural leaf height bounded");
                }
                if (hasCanopy) canopyColumns++;
            }
            require(chunkCount <= config.maxPerChunk(), "Per-chunk cap");
        }
        if (first == null) first = new BlockPos(anchor.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ()), anchor.getZ());
        JsonObject result = new JsonObject();
        result.addProperty("count", count); result.addProperty("twoLayers", doubles); result.addProperty("canopyColumns", canopyColumns);
        result.addProperty("sampleColumns", 2304); result.addProperty("generationMillis", generationMillis); result.add("types", types);
        result.addProperty("closeX", first.getX() + .5); result.addProperty("closeY", first.getY() + 2); result.addProperty("closeZ", first.getZ() - 3.5);
        result.addProperty("hash", HexFormat.of().formatHex(exact.digest())); result.addProperty("normalizedHash", HexFormat.of().formatHex(normalized.digest()));
        // Use a fixed baseline-derived camera in all modes, independent of where patches appeared.
        result.addProperty("cameraX", anchor.getX() + .5); result.addProperty("cameraY", level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ()) + 8);
        result.addProperty("cameraZ", anchor.getZ() - 10.5);
        return result;
    }
    private static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); } }
    private static void update(MessageDigest digest, int value) { digest.update((byte) (value >>> 24)); digest.update((byte) (value >>> 16)); digest.update((byte) (value >>> 8)); digest.update((byte) value); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void write(Path path, JsonObject value) throws IOException { Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(value)); }
    private static Path evidence() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("build.gradle"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("No project directory");
        path = path.getParent().resolve("docs/evidence/vegetation-leaves");
        try { Files.createDirectories(path); } catch (IOException e) { throw new UncheckedIOException(e); }
        return path;
    }
}
