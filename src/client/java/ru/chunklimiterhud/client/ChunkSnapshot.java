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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record ChunkSnapshot(
        ChunkPos chunk,
        Map<String, Integer> entities,
        Map<String, Integer> blocks,
        Map<String, Integer> groups,
        Map<String, List<BlockPos>> blockPositions
) {
    static ChunkSnapshot empty() {
        return new ChunkSnapshot(new ChunkPos(0, 0), Map.of(), Map.of(), Map.of(), Map.of());
    }

    static ChunkSnapshot scan(Minecraft client, HudConfig config) {
        ClientLevel level = client.level;
        if (level == null || client.player == null) return empty();

        ChunkPos chunk = client.player.chunkPosition();
        Map<String, Integer> entityCounts = zeroed(config.entities);
        Map<String, Integer> blockCounts = zeroed(config.blocks);
        Map<String, Integer> groupCounts = zeroedGroups(config.groups);
        Map<String, List<BlockPos>> positions = new LinkedHashMap<>();

        Map<String, List<String>> entityToGroups = memberIndex(config.groups, false);
        for (Entity entity : level.entitiesForRendering()) {
            if (!entity.chunkPosition().equals(chunk)) continue;
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
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int minX = chunk.getMinBlockX();
            int minZ = chunk.getMinBlockZ();
            for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                for (int z = minZ; z < minZ + 16; z++) {
                    for (int x = minX; x < minX + 16; x++) {
                        Block block = level.getBlockState(pos.set(x, y, z)).getBlock();
                        String exactId = exactTargets.get(block);
                        if (exactId != null) {
                            blockCounts.merge(exactId, 1, Integer::sum);
                            remember(positions, exactId, pos, config.maxHighlightedBlocks);
                        }

                        if (blockCounts.containsKey("group:pistons") && (block == Blocks.PISTON || block == Blocks.STICKY_PISTON)) {
                            blockCounts.merge("group:pistons", 1, Integer::sum);
                            remember(positions, "group:pistons", pos, config.maxHighlightedBlocks);
                        }

                        for (String group : blockToGroups.getOrDefault(block, List.of())) {
                            groupCounts.merge(group, 1, Integer::sum);
                            remember(positions, "custom:" + group, pos, config.maxHighlightedBlocks);
                        }
                    }
                }
            }
        }

        return new ChunkSnapshot(chunk, entityCounts, blockCounts, groupCounts, positions);
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
