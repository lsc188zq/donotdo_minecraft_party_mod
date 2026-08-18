package com.partygame.task;

import java.util.Set;

// 一条任务定义（来自配置）：多数任务只有一个触发类型，
// "不能破坏或放置方块"这类复合禁忌有多个
public record TaskDefinition(String id, TaskType type, Set<TriggerType> triggers, String displayName) {
}
