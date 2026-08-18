package com.partygame.game;

import com.partygame.arena.ArenaGenerator;
import com.partygame.config.ModConfig;
import com.partygame.task.Assignment;
import com.partygame.task.TaskAssigner;
import com.partygame.task.TaskDefinition;
import com.partygame.task.TaskPool;
import com.partygame.task.TriggerType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

// 游戏核心状态机：唯一事实来源。事件监听器只把信号转发到这里。
public class GameManager {
    private static final GameManager INSTANCE = new GameManager();
    public static GameManager get() { return INSTANCE; }

    private ModConfig config;
    private GamePhase phase = GamePhase.IDLE;
    private int countdownTicks;               // 当前阶段剩余 tick（每秒 20 tick）
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private BlockPos arenaCenter;
    private BlockPos platePos;
    private final Random random = new Random();
    private ServerLevel currentLevel;

    private GameManager() {}

    // 服务器启动时加载配置；事件监听由 PartyGame 构造器把单例注册到游戏总线
    public void init(ModConfig config) {
        this.config = config;
    }

    // ---------- 供命令调用 ----------

    public boolean isArenaSet() { return arenaCenter != null; }
    public void setArenaCenter(BlockPos center) { this.arenaCenter = center; }
    public BlockPos arenaCenter() { return arenaCenter; }
    public void setPlatePos(BlockPos pos) { this.platePos = pos; }
    public BlockPos platePos() { return platePos; }
    public GamePhase phase() { return phase; }

    public void startGame(ServerLevel level) {
        if (phase != GamePhase.IDLE) throw new IllegalStateException("游戏已在进行中");
        if (!isArenaSet()) throw new IllegalStateException("请先设置竞技场中心（/party setarena）");
        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        if (players.size() < 2) throw new IllegalStateException("至少需要 2 名玩家");
        currentLevel = level;
        beginRound();
    }

    public void stopGame() {
        phase = GamePhase.IDLE;
        states.clear();
        broadcast("游戏已终止");
    }

    // ---------- 回合流程 ----------

