# partygame —— MC 派对生存 mod

多人派对玩法：竞技场里每人 1 件必做任务 + 2 件自己看不见的禁忌任务。完成必做、躲避禁忌、PVP 存活，回合幸存者 +1 分，先到 5 分获胜。

完整玩法与设计见 [docs/superpowers/specs/2026-08-17-party-game-design.md](docs/superpowers/specs/2026-08-17-party-game-design.md)。

## 环境要求

| 工具 | 版本/说明 |
| --- | --- |
| Minecraft | 1.21.11 + NeoForge 21.11.45 |
| JDK | 21（Temurin 或 Microsoft OpenJDK 21，64 位） |
| IDE | IntelliJ IDEA Community |
| 构建 | Gradle（项目自带 wrapper，无需手动安装） |
| 版本管理 | Git（见 [docs/git-workflow.md](docs/git-workflow.md)） |

> 注：MCP（Mod Coder Pack）为旧 Forge 时代的工具链，NeoForge 已不再需要，映射由构建系统自动处理。

## 安装与游玩

1. 构建：`gradlew build`，产物在 `build/libs/partygame-<版本>.jar`。
2. 把 jar 放入客户端与服务端 mods 文件夹（PCL 用户：`D:\Game\PCL\.minecraft\mods`）。
3. 单人世界或专用服务器中，OP 输入 `/party setarena`（站在竞技场中心点）→ `/party start`。

## 开发快速开始

```bash
gradlew runServer   # 本地测试服务器（首次会下载 MC 本体，耗时较长）
gradlew runClient   # 本地测试客户端
gradlew build       # 打包
```

具体开发流程与实施计划见 `docs/superpowers/specs/` 目录。
