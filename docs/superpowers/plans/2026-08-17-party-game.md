# Party Game Mod 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现派对生存玩法 mod：竞技场内每人 1 必做 + 2 禁忌任务，回合制 PVP，幸存者加分，先到 5 分获胜。

**Architecture:** 集中式状态机。`GameManager` 是唯一事实来源（阶段、玩家状态、分数），事件监听器把 MC 事件翻译成触发信号喂给它；任务用数据模型（`TaskDefinition` + `TriggerType` 枚举）而非 13 个类，分配器为纯 Java 可 JUnit 测试。

**Tech Stack:** Minecraft 1.21.11 + NeoForge 21.11.45（ModDevGradle 构建）、Java 21、JUnit 5（纯逻辑测试）、GameTest（竞技场结构测试）、Gson（配置解析，MC 自带）。

**Spec:** `docs/superpowers/specs/2026-08-17-party-game-design.md`

## Global Constraints

- Minecraft 1.21.11 + NeoForge 21.11.45，Java 21（`gradle.properties` 中 `neo_version=21.11.45`）
- mod_id：`partygame`；包名 `com.partygame`
- 代码注释用中文；标识符用英文；提交信息禁止出现开发进度词（FIXED、Step、Phase 等）与 AI 工具名
- 玩家上限 8 人、下限 2 人；对局中触发判定只对存活玩家生效
- 每回合：30s 准备（玩家间攻击禁用）+ 180s 对局 + 10s 结算；幸存者 +1 分；先到 5 分胜
- git：main 分支保护，功能分支 `feat/<名>` 开发；提交由**用户本人执行**（学习目标），Claude 提供命令与解释
- Windows 环境；用户终端用 `.\gradlew.bat`，本会话 bash 用 `./gradlew`
- 事件类名以下列"验证点"为准，实施时在 IDE 内查 1.21.11 API 确认后再写，写错编译期即暴露

### 全局验证点（Task 5/6 实施前逐项在 IDE 确认）

| 用途 | 首选类名 | 后备方案 |
| --- | --- | --- |
| 跳跃 | `net.neoforged.neoforge.event.entity.living.LivingJumpEvent` | `PlayerTickEvent.Post` 内检查 `getDeltaMovement().y > 0.35` |
| 按下按钮 | `PlayerInteractEvent.RightClickBlock` | 监听按钮方块状态变化（邻居更新） |
| 吃食物 | `LivingEntityUseItemEvent.Finish` | 监听 `ItemStack` 数量变化 + `isEdible()` |
| 开箱 | `PlayerContainerEvent.Open` | `PlayerInteractEvent.RightClickBlock` + `ChestBlock` 判断 |
| 受伤 | `LivingHurtEvent` | `LivingIncomingDamageEvent`（1.21.4+ 可能改名） |
| 聊天 | `net.neoforged.neoforge.event.ServerChatEvent` | — |
| 破坏/放置 | `BlockEvent.BreakEvent` / `BlockEvent.EntityPlaceEvent` | — |
| 标题大字 | `Player#displayTitle(...)` | 仅 `sendSystemMessage` 聊天提示 |

---

### Task 0: 环境检查与 git 初始化

**Files:**
- 初始化仓库于 `D:\Projects\MCmod`（已存在 README.md、docs/）

**Interfaces:**
- Produces: git 仓库（main 分支），初始提交包含全部已有文档

- [ ] **Step 1: 检查 JDK 21（用户执行）**

在用户终端运行：`java -version`
预期：`openjdk version "21.x"`。若无或版本不符，从 https://adoptium.net/ 安装 Temurin 21（64 位），安装后重开终端再查。同时检查 `git --version`。

- [ ] **Step 2: 初始化仓库（用户执行）**

```bash
cd D:\Projects\MCmod
git init
git add README.md docs
git commit -m "docs: 添加项目说明、设计规格与 git 协作规范"
git log --oneline
```

预期：初始提交显示 `docs: 添加项目说明、设计规格与 git 协作规范`。

- [ ] **Step 3: 确认 main 分支（用户执行）**

`git branch` 预期显示 `* main`（默认分支即 main，无需重命名）。

---

### Task 1: MDK 项目骨架

**Files:**
- Fetch 参考：NeoForgeMDKs/MDK-1.21-ModDevGradle 模板的 `build.gradle`、`settings.gradle`、`gradle.properties`、`gradle/wrapper/gradle-wrapper.properties`
- Create: `build.gradle`、`settings.gradle`、`gradle.properties`、`.gitignore`、`gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.properties`、`gradle/wrapper/gradle-wrapper.jar`
- Create: `src/main/java/com/partygame/PartyGame.java`
- Create: `src/main/resources/META-INF/neoforge.mods.toml`

**Interfaces:**
- Produces: `PartyGame.MODID = "partygame"`（所有后续任务引用）；可运行的 `runClient`/`runServer`/`runGameTestServer` 配置

- [ ] **Step 1: 获取官方模板文件核对版本**

WebFetch `https://raw.githubusercontent.com/NeoForgeMDKs/MDK-1.21-ModDevGradle/main/build.gradle`、`settings.gradle`、`gradle.properties`、`gradle/wrapper/gradle-wrapper.properties` 四个文件，核对 ModDevGradle 插件版本号与 1.21.11 对应的 `neo_version` 写法。以模板为基准，按下方内容改出本项目版本。

- [ ] **Step 2: 写 gradle.properties**

```properties
# 以官方 MDK 模板核对后的版本号为准，其余键名不变
org.gradle.jvmargs=-Xmx4G
org.gradle.parallel=true

mod_id=partygame
mod_name=Party Game
mod_version=0.1.0
mod_group_id=com.partygame
neo_version=21.11.45
minecraft_version=1.21.11
```

- [ ] **Step 3: 写 build.gradle（含测试配置）**

```gradle
plugins {
    id 'java-library'
    id 'net.neoforged.moddev' version '<以 MDK-1.21-ModDevGradle 模板为准>'
}

repositories {
    mavenCentral()
}

base {
    archivesName = mod_id
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

neoForge {
    version = project.neo_version

    runs {
        configureEach {
            logLevel = org.slf4j.event.Level.DEBUG
        }
        client {
            client()
        }
        server {
            server()
        }
        gameTestServer {
            type = "gameTestServer"
        }
    }

    mods {
        "${mod_id}" {
            sourceSet sourceSets.main
        }
    }
}

dependencies {
    // 纯 Java 逻辑（任务分配器等）的单元测试
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

- [ ] **Step 4: 写 settings.gradle**

```gradle
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven { url = 'https://maven.neoforged.net/releases' }
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}
```

（以官方模板为准微调；foojay 插件用于自动寻找本机 JDK 21。）

- [ ] **Step 5: 写 .gitignore**

```gitignore
.gradle/
build/
run/
out/
.idea/
*.iml
.vscode/
bin/
```

- [ ] **Step 6: 写 PartyGame.java**

```java
package com.partygame;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

// 模组主类：仅负责注册，游戏逻辑挂在 GameManager 与事件监听器上
@Mod(PartyGame.MODID)
public class PartyGame {
    public static final String MODID = "partygame";

    public PartyGame(IEventBus modEventBus) {
        // 后续任务在此注册命令、配置与监听器
    }
}
```

- [ ] **Step 7: 写 neoforge.mods.toml**

```toml
modLoader = "javafml"
loaderVersion = "[3,)"

[[mods]]
modId = "partygame"
version = "${mod_version}"
displayName = "Party Game"

[[dependencies]]
modId = "neoforge"
type = "required"
versionRange = "[21.11.0,)"
ordering = "NONE"
side = "BOTH"

