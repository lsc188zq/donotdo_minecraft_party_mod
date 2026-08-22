package com.partygame.game;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

// 玩家开局前状态快照：游戏结束后恢复位置/背包/模式/血量（仅内存，服务器重启即失效）
public record PlayerSnapshot(
        BlockPos pos, ResourceKey<Level> dimension,
        ListTag inventory, GameType gameType,
        float health, int foodLevel, float xRot, float yRot) {

    // 捕获玩家当前状态（1.21.1 背包序列化为 ListTag）
    public static PlayerSnapshot capture(ServerPlayer p) {
        return new PlayerSnapshot(
                p.blockPosition(), p.level().dimension(),
                p.getInventory().save(new ListTag()),
                p.gameMode.getGameModeForPlayer(),
                p.getHealth(), p.getFoodData().getFoodLevel(),
                p.getXRot(), p.getYRot());
    }

    // 恢复玩家状态：传回原维度（目标位置由调用方给定，可能是场外调整后的位置），还原背包/模式/血量
    public void restore(ServerPlayer p, ServerLevel level, BlockPos targetPos) {
        p.getInventory().load(inventory);
        p.setGameMode(gameType);
        p.setHealth(health);
        p.getFoodData().setFoodLevel(foodLevel);
        p.teleportTo(level, targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, yRot, xRot);
    }
}
