package ru.chunklimiterhud.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

record ChunkSnapshot(
        ChunkPos chunk,
        int minChunkX,
        int minChunkZ,
        int areaSize,
        int loadedChunks,
        Map<String, Integer> entities,
        Map<String, Integer> blocks,
        Map<String, Integer> groups,
        Map<String, List<BlockPos>> blockPositions
) {
    static ChunkSnapshot empty() {
        return new ChunkSnapshot(new ChunkPos(0, 0), 0, 0, 1, 0, Map.of(), Map.of(), Map.of(), Map.of());
    }

    static ChunkSnapshot scan(Minecraft client, HudConfig config) {
        ClientLevel level = client.level;
        if (level == null || client.player == null) return empty();

        ChunkPos chunk = client.player.chunkPosition();
        int areaSize = config.chunkAreaSize;
        int minChunkX = chunk.x() - areaSize / 2;
        int minChunkZ = chunk.z() - areaSize / 2;
        int maxChunkX = minChunkX + areaSize;
        int maxChunkZ = minChunkZ + areaSize;
        Map<String, Integer> entityCounts = zeroed(config.entities);
        Map<String, Integer> blockCounts = zeroed(config.blocks);
        Map<String, Integer> groupCounts = zeroedGroups(config.groups);
        Map<String, List<BlockPos>> positions = new LinkedHashMap<>();
        List<LevelChunk> loadedChunks = new ArrayList<>();

        for (int chunkZ = minChunkZ; chunkZ < maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX < maxChunkX; chunkX++) {
                if (!level.hasChunk(chunkX, chunkZ)) continue;
                LevelChunk loaded = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (loaded != null) loadedChunks.add(loaded);
            }
        }

        Map<String, List<String>> entityToGroups = memberIndex(config.groups, false);
        for (Entity entity : level.entitiesForRendering()) {
            ChunkPos entityChunk = entity.chunkPosition();
            if (entityChunk.x() < minChunkX || entityChunk.x() >= maxChunkX
                    || entityChunk.z() < minChunkZ || entityChunk.z() >= maxChunkZ) continue;
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if (entityCounts.containsKey(id)) entityCounts.merge(id, 1, Integer::sum);
            incrementBuiltInEntityGroups(entityCounts, entity);
            for (String group : entityToGroups.getOrDefault(id, List.of())) {
                groupCounts.merge(group, 1, Integer::sum);
            }
        }

        Map<Block, String> exactTargets = new HashMap<>();
        for (String idText : config.blocks.keySet()) {
            Identifier id = Identifier.tryParse(idText);
            if (id != null && !"group".equals(id.getNamespace())) {
                BuiltInRegistries.BLOCK.getOptional(id).ifPresent(block -> exactTargets.put(block, idText));
            }
        }

        Map<Block, List<String>> blockToGroups = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : memberIndex(config.groups, true).entrySet()) {
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id != null) BuiltInRegistries.BLOCK.getOptional(id).ifPresent(block -> blockToGroups.put(block, entry.getValue()));
        }

        boolean scanBlocks = !exactTargets.isEmpty() || !blockToGroups.isEmpty() || blockCounts.containsKey("group:pistons");
        if (scanBlocks) {
            for (LevelChunk loaded : loadedChunks) {
                countBlocks(loaded, exactTargets, blockToGroups, blockCounts, groupCounts);
            }
            if (config.highlightOverloadedBlocks) {
                collectOverloadedBlockPositions(loadedChunks, exactTargets, blockToGroups, blockCounts, groupCounts, config, positions);
            }
        }

        return new ChunkSnapshot(
                chunk,
                minChunkX,
                minChunkZ,
                areaSize,
                loadedChunks.size(),
                entityCounts,
                blockCounts,
                groupCounts,
                positions
        );
    }

    boolean contains(ChunkPos other) {
        return other.x() >= minChunkX && other.x() < minChunkX + areaSize
                && other.z() >= minChunkZ && other.z() < minChunkZ + areaSize;
    }

    private static void countBlocks(
            LevelChunk chunk,
            Map<Block, String> exactTargets,
            Map<Block, List<String>> blockToGroups,
            Map<String, Integer> blockCounts,
            Map<String, Integer> groupCounts
    ) {
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.hasOnlyAir()) continue;
            section.getStates().count((state, count) -> {
                Block block = state.getBlock();
                String exactId = exactTargets.get(block);
                if (exactId != null) blockCounts.merge(exactId, count, Integer::sum);
                if (blockCounts.containsKey("group:pistons") && (block == Blocks.PISTON || block == Blocks.STICKY_PISTON)) {
                    blockCounts.merge("group:pistons", count, Integer::sum);
                }
                for (String group : blockToGroups.getOrDefault(block, List.of())) {
                    groupCounts.merge(group, count, Integer::sum);
                }
            });
        }
    }

    private static void collectOverloadedBlockPositions(
            List<LevelChunk> chunks,
            Map<Block, String> exactTargets,
            Map<Block, List<String>> blockToGroups,
            Map<String, Integer> blockCounts,
            Map<String, Integer> groupCounts,
            HudConfig config,
            Map<String, List<BlockPos>> positions
    ) {
        Set<String> overloadedExact = new HashSet<>();
        config.blocks.forEach((id, limit) -> {
            if (blockCounts.getOrDefault(id, 0) > limit) overloadedExact.add(id);
        });
        Set<String> overloadedGroups = new HashSet<>();
        config.groups.forEach((name, group) -> {
            if (group.isBlock() && groupCounts.getOrDefault(name, 0) > group.limit) overloadedGroups.add(name);
        });
        boolean pistonsOverloaded = overloadedExact.contains("group:pistons");

        Set<Block> targetBlocks = new HashSet<>();
        exactTargets.forEach((block, id) -> {
            if (overloadedExact.contains(id)) targetBlocks.add(block);
        });
        blockToGroups.forEach((block, groups) -> {
            if (groups.stream().anyMatch(overloadedGroups::contains)) targetBlocks.add(block);
        });
        if (pistonsOverloaded) {
            targetBlocks.add(Blocks.PISTON);
            targetBlocks.add(Blocks.STICKY_PISTON);
        }
        if (targetBlocks.isEmpty()) return;

        for (LevelChunk chunk : chunks) {
            chunk.findBlocks(
                    state -> targetBlocks.contains(state.getBlock()),
                    (pos, state) -> rememberOverloadedPosition(
                            state,
                            pos,
                            exactTargets,
                            blockToGroups,
                            overloadedExact,
                            overloadedGroups,
                            pistonsOverloaded,
                            positions,
                            config.maxHighlightedBlocks
                    )
            );
        }
    }

    private static void rememberOverloadedPosition(
            BlockState state,
            BlockPos pos,
            Map<Block, String> exactTargets,
            Map<Block, List<String>> blockToGroups,
            Set<String> overloadedExact,
            Set<String> overloadedGroups,
            boolean pistonsOverloaded,
            Map<String, List<BlockPos>> positions,
            int maximum
    ) {
        Block block = state.getBlock();
        String exactId = exactTargets.get(block);
        if (exactId != null && overloadedExact.contains(exactId)) remember(positions, exactId, pos, maximum);
        if (pistonsOverloaded && (block == Blocks.PISTON || block == Blocks.STICKY_PISTON)) {
            remember(positions, "group:pistons", pos, maximum);
        }
        for (String group : blockToGroups.getOrDefault(block, List.of())) {
            if (overloadedGroups.contains(group)) remember(positions, "custom:" + group, pos, maximum);
        }
    }

    private static void incrementBuiltInEntityGroups(Map<String, Integer> counts, Entity entity) {
        increment(counts, "group:entities", true);
        increment(counts, "group:mobs", entity instanceof Mob);
        increment(counts, "group:minecarts", entity instanceof AbstractMinecart);
        increment(counts, "group:items", entity instanceof ItemEntity);
    }

    private static Map<String, List<String>> memberIndex(Map<String, HudConfig.Group> groups, boolean blocks) {
        Map<String, List<String>> result = new HashMap<>();
        groups.forEach((name, group) -> {
            if (group.isBlock() != blocks) return;
            for (String member : group.members) {
                result.computeIfAbsent(member, ignored -> new ArrayList<>()).add(name);
            }
        });
        return result;
    }

    private static Map<String, Integer> zeroed(Map<String, Integer> limits) {
        Map<String, Integer> result = new LinkedHashMap<>();
        limits.keySet().forEach(id -> result.put(id, 0));
        return result;
    }

    private static Map<String, Integer> zeroedGroups(Map<String, HudConfig.Group> groups) {
        Map<String, Integer> result = new LinkedHashMap<>();
        groups.keySet().forEach(name -> result.put(name, 0));
        return result;
    }

    private static void increment(Map<String, Integer> counts, String id, boolean matches) {
        if (matches && counts.containsKey(id)) counts.merge(id, 1, Integer::sum);
    }

    private static void remember(Map<String, List<BlockPos>> positions, String key, BlockPos pos, int maximum) {
        List<BlockPos> list = positions.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (list.size() < maximum) list.add(pos.immutable());
    }
}