[[dependencies]]
modId = "minecraft"
type = "required"
versionRange = "[1.21.11,1.22)"
ordering = "NONE"
side = "BOTH"
```

- [ ] **Step 8: 复制 gradle wrapper 文件**

从模板仓库下载 `gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar`、`gradle/wrapper/gradle-wrapper.properties` 四个文件放入对应位置（wrapper jar 无法手写，必须下载）。

- [ ] **Step 9: 首次构建验证（用户执行，耗时 15-60 分钟）**

```bash
cd D:\Projects\MCmod
.\gradlew.bat build
```

预期：`BUILD SUCCESSFUL`，`build/libs/partygame-0.1.0.jar` 生成。若报 Gradle 找不到 JDK，在 IntelliJ 中打开项目，`File > Project Structure > SDK` 选 JDK 21，`Settings > Build Tools > Gradle > Gradle JVM` 选 JDK 21 后重试。

- [ ] **Step 10: 冒烟运行客户端（用户执行）**

```bash
.\gradlew.bat runClient
```

预期：MC 1.21.11 窗口启动，标题栏无崩溃，进入主菜单即可关闭。首次启动需下载 MC 本体与资源。

- [ ] **Step 11: 提交（用户执行）**

```bash
git checkout -b feat/project-skeleton
git add .
git commit -m "feat(project): 搭建 NeoForge 21.11.45 项目骨架与构建配置"
git checkout main
git merge feat/project-skeleton
git branch -d feat/project-skeleton
```

---

### Task 2: 配置系统

**Files:**
- Create: `src/main/java/com/partygame/config/ModConfig.java`
- Create: `src/test/java/com/partygame/config/ModConfigTest.java`

**Interfaces:**
- Consumes: `PartyGame.MODID`
- Produces:
  - `ModConfig.load(Path configFile)` → 读取 JSON；文件不存在时写入默认值再返回
  - `ModConfig.durations()` → `Durations` record（`int preparingSeconds, playingSeconds, scoringSeconds`）
  - `ModConfig.targetScore()` → `int`
  - `ModConfig.arenaHalfSize()` / `ModConfig.arenaWallHeight()` → `int`
  - `ModConfig.taskPool()` → `List<TaskDefinition>`（TaskDefinition 在 Task 3 定义；本任务先用 JSON 原始数据，Task 3 接上类型）
  - `ModConfig.loot()` → `Loot` record（`int chestCount`、`List<String> weapons, armor, food`，元素为物品 id 如 `minecraft:iron_sword`）

**说明：** 运行时配置文件位于服务器目录 `config/partygame.json`（runServer 时在 `run/server/config/`）。用 MC 自带的 Gson 解析。

- [ ] **Step 1: 定义默认配置 JSON（内嵌在 ModConfig 中）**

```java
package com.partygame.config;

// 默认配置内容，首次运行时写入 config/partygame.json
public class DefaultConfig {
    public static final String JSON = """
        {
          "durations": { "preparingSeconds": 30, "playingSeconds": 180, "scoringSeconds": 10 },
          "targetScore": 5,
          "arena": { "halfSize": 15, "wallHeight": 3 },
          "taskPool": [
            { "id": "press_button",   "type": "MUST_DO",    "triggers": ["PRESS_BUTTON"],     "displayName": "按下竞技场内的按钮" },
            { "id": "stand_plate",    "type": "MUST_DO",    "triggers": ["STAND_PLATE_3S"],   "displayName": "在压力板上站满 3 秒" },
            { "id": "damage_player",  "type": "MUST_DO",    "triggers": ["HIT_PLAYER"],       "displayName": "对任意玩家造成一次伤害" },
            { "id": "eat_food",       "type": "MUST_DO",    "triggers": ["EAT_FOOD"],         "displayName": "吃一个食物" },
            { "id": "open_chest",     "type": "MUST_DO",    "triggers": ["OPEN_CHEST"],       "displayName": "打开一个箱子" },
            { "id": "no_jump",        "type": "FORBIDDEN",  "triggers": ["JUMP"],             "displayName": "不能跳跃" },
            { "id": "no_sprint",      "type": "FORBIDDEN",  "triggers": ["SPRINT"],           "displayName": "不能疾跑" },
            { "id": "no_attack",      "type": "FORBIDDEN",  "triggers": ["HIT_PLAYER"],       "displayName": "不能攻击任何玩家" },
            { "id": "no_open_chest",  "type": "FORBIDDEN",  "triggers": ["OPEN_CHEST"],       "displayName": "不能打开箱子" },
            { "id": "no_break_place", "type": "FORBIDDEN",  "triggers": ["BREAK_BLOCK", "PLACE_BLOCK"], "displayName": "不能破坏或放置方块" },
            { "id": "no_eat",         "type": "FORBIDDEN",  "triggers": ["EAT_FOOD"],         "displayName": "不能吃食物" },
            { "id": "no_chat",        "type": "FORBIDDEN",  "triggers": ["CHAT"],             "displayName": "不能在聊天发言" },
            { "id": "no_sneak",       "type": "FORBIDDEN",  "triggers": ["SNEAK"],            "displayName": "不能蹲下" }
          ],
          "loot": {
            "chestCount": 4,
            "weapons": ["minecraft:iron_sword", "minecraft:stone_sword"],
            "armor": ["minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots", "minecraft:iron_helmet"],
            "food": ["minecraft:bread", "minecraft:apple"]
          }
        }
        """;
}
```

- [ ] **Step 2: 写 ModConfig（Gson 解析 + 默认值回写）**

```java
package com.partygame.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// 模组配置：读取 config/partygame.json，缺失时写入默认配置
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final JsonObject root;

    private ModConfig(JsonObject root) {
        this.root = root;
    }

    // 配置文件不存在时先写入默认内容；解析失败时抛异常让服务器尽早暴露问题
    public static ModConfig load(Path configFile) {
        try {
            if (!Files.exists(configFile)) {
                Files.createDirectories(configFile.getParent());
                Files.writeString(configFile, DefaultConfig.JSON);
            }
            return new ModConfig(GSON.fromJson(Files.readString(configFile), JsonObject.class));
        } catch (Exception e) {
            throw new RuntimeException("partygame 配置读取失败: " + configFile, e);
        }
    }

    public int preparingSeconds() { return root.getAsJsonObject("durations").get("preparingSeconds").getAsInt(); }
    public int playingSeconds()  { return root.getAsJsonObject("durations").get("playingSeconds").getAsInt(); }
    public int scoringSeconds()  { return root.getAsJsonObject("durations").get("scoringSeconds").getAsInt(); }
    public int targetScore()     { return root.get("targetScore").getAsInt(); }
    public int arenaHalfSize()   { return root.getAsJsonObject("arena").get("halfSize").getAsInt(); }
    public int arenaWallHeight() { return root.getAsJsonObject("arena").get("wallHeight").getAsInt(); }
    public JsonObject taskPoolRaw() { return root.getAsJsonObject("taskPool"); }
    public int chestCount()      { return root.getAsJsonObject("loot").get("chestCount").getAsInt(); }
    public List<String> loot(String kind) {
        return root.getAsJsonObject("loot").getAsJsonArray(kind).asList().stream()
                .map(e -> e.getAsString()).toList();
    }
}
```

（Task 3 会把 `taskPoolRaw()` 换成强类型 `List<TaskDefinition> taskPool()`；本任务保持 JSON 原始形态。）

- [ ] **Step 3: 写配置解析测试**

```java
package com.partygame.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

// 验证默认配置能被完整解析且字段值符合设计
class ModConfigTest {

    @Test
    void defaultsLoadCorrectly() throws Exception {
        Path tempFile = Files.createTempFile("partygame-config-test", ".json");
        Files.deleteIfExists(tempFile); // load() 要求文件不存在才会写入默认值
        ModConfig config = ModConfig.load(tempFile);

        assertEquals(30, config.preparingSeconds());
        assertEquals(180, config.playingSeconds());
        assertEquals(10, config.scoringSeconds());
        assertEquals(5, config.targetScore());
        assertEquals(15, config.arenaHalfSize());
        assertEquals(3, config.arenaWallHeight());
        assertEquals(4, config.chestCount());
        assertEquals(2, config.loot("weapons").size());
        assertEquals(4, config.loot("armor").size());
    }
}
```

> 验证点：若 Gson 不在测试类路径（ModDevGradle 通常会把 MC 依赖透传给 test），在 build.gradle 加 `testImplementation 'com.google.gson:gson:2.11.0'` 后重试。

- [ ] **Step 4: 运行测试验证通过**

Run: `./gradlew test`
Expected: PASS（`defaultsLoadCorrectly` 通过）

- [ ] **Step 5: 提交（用户执行）**

```bash
git checkout -b feat/config
git add .
git commit -m "feat(config): 新增 JSON 配置系统与默认配置"
git checkout main
git merge feat/config
git branch -d feat/config
```

---

### Task 3: 任务领域模型与分配器

**Files:**
- Create: `src/main/java/com/partygame/task/TaskType.java`
- Create: `src/main/java/com/partygame/task/TriggerType.java`
- Create: `src/main/java/com/partygame/task/TaskDefinition.java`
- Create: `src/main/java/com/partygame/task/TaskPool.java`
- Create: `src/main/java/com/partygame/task/TaskAssigner.java`
- Test: `src/test/java/com/partygame/task/TaskAssignerTest.java`
- Modify: `src/main/java/com/partygame/config/ModConfig.java`（`taskPoolRaw()` 替换为强类型 `taskPool()`）

**Interfaces:**
- Consumes: `ModConfig.taskPoolRaw()`（本任务内替换）
- Produces:
  - `TaskType` 枚举：`MUST_DO`、`FORBIDDEN`
  - `TriggerType` 枚举：`JUMP, SPRINT, HIT_PLAYER, OPEN_CHEST, BREAK_BLOCK, PLACE_BLOCK, EAT_FOOD, CHAT, SNEAK, PRESS_BUTTON, STAND_PLATE_3S`
  - `record TaskDefinition(String id, TaskType type, Set<TriggerType> triggers, String displayName)`
  - `TaskPool(List<TaskDefinition> all)`：`mustDos()`、`forbiddens()`
  - `TaskAssigner.assign(TaskPool pool, int playerCount, Random random)` → `List<Assignment>`
  - `record Assignment(TaskDefinition mustDo, List<TaskDefinition> forbiddens)`（forbiddens 恒为 2 个，**有序**，第一个为初始激活）

- [ ] **Step 1: 写枚举与数据模型**

```java
package com.partygame.task;

