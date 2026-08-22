# CLAUDE.md（partygame 项目）

## 项目

- 派对生存玩法 mod：MC 1.21.1 + NeoForge 21.1.248，mod_id=`partygame`，Java 21（主版本线在 main 分支；1.21.11 版本冻结在 `list` 分支）
- 设计规格：`docs/superpowers/specs/2026-08-17-party-game-design.md`
- 实施计划：`docs/superpowers/plans/2026-08-17-party-game.md`（按 Task 顺序执行）
- git 规范：`docs/git-workflow.md`

## 已知坑（勿重蹈）

- 直连 NeoForge/Mojang 官方仓库会下载到 0 字节 .pom，报 XML"前言中不允许有内容"。`build.gradle` 已配阿里云镜像优先；若再遇此错，删除 `~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>` 及对应 `metadata-*/descriptors` 后重试
- Gson 测试依赖坐标是 `com.google.code.gson:gson:2.13.2`（组名不是 `com.google.gson`，详见 build.gradle 注释）
- **构建与启动（1.21.1 实测）**：ModDev 插件必须用 1.x（当前 1.0.24）——2.x 生成的 dev 启动目标引用 21.1.x 不存在的类 `net.neoforged.fml.startup.Client`，启动直接 ClassNotFoundException；`mods.toml` 必须**顶层**声明 `modLoader="javafml"` 与 `loaderVersion="[4,)"`（FML 4.0.43 强制，放 `[[mods]]` 段里会报 "Missing ModLoader"）；`run/mods/` 里的外部 mod（如 WorldEdit）版本必须匹配 1.21.1，否则启动报 "not a valid mod file"
- **1.21.1 API 特点（与 21.11.x 反向）**：没有 `Identifier`（用 `ResourceLocation`，`parse`/`fromNamespaceAndPath`）；`ServerPlayer.displayTitle` 存在；`GameProfile.getName()` 存在；`teleportTo` 是旧 6 参签名 `(ServerLevel, x, y, z, yRot, xRot)`（无 Set<Relative>/setCamera）；命令权限用 `CommandSourceStack.hasPermission(int)`（op 等级 2）；`@EventBusSubscriber` 有 `bus` 属性；`LivingIncomingDamageEvent`、`LivingEvent.LivingJumpEvent` 嵌套类、`NbtAccounter` 重载、`ChestMenu` 开箱判定均可直接用。**凡不确定的签名，先查 `~/.gradle/caches/neoformruntime/intermediate_results/` 下的反编译 jar**（`compiledWithNeoForge_*_output.jar` 用 javap，`sourcesAndCompiledWithNeoForge_*_output.jar` 用 unzip -p；含 NeoForge 补丁的完整编译产物在 `build/moddev/artifacts/neoforge-21.1.248.jar`，源码在 `-sources.jar`）；FML 类在 `net.neoforged.fancymodloader:loader:4.0.43` jar 里
- GameTest 框架未使用（v1 Task 9 跳过）；若未来启用，1.21.1 的 GameTest 与 21.11.x 流程不同，需重新调研

## 协作规则

- 代码注释中文、标识符英文
- 提交信息：Conventional Commits + 中文描述；禁止进度词与 AI 工具名
- 开发直接在 main 工作区改文件；一个项目（大任务）完成后由用户自己建分支提交合并，Claude 只报进度名称、不给 git 命令；任务清单用大任务粒度
- 任何"已完成/已通过"结论必须附验证方式（gradle 输出、测试报告、日志）

## 进度

- v1 + v2 功能全部完成 ✅：地图系统/HUD/判定/配置命令/文档，及 v2 修复（HUD 颜色、退出重置、羊毛清理、破坏防护、白天、结算期重置、建图辅助、内置地图打包）（更新于 2026-08-22）
- 主版本线已切到 **MC 1.21.1 + NeoForge 21.1.248**（main 分支）；1.21.11 版本冻结在 `list` 分支
- 进行中：1.21.1 冒烟测试发现的两个 bug——对局超时不进入结算（诊断中）、压力板判定（模板地图扫描已修，待测）
- 待批方案：游戏结束后恢复玩家状态（位置/物品栏/血量/游戏模式），`docs/superpowers/plans/2026-08-21-1.21.1-port.md` 之后立项
- 距离类任务（"不能靠近某人"等）在 v2 之后加入（设计文档第 12 节）
