# CLAUDE.md（partygame 项目）

## 项目

- 派对生存玩法 mod：MC 1.21.11 + NeoForge 21.11.45，mod_id=`partygame`，Java 21
- 设计规格：`docs/superpowers/specs/2026-08-17-party-game-design.md`
- 实施计划：`docs/superpowers/plans/2026-08-17-party-game.md`（按 Task 顺序执行）
- git 规范：`docs/git-workflow.md`

## 已知坑（勿重蹈）

- 直连 NeoForge/Mojang 官方仓库会下载到 0 字节 .pom，报 XML"前言中不允许有内容"。`build.gradle` 已配阿里云镜像优先；若再遇此错，删除 `~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>` 及对应 `metadata-*/descriptors` 后重试
- Gson 测试依赖坐标是 `com.google.code.gson:gson:2.13.2`（组名不是 `com.google.gson`，详见 build.gradle 注释）
- 21.11.45 与常见教程的 API 差异大：`ResourceLocation`→`Identifier`；`ServerPlayer` 无 `displayTitle`（用 `ClientboundSetTitleTextPacket`）；`GameProfile.getName()` 已不存在（用 `getScoreboardName()`）；`teleportTo` 需 `Set<Relative>` + setCamera 布尔；`@EventBusSubscriber` 无 `bus` 属性（游戏总线事件用 `NeoForge.EVENT_BUS.register(obj)`）；事件 API 与教程差异：无 `LivingHurtEvent`（用 `LivingIncomingDamageEvent`）、`LivingJumpEvent` 是 `LivingEvent.LivingJumpEvent` 嵌套类、`ItemStack` 无 `isEdible()`（用 `has(DataComponents.FOOD)`）、菜单无 `getContainerBlockState()`（开箱判定用 `ChestMenu`）。**凡不确定的签名，先查 `~/.gradle/caches/neoformruntime/intermediate_results/` 下的反编译 jar**（`compiledWithNeoForge_*_output.jar` 用 javap，`sourcesAndCompiledWithNeoForge_*_output.jar` 用 unzip -p）

## 协作规则

- 代码注释中文、标识符英文
- 提交信息：Conventional Commits + 中文描述；禁止进度词与 AI 工具名
- 每任务一个 feature 分支（`feat/xxx`），合并后删除分支；git 操作由用户亲手执行（学习目标）
- 任何"已完成/已通过"结论必须附验证方式（gradle 输出、测试报告、日志）

## 进度

- Task 0 环境 ✅ / Task 1 骨架 ✅ / Task 2 配置系统 ✅ / Task 3 任务模型 ✅ / Task 4 状态机 ✅ / Task 5 竞技场 ✅ / Task 6 事件监听器 ✅（更新于 2026-08-19）
- 下一步：Task 7 计分板（分支 B 自定义 HUD）
- 之后：Task 8 /party 命令 → Task 9 GameTest → Task 10 联机手测
- 距离类任务（"不能靠近某人"等）在 v1 跑通后加入（设计文档第 12 节）