// 任务种类：必做任务只有自己可见；禁忌任务自己不可见、他人可见
public enum TaskType {
    MUST_DO,
    FORBIDDEN
}
```

```java
package com.partygame.task;

// 可被系统检测的玩家行为，事件监听器把 MC 事件映射为这些触发类型
public enum TriggerType {
    JUMP,
    SPRINT,
    HIT_PLAYER,
    OPEN_CHEST,
    BREAK_BLOCK,
    PLACE_BLOCK,
    EAT_FOOD,
    CHAT,
    SNEAK,
    PRESS_BUTTON,
    STAND_PLATE_3S
}
```

```java
package com.partygame.task;

import java.util.Set;

// 一条任务定义（来自配置）：多数任务只有一个触发类型，
// "不能破坏或放置方块"这类复合禁忌有多个
public record TaskDefinition(String id, TaskType type, Set<TriggerType> triggers, String displayName) {
}
```

```java
package com.partygame.task;

import java.util.List;

// 任务池：按种类过滤后的任务列表
public record TaskPool(List<TaskDefinition> all) {
    public List<TaskDefinition> mustDos() {
        return all.stream().filter(t -> t.type() == TaskType.MUST_DO).toList();
    }

    public List<TaskDefinition> forbiddens() {
        return all.stream().filter(t -> t.type() == TaskType.FORBIDDEN).toList();
    }
}
```

- [ ] **Step 2: 写分配器（纯 Java，无 MC 依赖）**

```java
package com.partygame.task;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

// 每回合为所有玩家分配任务：
// 1 件必做 + 2 件互不相同且与必做无触发冲突的禁忌
public class TaskAssigner {

    // 冲突定义：禁忌的触发类型与必做的触发类型有交集时不可分给同一人
    // （如"不能攻击"与"对玩家造成伤害"都依赖 HIT_PLAYER）
    private static boolean conflicts(TaskDefinition mustDo, TaskDefinition forbidden) {
        Set<TriggerType> overlap = new HashSet<>(mustDo.triggers());
        overlap.retainAll(forbidden.triggers());
        return !overlap.isEmpty();
    }

    public static List<Assignment> assign(TaskPool pool, int playerCount, Random random) {
        List<TaskDefinition> mustDos = pool.mustDos();
        List<TaskDefinition> forbiddens = pool.forbiddens();
        if (mustDos.isEmpty() || forbiddens.size() < 2) {
            throw new IllegalStateException("任务池不足：至少 1 个必做与 2 个禁忌");
        }

        List<Assignment> result = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            TaskDefinition mustDo = mustDos.get(random.nextInt(mustDos.size()));
            // 先筛掉与必做冲突的禁忌，再抽 2 个互不相同的
            List<TaskDefinition> candidates = forbiddens.stream()
                    .filter(f -> !conflicts(mustDo, f))
                    .toList();
            if (candidates.size() < 2) {
                throw new IllegalStateException("与必做任务不冲突的禁忌不足 2 个，请检查任务池配置");
            }
            int first = random.nextInt(candidates.size());
            int second;
            do {
                second = random.nextInt(candidates.size());
            } while (second == first);

            result.add(new Assignment(mustDo, List.of(candidates.get(first), candidates.get(second))));
        }
        return result;
    }
}
```

```java
package com.partygame.task;

import java.util.List;

// 一名玩家一回合的任务：forbiddens 恒为 2 个，第一个是初始激活的禁忌
public record Assignment(TaskDefinition mustDo, List<TaskDefinition> forbiddens) {
}
```

- [ ] **Step 3: 改 ModConfig 接入强类型任务池**

把 `ModConfig.java` 中的 `taskPoolRaw()` 替换为：

```java
public List<TaskDefinition> taskPool() {
    return root.getAsJsonArray("taskPool").asList().stream()
            .map(e -> {
                JsonObject o = e.getAsJsonObject();
                Set<TriggerType> triggers = o.getAsJsonArray("triggers").asList().stream()
                        .map(t -> TriggerType.valueOf(t.getAsString()))
                        .collect(java.util.stream.Collectors.toSet());
                return new TaskDefinition(
                        o.get("id").getAsString(),
                        TaskType.valueOf(o.get("type").getAsString()),
                        Set.copyOf(triggers),
                        o.get("displayName").getAsString());
            })
            .toList();
}
```

（同时删除 `taskPoolRaw()` 方法并补 import。）

- [ ] **Step 4: 写分配器测试**

```java
package com.partygame.task;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

// 验证分配结果满足全部约束：每人 1 必做 + 2 禁忌、禁忌互异、无触发冲突
class TaskAssignerTest {

    private static final TaskDefinition PRESS = new TaskDefinition("press_button", TaskType.MUST_DO, Set.of(TriggerType.PRESS_BUTTON), "按下按钮");
    private static final TaskDefinition DAMAGE = new TaskDefinition("damage_player", TaskType.MUST_DO, Set.of(TriggerType.HIT_PLAYER), "对玩家造成伤害");
    private static final TaskDefinition NO_JUMP = new TaskDefinition("no_jump", TaskType.FORBIDDEN, Set.of(TriggerType.JUMP), "不能跳跃");
    private static final TaskDefinition NO_ATTACK = new TaskDefinition("no_attack", TaskType.FORBIDDEN, Set.of(TriggerType.HIT_PLAYER), "不能攻击");
    private static final TaskDefinition NO_EAT = new TaskDefinition("no_eat", TaskType.FORBIDDEN, Set.of(TriggerType.EAT_FOOD), "不能吃食物");

    @Test
    void assignsOneMustDoAndTwoForbiddensPerPlayer() {
        TaskPool pool = new TaskPool(List.of(PRESS, DAMAGE, NO_JUMP, NO_ATTACK, NO_EAT));
        List<Assignment> result = TaskAssigner.assign(pool, 100, new Random(42));
        assertEquals(100, result.size());
        for (Assignment a : result) {
            assertNotNull(a.mustDo());
            assertEquals(TaskType.MUST_DO, a.mustDo().type());
            assertEquals(2, a.forbiddens().size());
            assertNotEquals(a.forbiddens().get(0), a.forbiddens().get(1)); // 两件禁忌互异
        }
    }

    @Test
    void neverAssignsConflictingForbiddenWithMustDo() {
        // DAMAGE 与 NO_ATTACK 都依赖 HIT_PLAYER，任何玩家的两件禁忌中都不应出现 NO_ATTACK
        TaskPool pool = new TaskPool(List.of(DAMAGE, PRESS, NO_JUMP, NO_ATTACK, NO_EAT));
        for (int i = 0; i < 500; i++) {
            for (Assignment a : TaskAssigner.assign(pool, 8, new Random(i))) {
                if (a.mustDo() == DAMAGE) {
                    for (TaskDefinition f : a.forbiddens()) {
                        assertFalse(f.triggers().contains(TriggerType.HIT_PLAYER),
                                "必做为造成伤害时不应分到依赖攻击的禁忌");
                    }
                }
            }
        }
    }

