package com.partygame.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.partygame.map.MapData;
import com.partygame.task.TaskDefinition;
import com.partygame.task.TaskType;
import com.partygame.task.TriggerType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 模组配置：读取 config/partygame.json，文件缺失时写入默认配置
// 解析失败直接抛异常，让服务器启动阶段尽早暴露配置错误
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final JsonObject root;
    private Path file;

    private ModConfig(JsonObject root) {
        this.root = root;
    }

    public static ModConfig load(Path configFile) {
        try {
            if (!Files.exists(configFile)) {
                Files.createDirectories(configFile.getParent());
                Files.writeString(configFile, DefaultConfig.JSON);
            }
            ModConfig config = new ModConfig(GSON.fromJson(Files.readString(configFile), JsonObject.class));
            config.file = configFile;
            // 旧配置缺少 maps/protectedBlocks 字段时补默认值，避免后续访问 NPE
            if (!config.root.has("maps")) {
                config.root.add("maps", new JsonArray());
            }
            if (!config.root.has("protectedBlocks")) {
                JsonArray blocks = new JsonArray();
                for (String id : List.of("minecraft:stone_bricks", "minecraft:red_wool", "minecraft:chest")) {
                    blocks.add(id);
                }
                config.root.add("protectedBlocks", blocks);
            }
            return config;
        } catch (Exception e) {
            throw new RuntimeException("partygame 配置读取失败: " + configFile, e);
        }
    }

    // 把内存中的配置写回原文件（/party config 与地图管理命令修改后调用）
    public void save() {
        try {
            Files.writeString(file, GSON.toJson(root));
        } catch (Exception e) {
            throw new RuntimeException("partygame 配置写回失败: " + file, e);
        }
    }

    public int preparingSeconds() { return root.getAsJsonObject("durations").get("preparingSeconds").getAsInt(); }
    public int playingSeconds()  { return root.getAsJsonObject("durations").get("playingSeconds").getAsInt(); }
    public int scoringSeconds()  { return root.getAsJsonObject("durations").get("scoringSeconds").getAsInt(); }
    public int targetScore()     { return root.get("targetScore").getAsInt(); }
    // 开局所需最少玩家数；单人测试时可把配置文件里的 minPlayers 改为 1
    // （旧配置文件没有该字段时按默认 2 处理）
    public int minPlayers()      { return root.has("minPlayers") ? root.get("minPlayers").getAsInt() : 2; }
    public int arenaHalfSize()   { return root.getAsJsonObject("arena").get("halfSize").getAsInt(); }
    public int arenaWallHeight() { return root.getAsJsonObject("arena").get("wallHeight").getAsInt(); }
    public int chestCount()      { return root.getAsJsonObject("loot").get("chestCount").getAsInt(); }

    // kind 取 "weapons" / "armor" / "food"，返回物品 id 列表（如 minecraft:iron_sword）
    public List<String> loot(String kind) {
        return root.getAsJsonObject("loot").getAsJsonArray(kind).asList().stream()
                .map(e -> e.getAsString())
                .toList();
    }

    // ---- 地图 ----

    public List<MapData> maps() {
        return root.getAsJsonArray("maps").asList().stream()
                .map(e -> MapData.fromJson(e.getAsJsonObject()))
                .toList();
    }

    // 当前选中地图名；旧配置缺省为内置程序生成房
    public String selectedMap() {
        return root.has("selectedMap") ? root.get("selectedMap").getAsString() : "procedural";
    }

    public void setSelectedMap(String name) {
        root.addProperty("selectedMap", name);
    }

    public void addMap(MapData map) {
        root.getAsJsonArray("maps").add(map.toJson());
    }

    // 删除地图登记；不存在时静默返回（模板文件由调用方删除）
    public void removeMap(String name) {
        root.getAsJsonArray("maps").asList()
                .removeIf(e -> e.getAsJsonObject().get("name").getAsString().equals(name));
    }

    // 游戏期间不可破坏的方块 id 名单；id 写错启动即抛异常，尽早暴露配置错误
    public Set<Block> protectedBlocks() {
        return root.getAsJsonArray("protectedBlocks").asList().stream()
                .map(e -> BuiltInRegistries.BLOCK
                        .getOptional(Identifier.parse(e.getAsString()))
                        .orElseThrow(() -> new IllegalArgumentException("配置中的受保护方块不存在：" + e.getAsString())))
                .collect(Collectors.toSet());
    }

    // ---- 可调的整型配置（/party config set）----

    // 可调的整型配置键：键名 → 路径写法
    private static final List<String> INT_KEYS = List.of(
            "minPlayers", "preparingSeconds", "playingSeconds", "scoringSeconds", "targetScore");

    public List<String> intKeys() { return INT_KEYS; }

    public int getInt(String key) {
        return switch (key) {
            case "minPlayers" -> minPlayers();
            case "preparingSeconds" -> preparingSeconds();
            case "playingSeconds" -> playingSeconds();
            case "scoringSeconds" -> scoringSeconds();
            case "targetScore" -> targetScore();
            default -> throw new IllegalArgumentException("未知配置项：" + key);
        };
    }

    // 修改整型配置并写回文件；对局中的时长类修改下一回合生效
    public void setInt(String key, int value) {
        switch (key) {
            case "minPlayers" -> root.addProperty("minPlayers", value);
            case "preparingSeconds" -> root.getAsJsonObject("durations").addProperty("preparingSeconds", value);
            case "playingSeconds" -> root.getAsJsonObject("durations").addProperty("playingSeconds", value);
            case "scoringSeconds" -> root.getAsJsonObject("durations").addProperty("scoringSeconds", value);
            case "targetScore" -> root.addProperty("targetScore", value);
            default -> throw new IllegalArgumentException("未知配置项：" + key);
        }
        save();
    }

    // ---- 任务池 ----

    // 任务池原始解析（不过滤 enabled）：任务启停判断与全量展示共用
    private List<TaskDefinition> taskPoolRaw() {
        return root.getAsJsonArray("taskPool").asList().stream()
                .map(e -> {
                    JsonObject o = e.getAsJsonObject();
                    Set<TriggerType> triggers = o.getAsJsonArray("triggers").asList().stream()
                            .map(t -> TriggerType.valueOf(t.getAsString()))
                            .collect(Collectors.toSet());
                    return new TaskDefinition(
                            o.get("id").getAsString(),
                            TaskType.valueOf(o.get("type").getAsString()),
                            Set.copyOf(triggers),
                            o.get("displayName").getAsString());
                })
                .toList();
    }

    // 任务池（仅启用项，供分配器使用）；旧配置无 enabled 字段视为启用
    // 解析配置里的 "triggers" 字符串为 TriggerType 枚举，
    // 名称写错（与枚举不符）会在加载时抛异常，尽早暴露配置错误
    public List<TaskDefinition> taskPool() {
        return taskPoolRaw().stream()
                .filter(t -> taskEnabled(t.id()))
                .toList();
    }

    // 任务池（全部任务，供 /party tasks list 展示含禁用项的完整列表）
    public List<TaskDefinition> taskPoolAll() {
        return taskPoolRaw();
    }

    // 任务是否启用；旧配置无 enabled 字段视为启用
    public boolean taskEnabled(String id) {
        for (var e : root.getAsJsonArray("taskPool")) {
            JsonObject o = e.getAsJsonObject();
            if (o.get("id").getAsString().equals(id)) {
                return !o.has("enabled") || o.get("enabled").getAsBoolean();
            }
        }
        throw new IllegalArgumentException("任务不存在：" + id);
    }

    // 启用/禁用任务并写回；未知任务 id 抛异常
    public void setTaskEnabled(String id, boolean enabled) {
        for (var e : root.getAsJsonArray("taskPool")) {
            JsonObject o = e.getAsJsonObject();
            if (o.get("id").getAsString().equals(id)) {
                o.addProperty("enabled", enabled);
                save();
                return;
            }
        }
        throw new IllegalArgumentException("任务不存在：" + id);
    }
}
