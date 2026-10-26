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
        if (System.getProperty("groundscape.worldgenTest") != null) return;
        context.runOnClient(client -> {
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
            server.runCommand("fill -4 4 -4 52 4 92 minecraft:stone");
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 24 40 96 180 32");
            server.runOnServer(s -> {
                int row = 0;
                for (var variant : GroundscapeBlocks.variants()) {
                    int column = 0;
                    for (var block : variant.blocks()) {
                        // Keep independent shapes apart so neighbor updates cannot alter corner states.
                        for (var state : block.getStateDefinition().getPossibleStates()) {
                            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                                    && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) continue;
                            var pos = new BlockPos(column % 25 * 2, 5, row * 8 + column / 25 * 2);
                            s.overworld().setBlock(pos, state, 2);
                            showcase.put(pos, state);
                            column++;
                        }
                    }
                    row++;
                }
            });
            require(showcase.size() == 742, "Showcase includes every dry block state without overlaps");
            context.waitTick();
            connection.waitForClientboundPackets();
            connection.waitForChunksRender();
            checkModels(context);
            checkStates(context, showcase);
            for (String biome : new String[]{"minecraft:plains", "minecraft:swamp", "minecraft:badlands"}) {
                server.runCommand("fillbiome -4 0 -4 52 16 92 " + biome);
                context.waitTicks(5);
                connection.waitForClientboundPackets();
                checkGrassTint(context, new BlockPos(0, 5, 0));
            }
            screenshot(context, "groundscape-showcase");
            server.runCommand("fill 59 4 0 75 4 8 minecraft:grass_block");
            server.runCommand("fillbiome 59 0 0 75 16 8 minecraft:plains");
            server.runOnServer(s -> {
                for (int i = 0; i < 4; i++) for (var vegetation : SodPatchBlock.Vegetation.values()) {
                    var pos = new BlockPos(61 + i * 3, 5, 1 + vegetation.ordinal() * 3);
                    var state = GroundscapeBlocks.get(SurfaceFamily.GRASS).patch().defaultBlockState()
                            .setValue(SodPatchBlock.LAYERS, 1 + i * 2).setValue(SodPatchBlock.VEGETATION, vegetation);
                    s.overworld().setBlock(pos, state, 2); showcase.put(pos, state);
                }
            });
            server.runCommand("tp @a 66 9 15 180 27");
            context.waitTicks(5); connection.waitForClientboundPackets(); connection.waitForChunksRender();
            screenshot(context, "groundscape-vegetation");
            server.runCommand("fill 60 4 18 82 4 48 minecraft:stone");
            server.runOnServer(s -> {
                int row = 0;
                for (var variant : GroundscapeBlocks.variants()) {
                    if (variant.family().hasSodBlock()) continue;
                    int z = 20 + row++ * 5;
                    s.overworld().setBlock(new BlockPos(62, 5, z), variant.family().vanillaBlock().defaultBlockState(), 2);
                    for (int i = 0; i < 6; i++) {
                        var state = i < 4 ? variant.patch().defaultBlockState().setValue(SodPatchBlock.LAYERS, 1 + i * 2)
                                : i == 4 ? variant.slab().defaultBlockState() : variant.stairs().defaultBlockState();
                        var pos = new BlockPos(65 + i * 3, 5, z);
                        s.overworld().setBlock(pos, state, 2); showcase.put(pos, state);
                    }
                }
            });
            server.runCommand("tp @a 71 23 60 180 38");
            context.waitTicks(5); connection.waitForClientboundPackets(); connection.waitForChunksRender();
            context.waitTicks(220); // Let transient login/game-mode messages fade before the visual evidence.
            screenshot(context, "groundscape-s2-materials");
            server.runCommand("tp @a 24 40 96 180 32");
            checkTexturePack(context, connection);
        }
        // Actual world close/reopen, not merely a codec round trip.
        try (var reopened = save.open()) {
            reopened.getConnection().waitForChunksDownload();
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
                connection.waitForChunksDownload();
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
                reconnected.waitForChunksDownload();
                context.runOnClient(client -> require(client.level.getBlockState(pos).getValue(SodPatchBlock.LAYERS) == 8,
                        "Dedicated reconnect retains stack"));
            }
        }
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        Path directory = Path.of("").toAbsolutePath().normalize();
        while (directory != null && !Files.exists(directory.resolve("build.gradle"))) directory = directory.getParent();
        require(directory != null, "Cannot locate project directory");
        Path evidence = directory.getParent().resolve("docs/evidence/s2");
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
                require(client.level.getBlockState(pos).equals(state), "State mismatch at " + pos + ": " + state)));
    }

    private static void require(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
