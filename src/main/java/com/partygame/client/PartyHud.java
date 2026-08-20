package com.partygame.client;

import com.partygame.network.SyncStatesPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

// 客户端 HUD：渲染服务端下发的轮数/阶段/倒计时/必做与个性化分数/禁忌列表。
// 倒计时在客户端本地递减：服务端仅在状态变化时发包，避免每秒刷新
// 21.11.45 的 @EventBusSubscriber 没有 bus 属性，改由 PartyGame 构造器
// 在客户端侧执行 NeoForge.EVENT_BUS.register(PartyHud.class) 注册。
public class PartyHud {
    private static int round;
    private static String phase = "";
    private static long countdownEndsAtMillis; // 倒计时结束的时间点（本地时钟）
    private static String myMustDo = "";
    private static boolean myMustDoDone;
    private static List<SyncStatesPayload.Row> rows = List.of();

    public static void apply(SyncStatesPayload payload) {
        round = payload.round();
        phase = payload.phase();
        countdownEndsAtMillis = System.currentTimeMillis() + payload.countdownSeconds() * 1000L;
        myMustDo = payload.myMustDo();
        myMustDoDone = payload.myMustDoDone();
        rows = payload.rows();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int x = event.getGuiGraphics().guiWidth() - 180;
        int y = 10;
        if (round > 0) {
            event.getGuiGraphics().drawString(mc.font, "§6第 " + round + " 轮", x, y, 0xFFFFFF, true);
            y += 12;
        }
        if (!phase.isEmpty()) {
            // 本地递减的剩余秒数；等待开局时无倒计时
            int remaining = Math.max(0, (int) ((countdownEndsAtMillis - System.currentTimeMillis() + 999) / 1000));
            String line = "等待开局".equals(phase) ? "§7" + phase : phase + " " + remaining + "s";
            event.getGuiGraphics().drawString(mc.font, line, x, y, 0xFFFFFF, true);
            y += 12;
        }
        if (!myMustDo.isEmpty()) {
            String done = myMustDoDone ? " §a✓" : "";
            event.getGuiGraphics().drawString(mc.font, "你的必做：§a" + myMustDo + done, x, y, 0xFFFFFF, true);
            y += 12;
        }
        for (SyncStatesPayload.Row row : rows) {
            int color = colorOf(row.score());
            String line = row.name() + " §7" + row.score() + "分 " + row.tabooText()
                    + " §c" + "♥".repeat(Math.max(0, row.lives()))
                    + (row.mustDoDone() ? " §a✓" : "");
            event.getGuiGraphics().drawString(mc.font, line, x, y, color, true);
            y += 12;
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
