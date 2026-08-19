package com.partygame.command;

import com.mojang.brigadier.CommandDispatcher;

import java.util.Comparator;
import java.util.List;

import com.partygame.game.GameManager;
import com.partygame.game.PlayerState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// /party 系列命令：setarena（站中心点）/ start / stop / score。
// 21.11.11 用新权限系统：管理员级 = Commands.LEVEL_GAMEMASTERS（旧 op 等级 2）
public class PartyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("party")
                .then(Commands.literal("setarena")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> setArena(ctx.getSource())))
                .then(Commands.literal("start")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> start(ctx.getSource())))
                .then(Commands.literal("stop")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("score")
                        .executes(ctx -> score(ctx.getSource()))));
    }

    private static int setArena(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer p)) {
            source.sendFailure(Component.literal("该命令需由玩家站在竞技场中心点执行"));
            return 0;
        }
        GameManager.get().setArenaCenter(p.blockPosition());
        source.sendSuccess(() -> Component.literal(
                "竞技场中心已设为 " + p.blockPosition().toShortString() + "（" +
                "下次 /party start 时以该点为中心生成场地，会覆盖原有方块）"), true);
        return 1;
    }

    private static int start(CommandSourceStack source) {
        GameManager gm = GameManager.get();
        if (!gm.isArenaSet()) {
            source.sendFailure(Component.literal("请先执行 /party setarena 设置竞技场中心"));
            return 0;
        }
        try {
            gm.startGame(source.getLevel());
            return 1;
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int stop(CommandSourceStack source) {
        GameManager.get().stopGame();
        source.sendSuccess(() -> Component.literal("游戏已终止"), true);
        return 1;
    }

    private static int score(CommandSourceStack source) {
        List<PlayerState> sorted = GameManager.get().allStates().stream()
                .sorted(Comparator.comparingInt(PlayerState::score).reversed())
                .toList();
        source.sendSuccess(() -> Component.literal("§6===== 分数榜 ====="), false);
        for (PlayerState s : sorted) {
            String name = GameManager.get().playerNameOf(s);
            source.sendSuccess(() -> Component.literal("§e" + name + " §7- " + s.score() + " 分"), false);
        }
        return 1;
    }
}
