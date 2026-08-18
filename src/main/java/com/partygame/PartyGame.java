package com.partygame;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

// 模组主类：仅负责注册，游戏逻辑挂在 GameManager 与事件监听器上
@Mod(PartyGame.MODID)
public class PartyGame {
    // 模组 ID：与 neoforge.mods.toml 中的 modId 一致
    public static final String MODID = "partygame";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PartyGame(IEventBus modEventBus) {
        // 后续任务在此注册命令、配置与监听器
        LOGGER.info("PartyGame 已加载");
    }
}
