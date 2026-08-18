package com.partygame.config;

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
}
