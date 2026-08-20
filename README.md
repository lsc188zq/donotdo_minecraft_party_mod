# donotdo —— MC 派对游戏 mod

## 简介

多人派对玩法：竞技场里每人 1 件必做任务 + 2 件自己不可视的禁忌任务。在规定时间内完成必做、躲避禁忌、PVP 存活，回合存活并完成必做者 +1 分，先到 5 分获胜。

## 关于本项目
本项目代码均由Claude Code负责，全流程项目管理与审批由人工负责。

## 环境要求

| 工具 | 版本/说明 |
| --- | --- |
| Minecraft | 1.21.11 + NeoForge 21.11.45 |
| JDK | 21（ Microsoft OpenJDK 21，64 位） |
| IDE | IntelliJ IDEA Community |
| 构建 | Gradle |
| 版本管理 | Git |

## 获取模组本体与安装

#### release版本获取
在本仓库的 GitHub Releases（现在还什么都没有）选择对应的MineCraft和neoforge版本的jar文件并下载

#### 最新开发版本获取
clone本项目到本地，在根目录打开终端并输入指令构建：`gradlew build`，产物在 `build/libs/partygame-<版本>.jar`。

#### 安装模组
把 jar文件 放入 mods 文件夹（PCL 用户：`~\PCL\.minecraft\mods`）。

## 玩法说明

#### 游戏流程
一局游戏有许多轮游戏（取决于何时胜利），每轮游戏分为三个阶段，准备阶段，游戏阶段，结算阶段。（默认时长分别为30s，180s，10s）

#### 准备阶段
每位玩家会收到只有自己可见的必做任务和自己看不见的两件禁忌（但是对手可见），接下来有时间搜集资源（准备阶段玩家互相伤害关闭）。
**注意：** 在准备阶段不会触发必做任务和禁忌 

#### 游戏阶段
游戏阶段玩家需要完成自己的必做任务，同时尽力去击败或者诱导其他玩家去做他们的禁忌。
两件禁忌任务有顺序，一个被触发另一个也才可以被触发，其它玩家只可见当前可被触发的禁忌。
某一件禁忌任务触发后，玩家会收到触发了什么禁忌的通知，两件禁忌任务都被触发后，该玩家本轮直接出局。
某位玩家完成必做任务后，会向其它玩家广播其必做任务已完成。

#### 结算阶段
游戏阶段结束后，当前必做任务完成且仍然存活（未出局）的玩家得到一分。
先达到五分的玩家获胜。


## 使用说明
详情可见 [docs\commands-guide.md](docs\commands-guide.md)

#### 基本命令
| 命令 | 作用 |
| --- | --- |
| `/party setarena` | 站在场地中心点执行：落地当前选中地图并设为竞技场中心 |
| `/party start` | 开始一局游戏 |
| `/party stop` | 终止游戏 |
| `/party score` | 显示分数榜 |
| `/party map save <名字> <半径>` | 保存自建地图模板并自动选中 |
| `/party map list` | 列出可用地图，标注当前选中 |
| `/party map choose <名字>` | 切换当前地图（下次 setarena/每回合生效） |
| `/party config show` | 查看当前配置 |
| `/party config set <键> <值>` | 修改配置并写回配置文件 |

## 给开发者

#### 本地测试

```bash
gradlew runServer   # 本地测试服务器（客户端可以在MC内多人模式加入localhost服务器，要在服务器GUI op 玩家名给权限）
gradlew runClient   # 本地测试客户端（在尾部加数字可以创建多个玩家辅助测试比如gradlew runClient2，环境允许将直接打开MC游戏）
```

#### 任务维护
详情可见[docs/task-maintenance-guide.md](docs/task-maintenance-guide.md)
