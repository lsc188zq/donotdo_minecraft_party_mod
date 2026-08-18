package com.partygame.task;

import java.util.List;

// 一名玩家一回合的任务：forbiddens 恒为 2 个，第一个是初始激活的禁忌
public record Assignment(TaskDefinition mustDo, List<TaskDefinition> forbiddens) {
}