    @Test
    void throwsWhenPoolTooSmall() {
        TaskPool pool = new TaskPool(List.of(PRESS, NO_JUMP));
        assertThrows(IllegalStateException.class, () -> TaskAssigner.assign(pool, 1, new Random()));
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `./gradlew test`
Expected: 3 个测试全部 PASS

- [ ] **Step 6: 提交（用户执行）**

```bash
git checkout -b feat/task-model
git add .
git commit -m "feat(task): 新增任务数据模型与带冲突约束的分配器"
git checkout main
git merge feat/task-model
git branch -d feat/task-model
```

---

### Task 4: GameManager 状态机

**Files:**
- Create: `src/main/java/com/partygame/game/GamePhase.java`
- Create: `src/main/java/com/partygame/game/PlayerState.java`
- Create: `src/main/java/com/partygame/game/GameManager.java`
- Modify: `src/main/java/com/partygame/PartyGame.java`（构造器里初始化 GameManager）

**Interfaces:**
- Consumes: `ModConfig`、`TaskPool`、`TaskAssigner`、`PartyGame.MODID`
- Produces（后续任务依赖的全部入口）:
  - `GameManager.get()` → 单例
  - `void setArenaCenter(BlockPos center)` / `boolean isArenaSet()`
  - `void startGame(ServerLevel level)` / `void stopGame()`
  - `GamePhase phase()`
  - `void onTrigger(ServerPlayer player, TriggerType type)`
  - `void onPlayerDeath(ServerPlayer player)`
  - `void onPlayerLeave(ServerPlayer player)`
  - `PlayerState stateOf(ServerPlayer player)`
  - `List<PlayerState> allStates()`（供计分板）
  - `void setPlatePos(BlockPos pos)`（Task 5 竞技场生成后写入，供 STAND_PLATE_3S 判定）
  - `BlockPos platePos()`

- [ ] **Step 1: 写 GamePhase 与 PlayerState**

```java
package com.partygame.game;

// 游戏阶段：状态机的一次状态；计时结束自动切换到下一阶段
public enum GamePhase {
    IDLE,       // 未开局
    PREPARING,  // 准备期：抢装备，玩家间攻击禁用
    PLAYING,    // 对局：任务判定与 PVP 生效
    SCORING,    // 结算：公告结果与加分
    FINISHED    // 游戏结束：宣布胜者
}
```

```java
package com.partygame.game;

import com.partygame.task.Assignment;
import com.partygame.task.TaskDefinition;
import com.partygame.task.TriggerType;

import java.util.List;
import java.util.Set;

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

    // 新回合：任务重新分配、状态清零，分数保留
    public void resetRound(Assignment newAssignment) {
        this.assignment = newAssignment;
        this.activeForbiddenIndex = 0;
        this.mustDoDone = false;
        this.alive = true;
        this.plateStandTicks = 0;
    }

    public int plateStandTicks() { return plateStandTicks; }
    public void addPlateStandTick() { plateStandTicks++; }
    public void resetPlateStandTicks() { plateStandTicks = 0; }

    public boolean triggersMatch(TriggerType type) {
        return activeForbidden().triggers().contains(type);
    }
}
```

- [ ] **Step 2: 写 GameManager**

```java
package com.partygame.game;

import com.partygame.config.ModConfig;
import com.partygame.task.Assignment;
import com.partygame.task.TaskAssigner;
import com.partygame.task.TaskDefinition;
import com.partygame.task.TaskPool;
import com.partygame.task.TriggerType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import com.partygame.PartyGame;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

// 游戏核心状态机：唯一事实来源。事件监听器只把信号转发到这里。
public class GameManager {
    private static final GameManager INSTANCE = new GameManager();
    public static GameManager get() { return INSTANCE; }

    private ModConfig config;
    private GamePhase phase = GamePhase.IDLE;
    private int countdownTicks;               // 当前阶段剩余 tick（每秒 20 tick）
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private BlockPos arenaCenter;
    private BlockPos platePos;
    private final Random random = new Random();
    private ServerLevel currentLevel;

    private GameManager() {}

    // 服务器启动时加载配置（tick 监听由下方静态订阅器注册）
    public void init(ModConfig config) {
        this.config = config;
    }

    // ServerTickEvent 属于 GAME 总线，用静态订阅器转发到单例
    @EventBusSubscriber(modid = PartyGame.MODID, bus = EventBusSubscriber.Bus.GAME)
    private static class TickSubscriber {
        @SubscribeEvent
        public static void onTick(ServerTickEvent.Post event) {
            INSTANCE.onServerTick(event);
        }
    }

    // ---------- 供命令调用 ----------

    public boolean isArenaSet() { return arenaCenter != null; }
    public void setArenaCenter(BlockPos center) { this.arenaCenter = center; }
    public BlockPos arenaCenter() { return arenaCenter; }
    public void setPlatePos(BlockPos pos) { this.platePos = pos; }
    public BlockPos platePos() { return platePos; }
    public GamePhase phase() { return phase; }

    public void startGame(ServerLevel level) {
        if (phase != GamePhase.IDLE) throw new IllegalStateException("游戏已在进行中");
        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> !p.isSpectator()).toList();
        if (players.size() < 2) throw new IllegalStateException("至少需要 2 名玩家");
        currentLevel = level;
        beginRound();
    }

    public void stopGame() {
        phase = GamePhase.IDLE;
        states.clear();
        broadcast("游戏已终止");
    }

    // ---------- 回合流程 ----------

    private void beginRound() {
        List<ServerPlayer> players = currentLevel.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> !p.isSpectator()).toList();
        TaskPool pool = new TaskPool(config.taskPool());
        List<Assignment> assignments = TaskAssigner.assign(pool, players.size(), random);

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            PlayerState old = states.get(p.getUUID());
            int score = old == null ? 0 : old.score();
            PlayerState s = new PlayerState(assignments.get(i), score);
            states.put(p.getUUID(), s);
            // 传送进场、清背包、回生存模式
            p.setGameMode(GameType.SURVIVAL);
            p.getInventory().clearContent();
            p.teleportTo(currentLevel,
                    arenaCenter.getX() + 0.5, arenaCenter.getY() + 1, arenaCenter.getZ() + 0.5,
                    p.getYRot(), p.getXRot());
            p.sendSystemMessage(Component.literal("你的必做任务：§a" + s.mustDo().displayName()));
            // 大标题提示（验证点：displayTitle 可用性，不可用则仅保留聊天提示）
            try {
                p.displayTitle(Component.literal("你的必做任务"), Component.literal("§a" + s.mustDo().displayName()), 10, 70, 20, true);
            } catch (NoSuchMethodError ignored) { }
        }
        enterPhase(GamePhase.PREPARING);
        broadcast("§6准备期开始（30 秒）：抢夺装备，玩家间攻击禁用！");
    }

    private void enterPhase(GamePhase next) {
        phase = next;
        countdownTicks = switch (next) {
            case PREPARING -> config.preparingSeconds() * 20;
            case PLAYING -> config.playingSeconds() * 20;
            case SCORING -> config.scoringSeconds() * 20;
            default -> 0;
        };
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (phase == GamePhase.IDLE || phase == GamePhase.FINISHED) return;
        if (--countdownTicks > 0) return;

        switch (phase) {
            case PREPARING -> {
                enterPhase(GamePhase.PLAYING);
                broadcast("§a对局开始（180 秒）！完成必做任务，别触犯禁忌！");
            }
            case PLAYING -> endRound();
            case SCORING -> {
                if (anyScoreReached(config.targetScore())) {
                    finishGame();
                } else {
                    beginRound();
                }
            }
            default -> { }
        }
    }

    private void endRound() {
        // 超时未完成必做的出局
        for (PlayerState s : states.values()) {
            if (s.isAlive() && !s.isMustDoDone()) {
                s.eliminate();
                ServerPlayer p = playerOf(s);
                if (p != null) broadcast("§c" + p.getGameProfile().getName() + " 超时未完成必做任务，出局");
            }
        }
        // 幸存者加分
        for (PlayerState s : states.values()) {
            if (s.isAlive()) {
                s.addScore(1);
                ServerPlayer p = playerOf(s);
                if (p != null) broadcast("§a" + p.getGameProfile().getName() + " 存活 +1 分（当前 " + s.score() + " 分）");
            }
        }
        enterPhase(GamePhase.SCORING);
    }

    private boolean anyScoreReached(int target) {
        return states.values().stream().anyMatch(s -> s.score() >= target);
    }

    private void finishGame() {
        PlayerState winner = states.values().stream()
                .max((a, b) -> Integer.compare(a.score(), b.score())).orElse(null);
        if (winner != null) {
            ServerPlayer p = playerOf(winner);
            broadcast("§6" + (p != null ? p.getGameProfile().getName() : "?") + " 率先达到 "
                    + config.targetScore() + " 分，获得胜利！");
        }
        phase = GamePhase.FINISHED;
    }

    // ---------- 供事件监听器调用 ----------

    public void onTrigger(ServerPlayer player, TriggerType type) {
        if (phase != GamePhase.PLAYING) return;
        PlayerState s = states.get(player.getUUID());
        if (s == null || !s.isAlive()) return;

        // 必做任务判定
        if (!s.isMustDoDone() && s.mustDo().triggers().contains(type)) {
            s.completeMustDo();
            player.sendSystemMessage(Component.literal("§a必做任务已完成！"));
        }
        // 禁忌判定
        if (s.triggersMatch(type)) {
            TaskDefinition broken = s.activeForbidden();
            s.triggerForbidden(broken);
            if (s.remainingLives() == 0) {
                eliminate(player, "触犯了禁忌：" + broken.displayName());
            } else {
                player.sendSystemMessage(Component.literal(
                        "§c你触犯了禁忌：§l" + broken.displayName() + "§r§c！剩余一条命。"));
                broadcast(player.getGameProfile().getName() + " 触犯了一条禁忌");
            }
        }
    }

    public void onPlayerDeath(ServerPlayer player) {
        PlayerState s = states.get(player.getUUID());
        if (s == null || !s.isAlive()) return;
        eliminate(player, "死亡");
    }

    public void onPlayerLeave(ServerPlayer player) {
        PlayerState s = states.get(player.getUUID());
        if (s != null && s.isAlive()) {
            s.eliminate();
            broadcast(player.getGameProfile().getName() + " 掉线，视作出局");
        }
        if (phase != GamePhase.IDLE && states.values().stream().noneMatch(PlayerState::isAlive)) {
            stopGame(); // 全员掉线，强制回 IDLE
        }
    }

    private void eliminate(ServerPlayer player, String reason) {
        PlayerState s = states.get(player.getUUID());
        if (s == null) return;
        s.eliminate();
        broadcast("§c" + player.getGameProfile().getName() + " 出局了（" + reason + "）");
        player.setGameMode(GameType.SPECTATOR);
    }

    // ---------- 查询 ----------

    public PlayerState stateOf(ServerPlayer player) { return states.get(player.getUUID()); }
    public List<PlayerState> allStates() { return new ArrayList<>(states.values()); }

    private ServerPlayer playerOf(PlayerState s) {
        if (currentLevel == null) return null;
        return currentLevel.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> states.get(p.getUUID()) == s).findFirst().orElse(null);
    }

    private void broadcast(String message) {
        if (currentLevel != null) {
            currentLevel.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal(message), false);
        }
    }
}
```

> 注意：`GameManager` 挂 tick 监听用的是 `modBus.addListener(this::onServerTick)`；若实现时发现 ServerTickEvent 需要挂游戏总线（GAME bus），改用 `@EventBusSubscriber` 静态方式注册到 `EventBusSubscriber.Bus.GAME`，方法体不变。

- [ ] **Step 3: PartyGame 构造器接入**

```java
public PartyGame(IEventBus modEventBus) {
    GameManager.get().init(ModConfig.load(Paths.get("config", "partygame.json")));
}
```

> 注意：mod 加载时的工作目录是运行目录（runServer 时是 `run/server/`），`Paths.get("config", "partygame.json")` 会落到 `run/server/config/partygame.json`，符合 NeoForge 配置惯例。

- [ ] **Step 4: 编译验证**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL（若 `displayTitle` 签名不符，按 IDE 提示改参数；若 ServerTickEvent 订阅方式报错，按 Task 4 注意项调整）

- [ ] **Step 5: 提交（用户执行）**

```bash
git checkout -b feat/game-manager
git add .
git commit -m "feat(game): 新增回合状态机与玩家状态管理"
git checkout main
git merge feat/game-manager
git branch -d feat/game-manager
```

---

### Task 5: 竞技场生成器

**Files:**
- Create: `src/main/java/com/partygame/arena/ArenaGenerator.java`
- Modify: `src/main/java/com/partygame/game/GameManager.java`（`beginRound()` 中调用生成器并记录 platePos）

**Interfaces:**
- Consumes: `ModConfig.arenaHalfSize()` / `arenaWallHeight()` / `chestCount()` / `loot(...)`、`GameManager.setPlatePos()`
- Produces: `ArenaGenerator.generate(ServerLevel level, BlockPos center, ModConfig config, RandomSource random)`（静态方法，供 GameTest 直接调用）

- [ ] **Step 1: 写 ArenaGenerator**

```java
package com.partygame.arena;

import com.partygame.config.ModConfig;
import com.partygame.game.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// 以 center 为中心生成固定尺寸的竞技场：石砖地板、围墙、按钮、压力板、装备箱
// 覆盖原有方块且不恢复，调用前需确认场地空旷
public class ArenaGenerator {

    public static void generate(ServerLevel level, BlockPos center, ModConfig config, RandomSource random) {
        int half = config.arenaHalfSize();
        int wallHeight = config.arenaWallHeight();
        int floorY = center.getY() - 1;
        int minX = center.getX() - half, maxX = center.getX() + half;
        int minZ = center.getZ() - half, maxZ = center.getZ() + half;

        BlockState floor = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();

        // 地板与围墙
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlockAndUpdate(new BlockPos(x, floorY, z), floor);
                boolean isBorder = x == minX || x == maxX || z == minZ || z == maxZ;
                if (isBorder) {
                    for (int y = 1; y <= wallHeight; y++) {
                        level.setBlockAndUpdate(new BlockPos(x, floorY + y, z), wall);
                    }
                }
            }
        }

        // 北墙内侧按钮（墙上 1 格）
        level.setBlockAndUpdate(new BlockPos(center.getX(), floorY + 1, minZ),
                Blocks.STONE_BUTTON.defaultBlockState());

        // 东南角地面压力板
        BlockPos plate = new BlockPos(center.getX() + half - 2, floorY + 1, center.getZ() + half - 2);
        level.setBlockAndUpdate(plate, Blocks.STONE_PRESSURE_PLATE.defaultBlockState());

        // 装备箱：分布在场内四分之一处
        int offset = half / 2;
        BlockPos[] chestSpots = {
                new BlockPos(center.getX() + offset, floorY + 1, center.getZ() + offset),
                new BlockPos(center.getX() + offset, floorY + 1, center.getZ() - offset),
                new BlockPos(center.getX() - offset, floorY + 1, center.getZ() + offset),
                new BlockPos(center.getX() - offset, floorY + 1, center.getZ() - offset)
        };
        for (int i = 0; i < Math.min(config.chestCount(), chestSpots.length); i++) {
            placeLootChest(level, chestSpots[i], config, random);
        }

        // 保存压力板位置供 GameManager 判定 STAND_PLATE_3S
        GameManager.get().setPlatePos(plate);
    }

    private static void placeLootChest(ServerLevel level, BlockPos pos, ModConfig config, RandomSource random) {
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            // 每箱：1 件武器 + 2 件护甲 + 4 个食物，随机抽取
            chest.setItem(0, new ItemStack(randomItem(level, config.loot("weapons"), random)));
            chest.setItem(1, new ItemStack(randomItem(level, config.loot("armor"), random)));
            chest.setItem(2, new ItemStack(randomItem(level, config.loot("armor"), random)));
            for (int i = 0; i < 4; i++) {
                chest.setItem(3 + i, new ItemStack(randomItem(level, config.loot("food"), random)));
            }
        }
    }

    private static net.minecraft.world.item.Item randomItem(ServerLevel level, java.util.List<String> ids, RandomSource random) {
        String id = ids.get(random.nextInt(ids.size()));
        // 用内置注册表按 id 查找；找不到时退回石头（异常 id 不应静默通过，但避免崩服）
        return net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getOptional(net.minecraft.resources.ResourceLocation.parse(id))
                .orElse(Items.STONE);
    }
}
```

- [ ] **Step 2: GameManager.beginRound() 接入生成器**

在 `beginRound()` 中"传送进场"之前插入：

```java
// 每回合重建竞技场（覆盖区域不恢复），并重新摆放装备箱
ArenaGenerator.generate(currentLevel, arenaCenter, config, currentLevel.getRandom());
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL（`ChestBlockEntity`、`setBlockAndUpdate` 等按 IDE 提示修正方法名/参数）

- [ ] **Step 4: 提交（用户执行）**

```bash
git checkout -b feat/arena
git add .
git commit -m "feat(arena): 新增程序生成的固定尺寸竞技场与装备箱"
git checkout main
git merge feat/arena
git branch -d feat/arena
```

---

### Task 6: 事件监听器

**Files:**
- Create: `src/main/java/com/partygame/event/GameEventListeners.java`

**Interfaces:**
- Consumes: `GameManager.get().onTrigger(...)`、`onPlayerDeath(...)`、`onPlayerLeave(...)`、`platePos()`、`stateOf(...)`、`phase()`
- Produces: 全部 MC 事件 → 触发信号的映射（无对外接口，仅副作用）

- [ ] **Step 1: 写 GameEventListeners**

```java
package com.partygame.event;

import com.partygame.PartyGame;
import com.partygame.game.GameManager;
import com.partygame.game.GamePhase;
import com.partygame.game.PlayerState;
import com.partygame.task.TriggerType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHurtEvent;
import net.neoforged.neoforge.event.entity.living.LivingJumpEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;

// 把 MC 事件映射为任务触发信号；全部判定交给 GameManager
@EventBusSubscriber(modid = PartyGame.MODID, bus = EventBusSubscriber.Bus.GAME)
public class GameEventListeners {

    private static GameManager gm() { return GameManager.get(); }

    // 存活且在对局中的玩家才参与判定
    private static boolean inPlay(ServerPlayer p) {
        if (p == null || gm().phase() != GamePhase.PLAYING) return false;
        PlayerState s = gm().stateOf(p);
        return s != null && s.isAlive();
    }

    // 跳跃 → JUMP
    @SubscribeEvent
    public static void onJump(LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && inPlay(p)) {
            gm().onTrigger(p, TriggerType.JUMP);
        }
    }

    // 玩家间伤害 → HIT_PLAYER；非对局阶段禁用玩家间攻击
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Player attacker
                && event.getEntity() instanceof Player victim
                && attacker != victim) {
            if (gm().phase() != GamePhase.PLAYING) {
                event.setCanceled(true); // 准备期/结算期玩家间攻击禁用
            } else if (attacker instanceof ServerPlayer ap && inPlay(ap)) {
                gm().onTrigger(ap, TriggerType.HIT_PLAYER);
            }
        }
    }

    // 死亡 → 出局（任何死因）
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            gm().onPlayerDeath(p);
        }
    }

    // 游戏进行中死亡不掉落物品
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (gm().phase() != GamePhase.IDLE && event.getEntity() instanceof Player) {
            event.setCanceled(true);
        }
    }

    // 破坏 / 放置
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && inPlay(p)) {
            gm().onTrigger(p, TriggerType.BREAK_BLOCK);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && inPlay(p)) {
            gm().onTrigger(p, TriggerType.PLACE_BLOCK);
        }
    }

    // 打开箱子
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer p && inPlay(p)
                && event.getContainer() != null
                && event.getContainer().getContainerBlockState() != null
                && event.getContainer().getContainerBlockState().getBlock() instanceof ChestBlock) {
            gm().onTrigger(p, TriggerType.OPEN_CHEST);
        }
    }

    // 吃食物（完成食用动作时触发）
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer p && inPlay(p)
                && event.getItem().isEdible()) {
            gm().onTrigger(p, TriggerType.EAT_FOOD);
        }
    }

    // 聊天发言
    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && inPlay(p)) {
            gm().onTrigger(p, TriggerType.CHAT);
        }
    }

    // 疾跑 / 蹲下 / 压力板计时
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !inPlay(p)) return;
        if (p.isSprinting()) {
            gm().onTrigger(p, TriggerType.SPRINT);
        }
        if (p.isShiftKeyDown()) {
            gm().onTrigger(p, TriggerType.SNEAK);
        }
        // 站在竞技场压力板上累计 60 tick（3 秒）
        PlayerState s = gm().stateOf(p);
        if (gm().platePos() != null
                && p.blockPosition().below().equals(gm().platePos())) {
            s.addPlateStandTick();
            if (s.plateStandTicks() >= 60) {
                s.resetPlateStandTicks();
                gm().onTrigger(p, TriggerType.STAND_PLATE_3S);
            }
        } else {
            s.resetPlateStandTicks();
        }
    }

    // 按下按钮
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer p && inPlay(p)
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ButtonBlock) {
            gm().onTrigger(p, TriggerType.PRESS_BUTTON);
        }
    }

    // 掉线视作出局
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            gm().onPlayerLeave(p);
        }
    }
}
```

> 验证点：本任务开始前按"全局验证点"表在 IDE 里逐项确认事件类名与包路径（`LivingJumpEvent`、`LivingHurtEvent`、`PlayerContainerEvent.Open`、`LivingEntityUseItemEvent.Finish`、`PlayerInteractEvent.RightClickBlock`、`BlockEvent` 子类），编译错误按 IDE 提示替换。行为不得偏离：每个事件映射到注释标明的 TriggerType。

- [ ] **Step 2: 编译验证**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交（用户执行）**

```bash
git checkout -b feat/event-listeners
git add .
git commit -m "feat(event): 新增事件监听器与任务触发映射"
git checkout main
git merge feat/event-listeners
git branch -d feat/event-listeners
```

---

### Task 7: 计分板与提示 UI

**Files:**
- 分支 B（推荐）：Create `src/main/java/com/partygame/ui/ScoreboardManager.java`（个性化视图打包与下发）、`src/main/java/com/partygame/network/SyncStatesPayload.java`、`src/main/java/com/partygame/client/PartyHud.java`
- 分支 A（妥协）：Create `src/main/java/com/partygame/ui/ScoreboardManager.java`（原版侧边栏）
- Modify: `src/main/java/com/partygame/PartyGame.java`（分支 B 注册 payload）、`src/main/java/com/partygame/game/GameManager.java`（状态变化处调用刷新，两分支共用）

**Interfaces:**
- Consumes: `GameManager.allStates()`、`playerNameOf(...)`、`stateOf(...)`、`phase()`
- Produces: `ScoreboardManager.refresh(MinecraftServer server)`（状态变化时调用）

**前提决策（2026-08-18 已与用户确认）：** 原版侧边栏是服务器全局状态，无法按 viewer 区分显示。**已选定分支 B（自定义客户端 HUD）**：执行 Step 3，跳过 Step 4（分支 A 仅保留作参考）。

- **分支 B（推荐）：自定义客户端 HUD。** 服务端为每位玩家构建个性化视图（他人的禁忌 + 自己的 ??? 行），通过自定义网络包发给对应玩家，客户端渲染仿侧边栏列表。完全符合规格，且为 v2 头顶悬浮文字打基础。代价：新增 1 个网络包 + 1 个客户端渲染类。
- **分支 A（妥协）：原版侧边栏，所有人看到相同内容（含自己的禁忌）。** 最简，但破坏"自己看不见自己的禁忌"这条核心规则。

- [ ] **Step 1: 向用户确认分支 B 或 A，选定后执行对应分支**

- [ ] **Step 2: GameManager 补充 `playerNameOf` 与刷新调用（两分支共用）**

`GameManager` 中：

```java
public String playerNameOf(PlayerState s) {
    if (currentLevel == null) return "?";
    return currentLevel.getServer().getPlayerList().getPlayers().stream()
            .filter(p -> states.get(p.getUUID()) == s)
            .findFirst().map(p -> p.getGameProfile().getName()).orElse("?");
}
```

在 `enterPhase()` 末尾、`onTrigger()` 末尾、`eliminate()` 末尾、`endRound()` 末尾各加一行：

```java
if (currentLevel != null) ScoreboardManager.refresh(currentLevel.getServer());
```

- [ ] **Step 3（分支 B）: 写网络包与客户端 HUD**

`SyncStatesPayload.java`：

```java
package com.partygame.network;

