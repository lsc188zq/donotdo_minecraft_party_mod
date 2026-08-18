package com.partygame.game;

import com.partygame.task.Assignment;
import com.partygame.task.TaskDefinition;
import com.partygame.task.TriggerType;

import java.util.List;

// 一名玩家的回合内状态：任务、禁忌激活进度、存活与分数
public class PlayerState {
    private Assignment assignment;
    private int activeForbiddenIndex; // 0 或 1；2 表示两件都已触发（此时已出局）
    private boolean mustDoDone;
    private boolean alive = true;
    private int score;
    private int plateStandTicks; // 站在竞技场压力板上的累计 tick

    public PlayerState(Assignment assignment, int score) {
        this.assignment = assignment;
        this.score = score;
    }

    // 新回合：任务重新分配、状态清零，分数保留
    public void resetRound(Assignment newAssignment) {
        this.assignment = newAssignment;
        this.activeForbiddenIndex = 0;
        this.mustDoDone = false;
        this.alive = true;
        this.plateStandTicks = 0;
    }

    public TaskDefinition mustDo() { return assignment.mustDo(); }
    public List<TaskDefinition> forbiddens() { return assignment.forbiddens(); }

    // 当前激活的禁忌：他人可见的"当前不能做的事情"
    public TaskDefinition activeForbidden() {
        return assignment.forbiddens().get(activeForbiddenIndex);
    }

    // 剩余命数：2 / 1 / 0
    public int remainingLives() { return 2 - activeForbiddenIndex; }

    public boolean triggerForbidden(TaskDefinition forbidden) {
        if (activeForbiddenIndex >= 2) return false;
        activeForbiddenIndex++;
        return true;
    }

    public boolean isMustDoDone() { return mustDoDone; }
    public void completeMustDo() { mustDoDone = true; }

    public boolean isAlive() { return alive; }
    public void eliminate() { alive = false; }

    public int score() { return score; }
    public void addScore(int delta) { score += delta; }

    public int plateStandTicks() { return plateStandTicks; }
    public void addPlateStandTick() { plateStandTicks++; }
    public void resetPlateStandTicks() { plateStandTicks = 0; }

    public boolean triggersMatch(TriggerType type) {
        return activeForbidden().triggers().contains(type);
    }
}
