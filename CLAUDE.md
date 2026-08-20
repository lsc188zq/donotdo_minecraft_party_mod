# CLAUDE.md（partygame 项目）

## 项目

- 派对生存玩法 mod：MC 1.21.11 + NeoForge 21.11.45，mod_id=`partygame`，Java 21
- 设计规格：`docs/superpowers/specs/2026-08-17-party-game-design.md`
- 实施计划：`docs/superpowers/plans/2026-08-17-party-game.md`（按 Task 顺序执行）
- git 规范：`docs/git-workflow.md`

## 已知坑（勿重蹈）

- 直连 NeoForge/Mojang 官方仓库会下载到 0 字节 .pom，报 XML"前言中不允许有内容"。`build.gradle` 已配阿里云镜像优先；若再遇此错，删除 `~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>` 及对应 `metadata-*/descriptors` 后重试
- Gson 测试依赖坐标是 `com.google.code.gson:gson:2.13.2`（组名不是 `com.google.gson`，详见 build.gradle 注释）
- 21.11.45 与常见教程的 API 差异大：`ResourceLocation`→`Identifier`；`ServerPlayer` 无 `displayTitle`（用 `ClientboundSetTitleTextPacket`）；`GameProfile.getName()` 已不存在（用 `getScoreboardName()`）；`teleportTo` 需 `Set<Relative>` + setCamera 布尔；`@EventBusSubscriber` 无 `bus` 属性（游戏总线事件用 `NeoForge.EVENT_BUS.register(obj)`）；事件 API 与教程差异：无 `LivingHurtEvent`（用 `LivingIncomingDamageEvent`）、`LivingJumpEvent` 是 `LivingEvent.LivingJumpEvent` 嵌套类、`ItemStack` 无 `isEdible()`（用 `has(DataComponents.FOOD)`）、菜单无 `getContainerBlockState()`（开箱判定用 `ChestMenu`）；命令权限用新系统 `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`（`CommandSourceStack.hasPermission(int)` 已删）；`RegisterCommandsEvent` 在游戏总线；FML 类在 `net.neoforged.fancymodloader:loader` jar 里（`@EventBusSubscriber` 的 `value`/`modid` 均必填）。**凡不确定的签名，先查 `~/.gradle/caches/neoformruntime/intermediate_results/` 下的反编译 jar**（`compiledWithNeoForge_*_output.jar` 用 javap，`sourcesAndCompiledWithNeoForge_*_output.jar` 用 unzip -p；含 NeoForge 补丁的完整编译产物在 `build/moddev/artifacts/neoforge-21.11.45.jar`，源码在 `-sources.jar`）
- GameTest 框架在 21.11.45 已重写（Task 9 因此跳过）：无 `@GameTest`/`@GameTestHolder`；新流程 = `TestFunctionLoader.registerLoader`（数据加载前）+ `RegisterGameTestsEvent`（MOD 总线）`registerEnvironment`/`registerTest`；测试锚定 `TestInstanceBlockEntity`，函数测试也必须引用能解析到的结构模板（否则 `structure.failure`）

## 协作规则

- 代码注释中文、标识符英文
- 提交信息：Conventional Commits + 中文描述；禁止进度词与 AI 工具名
- 开发直接在 main 工作区改文件；一个项目（大任务）完成后由用户自己建分支提交合并，Claude 只报进度名称、不给 git 命令；任务清单用大任务粒度
- 任何"已完成/已通过"结论必须附验证方式（gradle 输出、测试报告、日志）

## 进度

- v1 全部完成 ✅：Task 0-8 实现 + Task 9 跳过（GameTest 框架重写）+ Task 10 联机手测通过（更新于 2026-08-20）
- v2 代码完成 ✅：Task 1 地图系统 → 2 HUD → 3 判定 → 4 配置命令 → 5 文档全部实现并提交（设计规格 `docs/superpowers/specs/2026-08-20-v2-design.md`、实施计划 `docs/superpowers/plans/2026-08-20-v2.md`、任务维护指南 `docs/task-maintenance-guide.md`）（更新于 2026-08-20）
- 下一步：按 `docs/testing-checklist.md` 的 v2 部分联机手测；手测通过后 v2 整体验收
- 距离类任务（"不能靠近某人"等）在 v2 之后加入（设计文档第 12 节）
