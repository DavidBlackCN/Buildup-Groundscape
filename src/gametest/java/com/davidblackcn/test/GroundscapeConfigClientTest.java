package com.davidblackcn.test;

import com.davidblackcn.client.config.GroundscapeConfigScreen;
import com.davidblackcn.config.GroundscapeConfigManager;
import com.davidblackcn.block.SodPatchBlock;
import com.davidblackcn.worldgen.GroundscapeWorldgenConfig;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.gui.YACLScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import java.util.*;
import java.nio.file.*;
import java.io.*;

/** Real YACL screens and play-packet round trips, including a running world's off/on/off generation. */
public final class GroundscapeConfigClientTest implements FabricClientGameTest {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        if (System.getProperty("groundscape.worldgenTest") != null || System.getProperty("groundscape.leafTest") != null) return;
        var original = GroundscapeConfigManager.snapshot().config();
        context.runOnClient(client -> {
            client.getWindow().setWidth(1280); client.getWindow().setHeight(720);
            client.resizeGui();
        });
        try {
            apply(GroundscapeWorldgenConfig.defaults().json());
            context.runOnClient(client -> client.gui.setScreen(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")
                    ? new com.davidblackcn.client.config.GroundscapeModMenu().getModConfigScreenFactory().create(null)
                    : GroundscapeConfigScreen.open(null)));
            context.waitFor(client -> client.gui.screen() instanceof YACLScreen);
            context.runOnClient(client -> {
                var screen = (YACLScreen) client.gui.screen();
                var options = options(screen);
                require(options.size() == 38, "all 38 existing scalar settings visible");
                require(options.stream().allMatch(Option::available), "local settings editable");
                set(screen, "density", .2);
                require(GroundscapeWorldgenConfig.current().density() == .12, "draft does not apply before save");
                screen.cancelOrReset();
                require(GroundscapeWorldgenConfig.current().density() == .12, "cancel does not save");
            });
            context.runOnClient(client -> {
                client.gui.setScreen(GroundscapeConfigScreen.open(null));
                var screen = (YACLScreen) client.gui.screen();
                set(screen, "density", .2); screen.finishOrSave();
                require(GroundscapeWorldgenConfig.current().density() == .2, "local GUI save persists");
                client.gui.setScreen(GroundscapeConfigScreen.open(null));
                screen = (YACLScreen) client.gui.screen();
                screen.cancelOrReset(); // no edits: request default values, not immediate mutation
                require(GroundscapeWorldgenConfig.current().density() == .2, "reset is a draft until saved");
                screen.finishOrSave();
                require(GroundscapeWorldgenConfig.current().density() == .12, "reset saved default density");
                client.gui.setScreen(null);
            });
            var languageReload = context.computeOnClient(client -> {
                client.getLanguageManager().setSelected("zh_cn");
                return client.reloadResourcePacks();
            });
            context.waitFor(client -> languageReload.isDone(), 1200); languageReload.join();
            context.waitFor(client -> client.gui.overlay() == null, 1200);
            try (var world = context.worldBuilder().adjustSettings(creator -> {
                var normal = creator.getSettings().worldgenLoadContext().lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL);
                creator.setWorldType(new net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.WorldTypeEntry(normal));
                creator.setSeed("20261028");
            }).create()) {
                var server = world.getServer();
                int off = server.computeOnServer(s -> count(s.overworld(), 100, 100, true));
                require(off == 0, "startup-disabled area has no patches");
                open(context);
                context.runOnClient(client -> {
                    var screen = (YACLScreen) client.gui.screen();
                    require(options(screen).stream().allMatch(Option::available), "singleplayer host can edit");
                    set(screen, "enabled", true); set(screen, "density", .35); set(screen, "flatCoverage", 1.0);
                    screen.finishOrSave();
                });
                context.waitFor(client -> client.gui.screen() instanceof AlertScreen, 400);
                require(server.computeOnServer(s -> GroundscapeWorldgenConfig.current().enabled()), "server acknowledged hot enable");
                context.runOnClient(client -> client.gui.setScreen(null));
                int on = server.computeOnServer(s -> count(s.overworld(), 120, 120, true));
                require(on > 0, "startup-disabled feature generates after GUI enable without restart");
                require(server.computeOnServer(s -> count(s.overworld(), 100, 100, false)) == off, "old terrain never refilled");
                open(context);
                screenshot(context, "config-terrain");
                context.runOnClient(client -> {
                    var screen = (YACLScreen) client.gui.screen();
                    set(screen, "enabled", false); screen.finishOrSave();
                });
                context.waitFor(client -> client.gui.screen() instanceof AlertScreen, 400);
                context.runOnClient(client -> client.gui.setScreen(null));
                require(server.computeOnServer(s -> count(s.overworld(), 140, 140, true)) == 0, "hot disable stops future decoration");
                require(server.computeOnServer(s -> count(s.overworld(), 120, 120, false)) == on, "disable never deletes existing patches");
                write("hot-generation.txt", "startup OFF=0; GUI ON=" + on + "; GUI OFF=0; old chunks unchanged\n");
            }
            Properties properties = new Properties(); properties.setProperty("server-ip", "127.0.0.1");
            try (var server = context.worldBuilder().createServer(properties); var connection = server.connect()) {
                server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p -> s.getPlayerList().deop(p.nameAndId())));
                open(context);
                context.runOnClient(client -> require(options((YACLScreen) client.gui.screen()).stream().noneMatch(Option::available), "ordinary player sees read-only settings"));
                screenshot(context, "config-readonly");
                context.runOnClient(client -> client.gui.setScreen(null));
                server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p -> s.getPlayerList().op(p.nameAndId())));
                open(context);
                context.runOnClient(client -> { var screen = (YACLScreen) client.gui.screen(); set(screen, "leafPatches.enabled", true); set(screen, "leafPatches.coverage", .02); screen.finishOrSave(); });
                context.waitFor(client -> client.gui.screen() instanceof AlertScreen, 400);
                require(server.computeOnServer(s -> GroundscapeWorldgenConfig.current().leafPatches().enabled() && GroundscapeWorldgenConfig.current().leafPatches().coverage() == .02), "dedicated server saved packet edit");
                context.runOnClient(client -> client.gui.setScreen(null));
                open(context);
                server.runOnServer(s -> apply(GroundscapeWorldgenConfig.defaults().json()));
                context.runOnClient(client -> { var screen = (YACLScreen) client.gui.screen(); set(screen, "density", .3); screen.finishOrSave(); });
                context.waitFor(client -> client.gui.screen() instanceof AlertScreen, 400);
                require(server.computeOnServer(s -> GroundscapeWorldgenConfig.current().density() == .12), "stale GUI write rejected");
                context.runOnClient(client -> client.gui.setScreen(null));
                open(context);
                server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p -> s.getPlayerList().deop(p.nameAndId())));
                context.runOnClient(client -> { var screen = (YACLScreen) client.gui.screen(); set(screen, "density", .3); screen.finishOrSave(); });
                context.waitFor(client -> client.gui.screen() instanceof AlertScreen, 400);
                require(server.computeOnServer(s -> GroundscapeWorldgenConfig.current().density() == .12), "permission revocation checked at save time");
                context.runOnClient(client -> client.gui.setScreen(null));
                server.runCommand("groundscape reload");
                require(server.computeOnServer(s -> GroundscapeWorldgenConfig.current().density() == .12), "command reload retains persisted config");
            }
            write("config-client-results.txt", "PASS: YACL 38 settings; cancel; local/host/server sources; host off/on/off generation; remote save; read-only; stale revision; revoked permission; reload.\n");
        } finally {
            context.runOnClient(client -> client.gui.setScreen(null));
            apply(original.json());
        }
    }
    private static void open(ClientGameTestContext context) {
        context.runOnClient(client -> { client.gui.setScreen(null); client.getConnection().sendCommand("groundscape-config"); });
        context.waitFor(client -> client.gui.screen() instanceof YACLScreen, 400);
    }
    private static List<Option<?>> options(YACLScreen screen) {
        List<Option<?>> result = new ArrayList<>();
        for (var category : screen.config.categories()) for (var group : category.groups()) for (var option : group.options())
            if (!(option instanceof LabelOption)) result.add(option);
        return result;
    }
    @SuppressWarnings("unchecked") private static <T> void set(YACLScreen screen, String key, T value) {
        var name = Component.translatable("groundscape.config." + key).getString();
        Option<?> option = options(screen).stream().filter(o -> o.name().getString().equals(name)).findFirst().orElseThrow();
        ((Option<T>) option).requestSet(value);
    }
    private static void apply(String json) {
        try { GroundscapeConfigManager.apply(GroundscapeConfigManager.snapshot().revision(), json); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private static int count(ServerLevel level, int x, int z, boolean load) {
        if (load) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) level.getChunk(x + dx, z + dz);
        int count = 0;
        var pos = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            var chunk = level.getChunk(x + dx, z + dz);
            for (int bx = 0; bx < 16; bx++) for (int bz = 0; bz < 16; bz++) {
                int wx = ((x + dx) << 4) + bx, wz = ((z + dz) << 4) + bz;
                int top = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, bx, bz) + 1;
                for (int y = level.getMinY(); y <= top; y++) if (chunk.getBlockState(pos.set(wx, y, wz)).getBlock() instanceof SodPatchBlock) count++;
            }
        }
        return count;
    }
    private static Path evidence() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("build.gradle"))) p = p.getParent();
        if (p == null) throw new IllegalStateException("Missing project");
        Path result = p.getParent().resolve("docs/evidence/config-patch-rules");
        try { Files.createDirectories(result); } catch (IOException e) { throw new UncheckedIOException(e); }
        return result;
    }
    private static void screenshot(ClientGameTestContext context, String name) {
        context.waitTicks(5);
        context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1280, 720).withDestinationDir(evidence()).disableCounterPrefix());
    }
    private static void write(String name, String contents) {
        try { Files.writeString(evidence().resolve(name), contents); } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
