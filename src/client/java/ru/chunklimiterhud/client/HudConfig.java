package ru.chunklimiterhud.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class HudConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("chunk_limiter_hud.json");

    boolean enabled = true;
    boolean hudEnabled = true;
    int updateIntervalTicks = 20;
    int x = 8;
    int y = 8;
    boolean showOnlyNonZero = false;
    boolean highlightOverloadedBlocks = true;
    boolean highlightOverloadedEntities = true;
    int maxHighlightedBlocks = 128;
    int maxHighlightedEntities = 64;
    Map<String, Integer> entities = new LinkedHashMap<>();
    Map<String, Integer> blocks = new LinkedHashMap<>();
    Map<String, Group> groups = new LinkedHashMap<>();

    static HudConfig load() {
        if (Files.isRegularFile(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                HudConfig config = GSON.fromJson(reader, HudConfig.class);
                if (config != null) {
                    config.sanitize();
                    config.save();
                    return config;
                }
            } catch (Exception exception) {
                ChunkLimiterHudClient.LOGGER.error("Could not read {}", PATH, exception);
            }
        }

        HudConfig config = defaults();
        config.save();
        return config;
    }

    static HudConfig defaults() {
        HudConfig config = new HudConfig();
        config.entities.put("minecraft:villager", 30);
        config.entities.put("group:minecarts", 8);
        config.blocks.put("minecraft:hopper", 20);
        config.blocks.put("group:pistons", 64);
        config.blocks.put("minecraft:observer", 64);
        config.groups.put("хранилища", new Group("block", 64, List.of(
                "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:shulker_box"
        )));
        return config;
    }

    void save() {
        sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            ChunkLimiterHudClient.LOGGER.error("Could not write {}", PATH, exception);
        }
    }

    private void sanitize() {
        updateIntervalTicks = Math.max(1, updateIntervalTicks);
        maxHighlightedBlocks = Math.max(1, Math.min(1024, maxHighlightedBlocks));
        maxHighlightedEntities = Math.max(1, Math.min(512, maxHighlightedEntities));
        x = Math.max(0, x);
        y = Math.max(0, y);
        if (entities == null) entities = new LinkedHashMap<>();
        if (blocks == null) blocks = new LinkedHashMap<>();
        if (groups == null) groups = new LinkedHashMap<>();
        entities.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null || entry.getValue() < 1);
        blocks.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null || entry.getValue() < 1);
        groups.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null);
        groups.values().forEach(Group::sanitize);
    }

    static final class Group {
        String type;
        int limit;
        List<String> members;

        Group(String type, int limit, List<String> members) {
            this.type = type;
            this.limit = limit;
            this.members = new ArrayList<>(members);
            sanitize();
        }

        boolean isBlock() {
            return "block".equals(type);
        }

        boolean isEntity() {
            return "entity".equals(type);
        }

        private void sanitize() {
            if (!isBlock() && !isEntity()) type = "block";
            limit = Math.max(1, limit);
            if (members == null) members = new ArrayList<>();
            else members = new ArrayList<>(members);
            members.removeIf(id -> id == null || IdentifierHelper.invalid(id));
        }
    }

    private static final class IdentifierHelper {
        private static boolean invalid(String id) {
            return net.minecraft.resources.Identifier.tryParse(id) == null;
        }
    }
}