import com.partygame.PartyGame;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

// 服务端 → 客户端：个性化分数/禁忌视图
public record SyncStatesPayload(List<Row> rows) implements CustomPacketPayload {
    public static final Type<SyncStatesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PartyGame.MODID, "sync_states"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncStatesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    SyncStatesPayload::rows,
                    SyncStatesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // 一行数据：名字、禁忌文本（自己为 ???）、剩余命数、分数、是否是自己
    public record Row(String name, String tabooText, int lives, int score, boolean self) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, Row::name,
                        ByteBufCodecs.STRING_UTF8, Row::tabooText,
                        ByteBufCodecs.INT, Row::lives,
                        ByteBufCodecs.INT, Row::score,
                        ByteBufCodecs.BOOL, Row::self,
                        Row::new);
    }
}
```

`ScoreboardManager.java`（分支 B：个性化视图构建与下发）：

```java
package com.partygame.ui;

import com.partygame.game.GameManager;
import com.partygame.game.GamePhase;
import com.partygame.game.PlayerState;
import com.partygame.network.SyncStatesPayload;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;

// 分支 B：为每位玩家构建个性化视图并下发。
// 自己的行禁忌显示 ???，他人的行显示其当前激活的禁忌；按分数降序
public class ScoreboardManager {

    public static void refresh(MinecraftServer server) {
        GameManager gm = GameManager.get();
        List<PlayerState> states = gm.allStates().stream()
                .sorted(Comparator.comparingInt(PlayerState::score).reversed())
                .toList();

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            List<SyncStatesPayload.Row> rows = states.stream()
                    .map(s -> {
                        boolean self = gm.stateOf(viewer) == s;
                        String taboo = gm.phase() == GamePhase.IDLE ? "—"
                                : (self ? "???" : s.activeForbidden().displayName());
                        return new SyncStatesPayload.Row(
                                gm.playerNameOf(s), taboo,
                                s.remainingLives(), s.score(), self);
                    })
                    .toList();
            viewer.connection.send(new ClientboundCustomPayloadPacket(new SyncStatesPayload(rows)));
        }
    }
}
```

`PartyHud.java`（客户端渲染）：

```java
package com.partygame.client;

