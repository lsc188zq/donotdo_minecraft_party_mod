# CLAUDE.md（partygame 项目）

## 项目

- 派对生存玩法 mod：MC 1.21.11 + NeoForge 21.11.45，mod_id=`partygame`，Java 21
- 设计规格：`docs/superpowers/specs/2026-08-17-party-game-design.md`
- 实施计划：`docs/superpowers/plans/2026-08-17-party-game.md`（按 Task 顺序执行）
- git 规范：`docs/git-workflow.md`

## 已知坑（勿重蹈）

- 直连 NeoForge/Mojang 官方仓库会下载到 0 字节 .pom，报 XML"前言中不允许有内容"。`build.gradle` 已配阿里云镜像优先；若再遇此错，删除 `~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>` 及对应 `metadata-*/descriptors` 后重试
- Gson 测试依赖坐标是 `com.google.code.gson:gson:2.13.2`（组名不是 `com.google.gson`，详见 build.gradle 注释）

## 协作规则

- 代码注释中文、标识符英文
- 提交信息：Conventional Commits + 中文描述；禁止进度词与 AI 工具名
- 每任务一个 feature 分支（`feat/xxx`），合并后删除分支；git 操作由用户亲手执行（学习目标）
- 任何"已完成/已通过"结论必须附验证方式（gradle 输出、测试报告、日志）

## 进度

- Task 0 环境 ✅ / Task 1 骨架 ✅ / Task 2 配置系统 ✅（更新于 2026-08-18）
- 下一步：Task 3 任务领域模型与分配器（纯 Java + JUnit）
- 距离类任务（"不能靠近某人"等）在 v1 跑通后加入（设计文档第 12 节）