    private void beginRound() {
        // 每回合重建竞技场（覆盖区域不恢复），重新摆放装备箱
        ArenaGenerator.generate(currentLevel, arenaCenter, config, currentLevel.getRandom());
        List<ServerPlayer> players = currentLevel.getServer().getPlayerList().getPlayers();
        // 上一回合出局的旁观者重新进场参与，分数保留
        TaskPool pool = new TaskPool(config.taskPool());
        List<Assignment> assignments = TaskAssigner.assign(pool, players.size(), random);

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            PlayerState old = states.get(p.getUUID());
            PlayerState s;
            if (old == null) {
                s = new PlayerState(assignments.get(i), 0);
                states.put(p.getUUID(), s);
            } else {
                s = old;
                s.resetRound(assignments.get(i));
            }
            // 传送进场、清背包、回生存模式
            p.setGameMode(GameType.SURVIVAL);
            p.getInventory().clearContent();
            // 21.11.45 的签名带 setCamera 参数；空相对集 = 按绝对坐标传送
            p.teleportTo(currentLevel,
                    arenaCenter.getX() + 0.5, arenaCenter.getY() + 1.0, arenaCenter.getZ() + 0.5,
                    java.util.Set.<Relative>of(), p.getYRot(), p.getXRot(), true);
            p.sendSystemMessage(Component.literal("你的必做任务：§a" + s.mustDo().displayName()));
            // 屏幕大字提示（21.11.45 无 displayTitle 方法，用发包方式）
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("你的必做任务")));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§a" + s.mustDo().displayName())));
        }
        enterPhase(GamePhase.PREPARING);
        broadcast("§6准备期开始（30 秒）：抢夺装备，玩家间攻击禁用！");
    }

    private void enterPhase(GamePhase next) {
        phase = next;
        countdownTicks = switch (next) {
            case PREPARING -> config.preparingSeconds() * 20;
            case PLAYING -> config.playingSeconds() * 20;
            case SCORING -> config.scoringSeconds() * 20;
            default -> 0;
        };
    }

    // 每游戏刻回调：阶段倒计时与切换；由 NeoForge.EVENT_BUS 反射注册
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (phase == GamePhase.IDLE || phase == GamePhase.FINISHED) return;
        if (--countdownTicks > 0) return;

        switch (phase) {
            case PREPARING -> {
                enterPhase(GamePhase.PLAYING);
                broadcast("§a对局开始（" + config.playingSeconds() + " 秒）！完成必做任务，别触犯禁忌！");
            }
            case PLAYING -> endRound();
            case SCORING -> {
                if (anyScoreReached(config.targetScore())) {
                    finishGame();
                } else {
                    beginRound();
                }
            }
            default -> { }
        }
    }

    private void endRound() {
        // 超时未完成必做的出局
        for (PlayerState s : states.values()) {
            if (s.isAlive() && !s.isMustDoDone()) {
                s.eliminate();
                ServerPlayer p = playerOf(s);
                if (p != null) broadcast("§c" + p.getScoreboardName() + " 超时未完成必做任务，出局");
            }
        }
        // 幸存者加分
        for (PlayerState s : states.values()) {
            if (s.isAlive()) {
                s.addScore(1);
                ServerPlayer p = playerOf(s);
                if (p != null) broadcast("§a" + p.getScoreboardName() + " 存活 +1 分（当前 " + s.score() + " 分）");
            }
        }
        enterPhase(GamePhase.SCORING);
    }

    private boolean anyScoreReached(int target) {
        return states.values().stream().anyMatch(s -> s.score() >= target);
    }

    private void finishGame() {
        PlayerState winner = states.values().stream()
                .max((a, b) -> Integer.compare(a.score(), b.score())).orElse(null);
        if (winner != null) {
            ServerPlayer p = playerOf(winner);
            broadcast("§6" + (p != null ? p.getScoreboardName() : "?") + " 率先达到 "
                    + config.targetScore() + " 分，获得胜利！");
        }
        phase = GamePhase.FINISHED;
    }

    // ---------- 供事件监听器调用 ----------

    public void onTrigger(ServerPlayer player, TriggerType type) {
        if (phase != GamePhase.PLAYING) return;
        PlayerState s = states.get(player.getUUID());
        if (s == null || !s.isAlive()) return;

        // 必做任务判定
        if (!s.isMustDoDone() && s.mustDo().triggers().contains(type)) {
            s.completeMustDo();
            player.sendSystemMessage(Component.literal("§a必做任务已完成！"));
        }
        // 禁忌判定
        if (s.triggersMatch(type)) {
            TaskDefinition broken = s.activeForbidden();
            s.triggerForbidden(broken);
            if (s.remainingLives() == 0) {
                eliminate(player, "触犯了禁忌：" + broken.displayName());
            } else {
                player.sendSystemMessage(Component.literal(
                        "§c你触犯了禁忌：§l" + broken.displayName() + "§r§c！剩余一条命。"));
                broadcast(player.getScoreboardName() + " 触犯了一条禁忌");
            }
        }
    }

    public void onPlayerDeath(ServerPlayer player) {
        PlayerState s = states.get(player.getUUID());
        if (s == null || !s.isAlive()) return;
        eliminate(player, "死亡");
    }

    public void onPlayerLeave(ServerPlayer player) {
        PlayerState s = states.get(player.getUUID());
        if (s != null && s.isAlive()) {
            s.eliminate();
            broadcast(player.getScoreboardName() + " 掉线，视作出局");
        }
        if (phase != GamePhase.IDLE && states.values().stream().noneMatch(PlayerState::isAlive)) {
            stopGame(); // 全员掉线，强制回 IDLE
        }
    }

    private void eliminate(ServerPlayer player, String reason) {
        PlayerState s = states.get(player.getUUID());
        if (s == null) return;
        s.eliminate();
        broadcast("§c" + player.getScoreboardName() + " 出局了（" + reason + "）");
        player.setGameMode(GameType.SPECTATOR);
    }

    // ---------- 查询 ----------

    public PlayerState stateOf(ServerPlayer player) { return states.get(player.getUUID()); }
    public List<PlayerState> allStates() { return new ArrayList<>(states.values()); }

    public String playerNameOf(PlayerState s) {
        if (currentLevel == null) return "?";
        return currentLevel.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> states.get(p.getUUID()) == s)
                .findFirst().map(p -> p.getScoreboardName()).orElse("?");
    }

    private ServerPlayer playerOf(PlayerState s) {
        if (currentLevel == null) return null;
        return currentLevel.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> states.get(p.getUUID()) == s).findFirst().orElse(null);
    }

    private void broadcast(String message) {
        if (currentLevel != null) {
            currentLevel.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal(message), false);
        }
    }
}
