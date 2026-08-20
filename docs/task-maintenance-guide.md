# 任务维护指南（partygame）

> 适用版本：v2。本文档面向 mod 维护者：如何新增/调整必做与禁忌任务，什么情况只需改配置、什么情况需要写代码。

## 任务是什么

一局游戏中每位玩家每回合获得：1 件**必做**（只有自己知道内容）+ 2 件**禁忌**（自己不知道内容，其他玩家可见；触发第一件后第二件才激活）。
所有任务定义在服务器目录 `config/partygame.json` 的 `taskPool` 数组里。

## 任务 JSON 格式

```json
{
  "id": "press_button",
  "type": "MUST_DO",
  "triggers": ["PRESS_BUTTON"],
  "displayName": "按下竞技场内的按钮",
  "enabled": true
}
```

| 字段 | 说明 |
| --- | --- |
| id | 唯一英文标识（如 `press_button`），不可重复 |
| type | `MUST_DO`（必做）或 `FORBIDDEN`（禁忌） |
| triggers | 触发类型数组，见下方枚举表；复合禁忌可多个（如"不能破坏或放置方块"有 BREAK_BLOCK 和 PLACE_BLOCK 两个） |
| displayName | 玩家看到的中文显示名 |
| enabled | `true`/`false`；缺省视为 `true`。`false` 的任务不会被分配 |

## 触发类型枚举

| 枚举 | 行为 |
| --- | --- |
| JUMP | 跳跃 |
| SPRINT | 疾跑 |
| HIT_PLAYER | 对任意玩家造成伤害 |
| OPEN_CHEST | 打开箱子 |
| BREAK_BLOCK | 破坏方块 |
| PLACE_BLOCK | 放置方块 |
| EAT_FOOD | 吃食物 |
| CHAT | 聊天发言 |
| SNEAK | 蹲下 |
| PRESS_BUTTON | 按下按钮 |
| STAND_PLATE_3S | 在压力板上站满 3 秒 |

## 新增任务的两种情形

### 情形一：用已有触发类型（只改配置，无需写代码）

1. 在 `taskPool` 数组加一项（复制现有项，改 `id`/`displayName`/`triggers`）
2. 注意分配约束：`MUST_DO` 至少保留 1 项、`FORBIDDEN` 至少保留 2 项；且每种必做需要存在与它**无触发冲突**的至少 2 件禁忌（冲突 = triggers 有交集，如"对玩家造成伤害"必做与"不能攻击任何玩家"禁忌都依赖 HIT_PLAYER，不能分给同一人）
3. 重启服务器生效；或临时用 `/party tasks enable <id>` / `/party tasks disable <id>` 免重启切换
4. 验证：`/party config set minPlayers 1` → `/party setarena` → `/party start` 单人跑一局，确认判定正确

### 情形二：新触发类型（需要写代码）

1. `com.partygame.task.TriggerType` 枚举加值
2. `com.partygame.event.GameEventListeners` 加对应 MC 事件监听，把事件翻译为该 TriggerType（不确定 21.11.45 事件类名/签名时，先按 CLAUDE.md"已知坑"里的方法查反编译 jar）
3. `./gradlew compileJava` 通过后按情形一流程实测
4. 更新本指南的枚举表

## 常用维护命令

- `/party tasks list` —— 查看全部任务与启用状态
- `/party tasks disable <id>` —— 禁用任务（写回配置文件）
- `/party tasks enable <id>` —— 启用任务（写回配置文件）

## 内置地图发布流程（随 mod 打包分发）

内置地图 = 模板打包在 mod jar 里，所有安装者开箱即用、不可删除。发布新内置地图：

1. **游戏内建图**：用建图辅助（`/party map preview` / `/party map platform` / WorldEdit）搭好地图，确认红色羊毛出生点 ≥ `minPlayers` 个，站在中心执行 `/party map save <名字> <半径>`
2. **复制模板**：把 `run/config/partygame/maps/<名字>.nbt` 复制到 `src/main/resources/partygame/maps/`
3. **登记**：在 `DefaultConfig.DEFAULT_BUILTIN_MAPS` Java 列表加一条（**唯一登记处**，内置地图列表只由 mod 代码定义，不写进配置文件——写进配置会因旧文件残留导致 mod 更新后仍显示过期内容）
4. **验证**：`./gradlew compileJava test` 通过；游戏内 `/party map list` 看到 `[内置]` 标注，`choose` 后 `setarena` 能落地
5. **打包**：`./gradlew jar`，用 `unzip -l build/libs/*.jar` 确认 `partygame/maps/<名字>.nbt` 在 jar 内

注意：`/party map save` 的名字不能与内置地图重名；`/party map remove` 拒绝删除内置地图。移除一张内置图需要同时删资源文件与 `DEFAULT_BUILTIN_MAPS` 登记后重新发布。
