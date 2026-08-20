package com.partygame.game;

import com.partygame.arena.ArenaGenerator;
import com.partygame.config.ModConfig;
import com.partygame.map.MapData;
import com.partygame.map.MapManager;
import com.partygame.map.SpawnAssigner;
import com.partygame.task.Assignment;
import com.partygame.task.TaskAssigner;
import com.partygame.task.TaskDefinition;
import com.partygame.task.TaskPool;
import com.partygame.task.TriggerType;
import com.partygame.ui.ScoreboardManager;
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
    private int round;                        // 当前第几轮（beginRound 自增）
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

    // 配置对象：命令需要读取/修改配置（地图管理、config 命令）
    public ModConfig config() { return config; }

    // 当前选中的地图：procedural 为内置程序生成房，其余从配置查找
    public MapData selectedMapData() {
        String selected = config.selectedMap();
        if (selected.equals(MapData.PROCEDURAL.name())) {
            return MapData.PROCEDURAL;
        }
        return config.maps().stream()
                .filter(m -> m.name().equals(selected))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("选中的地图不存在：" + selected));
    }

    public boolean isArenaSet() { return arenaCenter != null; }
    public void setArenaCenter(BlockPos center) { this.arenaCenter = center; }
    public BlockPos arenaCenter() { return arenaCenter; }

    // 坐标是否落在已设置的竞技场区域内（供开局前 IDLE 期间的场地保护判断）
    public boolean isInsideArena(BlockPos pos) {
        if (arenaCenter == null) return false;
        MapData map = selectedMapData();
        int radius = map.type() == MapData.MapType.PROCEDURAL ? config.arenaHalfSize() : map.radius();
        return Math.abs(pos.getX() - arenaCenter.getX()) <= radius
                && Math.abs(pos.getY() - arenaCenter.getY()) <= radius
                && Math.abs(pos.getZ() - arenaCenter.getZ()) <= radius;
    }
    public void setPlatePos(BlockPos pos) { this.platePos = pos; }
    public BlockPos platePos() { return platePos; }
    public GamePhase phase() { return phase; }

    public void startGame(ServerLevel level) {
        if (phase != GamePhase.IDLE) throw new IllegalStateException("游戏已在进行中");
        if (!isArenaSet()) throw new IllegalStateException("请先设置竞技场中心（/party setarena）");
        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        if (players.size() < config.minPlayers()) {
            throw new IllegalStateException("至少需要 " + config.minPlayers() + " 名玩家");
        }
        currentLevel = level;
        beginRound();
    }

    public void stopGame() {
        phase = GamePhase.IDLE;
        states.clear();
        round = 0;
        broadcast("游戏已终止");
        // 手动终止后清掉各客户端残留的计分板内容
        if (currentLevel != null) {
            ScoreboardManager.refresh(currentLevel.getServer());
        }
    }

    // ---------- 回合流程 ----------

    private void beginRound() {
        round++;
        // 游戏时间设为正午（6000），避免对局入夜；每回合刷新一次
        currentLevel.setDayTime(6000);
        // 落地当前地图（程序生成房重建 / 自建地图重新粘贴，恢复被破坏的方块）
        MapData map = selectedMapData();
        MapManager.land(currentLevel, arenaCenter, config, map);
        int scanRadius = map.type() == MapData.MapType.PROCEDURAL ? config.arenaHalfSize() : map.radius();
        List<BlockPos> spawns = MapManager.scanSpawns(currentLevel, arenaCenter, scanRadius);

        List<ServerPlayer> players = currentLevel.getServer().getPlayerList().getPlayers();
        // 上一回合出局的旁观者重新进场参与，分数保留
        TaskPool pool = new TaskPool(config.taskPool());
        List<Assignment> assignments = TaskAssigner.assign(pool, players.size(), random);
        // 出生点每回合重新随机分配
        List<BlockPos> spawnPoints = SpawnAssigner.assign(spawns, players.size(), random);

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
            BlockPos spawn = spawnPoints.get(i);
            // 出生在随机分配的羊毛出生点上方 1 格
            // 21.11.45 的签名带 setCamera 参数；空相对集 = 按绝对坐标传送
            p.teleportTo(currentLevel,
                    spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5,
                    java.util.Set.<Relative>of(), p.getYRot(), p.getXRot(), true);
            p.sendSystemMessage(Component.literal("你的必做任务：§a" + s.mustDo().displayName()));
            // 屏幕大字提示（21.11.45 无 displayTitle 方法，用发包方式）
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("你的必做任务")));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§a" + s.mustDo().displayName())));
        }
        enterPhase(GamePhase.PREPARING);
        broadcast("§6第 " + round + " 轮准备期开始：抢夺装备，玩家间攻击禁用！");
    }

    private void enterPhase(GamePhase next) {
        phase = next;
        countdownTicks = switch (next) {
            case PREPARING -> config.preparingSeconds() * 20;
            case PLAYING -> config.playingSeconds() * 20;
            case SCORING -> config.scoringSeconds() * 20;
            default -> 0;
        };
        // 阶段切换后刷新各客户端的计分板视图
        if (currentLevel != null) {
            ScoreboardManager.refresh(currentLevel.getServer());
        }
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
        // 结算期开始前重置地图：修复对局中被破坏的方块
        MapManager.land(currentLevel, arenaCenter, config, selectedMapData());
        enterPhase(GamePhase.SCORING);
        // 分数变化后刷新计分板
        if (currentLevel != null) {
            ScoreboardManager.refresh(currentLevel.getServer());
        }
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
        // 结束回 IDLE 并清空状态：下次 /party start 是全新一局
        phase = GamePhase.IDLE;
        states.clear();
        round = 0;
        if (currentLevel != null) {
            ScoreboardManager.refresh(currentLevel.getServer());
        }
    }

    // ---------- 供事件监听器调用 ----------

    public void onTrigger(ServerPlayer player, TriggerType type) {
        if (phase != GamePhase.PLAYING) return;
        PlayerState s = states.get(player.getUUID());
        if (s == null || !s.isAlive()) return;

        boolean changed = false;
        // 必做任务判定
        if (!s.isMustDoDone() && s.mustDo().triggers().contains(type)) {
            s.completeMustDo();
            player.sendSystemMessage(Component.literal("§a必做任务已完成！"));
            broadcast("§a" + player.getScoreboardName() + " 完成了必做任务！");
            changed = true;
        }
        // 禁忌判定
        if (s.triggersMatch(type)) {
            TaskDefinition broken = s.activeForbidden();
            s.triggerForbidden(broken);
            changed = true;
            if (s.remainingLives() == 0) {
                eliminate(player, "触犯了禁忌：" + broken.displayName());
            } else {
                player.sendSystemMessage(Component.literal(
                        "§c你触犯了禁忌：§l" + broken.displayName() + "§r§c！剩余一条命。"));
                broadcast(player.getScoreboardName() + " 触犯了一条禁忌");
            }
        }
        // 状态有变才刷新计分板（疾跑等每 tick 触发的事件不应每 tick 发包）；
        // 出局路径由 eliminate() 负责刷新，此处只看仍存活的玩家
        if (changed && s.isAlive() && currentLevel != null) {
            ScoreboardManager.refresh(currentLevel.getServer());
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
        // 出局状态同步到计分板
        if (currentLevel != null) {
            ScoreboardManager.refresh(currentLevel.getServer());
        }
        // 对局中全员出局 → 立即结束本回合（无人得分），结算后自动进入下一回合；
        // "服务器仍有玩家在线"区分全员掉线：掉线走 onPlayerLeave 的 stopGame 回 IDLE
        if (phase == GamePhase.PLAYING
                && !currentLevel.getServer().getPlayerList().getPlayers().isEmpty()
                && states.values().stream().noneMatch(PlayerState::isAlive)) {
            broadcast("§c全军覆没！本回合无人得分");
            endRound();
        }
    }

    // ---------- 查询 ----------

    public int round() { return round; }

    // 当前阶段剩余秒数（向上取整），供客户端 HUD 显示
    public int countdownSeconds() {
        return (countdownTicks + 19) / 20;
    }

    // 阶段的中文显示名
    public String phaseLabel() {
        return switch (phase) {
            case IDLE -> "等待开局";
            case PREPARING -> "准备期";
            case PLAYING -> "对局";
            case SCORING -> "结算";
            case FINISHED -> "已结束";
        };
    }

    // 该玩家视角的必做任务文本（未入局返回空串）
    public String mustDoTextOf(ServerPlayer player) {
        PlayerState s = states.get(player.getUUID());
        return s == null ? "" : s.mustDo().displayName();
    }

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