import com.partygame.network.SyncStatesPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

// 客户端 HUD：渲染服务端下发的个性化分数/禁忌列表（仿右侧计分板）
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME, modid = "partygame")
public class PartyHud {
    private static List<SyncStatesPayload.Row> rows = List.of();

    public static void apply(List<SyncStatesPayload.Row> newRows) {
        rows = newRows;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || rows.isEmpty()) return;

        int x = event.getGuiGraphics().guiWidth() - 160;
        int y = 10;
        for (SyncStatesPayload.Row row : rows) {
            int color = colorOf(row.score());
            String line = row.name() + " " + row.tabooText() + " ♥".repeat(Math.max(0, row.lives()));
            event.getGuiGraphics().drawString(mc.font, line, x, y, color, true);
            y += 10;
        }
    }

    // 颜色按分数：0-1 白 / 2-3 黄 / 4 紫 / 5+ 金
    private static int colorOf(int score) {
        if (score >= 5) return 0xFFD700;
        if (score >= 4) return 0xAA00AA;
        if (score >= 2) return 0xFFFF00;
        return 0xFFFFFF;
    }
}
```

`PartyGame.java` 注册 payload（追加到构造器，与 Task 8 的 registerCommands 并存）：

```java
public PartyGame(IEventBus modEventBus) {
    GameManager.get().init(ModConfig.load(Paths.get("config", "partygame.json")));
    modEventBus.addListener(this::registerPayloads);
    // Task 8 会在此追加 registerCommands
}

