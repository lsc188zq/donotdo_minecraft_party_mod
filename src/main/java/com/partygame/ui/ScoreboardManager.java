package com.partygame.ui;

import com.partygame.game.GameManager;
import com.partygame.game.GamePhase;
import com.partygame.game.PlayerState;
import com.partygame.network.SyncStatesPayload;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;

// 分支 B：为每位玩家构建个性化视图并下发。
// 自己的行禁忌显示 ???，他人的行显示其当前激活的禁忌；按分数降序
public class ScoreboardManager {

    public static void refresh(MinecraftServer server) {
        GameManager gm = GameManager.get();
        List<PlayerState> states = gm.allStates().stream()
                .sorted(Comparator.comparingInt(PlayerState::score).reversed())
                .toList();

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            List<SyncStatesPayload.Row> rows = states.stream()
                    .map(s -> {
                        boolean self = gm.stateOf(viewer) == s;
                        String taboo;
                        if (gm.phase() == GamePhase.IDLE) {
                            taboo = "—";
                        } else if (!s.isAlive()) {
                            // 触犯两条禁忌的玩家 activeForbiddenIndex=2，不能再取当前禁忌
                            taboo = "已出局";
                        } else {
                            taboo = self ? "???" : s.activeForbidden().displayName();
                        }
                        return new SyncStatesPayload.Row(
                                gm.playerNameOf(s), taboo,
                                s.remainingLives(), s.score(), self);
                    })
                    .toList();
            viewer.connection.send(new ClientboundCustomPayloadPacket(new SyncStatesPayload(rows)));
        }
    }
}
