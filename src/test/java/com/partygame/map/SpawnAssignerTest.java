package com.partygame.map;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

// 出生点随机分配：数量足够时互不重复，不足时抛异常
class SpawnAssignerTest {

    @Test
    void assignsDistinctSpawns() {
        List<Integer> spawns = List.of(1, 2, 3, 4, 5);
        List<Integer> picked = SpawnAssigner.assign(spawns, 3, new Random(42));
        assertEquals(3, picked.size());
        assertEquals(3, picked.stream().distinct().count()); // 互不重复
        assertTrue(spawns.containsAll(picked));               // 全部来自候选列表
    }

    @Test
    void throwsWhenNotEnoughSpawns() {
        assertThrows(IllegalStateException.class,
                () -> SpawnAssigner.assign(List.of(1), 2, new Random()));
    }
}
