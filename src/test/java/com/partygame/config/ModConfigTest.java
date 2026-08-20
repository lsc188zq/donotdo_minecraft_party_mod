package com.partygame.config;

import com.partygame.map.MapData;
import com.partygame.task.TaskType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

// 验证默认配置能被完整解析，字段值符合设计文档
class ModConfigTest {

    @Test
    void defaultsLoadCorrectly() throws Exception {
        Path tempFile = Files.createTempFile("partygame-config-test", ".json");
        Files.deleteIfExists(tempFile); // load() 要求文件不存在时才会写入默认值
        ModConfig config = ModConfig.load(tempFile);

        assertEquals(30, config.preparingSeconds());
        assertEquals(180, config.playingSeconds());
        assertEquals(10, config.scoringSeconds());
        assertEquals(5, config.targetScore());
        assertEquals(2, config.minPlayers());
        assertEquals(15, config.arenaHalfSize());
        assertEquals(3, config.arenaWallHeight());
        assertEquals(4, config.chestCount());
        assertEquals(2, config.loot("weapons").size());
        assertEquals(4, config.loot("armor").size());
    }

    @Test
    void mapsAndSaveWork() throws Exception {
        Path tempFile = Files.createTempFile("partygame-config-test", ".json");
        Files.deleteIfExists(tempFile);
        ModConfig config = ModConfig.load(tempFile);

        assertEquals("procedural", config.selectedMap());
        config.addMap(new MapData("my_map", MapData.MapType.TEMPLATE, "my_map.nbt", 15));
        config.setSelectedMap("my_map");
        config.save();

        // 重新加载后改动仍在（写回生效）
        ModConfig reloaded = ModConfig.load(tempFile);
        assertEquals("my_map", reloaded.selectedMap());
        assertEquals(1, reloaded.maps().size());
        assertEquals("my_map", reloaded.maps().get(0).name());
    }

    @Test
    void oldConfigWithoutMinPlayersFallsBackToTwo() throws Exception {
        Path tempFile = Files.createTempFile("partygame-config-test", ".json");
        // 旧版本生成的配置没有 minPlayers 字段，按默认 2 处理避免读不到值崩溃
        Files.writeString(tempFile, "{ \"targetScore\": 5 }");
        ModConfig config = ModConfig.load(tempFile);

        assertEquals(2, config.minPlayers());
    }

    @Test
    void taskPoolParsesToTypedDefinitions() throws Exception {
        Path tempFile = Files.createTempFile("partygame-config-test", ".json");
        Files.deleteIfExists(tempFile);
        ModConfig config = ModConfig.load(tempFile);

        // 默认池：13 个任务 = 5 必做 + 8 禁忌；trigger 字符串全部能映射到枚举（否则 valueOf 抛异常）
        assertEquals(13, config.taskPool().size());
        long mustDos = config.taskPool().stream().filter(t -> t.type() == TaskType.MUST_DO).count();
        assertEquals(5, mustDos);
    }

    @Test
    void setIntWritesBackAndReloads() throws Exception {
        Path tempFile = Files.createTempFile("partygame-config-test", ".json");
        Files.deleteIfExists(tempFile);
        ModConfig config = ModConfig.load(tempFile);

        config.setInt("minPlayers", 1);
        config.setInt("playingSeconds", 120);

        ModConfig reloaded = ModConfig.load(tempFile);
        assertEquals(1, reloaded.minPlayers());
        assertEquals(120, reloaded.playingSeconds());
    }

    @Test
    void disabledTasksAreFilteredAndTogglePersists() throws Exception {
        Path tempFile = Files.createTempFile("partygame-config-test", ".json");
        Files.deleteIfExists(tempFile);
        ModConfig config = ModConfig.load(tempFile);

        int all = config.taskPool().size();
        config.setTaskEnabled("press_button", false);
        config.save();

        ModConfig reloaded = ModConfig.load(tempFile);
        assertFalse(reloaded.taskEnabled("press_button"));
        assertEquals(all - 1, reloaded.taskPool().size()); // 禁用项被过滤
        assertTrue(reloaded.taskPool().stream().noneMatch(t -> t.id().equals("press_button")));
    }
}
