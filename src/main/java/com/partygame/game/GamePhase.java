package com.partygame.game;

// 游戏阶段：状态机的一次状态；计时结束自动切换到下一阶段
public enum GamePhase {
    IDLE,       // 未开局
    PREPARING,  // 准备期：抢装备，玩家间攻击禁用
    PLAYING,    // 对局：任务判定与 PVP 生效
    SCORING,    // 结算：公告结果与加分
    FINISHED    // 游戏结束：宣布胜者
}
