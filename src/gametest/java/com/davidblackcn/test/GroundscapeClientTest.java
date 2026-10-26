package com.davidblackcn.test;

import com.davidblackcn.block.SodPatchBlock;
import com.davidblackcn.registry.GroundscapeBlocks;
import com.davidblackcn.registry.SurfaceFamily;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import java.util.ArrayList;
import javax.imageio.ImageIO;

/** End-to-end test mod only; never included in the distributable jar. */
public final class GroundscapeClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (System.getProperty("groundscape.worldgenTest") != null || System.getProperty("groundscape.leafTest") != null) return;
        context.runOnClient(client -> {
            client.options.renderDistance().set(8);
            client.getWindow().setWidth(1280);
            client.getWindow().setHeight(720);
            client.gameRenderer.resize(1280, 720);
        });
        TestWorldSave save;
        Map<BlockPos, BlockState> showcase = new LinkedHashMap<>();
        try (var world = context.worldBuilder().create()) {
            save = world.getWorldSave();
            var server = world.getServer();
            var connection = world.getConnection();
            server.runOnServer(s -> { s.getPlayerList().setViewDistance(8); for (int cx = -1; cx <= 8; cx++) for (int cz = -1; cz <= 8; cz++) s.overworld().getChunk(cx, cz); });
            server.runCommand("fill -4 4 -4 80 4 80 minecraft:stone");
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 60 105 110 180 45");
            server.runOnServer(s -> {
                int row = 0;
                int globalState = 0;
                for (var variant : GroundscapeBlocks.variants()) {
                    int column = 0;
                    for (var block : variant.blocks()) {
                        // Keep independent shapes apart so neighbor updates cannot alter corner states.
                        for (var state : block.getStateDefinition().getPossibleStates()) {
                            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                                    && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) continue;
                            int index = globalState++;
                            var pos = new BlockPos(index % 40 * 2, 5 + index / 1600 * 20, index / 40 % 40 * 2);
                            s.overworld().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
                            s.overworld().setBlock(pos, state, 2);
                            showcase.put(pos, state);
                            column++;
                        }
                    }
                    row++;
                }
            });
            require(showcase.size() == 6462, "Showcase includes every dry block state without overlaps");
            context.waitTicks(20);
            context.waitFor(client -> showcase.keySet().stream().allMatch(client.level::hasChunkAt), 1200);
            connection.waitForClientboundPackets();
            connection.waitForChunksRender(false);
            checkModels(context);
            checkStates(context, showcase);
            for (String biome : new String[]{"minecraft:plains", "minecraft:swamp", "minecraft:badlands"}) {
                server.runCommand("fillbiome -4 0 -4 80 96 80 " + biome);
                context.waitTicks(5);
                connection.waitForClientboundPackets();
                checkGrassTint(context, new BlockPos(0, 5, 0));
            }
            screenshot(context, "groundscape-showcase");
            server.runCommand("fill 90 4 0 116 4 26 minecraft:grass_block");
            server.runCommand("fillbiome 90 0 0 116 16 26 minecraft:plains");
            server.runOnServer(s -> {
                for (int i = 0; i < 1; i++) for (var vegetation : SodPatchBlock.Vegetation.values()) {
                    var pos = new BlockPos(91 + vegetation.ordinal() % 9 * 3, 5, 1 + vegetation.ordinal() / 9 * 3);
                    var state = GroundscapeBlocks.get(SurfaceFamily.GRASS).patch().defaultBlockState()
                            .setValue(SodPatchBlock.LAYERS, 3).setValue(SodPatchBlock.VEGETATION, vegetation);
                    s.overworld().setBlock(pos, state, 2); showcase.put(pos, state);
                }
            });
            server.runCommand("tp @a 103 22 32 180 45");
            context.waitTicks(5); connection.waitForClientboundPackets(); connection.waitForChunksRender(false);
            screenshot(context, "groundscape-vegetation");
            server.runCommand("fill 90 4 40 114 4 82 minecraft:stone");
            server.runOnServer(s -> {
                for (var family : com.davidblackcn.registry.LeafFamily.values()) for (int n = 1; n <= 8; n++) {
                    var pos = new BlockPos(90 + (n - 1) * 3, 5, 40 + family.ordinal() * 3);
                    var state = com.davidblackcn.registry.GroundscapeLeaves.get(family).defaultBlockState().setValue(com.davidblackcn.block.LeafPatchBlock.LAYERS, n);
                    s.overworld().setBlock(pos, state, 2); showcase.put(pos, state);
                }
            });
            server.runCommand("tp @a 102 26 92 180 40");
            context.waitTicks(5); connection.waitForClientboundPackets(); connection.waitForChunksRender(false);
            screenshot(context, "groundscape-leaf-patches");
            server.runCommand("fill 90 4 90 114 4 122 minecraft:stone");
            server.runOnServer(s -> {
                int row = 0;
                for (var variant : GroundscapeBlocks.variants()) {
                    if (variant.family().hasSodBlock()) continue;
                    int z = 92 + row++ * 5;
                    s.overworld().setBlock(new BlockPos(92, 5, z), variant.family().vanillaBlock().defaultBlockState(), 2);
                    for (int i = 0; i < 6; i++) {
                        var state = i < 4 ? variant.patch().defaultBlockState().setValue(SodPatchBlock.LAYERS, 1 + i * 2)
                                : i == 4 ? variant.slab().defaultBlockState() : variant.stairs().defaultBlockState();
                        var pos = new BlockPos(95 + i * 3, 5, z);
                        s.overworld().setBlock(pos, state, 2); showcase.put(pos, state);
                    }
                }
            });
            server.runCommand("tp @a 102 23 131 180 38");
            context.waitTicks(5); connection.waitForClientboundPackets(); connection.waitForChunksRender(false);
            context.waitTicks(220); // Let transient login/game-mode messages fade before the visual evidence.
            screenshot(context, "groundscape-s2-materials");
            server.runCommand("tp @a 60 105 110 180 45");
            checkTexturePack(context, connection);
        }
        // Actual world close/reopen, not merely a codec round trip.
        try (var reopened = save.open()) {
            reopened.getServer().runOnServer(s -> s.getPlayerList().setViewDistance(8));
            context.waitTicks(20);
            context.waitFor(client -> showcase.keySet().stream().allMatch(client.level::hasChunkAt), 1200);
            checkStates(context, showcase);
        }

        Properties properties = new Properties();
        properties.setProperty("server-ip", "127.0.0.1");
        try (var server = context.worldBuilder().createServer(properties)) {
            var patch = GroundscapeBlocks.get(SurfaceFamily.GRASS).patch();
            BlockPos pos = new BlockPos(0, 5, 0);
            try (var connection = server.connect()) {
                server.runCommand("fill -4 4 -4 4 4 4 minecraft:stone");
                server.runCommand("gamemode survival @a");
                server.runCommand("tp @a 0.5 5 2.5 180 30");
                server.runOnServer(s -> {
                    connection.getServerPlayer().getInventory().setSelectedItem(new ItemStack(patch, 8));
                });
                context.waitTick();
                connection.waitForClientboundPackets();
                context.waitFor(client -> client.level.getBlockState(pos.below()).is(Blocks.STONE), 1200);
                for (int layers = 1; layers <= 8; layers++) {
                    int previous = layers - 1;
                    context.runOnClient(client -> {
                        BlockPos clicked = previous == 0 ? pos.below() : pos;
                        Vec3 hit = new Vec3(0.5, 5 + previous / 8.0, 0.5);
                        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                                new BlockHitResult(hit, Direction.UP, clicked, false));
                    });
                    connection.waitForServerboundPackets();
                    context.waitTick();
                    connection.waitForClientboundPackets();
                    int expected = layers;
                    server.runOnServer(s -> require(s.overworld().getBlockState(pos).getValue(SodPatchBlock.LAYERS) == expected,
                            "Dedicated server patch stacking " + expected));
                }
                server.runOnServer(s -> require(connection.getServerPlayer().getMainHandItem().isEmpty(), "Survival consumes eight items"));
                context.takeScreenshot("groundscape-dedicated-stacking");
            }
            try (var reconnected = server.connect()) {
                context.waitFor(client -> client.level.getBlockState(pos).is(patch), 1200);
                context.runOnClient(client -> require(client.level.getBlockState(pos).getValue(SodPatchBlock.LAYERS) == 8,
                        "Dedicated reconnect retains stack"));
                // Real client prediction + START/STOP packets to a separate dedicated server.
                for (String mode : new String[]{"survival", "creative"}) {
                    server.runCommand("gamemode " + mode + " @a");
                    for (var plant : new SodPatchBlock.Vegetation[]{SodPatchBlock.Vegetation.GRASS, SodPatchBlock.Vegetation.FERN}) {
                        for (boolean shears : new boolean[]{false, true}) {
                            var composite = patch.defaultBlockState().setValue(SodPatchBlock.LAYERS, 5)
                                    .setValue(SodPatchBlock.VEGETATION, plant);
                            var bare = composite.setValue(SodPatchBlock.VEGETATION, SodPatchBlock.Vegetation.NONE);
                            server.runOnServer(s -> {
                                s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                                        new net.minecraft.world.phys.AABB(pos).inflate(4)).forEach(net.minecraft.world.entity.item.ItemEntity::discard);
                                s.overworld().setBlock(pos, composite, 3);
                                reconnected.getServerPlayer().getInventory().setSelectedItem(shears
                                        ? new ItemStack(net.minecraft.world.item.Items.SHEARS) : ItemStack.EMPTY);
                            });
                            context.waitTicks(6); reconnected.waitForClientboundPackets();
                            context.runOnClient(client -> {
                                require(client.level.getBlockState(pos).equals(composite), "composite synchronized before right click: " + client.level.getBlockState(pos));
                                require(shears ? client.player.getMainHandItem().is(net.minecraft.world.item.Items.SHEARS) : client.player.getMainHandItem().isEmpty(),
                                        "expected right-click tool: " + client.player.getMainHandItem());
                                client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                            });
                            reconnected.waitForServerboundPackets(); context.waitTick(); reconnected.waitForClientboundPackets();
                            server.runOnServer(s -> require(s.overworld().getBlockState(pos).equals(composite), "right click no longer weeds: " + mode + "/" + plant + "/shears=" + shears + " got " + s.overworld().getBlockState(pos)));
                            context.runOnClient(client -> {
                                require(client.gameMode.startDestroyBlock(pos, Direction.UP), "first native left click succeeds");
                                require(client.level.getBlockState(pos).equals(bare), "client predicts plant-only removal immediately");
                                client.gameMode.stopDestroyBlock();
                            });
                            reconnected.waitForServerboundPackets(); context.waitTicks(6); reconnected.waitForClientboundPackets();
                            server.runOnServer(s -> require(s.overworld().getBlockState(pos).equals(bare), "server retains five layers after first break"));
                            context.runOnClient(client -> require(client.level.getBlockState(pos).equals(bare), "plant removal survives server acknowledgement"));
                            context.runOnClient(client -> client.gameMode.startDestroyBlock(pos, Direction.UP));
                            for (int tick = 0; tick < 80 && context.computeOnClient(client -> !client.level.getBlockState(pos).isAir()); tick++) {
                                context.runOnClient(client -> client.gameMode.continueDestroyBlock(pos, Direction.UP));
                                context.waitTick();
                            }
                            reconnected.waitForServerboundPackets(); context.waitTicks(2); reconnected.waitForClientboundPackets();
                            server.runOnServer(s -> require(s.overworld().getBlockState(pos).isAir(), "second native left click removes thin layer"));
                            context.runOnClient(client -> {
                                require(client.level.getBlockState(pos).isAir(), "no ghost thin layer after second break");
                                client.gameMode.stopDestroyBlock();
                            });
                        }
                    }
                }
                for (String mode : new String[]{"survival", "creative"}) {
                    server.runCommand("gamemode " + mode + " @a");
                    for (var plantBlock : new net.minecraft.world.level.block.Block[]{Blocks.SHORT_GRASS, Blocks.WILDFLOWERS, Blocks.LEAF_LITTER}) {
                        for (int n : new int[]{3, 8}) {
                            server.runOnServer(s -> {
                                s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(4)).forEach(net.minecraft.world.entity.item.ItemEntity::discard);
                                s.overworld().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                                s.overworld().setBlock(pos, patch.defaultBlockState().setValue(SodPatchBlock.LAYERS, n), 3);
                                reconnected.getServerPlayer().getInventory().setSelectedItem(new ItemStack(plantBlock, 8));
                            });
                            context.waitTicks(6); reconnected.waitForClientboundPackets();
                            int clicks = plantBlock instanceof net.minecraft.world.level.block.SegmentableBlock ? 4 : 1;
                            for (int click = 0; click < clicks; click++) {
                                context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                                        new BlockHitResult(Vec3.atCenterOf(pos).add(0, .49, 0), Direction.UP, pos, false)));
                                reconnected.waitForServerboundPackets(); context.waitTicks(2); reconnected.waitForClientboundPackets();
                            }
                            server.runOnServer(s -> {
                                var state = s.overworld().getBlockState(pos);
                                require(state.getValue(SodPatchBlock.LAYERS) == n, "planting keeps height");
                                var plant = n == 8 ? s.overworld().getBlockState(pos.above()) : state.getValue(SodPatchBlock.VEGETATION).plant();
                                require(plant.is(plantBlock), "planting synchronizes native or contained plant");
                                if (plantBlock instanceof net.minecraft.world.level.block.SegmentableBlock segments)
                                    require(plant.getValue(segments.getSegmentAmountProperty()) == 4, "four decoration segments survive packet roundtrip");
                                require(reconnected.getServerPlayer().getMainHandItem().getCount() == (mode.equals("creative") ? 8 : 8 - clicks), "native inventory consumption");
                            });
                        }
                    }
                }

            }
        }
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        Path directory = Path.of("").toAbsolutePath().normalize();
        while (directory != null && !Files.exists(directory.resolve("build.gradle"))) directory = directory.getParent();
        require(directory != null, "Cannot locate project directory");
        Path evidence = directory.getParent().resolve("docs/evidence/vegetation-leaves");
        try { Files.createDirectories(evidence); } catch (IOException e) { throw new UncheckedIOException(e); }
        context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1280, 720)
                .withDestinationDir(evidence).disableCounterPrefix());
    }

    private static void checkModels(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var models = client.getModelManager().getBlockStateModelSet();
            for (var block : GroundscapeBlocks.allBlocks()) {
                for (var state : block.getStateDefinition().getPossibleStates()) {
                    require(models.get(state) != models.missingModel(), "Missing baked model: " + state);
                }
            }
        });
    }

    private static void checkTexturePack(ClientGameTestContext context, TestServerConnection connection) {
        // An original diagnostic fixture, created only in the disposable test run directory.
        var directory = context.computeOnClient(client -> client.getResourcePackDirectory().resolve("groundscape-diagnostic"));
        try {
            var textures = directory.resolve("assets/minecraft/textures/block");
            Files.createDirectories(textures);
            Files.writeString(directory.resolve("pack.mcmeta"), """
                    {"pack":{"description":"Groundscape original diagnostic checkerboard","min_format":[97,1],"max_format":[97,1]}}
                    """);
            var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
                image.setRGB(x, y, ((x / 4 + y / 4) % 2 == 0) ? 0xFFFFFFFF : 0xFF404040);
            }
            for (var family : SurfaceFamily.values()) {
                ImageIO.write(image, "png", textures.resolve(textureName(family) + ".png").toFile());
            }
            for (var family : com.davidblackcn.registry.LeafFamily.values()) ImageIO.write(image, "png", textures.resolve(family.id() + "_leaves.png").toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        var oldSelection = context.computeOnClient(client -> new ArrayList<>(client.getResourcePackRepository().getSelectedIds()));
        var reload = context.computeOnClient(client -> {
            var repository = client.getResourcePackRepository();
            repository.reload();
            require(repository.addPack("file/groundscape-diagnostic"), "Diagnostic pack selected");
            return client.reloadResourcePacks();
        });
        context.waitFor(client -> reload.isDone(), 1200);
        reload.join();
        context.waitFor(client -> client.gui.overlay() == null && client.gui.screen() == null, 1200);
        connection.waitForChunksRender(false);
        checkModels(context);
        context.runOnClient(client -> {
            for (var variant : GroundscapeBlocks.variants()) {
                var texture = Identifier.withDefaultNamespace("textures/block/" + textureName(variant.family()) + ".png");
                require(client.getResourceManager().getResource(texture).orElseThrow().sourcePackId().equals("file/groundscape-diagnostic"),
                        "Vanilla texture is overridden by diagnostic resource pack");
                var spriteId = Identifier.withDefaultNamespace("block/" + textureName(variant.family()));
                for (var block : variant.blocks()) {
                    var model = client.getModelManager().getBlockStateModelSet().get(block.defaultBlockState());
                    require(model.particleMaterial().sprite().contents().name().equals(spriteId), "Sod model uses overridden vanilla sprite");
                }
            }
        });
        context.runOnClient(client -> {
            for (var family : com.davidblackcn.registry.LeafFamily.values()) {
                var texture = Identifier.withDefaultNamespace("textures/block/" + family.id() + "_leaves.png");
                require(client.getResourceManager().getResource(texture).orElseThrow().sourcePackId().equals("file/groundscape-diagnostic"), "Leaf texture replacement");
                for (var state : com.davidblackcn.registry.GroundscapeLeaves.get(family).getStateDefinition().getPossibleStates()) {
                    var model = client.getModelManager().getBlockStateModelSet().get(state);
                    require(model.particleMaterial().sprite().contents().name().equals(Identifier.withDefaultNamespace("block/" + family.id() + "_leaves")), "Leaf model uses native sprite");
                }
            }
        });
        context.waitTicks(10);
        screenshot(context, "groundscape-resourcepack");
        var restore = context.computeOnClient(client -> {
            client.getResourcePackRepository().setSelected(oldSelection);
            return client.reloadResourcePacks();
        });
        context.waitFor(client -> restore.isDone(), 1200);
        restore.join();
        context.waitFor(client -> client.gui.overlay() == null, 1200);
    }

    private static String textureName(SurfaceFamily family) {
        return switch (family) {
            case GRASS -> "grass_block_top";
            case MYCELIUM -> "mycelium_top";
            case PODZOL -> "podzol_top";
            case CRIMSON_NYLIUM -> "crimson_nylium";
            case WARPED_NYLIUM -> "warped_nylium";
            default -> family.id();
        };
    }

    private static void checkGrassTint(ClientGameTestContext context, BlockPos pos) {
        context.runOnClient(client -> {
            var colors = client.getBlockColors();
            for (var family : com.davidblackcn.registry.LeafFamily.values()) {
                var nativeState = family.source().defaultBlockState();
                var patchState = com.davidblackcn.registry.GroundscapeLeaves.get(family).defaultBlockState();
                if (family.tinted()) require(colors.getTintSource(nativeState, 0).colorInWorld(nativeState, client.level, pos)
                        == colors.getTintSource(patchState, 0).colorInWorld(patchState, client.level, pos), "Native leaf tint " + family);
            }
            var vanilla = Blocks.GRASS_BLOCK.defaultBlockState();
            int expected = colors.getTintSource(vanilla, 0).colorInWorld(vanilla, client.level, pos);
            for (var variant : GroundscapeBlocks.variants()) {
                for (var block : variant.blocks()) {
                    var state = block.defaultBlockState();
                    if (variant.family().usesGrassTint()) {
                        require(colors.getTintSource(state, 0).colorInWorld(state, client.level, pos) == expected,
                                "Grass tint differs from vanilla: " + state);
                    } else if (block instanceof SodPatchBlock) {
                        require(colors.getTintSource(state, 0).colorInWorld(state, client.level, pos) == -1,
                                "Non-grass sod retains neutral particle tint: " + state);
                    } else {
                        require(colors.getTintSources(state).isEmpty(), "Unexpected grass tint: " + state);
                    }
                    if (block instanceof SodPatchBlock) {
                        require(colors.getTintSource(state, 1).colorInWorld(state, client.level, pos) == expected,
                                "Plant tint differs from native biome grass: " + state);
                    }
                }
            }
        });
    }

    private static void checkStates(ClientGameTestContext context, Map<BlockPos, BlockState> expected) {
        context.runOnClient(client -> expected.forEach((pos, state) ->
                require(client.level.getBlockState(pos).equals(state), "State mismatch at " + pos + ": expected " + state + " got " + client.level.getBlockState(pos))));
    }

    private static void require(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
