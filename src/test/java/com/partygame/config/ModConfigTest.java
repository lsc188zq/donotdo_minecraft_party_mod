package com.partygame.config;

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
        assertEquals(15, config.arenaHalfSize());
        assertEquals(3, config.arenaWallHeight());
        assertEquals(4, config.chestCount());
        assertEquals(2, config.loot("weapons").size());
        assertEquals(4, config.loot("armor").size());
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
}
