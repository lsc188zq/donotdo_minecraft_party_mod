package com.partygame.task;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// 验证分配结果满足全部约束：每人 1 必做 + 2 禁忌、禁忌互异、无触发冲突
class TaskAssignerTest {

    private static final TaskDefinition PRESS = new TaskDefinition("press_button", TaskType.MUST_DO, Set.of(TriggerType.PRESS_BUTTON), "按下按钮");
    private static final TaskDefinition DAMAGE = new TaskDefinition("damage_player", TaskType.MUST_DO, Set.of(TriggerType.HIT_PLAYER), "对玩家造成伤害");
    private static final TaskDefinition NO_JUMP = new TaskDefinition("no_jump", TaskType.FORBIDDEN, Set.of(TriggerType.JUMP), "不能跳跃");
    private static final TaskDefinition NO_ATTACK = new TaskDefinition("no_attack", TaskType.FORBIDDEN, Set.of(TriggerType.HIT_PLAYER), "不能攻击");
    private static final TaskDefinition NO_EAT = new TaskDefinition("no_eat", TaskType.FORBIDDEN, Set.of(TriggerType.EAT_FOOD), "不能吃食物");

    @Test
    void assignsOneMustDoAndTwoForbiddensPerPlayer() {
        TaskPool pool = new TaskPool(List.of(PRESS, DAMAGE, NO_JUMP, NO_ATTACK, NO_EAT));
        List<Assignment> result = TaskAssigner.assign(pool, 100, new Random(42));
        assertEquals(100, result.size());
        for (Assignment a : result) {
            assertNotNull(a.mustDo());
            assertEquals(TaskType.MUST_DO, a.mustDo().type());
            assertEquals(2, a.forbiddens().size());
            assertNotEquals(a.forbiddens().get(0), a.forbiddens().get(1)); // 两件禁忌互异
        }
    }

    @Test
    void neverAssignsConflictingForbiddenWithMustDo() {
        // DAMAGE 与 NO_ATTACK 都依赖 HIT_PLAYER：拿到必做 DAMAGE 的玩家不应分到 NO_ATTACK
        TaskPool pool = new TaskPool(List.of(DAMAGE, PRESS, NO_JUMP, NO_ATTACK, NO_EAT));
        for (int i = 0; i < 500; i++) {
            for (Assignment a : TaskAssigner.assign(pool, 8, new Random(i))) {
                if (a.mustDo() == DAMAGE) {
                    for (TaskDefinition f : a.forbiddens()) {
                        assertFalse(f.triggers().contains(TriggerType.HIT_PLAYER),
                                "必做为造成伤害时不应分到依赖攻击的禁忌");
                    }
                }
            }
        }
    }

    @Test
    void throwsWhenPoolTooSmall() {
        TaskPool pool = new TaskPool(List.of(PRESS, NO_JUMP));
        assertThrows(IllegalStateException.class, () -> TaskAssigner.assign(pool, 1, new Random()));
    }
}
