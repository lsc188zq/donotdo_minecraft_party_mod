package com.partygame.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// 从候选出生点列表中为每位玩家随机分配一个互不重复的出生点
// 泛型化以便 JUnit 直接测试（调用方传入 BlockPos 列表）
public class SpawnAssigner {
    public static <T> List<T> assign(List<T> spawns, int count, Random random) {
        if (spawns.size() < count) {
            throw new IllegalStateException("出生点数量不足：" + spawns.size() + " < " + count);
        }
        List<T> pool = new ArrayList<>(spawns);
        Collections.shuffle(pool, random);
        return List.copyOf(pool.subList(0, count));
    }
}
