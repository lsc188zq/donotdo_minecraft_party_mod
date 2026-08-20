package com.partygame.arena;

import com.partygame.config.ModConfig;
import com.partygame.game.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

import java.util.List;

// 以 center 为中心生成固定尺寸的竞技场：石砖地板、围墙、按钮、压力板、装备箱。
// 覆盖原有方块且不恢复，调用前需确认场地空旷
public class ArenaGenerator {

    public static void generate(ServerLevel level, BlockPos center, ModConfig config, RandomSource random) {
        int half = config.arenaHalfSize();
        int wallHeight = config.arenaWallHeight();
        int floorY = center.getY() - 1;
        int minX = center.getX() - half, maxX = center.getX() + half;
        int minZ = center.getZ() - half, maxZ = center.getZ() + half;

        BlockState floor = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();

        // 地板与围墙
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlockAndUpdate(new BlockPos(x, floorY, z), floor);
                boolean isBorder = x == minX || x == maxX || z == minZ || z == maxZ;
                if (isBorder) {
                    for (int y = 1; y <= wallHeight; y++) {
                        level.setBlockAndUpdate(new BlockPos(x, floorY + y, z), wall);
                    }
                }
            }
        }

        // 北墙内侧按钮（贴在南边的墙块上）
        BlockState button = Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACING, Direction.SOUTH)
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL);
        level.setBlockAndUpdate(new BlockPos(center.getX(), floorY + 1, minZ + 1), button);

        // 出生点标记：地板环形均匀放 8 块红色羊毛，供扫描后随机分配出生点
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            int sx = center.getX() + (int) Math.round((half - 3) * Math.cos(angle));
            int sz = center.getZ() + (int) Math.round((half - 3) * Math.sin(angle));
            level.setBlockAndUpdate(new BlockPos(sx, floorY + 1, sz), Blocks.RED_WOOL.defaultBlockState());
        }

        // 内墙火把照明：四面墙每隔 4 格挂一支（朝场内），避免夜晚场地全黑
        BlockState torch = Blocks.WALL_TORCH.defaultBlockState();
        for (int i = minX; i <= maxX; i += 4) {
            level.setBlockAndUpdate(new BlockPos(i, floorY + 2, minZ),
                    torch.setValue(WallTorchBlock.FACING, Direction.SOUTH));
            level.setBlockAndUpdate(new BlockPos(i, floorY + 2, maxZ),
                    torch.setValue(WallTorchBlock.FACING, Direction.NORTH));
        }
        for (int i = minZ + 1; i < maxZ; i += 4) {
            level.setBlockAndUpdate(new BlockPos(minX, floorY + 2, i),
                    torch.setValue(WallTorchBlock.FACING, Direction.EAST));
            level.setBlockAndUpdate(new BlockPos(maxX, floorY + 2, i),
                    torch.setValue(WallTorchBlock.FACING, Direction.WEST));
        }

        // 东南角地面压力板
        BlockPos plate = new BlockPos(center.getX() + half - 2, floorY + 1, center.getZ() + half - 2);
        level.setBlockAndUpdate(plate, Blocks.STONE_PRESSURE_PLATE.defaultBlockState());
        // 保存压力板位置供 GameManager 判定 STAND_PLATE_3S
        GameManager.get().setPlatePos(plate);

        // 装备箱：分布在场内四分之一处
        int offset = half / 2;
        BlockPos[] chestSpots = {
                new BlockPos(center.getX() + offset, floorY + 1, center.getZ() + offset),
                new BlockPos(center.getX() + offset, floorY + 1, center.getZ() - offset),
                new BlockPos(center.getX() - offset, floorY + 1, center.getZ() + offset),
                new BlockPos(center.getX() - offset, floorY + 1, center.getZ() - offset)
        };
        for (int i = 0; i < Math.min(config.chestCount(), chestSpots.length); i++) {
            placeLootChest(level, chestSpots[i], config, random);
        }
    }

    // 每箱：1 件武器 + 2 件护甲 + 4 个食物，从配置物品池随机抽取
    private static void placeLootChest(ServerLevel level, BlockPos pos, ModConfig config, RandomSource random) {
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(randomItem(level, config.loot("weapons"), random)));
            chest.setItem(1, new ItemStack(randomItem(level, config.loot("armor"), random)));
            chest.setItem(2, new ItemStack(randomItem(level, config.loot("armor"), random)));
            for (int i = 0; i < 4; i++) {
                chest.setItem(3 + i, new ItemStack(randomItem(level, config.loot("food"), random)));
            }
        }
    }

    // 按物品 id 查注册表；配置写错时退回石头，避免崩服
    private static Item randomItem(ServerLevel level, List<String> ids, RandomSource random) {
        String id = ids.get(random.nextInt(ids.size()));
        return BuiltInRegistries.ITEM
                .getOptional(Identifier.parse(id))
                .orElse(Items.STONE);
    }
}
