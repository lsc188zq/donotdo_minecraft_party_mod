package com.partygame.config;

// 默认配置内容：首次运行时由 ModConfig 写入 config/partygame.json
public class DefaultConfig {
    public static final String JSON = """
        {
          "durations": { "preparingSeconds": 30, "playingSeconds": 180, "scoringSeconds": 10 },
          "targetScore": 5,
          "minPlayers": 2,
          "arena": { "halfSize": 15, "wallHeight": 3 },
          "taskPool": [
            { "id": "press_button",   "type": "MUST_DO",   "triggers": ["PRESS_BUTTON"],    "displayName": "按下竞技场内的按钮" },
            { "id": "stand_plate",    "type": "MUST_DO",   "triggers": ["STAND_PLATE_3S"],  "displayName": "在压力板上站满 3 秒" },
            { "id": "damage_player",  "type": "MUST_DO",   "triggers": ["HIT_PLAYER"],      "displayName": "对任意玩家造成一次伤害" },
            { "id": "eat_food",       "type": "MUST_DO",   "triggers": ["EAT_FOOD"],        "displayName": "吃一个食物" },
            { "id": "open_chest",     "type": "MUST_DO",   "triggers": ["OPEN_CHEST"],      "displayName": "打开一个箱子" },
            { "id": "no_jump",        "type": "FORBIDDEN", "triggers": ["JUMP"],            "displayName": "不能跳跃" },
            { "id": "no_sprint",      "type": "FORBIDDEN", "triggers": ["SPRINT"],          "displayName": "不能疾跑" },
            { "id": "no_attack",      "type": "FORBIDDEN", "triggers": ["HIT_PLAYER"],      "displayName": "不能攻击任何玩家" },
            { "id": "no_open_chest",  "type": "FORBIDDEN", "triggers": ["OPEN_CHEST"],      "displayName": "不能打开箱子" },
            { "id": "no_break_place", "type": "FORBIDDEN", "triggers": ["BREAK_BLOCK", "PLACE_BLOCK"], "displayName": "不能破坏或放置方块" },
            { "id": "no_eat",         "type": "FORBIDDEN", "triggers": ["EAT_FOOD"],        "displayName": "不能吃食物" },
            { "id": "no_chat",        "type": "FORBIDDEN", "triggers": ["CHAT"],            "displayName": "不能在聊天发言" },
            { "id": "no_sneak",       "type": "FORBIDDEN", "triggers": ["SNEAK"],           "displayName": "不能蹲下" }
          ],
          "loot": {
            "chestCount": 4,
            "weapons": ["minecraft:iron_sword", "minecraft:stone_sword"],
            "armor": ["minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots", "minecraft:iron_helmet"],
            "food": ["minecraft:bread", "minecraft:apple"]
          }
        }
        """;
}
