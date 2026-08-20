package com.partygame;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.partygame.client.PartyHud;
import com.partygame.command.PartyCommand;
import com.partygame.config.ModConfig;
import com.partygame.event.GameEventListeners;
import com.partygame.game.GameManager;
import com.partygame.network.SyncStatesPayload;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.nio.file.Paths;

// 模组主类：仅负责注册，游戏逻辑挂在 GameManager 与事件监听器上
@Mod(PartyGame.MODID)
public class PartyGame {
    // 模组 ID：与 neoforge.mods.toml 中的 modId 一致
    public static final String MODID = "partygame";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PartyGame(IEventBus modEventBus) {
        // 加载配置（mod 加载时工作目录为运行目录，配置文件落在 run/config/partygame.json）
        GameManager.get().init(ModConfig.load(Paths.get("config", "partygame.json")));
        // 把 GameManager 单例注册到游戏事件总线（其 @SubscribeEvent 方法会被反射发现）
        NeoForge.EVENT_BUS.register(GameManager.get());
        // 注册事件监听器类（静态 @SubscribeEvent 方法，注册 Class 即可）
        NeoForge.EVENT_BUS.register(GameEventListeners.class);
        // 客户端专用：注册 HUD 渲染监听器（专用服务器上此分支不执行，不加载 PartyHud）
        if (FMLEnvironment.getDist().isClient()) {
            NeoForge.EVENT_BUS.register(PartyHud.class);
        }
        // 注册计分板同步包（服务端 → 客户端；处理器只在客户端执行）
        modEventBus.addListener(this::registerPayloads);
        // 注册 /party 命令（RegisterCommandsEvent 在游戏总线上触发）
        NeoForge.EVENT_BUS.addListener(
                (RegisterCommandsEvent event) -> PartyCommand.register(event.getDispatcher()));
        LOGGER.info("PartyGame 已加载");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                SyncStatesPayload.TYPE,
                SyncStatesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> PartyHud.apply(payload)));
    }
}
