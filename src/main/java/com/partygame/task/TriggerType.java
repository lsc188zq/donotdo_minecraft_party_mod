package com.partygame.task;

// 可被系统检测的玩家行为：事件监听器把 MC 原生事件翻译成这些触发类型，
// 配置 JSON 中 "triggers" 字段按枚举名引用（如 "SNEAK"）
public enum TriggerType {
    JUMP,
    SPRINT,
    HIT_PLAYER,
    OPEN_CHEST,
    BREAK_BLOCK,
    PLACE_BLOCK,
    EAT_FOOD,
    CHAT,
    SNEAK,
    PRESS_BUTTON,
    STAND_PLATE_3S
}
