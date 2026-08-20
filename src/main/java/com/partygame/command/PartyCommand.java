package com.partygame.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.partygame.config.ModConfig;
import com.partygame.game.GameManager;
import com.partygame.game.PlayerState;
import com.partygame.map.MapData;
import com.partygame.map.MapManager;
import com.partygame.task.TaskDefinition;
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
                        .executes(ctx -> score(ctx.getSource())))
                .then(mapCommand())
                .then(configCommand())
                .then(tasksCommand()));
    }

    // /party map 子命令：save/list/remove/choose/preview/platform（独立方法，避免深层嵌套的括号堆叠）
    private static LiteralArgumentBuilder<CommandSourceStack> mapCommand() {
        return Commands.literal("map")
                .then(Commands.literal("save")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(5, 100))
                                        .executes(ctx -> mapSave(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.literal("list")
                        .executes(ctx -> mapList(ctx.getSource())))
                .then(Commands.literal("remove")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> mapRemove(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("choose")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> mapChoose(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(previewCommand())
                .then(Commands.literal("platform")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(5, 100))
                                .executes(ctx -> mapPlatform(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius")))));
    }

    // /party map preview 子命令：radius 放置范围标记 / clear 清除标记（建图辅助）
    private static LiteralArgumentBuilder<CommandSourceStack> previewCommand() {
        return Commands.literal("preview")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("radius", IntegerArgumentType.integer(5, 100))
                        .executes(ctx -> mapPreview(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "radius"))))
                .then(Commands.literal("clear")
                        .executes(ctx -> mapPreviewClear(ctx.getSource())));
    }

    // /party config 子命令：show 查看 / set 修改整型配置并写回
    private static LiteralArgumentBuilder<CommandSourceStack> configCommand() {
        return Commands.literal("config")
                .then(Commands.literal("show")
                        .executes(ctx -> configShow(ctx.getSource())))
                .then(Commands.literal("set")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(ctx -> configSet(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "key"),
                                                IntegerArgumentType.getInteger(ctx, "value"))))));
    }

    // /party tasks 子命令：list 查看全部任务 / enable|disable 启停任务并写回
    private static LiteralArgumentBuilder<CommandSourceStack> tasksCommand() {
        return Commands.literal("tasks")
                .then(Commands.literal("list")
                        .executes(ctx -> tasksList(ctx.getSource())))
                .then(Commands.literal("enable")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> tasksSet(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"), true))))
                .then(Commands.literal("disable")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> tasksSet(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"), false))));
    }

    private static int setArena(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer p)) {
            source.sendFailure(Component.literal("该命令需由玩家站在竞技场中心点执行"));
            return 0;
        }
        GameManager gm = GameManager.get();
        ModConfig config = gm.config();
        MapData map = gm.selectedMapData();
        // 先落地并扫描出生点，数量不足直接拒绝，不记录中心
        MapManager.land(source.getLevel(), p.blockPosition(), config, map);
        int scanRadius = map.type() == MapData.MapType.PROCEDURAL ? config.arenaHalfSize() : map.radius();
        int spawnCount = MapManager.scanSpawns(source.getLevel(), p.blockPosition(), scanRadius).size();
        if (spawnCount < config.minPlayers()) {
            source.sendFailure(Component.literal(
                    "出生点不足：场地内只有 " + spawnCount + " 个红色羊毛，少于 minPlayers(" + config.minPlayers() + ")"));
            return 0;
        }
        gm.setArenaCenter(p.blockPosition());
        source.sendSuccess(() -> Component.literal(
                "竞技场中心已设为 " + p.blockPosition().toShortString() + "，地图 " + map.name()
                + " 已落地，出生点 " + spawnCount + " 个"), true);
        return 1;
    }

    private static int mapSave(CommandSourceStack source, String name, int radius) {
        if (!(source.getEntity() instanceof ServerPlayer p)) {
            source.sendFailure(Component.literal("该命令需由玩家站在地图中心点执行"));
            return 0;
        }
        if (name.equals(MapData.PROCEDURAL.name())) {
            source.sendFailure(Component.literal("该名字被内置程序生成房占用"));
            return 0;
        }
        ModConfig config = GameManager.get().config();
        if (config.builtinMaps().stream().anyMatch(m -> m.name().equals(name))) {
            source.sendFailure(Component.literal("该名字与内置地图冲突"));
            return 0;
        }
        // 出生点预检查：保存前统计区域内红色羊毛，不足 minPlayers 时在反馈里追加警告（不阻断保存）
        int spawnCount = MapManager.scanSpawns(source.getLevel(), p.blockPosition(), radius).size();
        try {
            MapManager.saveTemplate(source.getLevel(), p.blockPosition(), radius, name);
            config.removeMap(name);
            config.addMap(new MapData(name, MapData.MapType.TEMPLATE, name + ".nbt", radius));
            config.setSelectedMap(name);
            config.save();
        } catch (Exception e) {
            source.sendFailure(Component.literal("地图保存失败：" + e.getMessage()));
            return 0;
        }
        String spawnNote = spawnCount < config.minPlayers()
                ? " §c⚠ 出生点仅 " + spawnCount + " 个，少于 minPlayers(" + config.minPlayers() + ")，setarena 落地会被拒绝"
                : "（出生点 " + spawnCount + " 个）";
        source.sendSuccess(() -> Component.literal("地图 " + name + " 已保存（半径 " + radius + "）并选中" + spawnNote), true);
        return 1;
    }

    private static int mapPreview(CommandSourceStack source, int radius) {
        if (!(source.getEntity() instanceof ServerPlayer p)) {
            source.sendFailure(Component.literal("该命令需由玩家站在地图中心点执行"));
            return 0;
        }
        MapManager.placePreviewMarkers(source.getLevel(), p.blockPosition(), radius);
        source.sendSuccess(() -> Component.literal(
                "已标记半径 " + radius + " 的保存范围：四角玻璃在边界外一圈，中心荧石为保存点；"
                + "保存范围是 ±" + radius + " 的立方（含高度）；建完用 /party map preview clear 清除标记"), true);
        return 1;
    }

    private static int mapPreviewClear(CommandSourceStack source) {
        MapManager.clearPreviewMarkers(source.getLevel());
        source.sendSuccess(() -> Component.literal("预览标记已清除"), true);
        return 1;
    }

    private static int mapPlatform(CommandSourceStack source, int radius) {
        if (!(source.getEntity() instanceof ServerPlayer p)) {
            source.sendFailure(Component.literal("该命令需由玩家站在平地中心点执行"));
            return 0;
        }
        MapManager.flatten(source.getLevel(), p.blockPosition(), radius);
        source.sendSuccess(() -> Component.literal(
                "已平整半径 " + radius + " 的区域并铺设石砖地板（脚下层）"), true);
        return 1;
    }

    private static int mapList(CommandSourceStack source) {
        ModConfig config = GameManager.get().config();
        List<String> lines = new ArrayList<>();
        lines.add("§6程序生成房（内置）" + (config.selectedMap().equals("procedural") ? " §a[当前]" : ""));
        for (MapData m : config.builtinMaps()) {
            lines.add("§b" + m.name() + "（半径 " + m.radius() + "）[内置]"
                    + (config.selectedMap().equals(m.name()) ? " §a[当前]" : ""));
        }
        for (MapData m : config.maps()) {
            lines.add(m.name() + "（半径 " + m.radius() + "）" + (config.selectedMap().equals(m.name()) ? " §a[当前]" : ""));
        }
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int mapRemove(CommandSourceStack source, String name) {
        ModConfig config = GameManager.get().config();
        if (config.builtinMaps().stream().anyMatch(m -> m.name().equals(name))) {
            source.sendFailure(Component.literal("内置地图不可删除：" + name));
            return 0;
        }
        boolean existed = config.maps().stream().anyMatch(m -> m.name().equals(name));
        if (!existed) {
            source.sendFailure(Component.literal("地图不存在：" + name));
            return 0;
        }
        config.removeMap(name);
        if (config.selectedMap().equals(name)) {
            config.setSelectedMap(MapData.PROCEDURAL.name());
        }
        config.save();
        // 模板文件删除失败不阻塞命令（残留文件不影响游戏）
        try { Files.deleteIfExists(Paths.get("config", "partygame", "maps", name + ".nbt")); } catch (IOException ignored) { }
        source.sendSuccess(() -> Component.literal("地图 " + name + " 已删除"), true);
        return 1;
    }

    private static int mapChoose(CommandSourceStack source, String name) {
        ModConfig config = GameManager.get().config();
        boolean exists = name.equals(MapData.PROCEDURAL.name())
                || config.builtinMaps().stream().anyMatch(m -> m.name().equals(name))
                || config.maps().stream().anyMatch(m -> m.name().equals(name));
        if (!exists) {
            source.sendFailure(Component.literal("地图不存在：" + name));
            return 0;
        }
        config.setSelectedMap(name);
        config.save();
        source.sendSuccess(() -> Component.literal("已选中地图：" + name + "（/party setarena 落地）"), true);
        return 1;
    }

    private static int configShow(CommandSourceStack source) {
        ModConfig config = GameManager.get().config();
        source.sendSuccess(() -> Component.literal("§6===== 配置 ====="), false);
        for (String key : config.intKeys()) {
            String line = key + " = " + config.getInt(key);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int configSet(CommandSourceStack source, String key, int value) {
        ModConfig config = GameManager.get().config();
        try {
            config.setInt(key, value);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已设置 " + key + " = " + value + "（已写回配置文件）"), true);
        return 1;
    }

    private static int tasksList(CommandSourceStack source) {
        ModConfig config = GameManager.get().config();
        source.sendSuccess(() -> Component.literal("§6===== 任务池 ====="), false);
        for (TaskDefinition t : config.taskPoolAll()) {
            String disabled = config.taskEnabled(t.id()) ? "" : " §8[已禁用]";
            source.sendSuccess(() -> Component.literal(
                    t.id() + " §7[" + t.type() + "] " + t.displayName() + disabled), false);
        }
        return 1;
    }

    private static int tasksSet(CommandSourceStack source, String id, boolean enabled) {
        ModConfig config = GameManager.get().config();
        try {
            config.setTaskEnabled(id, enabled);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("任务 " + id + " 已" + (enabled ? "启用" : "禁用")), true);
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