private void registerPayloads(RegisterPayloadHandlersEvent event) {
    event.getRegistrar().playToClient(
            SyncStatesPayload.TYPE,
            SyncStatesPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                    PartyHud.apply(payload.rows())));
}
```

> 验证点：`RenderGuiEvent.Post` 在该版本可能已改名（如 `ClientEvents.RenderGui`），以 IDE 提示为准；`ClientboundCustomPayloadPacket` 若不存在则用 IDE 提示的等价发包方式；`guiWidth()` 若不存在则从窗口宽度计算。若担心专用服务器加载客户端类，可把 handler 的 lambda 移到一个 `@EventBusSubscriber(Dist.CLIENT)` 独立注册类中，逻辑不变。

- [ ] **Step 4（分支 A）: 原版侧边栏实现**

`ScoreboardManager.java`（分支 A：全局一致，自己的行同样显示真禁忌）：

```java
package com.partygame.ui;

import com.partygame.game.GameManager;
import com.partygame.game.GamePhase;
import com.partygame.game.PlayerState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.Comparator;
import java.util.List;

// 分支 A：原版侧边栏（妥协：所有人的行都显示真禁忌，含自己的）
public class ScoreboardManager {

    private static final String OBJECTIVE_NAME = "partygame";

    public static void refresh(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        Objective obj = sb.getObjective(OBJECTIVE_NAME);
        if (obj == null) {
            obj = sb.addObjective(OBJECTIVE_NAME, ObjectiveCriteria.DUMMY,
                    Component.literal("§6§l派对生存"), ObjectiveCriteria.RenderType.INTEGER, true, null);
        }
        sb.setDisplayObjective(1, obj); // 1 = SIDEBAR 显示位
        sb.getPlayerScores(obj).forEach(Score::remove);

        GameManager gm = GameManager.get();
        List<PlayerState> states = gm.allStates().stream()
                .sorted(Comparator.comparingInt(PlayerState::score).reversed())
                .toList();

        for (PlayerState s : states) {
            String taboo = gm.phase() == GamePhase.IDLE ? "—" : s.activeForbidden().displayName();
            String line = colorOf(s.score()) + gm.playerNameOf(s)
                    + " §7" + taboo
                    + " §c" + "♥".repeat(Math.max(0, s.remainingLives()));
            Score score = sb.getOrCreatePlayerScore(
                    net.minecraft.world.scores.ScoreHolder.forNameOnly(line), obj);
            score.set(s.score());
        }
    }

