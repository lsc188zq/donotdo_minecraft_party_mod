package com.partygame.client;

import com.partygame.network.SyncStatesPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

// 客户端 HUD：渲染服务端下发的个性化分数/禁忌列表（仿右侧计分板）。
// 21.11.45 的 @EventBusSubscriber 没有 bus 属性，改由 PartyGame 构造器
// 在客户端侧执行 NeoForge.EVENT_BUS.register(PartyHud.class) 注册。
public class PartyHud {
    private static List<SyncStatesPayload.Row> rows = List.of();

    public static void apply(List<SyncStatesPayload.Row> newRows) {
        rows = newRows;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || rows.isEmpty()) return;

        int x = event.getGuiGraphics().guiWidth() - 160;
        int y = 10;
        for (SyncStatesPayload.Row row : rows) {
            int color = colorOf(row.score());
            String line = row.name() + " " + row.tabooText() + " ♥".repeat(Math.max(0, row.lives()));
            event.getGuiGraphics().drawString(mc.font, line, x, y, color, true);
            y += 10;
        }
    }

    // 颜色按分数：0-1 白 / 2-3 黄 / 4 紫 / 5+ 金
    private static int colorOf(int score) {
        if (score >= 5) return 0xFFD700;
        if (score >= 4) return 0xAA00AA;
        if (score >= 2) return 0xFFFF00;
        return 0xFFFFFF;
    }
}
