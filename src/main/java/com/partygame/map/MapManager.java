package com.partygame.map;

import com.partygame.arena.ArenaGenerator;
import com.partygame.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 地图落地：程序生成房走生成器；自建地图保存/粘贴结构模板；统一扫描红色羊毛出生点
public class MapManager {
    private static final String TEMPLATE_DIR = "config/partygame/maps";

    // 把当前地图落地到以 center 为中心的位置（每回合调用，覆盖区域不恢复）
    public static void land(ServerLevel level, BlockPos center, ModConfig config, MapData map) {
        // 先清空区域内所有旧方块与掉落物，避免上一张地图/地形的残留与新地图混杂；
        // 新地图内容由随后的生成/粘贴重建（模板粘贴会连同空气一起覆盖）
        int scanRadius = map.type() == MapData.MapType.PROCEDURAL ? config.arenaHalfSize() : map.radius();
        clearRegion(level, center, scanRadius);
        clearDroppedItems(level, center, scanRadius);
        if (map.type() == MapData.MapType.PROCEDURAL) {
            ArenaGenerator.generate(level, center, config, level.getRandom());
        } else {
            pasteTemplate(level, center, map);
        }
    }

    // 把以 center 为中心 ±radius 立方范围内所有非空气方块替换为空气
    private static void clearRegion(ServerLevel level, BlockPos center, int radius) {
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    // 清除区域内的掉落物（上局死亡/丢弃的物品不残留到新地图）
    private static void clearDroppedItems(ServerLevel level, BlockPos center, int radius) {
        AABB area = new AABB(
                center.getX() - radius, center.getY() - radius, center.getZ() - radius,
                center.getX() + radius + 1, center.getY() + radius + 1, center.getZ() + radius + 1);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            item.discard();
        }
    }

    // 以玩家站位为中心保存 ±radius 柱形范围（含方块实体，如箱子内容）为模板
    public static void saveTemplate(ServerLevel level, BlockPos center, int radius, String name) throws IOException {
        StructureTemplate template = new StructureTemplate();
        BlockPos from = center.offset(-radius, -radius, -radius);
        Vec3i size = new Vec3i(radius * 2 + 1, radius * 2 + 1, radius * 2 + 1);
        // 忽略方块传结构空位：地图里即使有也原样保存（1.21.1 为单方块参数）
        template.fillFromWorld(level, from, size, false, Blocks.STRUCTURE_VOID);
        Path dir = Paths.get(TEMPLATE_DIR);
        Files.createDirectories(dir);
        NbtIo.writeCompressed(template.save(new CompoundTag()), dir.resolve(name + ".nbt"));
    }

    // 粘贴模板：以 center - radius 为左上角对齐，实现"程序把地图搬进主世界"
    public static void pasteTemplate(ServerLevel level, BlockPos center, MapData map) {
        try {
            // 内置地图从 jar 资源读取（随 mod 打包），自建地图从 config 目录读取
            CompoundTag tag = map.builtin()
                    ? readBuiltinTemplate(map.template())
                    : readSavedTemplate(map.template());
            StructureTemplate template = new StructureTemplate();
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
            BlockPos anchor = center.offset(-map.radius(), -map.radius(), -map.radius());
            template.placeInWorld(level, anchor, anchor, new StructurePlaceSettings(), level.getRandom(), 2);
        } catch (IOException e) {
            throw new IllegalStateException("地图模板读取失败：" + map.template(), e);
        }
    }

    // 读取自建地图模板（config/partygame/maps/ 目录）
    private static CompoundTag readSavedTemplate(String name) throws IOException {
        Path file = Paths.get(TEMPLATE_DIR, name);
        if (!Files.exists(file)) {
            throw new IllegalStateException("地图模板不存在：" + name);
        }
        return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
    }

    // 从 jar 资源读取内置地图模板
    private static CompoundTag readBuiltinTemplate(String name) throws IOException {
        try (InputStream in = MapManager.class.getResourceAsStream("/partygame/maps/" + name)) {
            if (in == null) {
                throw new IllegalStateException("内置地图资源不存在：" + name);
            }
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }
    }

    // ---- 建图辅助 ----

    // 预览标记位置记录（内存）：服务器重启后丢失，残留标记只能手动拆除
    private static final Set<BlockPos> PREVIEW_MARKERS = new HashSet<>();

    // 放置保存范围预览标记：X/Z 平面 4 个角在 ±(radius+1)（模板范围外一圈，不会混进保存内容），中心 1 块荧石。
    // 高度方向不标记：保存范围为 ±radius 的立方，高度边界见命令反馈说明
    public static void placePreviewMarkers(ServerLevel level, BlockPos center, int radius) {
        clearPreviewMarkers(level);
        int r = radius + 1;
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                BlockPos corner = new BlockPos(center.getX() + dx * r, center.getY(), center.getZ() + dz * r);
                level.setBlockAndUpdate(corner, Blocks.GLASS.defaultBlockState());
                PREVIEW_MARKERS.add(corner);
            }
        }
        level.setBlockAndUpdate(center, Blocks.GLOWSTONE.defaultBlockState());
        PREVIEW_MARKERS.add(center);
    }

    // 清除预览标记：仅当该位置当前仍是标记方块时才删除，不误删玩家后来放置的方块
    public static void clearPreviewMarkers(ServerLevel level) {
        for (BlockPos p : PREVIEW_MARKERS) {
            BlockState state = level.getBlockState(p);
            if (state.is(Blocks.GLASS) || state.is(Blocks.GLOWSTONE)) {
                level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }
        }
        PREVIEW_MARKERS.clear();
    }

    // 建图辅助：清空区域与掉落物，在脚下 Y-1 层铺满石砖地板作为搭图基座
    public static void flatten(ServerLevel level, BlockPos center, int radius) {
        clearRegion(level, center, radius);
        clearDroppedItems(level, center, radius);
        BlockState floor = Blocks.STONE_BRICKS.defaultBlockState();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                level.setBlockAndUpdate(new BlockPos(x, center.getY() - 1, z), floor);
            }
        }
    }

    // 扫描以 center 为中心 ±radius 柱形范围内的红色羊毛（出生点）
    public static List<BlockPos> scanSpawns(ServerLevel level, BlockPos center, int radius) {
        List<BlockPos> spawns = new ArrayList<>();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.getBlockState(p).is(Blocks.RED_WOOL)) {
                        spawns.add(p);
                    }
                }
            }
        }
        return spawns;
    }
}
