package com.partygame.task;

import java.util.List;

// 任务池：按种类过滤后的任务列表
public record TaskPool(List<TaskDefinition> all) {
    public List<TaskDefinition> mustDos() {
        return all.stream().filter(t -> t.type() == TaskType.MUST_DO).toList();
    }

    public List<TaskDefinition> forbiddens() {
        return all.stream().filter(t -> t.type() == TaskType.FORBIDDEN).toList();
    }
}