    // 颜色按分数：0-1 白 / 2-3 黄 / 4 紫 / 5+ 金
    private static String colorOf(int score) {
        if (score >= 5) return ChatFormatting.GOLD.toString();
        if (score >= 4) return ChatFormatting.LIGHT_PURPLE.toString();
        if (score >= 2) return ChatFormatting.YELLOW.toString();
        return ChatFormatting.WHITE.toString();
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL（客户端类报错时按 IDE 提示修正，不改变渲染行为）

- [ ] **Step 6: 提交（用户执行）**

```bash
git checkout -b feat/scoreboard
git add .
git commit -m "feat(ui): 新增分数/禁忌显示界面"
git checkout main
git merge feat/scoreboard
git branch -d feat/scoreboard
```

---

### Task 8: /party 命令

**Files:**
- Create: `src/main/java/com/partygame/command/PartyCommand.java`
- Modify: `src/main/java/com/partygame/PartyGame.java`（注册 RegisterCommandsEvent）

**Interfaces:**
- Consumes: `GameManager.setArenaCenter(...)`、`startGame(...)`、`stopGame()`、`isArenaSet()`、`allStates()`
- Produces: `/party setarena`、`/party start`、`/party stop`、`/party score` 四个命令

- [ ] **Step 1: 写 PartyCommand**

```java
package com.partygame.command;

import com.mojang.brigadier.CommandDispatcher;

import java.util.Comparator;
import java.util.List;

import com.partygame.game.GameManager;
import com.partygame.game.PlayerState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// /party 系列命令：setarena（站中心点）/ start / stop / score
public class PartyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("party")
                .then(Commands.literal("setarena")
                        .requires(cs -> cs.hasPermission(2))
                        .executes(ctx -> setArena(ctx.getSource())))
                .then(Commands.literal("start")
                        .requires(cs -> cs.hasPermission(2))
                        .executes(ctx -> start(ctx.getSource())))
                .then(Commands.literal("stop")
                        .requires(cs -> cs.hasPermission(2))
                        .executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("score")
                        .executes(ctx -> score(ctx.getSource()))));
    }

    private static int setArena(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer p)) {
            source.sendFailure(Component.literal("该命令需由玩家站在竞技场中心点执行"));
            return 0;
        }
        GameManager.get().setArenaCenter(p.blockPosition());
        source.sendSuccess(() -> Component.literal(
                "竞技场中心已设为 " + p.blockPosition().toShortString() + "（" +
                "下次 /party start 时以该点为中心生成场地，会覆盖原有方块）"), true);
        return 1;
    }

    private static int start(CommandSourceStack source) {
        GameManager gm = GameManager.get();
        if (!gm.isArenaSet()) {
            source.sendFailure(Component.literal("请先执行 /party setarena 设置竞技场中心"));
            return 0;
        }
        try {
            gm.startGame(source.getLevel());
            return 1;
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int stop(CommandSourceStack source) {
        GameManager.get().stopGame();
        source.sendSuccess(() -> Component.literal("游戏已终止"), true);
        return 1;
    }

    private static int score(CommandSourceStack source) {
        List<PlayerState> sorted = GameManager.get().allStates().stream()
                .sorted(Comparator.comparingInt(PlayerState::score).reversed())
                .toList();
        source.sendSuccess(() -> Component.literal("§6===== 分数榜 ====="), false);
        for (PlayerState s : sorted) {
            String name = GameManager.get().playerNameOf(s);
            source.sendSuccess(() -> Component.literal("§e" + name + " §7- " + s.score() + " 分"), false);
        }
        return 1;
    }
}
```

- [ ] **Step 2: 注册命令**

`PartyGame.java` 中：

```java
public PartyGame(IEventBus modEventBus) {
    GameManager.get().init(ModConfig.load(Paths.get("config", "partygame.json")));
    // 若 Task 7 选了分支 B，此处已有 registerPayloads 监听，保留并追加
    modEventBus.addListener(this::registerCommands);
}

private void registerCommands(RegisterCommandsEvent event) {
    PartyCommand.register(event.getDispatcher());
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交（用户执行）**

```bash
git checkout -b feat/commands
git add .
git commit -m "feat(command): 新增 /party 系列命令"
git checkout main
git merge feat/commands
git branch -d feat/commands
```

---

### Task 9: GameTest 竞技场结构测试

> **已跳过（2026-08-20 与用户确认）**：21.11.45 的 GameTest 框架已完全重写——`@GameTest`/`@GameTestHolder` 注解不存在，测试改为 TestFunctionLoader + RegisterGameTestsEvent + 结构模板 NBT 的注册方式，且测试必须锚定真实存在的结构模板（无模板报 `structure.failure`）。实现成本高、文档少，竞技场结构验证并入 Task 10 手测清单。若以后要自动化，按 CLAUDE.md 已知坑中的新框架要点实现。

**Files:**
- Create: `src/main/java/com/partygame/gametest/ArenaGameTests.java`

**Interfaces:**
- Consumes: `ArenaGenerator.generate(...)`、`ModConfig`（默认配置）、`PartyGame.MODID`
- Produces: 可在 `runGameTestServer` 中运行的竞技场结构断言

- [ ] **Step 1: 写 ArenaGameTests**

```java
package com.partygame.gametest;

import com.partygame.PartyGame;
import com.partygame.arena.ArenaGenerator;
import com.partygame.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Paths;

// 竞技场结构测试：断言生成结果的关键方块状态
@GameTestHolder(PartyGame.MODID)
public class ArenaGameTests {

    // 在空模板上直接调用生成器，再断言结构（生成器是纯坐标逻辑，可脱离命令测试）
    @GameTest(template = GameTest.EMPTY_STRUCTURE)
    public static void arenaHasFloorWallsButtonPlateAndChests(GameTestHelper helper) {
        ModConfig config = ModConfig.load(Paths.get("config", "partygame.json"));
        BlockPos center = helper.absolutePos(new BlockPos(0, 3, 0));
        int half = config.arenaHalfSize();

        ArenaGenerator.generate(helper.getLevel(), center, config, helper.getLevel().getRandom());

        // 地板：中心点下方必须是石砖
        helper.assertBlock(new BlockPos(0, -1, 0),
                b -> b.is(Blocks.STONE_BRICKS), "地板应为石砖");
        // 围墙：边界处应有 3 格高的墙
        helper.assertBlock(new BlockPos(-half, 0, 0),
                b -> b.is(Blocks.STONE_BRICKS), "北墙底格应为石砖");
        helper.assertBlock(new BlockPos(-half, 2, 0),
                b -> b.is(Blocks.STONE_BRICKS), "北墙顶格应为石砖");
        // 按钮与压力板
        helper.assertBlock(new BlockPos(0, 1, -half),
                b -> b.is(Blocks.STONE_BUTTON), "北墙应有按钮");
        helper.assertBlock(new BlockPos(half - 2, 0, half - 2),
                b -> b.is(Blocks.STONE_PRESSURE_PLATE), "东南应有压力板");
        // 装备箱：四分之一处应有箱子
        helper.assertBlock(new BlockPos(half / 2, 0, half / 2),
                b -> b.is(Blocks.CHEST), "应有装备箱");

        helper.succeed();
    }
}
```

> 验证点：`GameTest.EMPTY_STRUCTURE` 的常量名与包路径以 IDE 提示为准。坐标系规则（实现时执行）：`generate` 接收绝对坐标（经 `helper.absolutePos(...)` 转换后传入）；`assertBlock` 一律使用相对测试原点的坐标（不再包 absolutePos）。若 `@GameTestHolder` 未被自动注册，检查 `neoforge.mods.toml` 与 `runGameTestServer` 配置是否存在。

- [ ] **Step 2: 运行 GameTest 验证（用户执行，首次需下载测试服务器依赖）**

```bash
.\gradlew.bat runGameTestServer
```

Expected: 测试输出 `arenaHasFloorWallsButtonPlateAndChests` PASS，服务器自动退出，退出码 0。若失败，按报错坐标修正生成器或断言。

- [ ] **Step 3: 提交（用户执行）**

```bash
git checkout -b feat/gametest
git add .
git commit -m "test(arena): 新增竞技场结构 GameTest"
git checkout main
git merge feat/gametest
git branch -d feat/gametest
```

---

### Task 10: 联机手测与收尾

**Files:**
- Create: `docs/testing-checklist.md`（手动测试清单）

**Interfaces:**
- Consumes: 全部已实现功能
- Produces: 可玩的 0.1.0 版本 + 测试报告

- [ ] **Step 1: 写测试清单**

```markdown
# 手动测试清单（v0.1.0）

前置：`.\gradlew.bat runServer`，编辑 run/server/eula.txt 为 eula=true，
server.properties 设 online-mode=false；另开两个 runClient 连接 localhost。
OP 自己：控制台输入 op <名字>（或 runClient 单人世界单人测试结构）。

## 准备
- [ ] /party setarena：反馈中心点坐标
- [ ] /party start（1 人时）：报错"至少需要 2 名玩家"
- [ ] /party start（2 人）：两人被传送进场、背包清空、屏幕出现必做任务提示

## 准备期
- [ ] 玩家间攻击无效（不掉血）
- [ ] 4 个箱子存在且可开，含武器/护甲/食物
- [ ] 计分板显示两人名字与禁忌（按分数排序、颜色正确）

## 对局
- [ ] 完成必做任务：收到"必做任务已完成"提示
- [ ] 触发第一件禁忌：收到含具体内容的警告，计分板该玩家禁忌切换为第二件
- [ ] 触发第二件禁忌：出局转旁观
- [ ] PVP 打死对方：对方出局转旁观，自己若抽到"造成伤害"必做则完成
- [ ] 死亡不掉落物品
- [ ] 超时未完成必做者出局，幸存者 +1 并进入下一回合
- [ ] 下一回合所有人重新进场、任务重新分配、分数保留

## 胜负与命令
- [ ] 某人到 5 分：宣布胜者，游戏结束
- [ ] /party stop：游戏终止
- [ ] 掉线玩家视作出局

## 回归
- [ ] 无 Mod 崩溃日志（logs/latest.log 无 ERROR）
```

- [ ] **Step 2: 用户按清单联机测试并记录结果**

每个失败项按"先复现 → 修复 → 再验证"流程处理；修复提交用 `fix(...)` 分支。

- [ ] **Step 3: 打包与安装（用户执行）**

```bash
.\gradlew.bat build
```

把 `build/libs/partygame-0.1.0.jar` 复制到 `D:\Game\PCL\.minecraft\mods`，用 PCL 启动 1.21.11-NeoForge 单人世界复测一遍核心流程。

- [ ] **Step 4: 提交收尾**

```bash
git checkout -b docs/testing
git add docs/testing-checklist.md
git commit -m "docs(test): 新增手动测试清单"
git checkout main
git merge docs/testing
git branch -d docs/testing
```

---

## Self-Review

**Spec coverage 对照：**
- 2.1 任务分配（1 必做+2 禁忌、可见性、顺序激活）→ Task 3（分配器）+ Task 4（PlayerState）+ Task 7（计分板）
- 2.2 出局与得分、告知内容 → Task 4（GameManager.onTrigger/endRound）+ Task 6（死亡/掉线）
- 2.3 PVP 与装备（准备期抢箱、禁攻击、不掉落）→ Task 5（箱子）+ Task 6（onHurt/onDrops）
- 2.4 任务池 13 项 → Task 2 默认配置 JSON
- 3 状态机 → Task 4；4 时间线 → Task 4+5+6+7；5 竞技场 → Task 5+9；6 配置 → Task 2；7 命令 → Task 8；8 UI → Task 7；9 边界 → Task 4+6；10 测试 → Task 3/9/10；11 分工 git → 各任务提交步骤

**已解决（2026-08-18）：** Task 7 计分板方案已与用户确认选分支 B（自定义客户端 HUD，完全符合规格），实施时执行 Task 7 Step 3，跳过 Step 4。

**Placeholder scan：** 无 TBD/TODO；事件类名以"全局验证点"表给出首选+后备，均可在编译期验证，不属于占位。

**Type consistency：** `TaskDefinition/Assignment/TaskPool/TaskAssigner` 签名在 Task 3 定义后保持不变；`GameManager` 公开方法（onTrigger/onPlayerDeath/onPlayerLeave/stateOf/allStates/platePos/setPlatePos/playerNameOf）在 Task 4 定义、Task 5-8 按签名消费；`ModConfig` 访问器在 Task 2 定义后仅 Task 5 新增 `loot(kind)`，无改名。
