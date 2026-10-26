package com.davidblackcn.test;

import com.davidblackcn.block.SodPatchBlock;
import com.davidblackcn.registry.GroundscapeBlocks;
import com.davidblackcn.registry.SurfaceFamily;
import com.davidblackcn.worldgen.GroundscapeWorldgenConfig;
import com.davidblackcn.worldgen.SodPatchFeature;
import com.davidblackcn.worldgen.SurfaceAnalyzer;
import com.davidblackcn.worldgen.SlopePlanner;
import com.davidblackcn.worldgen.SlopeTerrain;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Real fixed-seed terrain tests, opt-in and excluded from the published mod. */
public final class GroundscapeWorldgenClientTest implements FabricClientGameTest {
    private static final long SEED = 20261024L;
    private static final int WIDTH = 48;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SNAPSHOT_MAGIC = "groundscape-g1-snapshot-v1";
    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("plains", Level.OVERWORLD, Biomes.PLAINS, 0),
            new Scenario("forest", Level.OVERWORLD, Biomes.FOREST, 1),
            new Scenario("hills", Level.OVERWORLD, Biomes.WINDSWEPT_HILLS, 2),
            new Scenario("snow", Level.OVERWORLD, Biomes.SNOWY_PLAINS, 3),
            new Scenario("swamp", Level.OVERWORLD, Biomes.SWAMP, 4),
            new Scenario("crimson", Level.NETHER, Biomes.CRIMSON_FOREST, 5),
            new Scenario("warped", Level.NETHER, Biomes.WARPED_FOREST, 6),
            new Scenario("crimson_cluster", Level.NETHER, Biomes.CRIMSON_FOREST, 7),
            new Scenario("warped_cluster", Level.NETHER, Biomes.WARPED_FOREST, 8),
            new Scenario("meadow", Level.OVERWORLD, Biomes.MEADOW, 9),
            new Scenario("hills_open", Level.OVERWORLD, Biomes.WINDSWEPT_HILLS, 10),
            new Scenario("dappled_forest", Level.OVERWORLD, Biomes.DAPPLED_FOREST, 11),
            new Scenario("cherry_grove", Level.OVERWORLD, Biomes.CHERRY_GROVE, 12),
            new Scenario("birch_forest", Level.OVERWORLD, Biomes.BIRCH_FOREST, 13),
            new Scenario("cherry_open", Level.OVERWORLD, Biomes.CHERRY_GROVE, 14),
            new Scenario("birch_open", Level.OVERWORLD, Biomes.BIRCH_FOREST, 15));

    @Override
    public void runTest(ClientGameTestContext context) {
        String mode = System.getProperty("groundscape.worldgenTest");
        if (mode == null) return;
        require(List.of("off", "on", "off_reverse", "repeat").contains(mode), "Invalid worldgenTest mode");
        boolean enabled = mode.equals("on") || mode.equals("repeat");
        boolean reverse = mode.equals("off_reverse") || mode.equals("repeat");
        String disabledMode = reverse ? "off_reverse" : "off";
        GroundscapeWorldgenConfig config = GroundscapeWorldgenConfig.current();
        Path evidence = evidenceDirectory();
        JsonObject report = new JsonObject();
        report.addProperty("mode", mode);
        report.addProperty("seed", SEED);
        report.addProperty("preset", "minecraft:normal");
        report.addProperty("structures", true);
        report.addProperty("sample_width_blocks", WIDTH);
        report.addProperty("generation_halo_width_chunks", 7);
        report.addProperty("config_is_startup_only", true);
        report.addProperty("background_thread_limit", System.getProperty("max.bg.threads", "default"));
        report.add("startup_config", JSON.toJsonTree(config));
        report.addProperty("tick_scope", "World ticks frozen for generation/snapshot isolation; this is not a long-term ecology test");
        report.addProperty("performance_scope", "Single-run generation smoke timing, including dependency chunks; not a statistical benchmark");
        JsonObject scenes = new JsonObject();
        report.add("scenarios", scenes);
        JsonObject featureOrders = new JsonObject();
        report.add("feature_orders", featureOrders);
        Map<String, PatchInfo> patchStates = patchStates();
        long totalPatches = 0;

        try {
            Files.createDirectories(evidence);
            report.addProperty("result", "RUNNING");
            writeJson(evidence.resolve(mode + ".json"), report);
            require(config.enabled() == enabled, "Startup worldgen enable flag differs from test mode");
            require(Double.compare(config.density(), 0.12) == 0 && config.maxLayers() == 7,
                    "Startup worldgen density/layers differ from the fixed test profile");
            JsonObject baselineReport = mode.equals("off") ? null : readJson(evidence.resolve("off.json"));
            Path historicalPath = evidence.resolveSibling("g1").resolve("off.json");
            JsonObject historical = mode.equals("off") && Files.exists(historicalPath) ? readJson(historicalPath) : null;
            if (baselineReport != null) require(baselineReport.has("result")
                    && baselineReport.get("result").getAsString().equals("PASS"), "A complete PASS off baseline is required");
            if (mode.equals("repeat")) {
                JsonObject enabledBaseline = readJson(evidence.resolve("on.json"));
                require(enabledBaseline.has("result") && enabledBaseline.get("result").getAsString().equals("PASS"),
                        "A complete PASS on baseline is required for reverse-order comparison");
                require(readJson(evidence.resolve("off_reverse.json")).get("result").getAsString().equals("PASS"),
                        "A complete PASS reverse-disabled control is required");
            }
            context.runOnClient(client -> {
                client.options.renderDistance().set(4);
                // Establish the final viewport before chunk visibility is measured. Screenshot
                // resizing alone renders one new frame without waiting for its wider frustum.
                client.getWindow().setWidth(1280);
                client.getWindow().setHeight(720);
                client.gameRenderer.resize(1280, 720);
            });
            var builder = context.worldBuilder().adjustSettings(creator -> {
                var normal = creator.getSettings().worldgenLoadContext()
                        .lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL);
                creator.setWorldType(new WorldCreationUiState.WorldTypeEntry(normal));
                creator.setName("Groundscape S1 " + mode + " " + SEED);
                creator.setSeed(Long.toString(SEED));
                creator.setGenerateStructures(true);
                creator.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, null);
            });
            TestWorldSave savedWorld;
            try (var world = builder.create()) {
                savedWorld = world.getWorldSave();
                var server = world.getServer();
                var connection = world.getConnection();
                server.runCommand("tick freeze");
                server.runCommand("gamemode spectator @a");
                server.runCommand("time set noon");
                server.runCommand("weather clear");
                for (Scenario scenario : SCENARIOS) {
                    JsonObject baselineScene = baselineReport == null ? null
                            : baselineReport.getAsJsonObject("scenarios").getAsJsonObject(scenario.name);
                    JsonObject referenceScene = baselineScene != null ? baselineScene : historical == null ? null
                            : historical.getAsJsonObject("scenarios").getAsJsonObject(scenario.name);
                    Sample sample = server.computeOnServer(s -> {
                        ServerLevel level = Objects.requireNonNull(s.getLevel(scenario.dimension));
                        String dimension = scenario.dimension.identifier().toString();
                        if (!featureOrders.has(dimension)) featureOrders.add(dimension, featureOrder(level));
                        require(level.getSeed() == SEED, "World seed differs from fixed test seed");
                        BlockPos anchor;
                        if (referenceScene == null) {
                            BlockPos origin = new BlockPos(20_000 + scenario.ordinal * 20_000, 64, 20_000);
                            // Additional positive examples deliberately target nearby noise peaks.
                            // The original two zero-coverage Nether scenes remain in the matrix.
                            if (scenario.name.equals("crimson_cluster")) origin = new BlockPos(119680, 63, 19488);
                            if (scenario.name.equals("warped_cluster")) origin = new BlockPos(140448, 63, 20450);
                            var found = level.findClosestBiome3d(biome -> biome.is(scenario.biome), origin, 6400, 32, 64);
                            require(found != null, "Could not locate actual biome " + scenario.name + " within 6400 blocks");
                            anchor = found.getFirst();
                            if (scenario.name.equals("crimson_cluster")) anchor = new BlockPos(119680, 63, 19488);
                            if (scenario.name.equals("warped_cluster")) anchor = new BlockPos(140448, 63, 20450);
                        } else {
                            anchor = new BlockPos(referenceScene.get("anchor_x").getAsInt(),
                                    referenceScene.get("anchor_y").getAsInt(), referenceScene.get("anchor_z").getAsInt());
                        }
                        return sample(level, anchor, scenario, reverse, referenceScene);
                    });

                    Snapshot baseline = !enabled ? null
                            : readSnapshot(evidence.resolve(disabledMode + "-" + scenario.name + ".snapshot.gz"));
                    JsonObject scene = summarize(sample, patchStates);
                    if (historical != null && referenceScene != null) {
                        scene.addProperty("historical_g1_disabled_state_hash_identical",
                                scene.get("state_hash").equals(referenceScene.get("state_hash")));
                        scene.addProperty("historical_g1_camera_reused", true);
                    }
                    if (enabled) scene.addProperty("live_server_retrofit", "REFUSED_WITH_IDENTICAL_BLOCK_STATES");
                    scenes.add(scenario.name, scene);
                    // Preserve the actual sample even when a comparison fails, so differences remain inspectable.
                    writeSnapshot(evidence.resolve(mode + "-" + scenario.name + ".snapshot.gz"), sample.snapshot);
                    if (baseline == null) {
                        require(scene.get("patch_count").getAsLong() == 0, "Disabled feature generated sod patches");
                        scene.addProperty("protected_state_hash", sample.snapshot.hash(Map.of()));
                        scene.addProperty("comparison", "BASELINE_DISABLED");
                    } else {
                        Comparison comparison = compare(baseline, sample.snapshot, patchStates, false);
                        scene.addProperty("changed_blocks", comparison.changed);
                        scene.addProperty("protected_state_hash", comparison.protectedHash);
                        scene.addProperty("comparison", "ONLY_AIR_TO_MATCHING_PATCH");
                        if (reverse) scene.add("order_determinism", compareStableInputs(
                                readSnapshot(evidence.resolve("off-" + scenario.name + ".snapshot.gz")), baseline,
                                readSnapshot(evidence.resolve("on-" + scenario.name + ".snapshot.gz")), sample.snapshot,
                                patchStates, scenario.dimension == Level.NETHER));
                    }
                    totalPatches += scene.get("patch_count").getAsLong();
                    writeJson(evidence.resolve(mode + ".json"), report);

                    if (!reverse) {
                        String dimension = scenario.dimension == Level.NETHER ? "minecraft:the_nether" : "minecraft:overworld";
                        server.runCommand("execute in " + dimension + " run tp @a " + sample.cameraX + " "
                                + sample.cameraY + " " + sample.cameraZ + " 0 90");
                        context.waitTick();
                        connection.waitForClientboundPackets();
                        connection.waitForChunksRender(2400);
                        context.runOnClient(client -> {
                            if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
                            client.gui.toastManager().clear();
                        });
                        Path screenshot = context.takeScreenshot(TestScreenshotOptions.of(mode + "-" + scenario.name)
                                .withSize(1280, 720).withDestinationDir(evidence).disableCounterPrefix());
                        scene.addProperty("screenshot", screenshot.getFileName().toString());
                        if (scenario.dimension == Level.OVERWORLD) {
                            server.runCommand("execute in " + dimension + " run tp @a " + sample.cameraX + " "
                                    + (sample.cameraY + 6) + " " + (sample.cameraZ - 10) + " 0 50");
                            context.waitTick();
                            connection.waitForClientboundPackets();
                            connection.waitForChunksRender(2400);
                            Path oblique = context.takeScreenshot(TestScreenshotOptions.of(mode + "-" + scenario.name + "-oblique")
                                    .withSize(1280, 720).withDestinationDir(evidence).disableCounterPrefix());
                            scene.addProperty("oblique_screenshot", oblique.getFileName().toString());
                        }
                        writeJson(evidence.resolve(mode + ".json"), report);
                    }
                }
                report.addProperty("save_directory", world.getWorldSave().getSaveDirectory().toString());
                // Reopen at spawn, not inside a measured region: no vanilla scheduled updates should
                // advance in the measured chunks before the new server receives the freeze command.
                server.runCommand("execute in minecraft:overworld run tp @a 0 320 0");
                context.waitTick();
                connection.waitForClientboundPackets();
                for (Scenario scenario : SCENARIOS) {
                    Snapshot generated = readSnapshot(evidence.resolve(mode + "-" + scenario.name + ".snapshot.gz"));
                    Snapshot beforeClose = server.computeOnServer(s -> captureSavedRegion(
                            Objects.requireNonNull(s.getLevel(scenario.dimension)), generated));
                    writeSnapshot(evidence.resolve(mode + "-" + scenario.name + "-presave.snapshot.gz"), beforeClose);
                    JsonObject scene = scenes.getAsJsonObject(scenario.name);
                    scene.addProperty("presave_state_hash", beforeClose.hash(Map.of()));
                    scene.add("post_activation_state_changes", differences(generated, beforeClose, patchStates));
                    if (enabled) {
                        Snapshot disabledPresave = readSnapshot(evidence.resolve(disabledMode + "-" + scenario.name + "-presave.snapshot.gz"));
                        compare(disabledPresave, beforeClose, patchStates, false);
                        scene.addProperty("presave_disabled_comparison", "ONLY_AIR_TO_MATCHING_PATCH");
                    }
                    writeJson(evidence.resolve(mode + ".json"), report);
                }
            }
            try (var reopened = savedWorld.open()) {
                var server = reopened.getServer();
                server.runCommand("tick freeze");
                for (Scenario scenario : SCENARIOS) {
                    Snapshot beforeClose = readSnapshot(evidence.resolve(mode + "-" + scenario.name + "-presave.snapshot.gz"));
                    Snapshot afterReopen = server.computeOnServer(s -> captureSavedRegion(
                            Objects.requireNonNull(s.getLevel(scenario.dimension)), beforeClose));
                    compare(beforeClose, afterReopen, patchStates, true);
                    JsonObject scene = scenes.getAsJsonObject(scenario.name);
                    scene.addProperty("save_reload", "IDENTICAL_ALL_BLOCK_STATES");
                    scene.addProperty("reloaded_state_hash", afterReopen.hash(Map.of()));
                    writeJson(evidence.resolve(mode + ".json"), report);
                }
            }
            if (enabled) require(totalPatches > 0, "Enabled generation produced no patches in all sample regions");
            if (enabled) {
                long[] flat = new long[9], slope = new long[9];
                for (var scene : scenes.entrySet()) {
                    JsonObject metrics = scene.getValue().getAsJsonObject().getAsJsonObject("surface_diagnostics")
                            .getAsJsonObject("terrain_metrics");
                    for (int i = 0; i < 9; i++) {
                        flat[i] += metrics.getAsJsonArray("flat").get(i).getAsLong();
                        slope[i] += metrics.getAsJsonArray("slope").get(i).getAsLong();
                    }
                }
                long flatPlaced = java.util.Arrays.stream(flat).skip(2).sum();
                long slopePlaced = java.util.Arrays.stream(slope).skip(2).sum();
                double flatRate = (double) flatPlaced / Math.max(1, flat[0]);
                double slopeRate = (double) slopePlaced / Math.max(1, slope[0]);
                double middleShare = (double) (slope[4] + slope[5] + slope[6] + slope[7]) / Math.max(1, slopePlaced);
                require(slopeRate > flatRate * 3, "Actual safe slopes must be covered more often than safe flats");
                require(middleShare > .5, "Actual 3-6 layers must be common on slopes");
                JsonObject metrics = new JsonObject();
                metrics.addProperty("safe_flat_coverage", flatRate);
                metrics.addProperty("safe_slope_coverage", slopeRate);
                metrics.addProperty("slope_layers_3_to_6_share", middleShare);
                report.add("slope_acceptance", metrics);
            }
            if (enabled) {
                for (String name : List.of("crimson_cluster", "warped_cluster")) {
                    String family = name.replace("_cluster", "_nylium");
                    JsonObject families = scenes.getAsJsonObject(name).getAsJsonObject("patch_families");
                    require(families.has(family) && families.get(family).getAsLong() > 0,
                            "Positive Nether generation example contains no natural patches: " + name);
                }
            }
            report.addProperty("total_patches", totalPatches);
            report.addProperty("result", "PASS");
            writeJson(evidence.resolve(mode + ".json"), report);
        } catch (IOException exception) {
            report.addProperty("result", "FAIL");
            report.addProperty("failure", exception.toString());
            writeJson(evidence.resolve(mode + ".json"), report);
            throw new UncheckedIOException(exception);
        } catch (RuntimeException | Error failure) {
            report.addProperty("result", "FAIL");
            report.addProperty("failure", failure.toString());
            writeJson(evidence.resolve(mode + ".json"), report);
            throw failure;
        }
    }

    /** Check native feature seed indices, not merely their relative order. */
    private static JsonObject featureOrder(ServerLevel level) {
        var generator = level.getChunkSource().getGenerator();
        var biomes = List.copyOf(generator.getBiomeSource().possibleBiomes());
        Map<PlacedFeature, String> names = new IdentityHashMap<>();
        for (var biome : biomes) {
            for (var step : generator.getBiomeGenerationSettings(biome).features()) {
                for (var holder : step) names.put(holder.value(), holder.unwrapKey().orElseThrow().identifier().toString());
            }
        }
        String own = "buildup_groundscape:natural_sod_patches";
        var actual = FeatureSorter.buildFeaturesPerStep(biomes,
                biome -> generator.getBiomeGenerationSettings(biome).features(), true);
        var withoutMod = FeatureSorter.buildFeaturesPerStep(biomes,
                biome -> generator.getBiomeGenerationSettings(biome).features().stream()
                        .<HolderSet<PlacedFeature>>map(step -> HolderSet.direct(step.stream()
                                .filter(holder -> !own.equals(names.get(holder.value()))).toList())).toList(), true);
        JsonObject result = new JsonObject();
        JsonArray steps = new JsonArray();
        for (int step = 0; step < actual.size(); step++) {
            JsonArray entries = new JsonArray();
            for (int index = 0; index < actual.get(step).features().size(); index++) {
                PlacedFeature feature = actual.get(step).features().get(index);
                String name = names.get(feature);
                entries.add(name);
                if (!own.equals(name)) require(step < withoutMod.size()
                                && index < withoutMod.get(step).features().size()
                                && withoutMod.get(step).features().get(index) == feature,
                        "Mod registration changed a native feature seed index: " + name + " step=" + step + " index=" + index);
            }
            steps.add(entries);
        }
        result.add("steps", steps);
        result.addProperty("native_seed_indices", "IDENTICAL_WITH_MOD_REMOVED");
        return result;
    }

    private static Sample sample(ServerLevel level, BlockPos anchor, Scenario scenario, boolean reverse, JsonObject baseline) {
        int minChunkX = Math.floorDiv(anchor.getX(), 16) - 1;
        int minChunkZ = Math.floorDiv(anchor.getZ(), 16) - 1;
        List<int[]> positions = new ArrayList<>();
        // Complete a two-chunk halo before measuring the center. A later neighboring feature
        // must not turn a partial generation sample into a false protected-terrain regression.
        for (int x = -2; x < 5; x++) for (int z = -2; z < 5; z++) positions.add(new int[]{minChunkX + x, minChunkZ + z});
        if (reverse) Collections.reverse(positions);
        // Serialize native decoration before FULL conversion. Otherwise asynchronous LIGHT/FULL
        // dependency scheduling can reorder overlapping vanilla fungi even with one worker.
        long nativeStarted = System.nanoTime();
        for (int[] position : positions) {
            require(level.getChunkSource().getChunk(position[0], position[1], ChunkStatus.FEATURES, true) != null,
                    "Native FEATURES prepass returned null");
        }
        long nativeNanos = System.nanoTime() - nativeStarted;
        JsonArray timings = new JsonArray();
        Map<Long, LevelChunk> chunks = new HashMap<>();
        for (int[] position : positions) {
            boolean loaded = level.getChunkSource().getChunkNow(position[0], position[1]) != null;
            long started = System.nanoTime();
            LevelChunk chunk = (LevelChunk) level.getChunkSource().getChunk(position[0], position[1], ChunkStatus.FULL, true);
            long elapsed = System.nanoTime() - started;
            require(chunk != null, "Full chunk generation returned null");
            chunks.put(chunkKey(position[0], position[1]), chunk);
            JsonObject timing = new JsonObject();
            timing.addProperty("chunk_x", position[0]);
            timing.addProperty("chunk_z", position[1]);
            timing.addProperty("already_full", loaded);
            timing.addProperty("structure_protected", SodPatchFeature.hasStructures(chunk));
            timing.addProperty("sample_center", position[0] >= minChunkX && position[0] < minChunkX + 3
                    && position[1] >= minChunkZ && position[1] < minChunkZ + 3);
            timing.addProperty("nanoseconds", elapsed);
            timings.add(timing);
        }
        int minX = minChunkX * 16, minZ = minChunkZ * 16;
        Snapshot snapshot = capture(chunks, minX, minZ, level.getMinY(), level.getMaxY() - level.getMinY() + 1);
        if (GroundscapeWorldgenConfig.current().enabled()) {
            require(!new SodPatchFeature().place(level, level.getChunkSource().getGenerator(),
                    RandomSource.create(SEED), anchor), "Live ServerLevel feature call must refuse retrofitting");
            Snapshot afterRejectedRetrofit = capture(chunks, minX, minZ, level.getMinY(),
                    level.getMaxY() - level.getMinY() + 1);
            compare(snapshot, afterRejectedRetrofit, Map.of(), true);
        }
        long eligible = 0, biomeGround = 0;
        long safetyAccepted = 0, noiseSelected = 0, unprotectedSelected = 0;
        Map<String, Long> rejections = new LinkedHashMap<>();
        // Counts are taken after all safety and structure gates, on original ground.
        // Index 0 = eligible surfaces; 1 = proposals; 2..8 = actual layers 1..7.
        Map<String, long[]> terrainMetrics = new LinkedHashMap<>();
        for (String category : List.of("flat", "gentle", "slope")) terrainMetrics.put(category, new long[9]);
        BlockPos cameraGround = null;
        long bestDistance = Long.MAX_VALUE;
        int headroom = 0;
        int bestCameraScore = -1;
        int low = scenario.dimension == Level.NETHER ? Math.max(31, level.getMinY()) : level.getMinY();
        int high = scenario.dimension == Level.NETHER ? Math.min(level.getLogicalHeight() - 1, level.getMaxY()) : level.getMaxY() - 1;
        for (int x = minX; x < minX + WIDTH; x++) {
            for (int z = minZ; z < minZ + WIDTH; z++) {
                for (int y = high; y >= low; y--) {
                    BlockPos ground = new BlockPos(x, y, z);
                    BlockState material = level.getBlockState(ground);
                    if (familyForGround(material.getBlock()) == null || !level.getBiome(ground).is(scenario.biome)) continue;
                    biomeGround++;
                    BlockState above = level.getBlockState(ground.above());
                    boolean free = above.isAir() || above.getBlock() instanceof SodPatchBlock;
                    if (free && material.isFaceSturdy(level, ground, Direction.UP)) eligible++;
                    var analysis = SurfaceAnalyzer.analyze(level, ground.above(), pos ->
                            pos.getX() >= minX - 16 && pos.getX() < minX + WIDTH + 16
                                    && pos.getZ() >= minZ - 16 && pos.getZ() < minZ + WIDTH + 16);
                    if (analysis.accepted()) {
                        safetyAccepted++;
                        var settings = GroundscapeWorldgenConfig.current();
                        SlopeTerrain terrain = SlopeTerrain.sample(level, ground.above(), pos ->
                                pos.getX() >= minX - 16 && pos.getX() < minX + WIDTH + 16
                                        && pos.getZ() >= minZ - 16 && pos.getZ() < minZ + WIDTH + 16);
                        if (terrain != null) {
                            int proposed = SlopePlanner.layers(SEED, x, z, analysis.profile(), terrain, settings);
                            if (proposed > 0) noiseSelected++;
                            if (!SodPatchFeature.hasStructures(chunks.get(chunkKey(x >> 4, z >> 4)))) {
                                if (proposed > 0) unprotectedSelected++;
                                long[] counts = terrainMetrics.get(terrain.strength() < .15 ? "flat"
                                        : terrain.strength() < .65 ? "gentle" : "slope");
                                counts[0]++;
                                if (proposed > 0) counts[1]++;
                                if (above.getBlock() instanceof SodPatchBlock) {
                                    counts[above.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LAYERS) + 1]++;
                                }
                            }
                        }
                    } else rejections.merge(analysis.rejection().name(), 1L, Long::sum);
                    int clearance = 0;
                    while (clearance < 16 && y + clearance + 1 <= high) {
                        BlockState state = level.getBlockState(ground.above(clearance + 1));
                        if (!state.isAir() && !(state.getBlock() instanceof SodPatchBlock)) break;
                        clearance++;
                    }
                    long distance = (long) (x - anchor.getX()) * (x - anchor.getX())
                            + (long) (z - anchor.getZ()) * (z - anchor.getZ());
                    int visibleSurface = 0;
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                        BlockPos nearby = ground.offset(dx, 0, dz);
                        BlockState nearbyGround = level.getBlockState(nearby);
                        BlockState nearbyAbove = level.getBlockState(nearby.above());
                        if (nearbyGround.getBlock() == material.getBlock()
                                && (nearbyAbove.isAir() || nearbyAbove.getBlock() instanceof SodPatchBlock
                                || nearbyAbove.is(net.minecraft.world.level.block.Blocks.SNOW))) visibleSurface++;
                    }
                    int cameraScore = (clearance >= 6 ? 1000 : 0) + visibleSurface * 20 + Math.min(clearance, 12);
                    if (cameraGround == null || cameraScore > bestCameraScore
                            || (cameraScore == bestCameraScore && distance < bestDistance)) {
                        cameraGround = ground;
                        headroom = clearance;
                        bestCameraScore = cameraScore;
                        bestDistance = distance;
                    }
                }
            }
        }
        require(cameraGround != null, "No actual surface of the requested biome within sample: " + scenario.name);
        double cameraX = cameraGround.getX() + 0.5;
        double cameraY = cameraGround.getY() + Math.max(2, Math.min(6, headroom - 2));
        // Aim at the selected ground, rather than at a point four blocks beyond a small Nether
        // platform. Teleport coordinates are feet coordinates; spectator eyes add 1.62 blocks.
        double cameraZ = cameraGround.getZ() + 0.5;
        if (baseline != null) {
            cameraX = baseline.get("camera_x").getAsDouble();
            cameraY = baseline.get("camera_y").getAsDouble();
            cameraZ = baseline.get("camera_z").getAsDouble();
        }
        JsonObject diagnostics = new JsonObject();
        diagnostics.addProperty("native_features_prepass_nanoseconds", nativeNanos);
        diagnostics.addProperty("chunk_timing_scope", "FULL conversion after serialized native FEATURES prepass");
        diagnostics.addProperty("safe_target_biome_surfaces", safetyAccepted);
        diagnostics.addProperty("noise_selected_target_biome_surfaces", noiseSelected);
        diagnostics.addProperty("selected_outside_structure_chunks", unprotectedSelected);
        diagnostics.add("rejections", JSON.toJsonTree(rejections));
        diagnostics.addProperty("terrain_metric_columns", "eligible, proposals, actual_layers_1,2,3,4,5,6,7");
        diagnostics.add("terrain_metrics", JSON.toJsonTree(terrainMetrics));
        return new Sample(scenario, anchor, snapshot, timings, diagnostics, eligible, biomeGround, cameraX, cameraY, cameraZ);
    }

    private static Snapshot captureSavedRegion(ServerLevel level, Snapshot bounds) {
        Map<Long, LevelChunk> chunks = new HashMap<>();
        for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 3; dz++) {
            int x = Math.floorDiv(bounds.minX, 16) + dx;
            int z = Math.floorDiv(bounds.minZ, 16) + dz;
            // Re-read only the same nine previously generated FULL chunks, never a new sample.
            LevelChunk chunk = (LevelChunk) level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, true);
            require(chunk != null, "Saved FULL chunk could not be loaded");
            chunks.put(chunkKey(x, z), chunk);
        }
        return capture(chunks, bounds.minX, bounds.minZ, bounds.minY, bounds.height);
    }

    private static JsonObject differences(Snapshot generated, Snapshot activated, Map<String, PatchInfo> patches) {
        Map<String, Integer> changes = new java.util.TreeMap<>();
        for (int index = 0; index < generated.states.length; index++) {
            String before = generated.palette.get(generated.states[index]);
            String after = activated.palette.get(activated.states[index]);
            if (before.equals(after)) continue;
            require(!patches.containsKey(before) && !patches.containsKey(after),
                    "Natural patch changed during client activation at " + generated.position(index));
            changes.merge(before + " -> " + after, 1, Integer::sum);
        }
        JsonObject result = new JsonObject();
        changes.forEach(result::addProperty);
        return result;
    }

    private static Snapshot capture(Map<Long, LevelChunk> chunks, int minX, int minZ, int minY, int height) {
        List<String> palette = new ArrayList<>();
        Map<BlockState, Integer> indices = new IdentityHashMap<>();
        int[] states = new int[WIDTH * WIDTH * height];
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int index = 0;
        for (int x = minX; x < minX + WIDTH; x++) {
            for (int z = minZ; z < minZ + WIDTH; z++) {
                LevelChunk chunk = chunks.get(chunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
                for (int y = minY; y < minY + height; y++) {
                    BlockState state = chunk.getBlockState(position.set(x, y, z));
                    states[index++] = indices.computeIfAbsent(state, value -> {
                        palette.add(stateName(value));
                        return palette.size() - 1;
                    });
                }
            }
        }
        return new Snapshot(minX, minZ, minY, WIDTH, height, palette, states);
    }

    private static JsonObject summarize(Sample sample, Map<String, PatchInfo> patchStates) {
        JsonObject result = new JsonObject();
        result.addProperty("biome", sample.scenario.biome.identifier().toString());
        result.addProperty("dimension", sample.scenario.dimension.identifier().toString());
        result.addProperty("anchor_x", sample.anchor.getX());
        result.addProperty("anchor_y", sample.anchor.getY());
        result.addProperty("anchor_z", sample.anchor.getZ());
        result.addProperty("camera_x", sample.cameraX);
        result.addProperty("camera_y", sample.cameraY);
        result.addProperty("camera_z", sample.cameraZ);
        result.addProperty("eligible_ground", sample.eligible);
        result.addProperty("eligible_definition", "Vanilla surface family in target biome, sturdy top, air/patch above; excludes further production safety filters");
        result.addProperty("target_biome_ground", sample.biomeGround);
        result.addProperty("sampled_blocks", sample.snapshot.states.length);
        result.addProperty("state_hash", sample.snapshot.hash(Map.of()));
        long patches = 0, nonAir = 0, fluids = 0, snow = 0;
        long[] layers = new long[9];
        JsonObject families = new JsonObject();
        Map<SurfaceFamily, Long> familyCounts = new LinkedHashMap<>();
        for (int id : sample.snapshot.states) {
            String state = sample.snapshot.palette.get(id);
            PatchInfo info = patchStates.get(state);
            if (info != null) {
                patches++;
                layers[info.layers]++;
                familyCounts.merge(info.family, 1L, Long::sum);
                require(info.layers >= 1 && info.layers <= 7, "Natural patch outside layers 1..7");
            }
            if (!isAir(state)) nonAir++;
            if (state.startsWith("minecraft:water[") || state.startsWith("minecraft:lava[") || state.contains("waterlogged=true")) fluids++;
            if (state.startsWith("minecraft:snow[") || state.equals("minecraft:snow_block")) snow++;
        }
        familyCounts.forEach((family, count) -> families.addProperty(family.id(), count));
        JsonObject histogram = new JsonObject();
        for (int layer = 1; layer < layers.length; layer++) histogram.addProperty(Integer.toString(layer), layers[layer]);
        result.addProperty("patch_count", patches);
        result.addProperty("non_air_blocks", nonAir);
        result.addProperty("fluid_blocks", fluids);
        result.addProperty("snow_blocks", snow);
        result.add("patch_families", families);
        result.add("layer_histogram", histogram);
        result.add("chunk_timings", sample.timings);
        result.add("surface_diagnostics", sample.diagnostics);
        long[] times = new long[sample.timings.size()];
        for (int i = 0; i < times.length; i++) times[i] = sample.timings.get(i).getAsJsonObject().get("nanoseconds").getAsLong();
        java.util.Arrays.sort(times);
        result.addProperty("generation_total_ms", java.util.Arrays.stream(times).sum() / 1_000_000.0);
        result.addProperty("generation_median_ms", times[times.length / 2] / 1_000_000.0);
        result.addProperty("generation_p95_ms", times[(int) Math.ceil(times.length * 0.95) - 1] / 1_000_000.0);
        return result;
    }

    /** Native overlapping features change with traversal order, even when this mod is disabled.
     * Each enabled run still has a strict, full-volume disabled control. Across orders, compare
     * patch choices only where every local input (and the center vertical scan) is identical. */
    private static JsonObject compareStableInputs(Snapshot forwardGround, Snapshot reverseGround,
            Snapshot forward, Snapshot reverse, Map<String, PatchInfo> patches, boolean nether) {
        long nativeDifferences = 0, stablePatches = 0, changedInputs = 0, boundary = 0;
        for (int i = 0; i < forward.states.length; i++) {
            if (!forwardGround.palette.get(forwardGround.states[i]).equals(reverseGround.palette.get(reverseGround.states[i]))) nativeDifferences++;
            String a = forward.palette.get(forward.states[i]), b = reverse.palette.get(reverse.states[i]);
            if (!patches.containsKey(a) && !patches.containsKey(b)) continue;
            BlockPos pos = forward.position(i);
            if (pos.getX() < forward.minX + 2 || pos.getX() >= forward.minX + WIDTH - 2
                    || pos.getZ() < forward.minZ + 2 || pos.getZ() >= forward.minZ + WIDTH - 2) {
                boundary++;
                continue; // The saved comparison has no neighbouring column beyond this bound.
            }
            boolean same = true;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                for (int y = pos.getY() - 5; y <= pos.getY() + 2; y++) {
                    same &= sameState(forwardGround, reverseGround, pos.getX() + dx, y, pos.getZ() + dz);
                }
            }
            int low = nether ? forward.minY : pos.getY();
            int high = nether ? Math.min(128, forward.minY + forward.height) : forward.minY + forward.height;
            for (int y = low; y < high; y++) same &= sameState(forwardGround, reverseGround, pos.getX(), y, pos.getZ());
            if (same) {
                require(a.equals(b), "Same native input produced a different patch under reverse order at " + pos + ": " + a + " -> " + b);
                stablePatches++;
            } else changedInputs++;
        }
        JsonObject result = new JsonObject();
        result.addProperty("native_control_different_blocks", nativeDifferences);
        result.addProperty("identical_input_patch_locations", stablePatches);
        result.addProperty("changed_native_input_locations", changedInputs);
        result.addProperty("sample_boundary_locations_not_compared", boundary);
        result.addProperty("result", stablePatches > 0 ? "IDENTICAL_FOR_IDENTICAL_INPUTS" : "NO_STABLE_PATCH_LOCATIONS_IN_THIS_SCENE");
        return result;
    }

    private static boolean sameState(Snapshot a, Snapshot b, int x, int y, int z) {
        if (y < a.minY || y >= a.minY + a.height) return false;
        int i = ((x - a.minX) * a.width + z - a.minZ) * a.height + y - a.minY;
        return a.palette.get(a.states[i]).equals(b.palette.get(b.states[i]));
    }

    private static Comparison compare(Snapshot baseline, Snapshot actual, Map<String, PatchInfo> patches, boolean exact) {
        require(baseline.minX == actual.minX && baseline.minZ == actual.minZ && baseline.minY == actual.minY
                && baseline.width == actual.width && baseline.height == actual.height, "Snapshot bounds differ");
        int changed = 0;
        Map<Integer, String> normalized = new HashMap<>();
        for (int index = 0; index < actual.states.length; index++) {
            String before = baseline.palette.get(baseline.states[index]);
            String after = actual.palette.get(actual.states[index]);
            if (before.equals(after)) continue;
            BlockPos position = actual.position(index);
            require(!exact, "Exact snapshot comparison differs at " + position + ": " + before + " -> " + after);
            PatchInfo patch = patches.get(after);
            require(isAir(before) && patch != null, "Protected terrain changed at " + position + ": " + before + " -> " + after);
            require(position.getY() > actual.minY, "Patch at minimum build height");
            String support = baseline.palette.get(baseline.states[index - 1]);
            String expected = BuiltInRegistries.BLOCK.getKey(patch.family.vanillaBlock()).toString();
            require(blockName(support).equals(expected), "Patch family differs from original ground at " + position);
            require(patch.layers >= 1 && patch.layers <= 7, "Generated layer count outside 1..7");
            normalized.put(index, before);
            changed++;
        }
        String protectedHash = actual.hash(normalized);
        require(protectedHash.equals(baseline.hash(Map.of())), "Protected-state hash differs from baseline");
        return new Comparison(changed, protectedHash);
    }

    private static Map<String, PatchInfo> patchStates() {
        Map<String, PatchInfo> result = new HashMap<>();
        for (var variant : GroundscapeBlocks.variants()) {
            for (BlockState state : variant.patch().getStateDefinition().getPossibleStates()) {
                result.put(stateName(state), new PatchInfo(variant.family(), state.getValue(SodPatchBlock.LAYERS)));
            }
        }
        return result;
    }

    private static SurfaceFamily familyForGround(Block block) {
        for (SurfaceFamily family : SurfaceFamily.values()) if (family.vanillaBlock() == block) return family;
        return null;
    }

    private static String stateName(BlockState state) {
        String properties = state.getValues().map(value -> value.property().getName() + "=" + value.valueName())
                .sorted().collect(java.util.stream.Collectors.joining(","));
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()) + (properties.isEmpty() ? "" : "[" + properties + "]");
    }

    private static String blockName(String state) {
        int bracket = state.indexOf('[');
        return bracket < 0 ? state : state.substring(0, bracket);
    }

    private static boolean isAir(String state) {
        return state.equals("minecraft:air") || state.equals("minecraft:cave_air") || state.equals("minecraft:void_air");
    }

    private static long chunkKey(int x, int z) {
        return (long) x << 32 | z & 0xffffffffL;
    }

    private static Path evidenceDirectory() {
        Path directory = Path.of("").toAbsolutePath().normalize();
        while (directory != null && !Files.exists(directory.resolve("build.gradle"))) directory = directory.getParent();
        require(directory != null, "Cannot locate target-version project directory");
        return directory.getParent().resolve("docs/evidence/s1-fix");
    }

    private static JsonObject readJson(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException("Run the off baseline first: " + path, exception);
        }
    }

    private static void writeJson(Path path, JsonObject value) {
        try {
            Files.writeString(path, JSON.toJson(value) + "\n", StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void writeSnapshot(Path path, Snapshot snapshot) throws IOException {
        require(snapshot.palette.size() <= 65535, "Snapshot palette too large");
        try (var output = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(path))))) {
            output.writeUTF(SNAPSHOT_MAGIC);
            output.writeLong(SEED);
            output.writeInt(snapshot.minX);
            output.writeInt(snapshot.minZ);
            output.writeInt(snapshot.minY);
            output.writeInt(snapshot.width);
            output.writeInt(snapshot.height);
            output.writeInt(snapshot.palette.size());
            for (String entry : snapshot.palette) output.writeUTF(entry);
            output.writeInt(snapshot.states.length);
            for (int state : snapshot.states) output.writeShort(state);
        }
    }

    private static Snapshot readSnapshot(Path path) throws IOException {
        try (var input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))))) {
            require(input.readUTF().equals(SNAPSHOT_MAGIC) && input.readLong() == SEED, "Invalid snapshot header: " + path);
            int x = input.readInt(), z = input.readInt(), y = input.readInt(), width = input.readInt(), height = input.readInt();
            require(width == WIDTH && height > 0 && height <= 1024, "Invalid snapshot dimensions");
            int paletteSize = input.readInt();
            require(paletteSize > 0 && paletteSize <= 65535, "Invalid snapshot palette");
            List<String> palette = new ArrayList<>();
            for (int i = 0; i < paletteSize; i++) palette.add(input.readUTF());
            int length = input.readInt();
            require(length == width * width * height, "Invalid snapshot size");
            int[] states = new int[length];
            for (int i = 0; i < length; i++) {
                states[i] = input.readUnsignedShort();
                require(states[i] < paletteSize, "Invalid state palette index");
            }
            return new Snapshot(x, z, y, width, height, palette, states);
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Scenario(String name, ResourceKey<Level> dimension, ResourceKey<Biome> biome, int ordinal) { }
    private record PatchInfo(SurfaceFamily family, int layers) { }
    private record Comparison(int changed, String protectedHash) { }
    private record Sample(Scenario scenario, BlockPos anchor, Snapshot snapshot, JsonArray timings, JsonObject diagnostics,
                          long eligible, long biomeGround, double cameraX, double cameraY, double cameraZ) { }

    private record Snapshot(int minX, int minZ, int minY, int width, int height, List<String> palette, int[] states) {
        BlockPos position(int index) {
            int column = index / height;
            return new BlockPos(minX + column / width, minY + index % height, minZ + column % width);
        }

        String hash(Map<Integer, String> replacements) {
            MessageDigest digest = digest();
            byte[][] values = palette.stream().map(value -> digest().digest(value.getBytes(StandardCharsets.UTF_8))).toArray(byte[][]::new);
            Map<String, byte[]> replacementHashes = new HashMap<>();
            for (int index = 0; index < states.length; index++) {
                String replacement = replacements.get(index);
                digest.update(replacement == null ? values[states[index]]
                        : replacementHashes.computeIfAbsent(replacement, value -> digest().digest(value.getBytes(StandardCharsets.UTF_8))));
            }
            return HexFormat.of().formatHex(digest.digest());
        }
    }
}
