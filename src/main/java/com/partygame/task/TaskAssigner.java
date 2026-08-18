package com.partygame.task;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

// 每回合为所有玩家分配任务：1 件必做 + 2 件互不相同且与必做无触发冲突的禁忌。
// 纯 Java 实现（注入 java.util.Random），不依赖 MC，可单元测试
public class TaskAssigner {

    // 冲突定义：禁忌与必做的触发类型有交集时不可分给同一人
    // （如"不能攻击"与"对玩家造成伤害"都依赖 HIT_PLAYER）
    private static boolean conflicts(TaskDefinition mustDo, TaskDefinition forbidden) {
        Set<TriggerType> overlap = new HashSet<>(mustDo.triggers());
        overlap.retainAll(forbidden.triggers());
        return !overlap.isEmpty();
    }

    public static List<Assignment> assign(TaskPool pool, int playerCount, Random random) {
        List<TaskDefinition> mustDos = pool.mustDos();
        List<TaskDefinition> forbiddens = pool.forbiddens();
        if (mustDos.isEmpty() || forbiddens.size() < 2) {
            throw new IllegalStateException("任务池不足：至少 1 个必做与 2 个禁忌");
        }

        List<Assignment> result = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            TaskDefinition mustDo = mustDos.get(random.nextInt(mustDos.size()));
            // 先筛掉与必做冲突的禁忌，再抽 2 个互不相同的
            List<TaskDefinition> candidates = forbiddens.stream()
                    .filter(f -> !conflicts(mustDo, f))
                    .toList();
            if (candidates.size() < 2) {
                throw new IllegalStateException("与必做任务不冲突的禁忌不足 2 个，请检查任务池配置");
            }
            int first = random.nextInt(candidates.size());
            int second;
            do {
                second = random.nextInt(candidates.size());
            } while (second == first);

            result.add(new Assignment(mustDo, List.of(candidates.get(first), candidates.get(second))));
        }
        return result;
    }
}
