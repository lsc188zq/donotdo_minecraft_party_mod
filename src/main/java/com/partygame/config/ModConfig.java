package com.partygame.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.partygame.task.TaskDefinition;
import com.partygame.task.TaskType;
import com.partygame.task.TriggerType;

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

    private ModConfig(JsonObject root) {
        this.root = root;
    }

    public static ModConfig load(Path configFile) {
        try {
            if (!Files.exists(configFile)) {
                Files.createDirectories(configFile.getParent());
                Files.writeString(configFile, DefaultConfig.JSON);
            }
            return new ModConfig(GSON.fromJson(Files.readString(configFile), JsonObject.class));
        } catch (Exception e) {
            throw new RuntimeException("partygame 配置读取失败: " + configFile, e);
        }
    }

    public int preparingSeconds() { return root.getAsJsonObject("durations").get("preparingSeconds").getAsInt(); }
    public int playingSeconds()  { return root.getAsJsonObject("durations").get("playingSeconds").getAsInt(); }
    public int scoringSeconds()  { return root.getAsJsonObject("durations").get("scoringSeconds").getAsInt(); }
    public int targetScore()     { return root.get("targetScore").getAsInt(); }
    public int arenaHalfSize()   { return root.getAsJsonObject("arena").get("halfSize").getAsInt(); }
    public int arenaWallHeight() { return root.getAsJsonObject("arena").get("wallHeight").getAsInt(); }
    public int chestCount()      { return root.getAsJsonObject("loot").get("chestCount").getAsInt(); }

    // kind 取 "weapons" / "armor" / "food"，返回物品 id 列表（如 minecraft:iron_sword）
    public List<String> loot(String kind) {
        return root.getAsJsonObject("loot").getAsJsonArray(kind).asList().stream()
                .map(e -> e.getAsString())
                .toList();
    }

    // 任务池：解析配置里的 "triggers" 字符串为 TriggerType 枚举，
    // 名称写错（与枚举不符）会在加载时抛异常，尽早暴露配置错误
    public List<TaskDefinition> taskPool() {
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
}
