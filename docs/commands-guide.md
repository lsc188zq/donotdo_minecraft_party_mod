# /party 游戏命令说明

> 适用版本：v2。本文档面向服主与玩家：所有 `/party` 命令的用途、权限、参数与反馈。
> 权限说明："需管理员" = 需要服务器 op（服务器控制台执行 `op <名字>`）。

## 快速开始（最小流程）

1. 一名管理员站在想建竞技场的中心点：`/party setarena`
2. 玩家到齐后：`/party start`
3. 一局结束想再来一局：再输 `/party start` 即可
4. 单人调试：`/party config set minPlayers 1` 后只需一个客户端就能开局

## 命令总览

| 命令 | 权限 | 作用 |
| --- | --- | --- |
| `/party setarena` | 管理员 | 站在场地中心点执行：落地当前选中地图并设为竞技场中心 |
| `/party start` | 管理员 | 开始一局游戏 |
| `/party stop` | 管理员 | 终止游戏 |
| `/party score` | 所有人 | 显示分数榜 |
| `/party map save <名字> <半径>` | 管理员 | 保存自建地图模板并自动选中 |
| `/party map list` | 所有人 | 列出可用地图，标注当前选中 |
| `/party map remove <名字>` | 管理员 | 删除自建地图 |
| `/party map choose <名字>` | 管理员 | 切换当前地图（下次 setarena/每回合生效） |
| `/party map preview <半径>` | 管理员 | 标记保存范围（建图辅助） |
| `/party map preview clear` | 管理员 | 清除范围标记 |
| `/party map platform <半径>` | 管理员 | 一键平整场地并铺石砖地板（建图辅助） |
| `/party config show` | 所有人 | 查看当前配置 |
| `/party config set <键> <值>` | 管理员 | 修改配置并写回配置文件 |
| `/party tasks list` | 所有人 | 查看全部任务（含禁用项） |
| `/party tasks enable <id>` | 管理员 | 启用任务 |
| `/party tasks disable <id>` | 管理员 | 禁用任务 |

## 竞技场

### /party setarena

由玩家站在**新竞技场的中心点**执行。命令会：

1. 把当前选中的地图落地到该位置（区域内旧方块与掉落物全部清空）
2. 扫描地图中的红色羊毛（出生点），数量少于 `minPlayers` 时拒绝设置并报错
3. 成功后记录竞技场中心，之后 `/party start` 才可用

反馈示例：`竞技场中心已设为 -3, 75, 13，地图 procedural 已落地，出生点 8 个`

## 对局

### /party start

开始一局游戏。失败情况：

- 未 setarena：`请先执行 /party setarena 设置竞技场中心`
- 在线人数不足：`至少需要 N 名玩家`
- 已在游戏中：`游戏已在进行中`

开局后每回合：玩家出生在随机羊毛出生点、背包清空、收到大字必做任务提示；地图落地时世界时间设为正午。

### /party stop

立即终止游戏：广播提示、状态清零、各客户端 HUD 清空。终止后 `/party start` 是全新一局。

## 地图

### /party map save <名字> <半径>

玩家站在**自建地图的中心点**执行，保存以站位为中心 ±半径的柱形区域（含箱子内容）为模板，保存到 `config/partygame/maps/<名字>.nbt`，并自动选中该地图。

- 半径范围：5–100
- 名字不能是 `procedural`（被内置程序生成房占用）
- 模板里放红色羊毛 = 出生点（建议至少放 `minPlayers` 个）
- 保存时自动预检查出生点：羊毛不足 `minPlayers` 时照常保存，但反馈里追加警告（"setarena 落地会被拒绝"）

### /party map preview <半径> / preview clear

建图辅助：以玩家站位为中心标记保存范围——**四角玻璃**放在边界外一圈（不会混进模板），**中心荧石**是保存点。注意保存范围是 ±半径的**立方**（含高度），标记只标 X/Z 平面。建完用 `preview clear` 清除标记（只删标记块，不误删你后来放的其他方块；服务器重启后标记记录丢失，残留玻璃需手动拆）。

### /party map platform <半径>

建图辅助：以玩家站位为中心，清空区域内全部方块与掉落物，在脚下 Y-1 层铺满石砖地板，作为搭图基座。

### /party map list

列出内置"程序生成房"与全部自建地图，当前选中的标注 `[当前]`。

### /party map choose <名字>

切换地图（`procedural` = 内置程序生成房）。切换后在下一次 `/party setarena` 或下一回合落地时生效。

### /party map remove <名字>

删除地图登记与模板文件；若删除的是当前选中地图，自动回退到程序生成房。

## 配置

### /party config show

显示 5 个可调整的整型配置项：`minPlayers`、`preparingSeconds`、`playingSeconds`、`scoringSeconds`、`targetScore`。

### /party config set <键> <值>

修改配置并**立即写回** `config/partygame.json`（重启服务器后仍生效）。值最小为 1。

- 时长类（preparing/playing/scoringSeconds）修改在**下一回合**生效
- `minPlayers` 影响下一次 `/party start` 的校验
- `targetScore` 是胜利所需分数（先到者胜）

## 任务池

### /party tasks list

列出全部任务（含禁用项，标注 `[已禁用]`）。每行：`id [类型] 显示名`。

### /party tasks enable/disable <id>

启用/禁用任务并写回配置文件，**免重启生效**。注意保持任务池至少有 1 个必做与 2 个禁忌，否则 `/party start` 会报"任务池不足"。

## 分数榜

### /party score

按分数降序显示所有入局玩家的当前分数。

---

## 相关配置文件（config/partygame.json）

| 字段 | 说明 |
| --- | --- |
| `durations` | 准备/对局/结算时长（秒） |
| `targetScore` | 胜利分数 |
| `minPlayers` | 开局最少玩家数 |
| `maps` / `selectedMap` | 自建地图列表 / 当前选中地图 |
| `protectedBlocks` | 不可破坏的方块 id 名单（按钮/压力板由代码保护；已 setarena 后场内区域任何时候受保护，对局期间全场受保护） |
| `taskPool` | 任务定义（`enabled: false` 的任务不参与分配） |

- 地图模板文件：`config/partygame/maps/<名字>.nbt`
- 旧版本生成的配置文件缺 `maps`/`protectedBlocks` 字段时启动会自动补默认值
