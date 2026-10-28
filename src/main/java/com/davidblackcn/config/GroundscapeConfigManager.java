package com.davidblackcn.config;

import com.davidblackcn.worldgen.GroundscapeWorldgenConfig;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.*;

/** One configuration per game/server instance, published only after successful atomic file replacement. */
public final class GroundscapeConfigManager {
    public record Snapshot(long revision, GroundscapeWorldgenConfig config) {}
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("buildup_groundscape-worldgen.json");
    private static volatile Snapshot current = new Snapshot(0, GroundscapeWorldgenConfig.load(PATH));
    private GroundscapeConfigManager() {}
    public static Snapshot snapshot() { return current; }
    public static synchronized Snapshot apply(long expected, String json) throws IOException {
        if (expected != current.revision()) throw new IllegalArgumentException("Configuration changed; reopen the screen before saving");
        GroundscapeWorldgenConfig next = GroundscapeWorldgenConfig.parse(json);
        save(PATH, next);
        current = new Snapshot(current.revision() + 1, next);
        return current;
    }
    public static synchronized Snapshot reload() throws IOException {
        GroundscapeWorldgenConfig next = GroundscapeWorldgenConfig.parse(Files.readString(PATH));
        current = new Snapshot(current.revision() + 1, next);
        return current;
    }
    public static void save(Path path, GroundscapeWorldgenConfig config) throws IOException {
        Path absolute = path.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), "groundscape-", ".tmp");
        try {
            Files.writeString(temporary, config.json());
            // Fail safely on filesystems that cannot atomically replace; do not truncate the old config.
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
