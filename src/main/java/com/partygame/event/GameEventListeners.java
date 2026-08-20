package com.partygame.event;

import com.partygame.game.GameManager;
import com.partygame.game.GamePhase;
import com.partygame.game.PlayerState;
import com.partygame.task.TriggerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 把 MC 事件翻译成任务触发信号，所有判定交给 GameManager。
// 21.11.45 的 @EventBusSubscriber 没有 bus 属性，改由 PartyGame 构造器
// 执行 NeoForge.EVENT_BUS.register(GameEventListeners.class) 注册（静态方法）。
public class GameEventListeners {

    private static GameManager gm() { return GameManager.get(); }

    // 存活且在对局中的玩家才参与任务判定
    private static boolean inPlay(ServerPlayer p) {
        if (p == null || gm().phase() != GamePhase.PLAYING) return false;
        PlayerState s = gm().stateOf(p);
        return s != null && s.isAlive();
    }

    // 跳跃 → JUMP
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && inPlay(p)) {
            gm().onTrigger(p, TriggerType.JUMP);
        }
    }

    // 玩家间伤害 → HIT_PLAYER；非对局阶段禁用玩家间攻击
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Player attacker
                && event.getEntity() instanceof Player victim
                && attacker != victim) {
            if (gm().phase() != GamePhase.PLAYING) {
                event.setCanceled(true); // 准备期/结算期玩家间攻击无效
            } else if (attacker instanceof ServerPlayer ap && inPlay(ap)) {
                gm().onTrigger(ap, TriggerType.HIT_PLAYER);
            }
        }
    }

    // 死亡 → 出局（任何死因）
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            gm().onPlayerDeath(p);
        }
    }

    // 游戏进行中死亡不掉落物品
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (gm().phase() != GamePhase.IDLE && event.getEntity() instanceof Player) {
            event.setCanceled(true);
        }
    }

    // 破坏方块 → BREAK_BLOCK；游戏期间受保护方块（配置名单 + 按钮/压力板）不可破坏
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && inPlay(p)) {
            gm().onTrigger(p, TriggerType.BREAK_BLOCK);
        }
        // 取消保护方块的实际破坏；触发判定在上方照常执行（no_break_place 任务仍有效）。
        // 保护时机：对局期间全场生效；已设置竞技场时场内区域在开局前（IDLE）也保护
        if (isProtected(event.getState())
                && (gm().phase() != GamePhase.IDLE || gm().isInsideArena(event.getPos()))) {
            event.setCanceled(true);
        }
    }

    // 受保护方块：按钮/压力板（任意材质，任务机制）+ 配置名单（地板/围墙/羊毛/箱子等）
    private static boolean isProtected(BlockState state) {
        Block block = state.getBlock();
        return block instanceof ButtonBlock
                || block instanceof BasePressurePlateBlock
                || gm().config().protectedBlocks().contains(block);
    }

    // 放置方块 → PLACE_BLOCK
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && inPlay(p)) {
            gm().onTrigger(p, TriggerType.PLACE_BLOCK);
        }
    }

    // 打开箱子（ChestMenu 覆盖箱子/大箱子，竞技场内只存在箱子）
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer p && inPlay(p)
                && event.getContainer() instanceof ChestMenu) {
            gm().onTrigger(p, TriggerType.OPEN_CHEST);
        }
    }

    // 吃食物（完成食用动作时触发；DataComponents.FOOD 区分食物与药水）
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer p && inPlay(p)
                && event.getItem().has(DataComponents.FOOD)) {
            gm().onTrigger(p, TriggerType.EAT_FOOD);
        }
    }

    // 聊天发言 → CHAT
    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        if (inPlay(event.getPlayer())) {
            gm().onTrigger(event.getPlayer(), TriggerType.CHAT);
        }
    }

    // 疾跑 / 蹲下 / 压力板计时（每 tick 检查；PlayerTickEvent 两端都发，靠 ServerPlayer 过滤）
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !inPlay(p)) return;
        if (p.isSprinting()) {
            gm().onTrigger(p, TriggerType.SPRINT);
        }
        if (p.isShiftKeyDown()) {
            gm().onTrigger(p, TriggerType.SNEAK);
        }
        // 站在竞技场压力板上累计 60 tick（3 秒）触发 STAND_PLATE_3S。
        // 压力板只有 1/16 格厚，站在上面时脚部所在方块就是板子那一格，
        // 用 below() 会拿到地板永远不匹配；同时兼容脚在板子上方一格的情形
        PlayerState s = gm().stateOf(p);
        BlockPos feet = p.blockPosition();
        if (gm().platePos() != null
                && (feet.equals(gm().platePos()) || feet.below().equals(gm().platePos()))) {
            s.addPlateStandTick();
            if (s.plateStandTicks() >= 60) {
                s.resetPlateStandTicks();
                gm().onTrigger(p, TriggerType.STAND_PLATE_3S);
            }
        } else {
            s.resetPlateStandTicks();
        }
    }

    // 按下按钮 → PRESS_BUTTON
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer p && inPlay(p)
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ButtonBlock) {
            gm().onTrigger(p, TriggerType.PRESS_BUTTON);
        }
    }

    // 掉线视作出局
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            gm().onPlayerLeave(p);
        }
    }
}
