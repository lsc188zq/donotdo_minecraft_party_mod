# 内置地图打包方案

**Goal:** 把地图模板作为 mod 资源打包进 jar，所有安装此 mod 的服务器开箱即有这些地图。

**Architecture:** 沿用现有地图体系：新增"内置地图"概念（与"程序生成房"同为固定项），模板从 jar 资源读取而非 config 目录；游戏内保存的自建地图机制不变。

---

### Task 1: 资源与配置

**方案：**

1. **资源位置**：地图模板放入 `src/main/resources/partygame/maps/<名字>.nbt`，随 jar 分发（ModDev 开发时从 `build/resources/main` 加载，与现有资源机制一致）
2. **默认配置**新增 `"builtinMaps": [{ "name": "xxx", "type": "TEMPLATE", "template": "xxx.nbt", "radius": 15, "builtin": true }]`；`MapData` 增加 `builtin` 字段（JSON 序列化含该字段，旧数据缺省 false）
3. **旧配置兼容**：加载时缺 `builtinMaps` 字段则补默认值（与 maps/protectedBlocks 同样处理）
4. **本次实际打包**：把用户已保存的地图（`run/config/partygame/maps/` 下的 nbt）复制进资源目录并登记

### Task 2: 读取与命令

**方案：**

- `MapManager.pasteTemplate`：`builtin=true` 从 jar 资源流读取（`PartyGame.class.getResourceAsStream("/partygame/maps/xxx.nbt")`，签名以 javap 核对 `NbtIo.readCompressed` 的 InputStream 重载），否则维持从 config 目录读
- `ModConfig`：新增 `builtinMaps()` 访问器；`maps()` 保持只返回自建地图
- `GameManager.selectedMapData()`：先查内置列表，再查自建
- `/party map list`：显示内置地图并标注 `§b[内置]`
- `/party map choose`：允许选内置地图
- `/party map remove`：**拒绝删除内置地图**（"内置地图不可删除"）
- `/party map save`：行为不变（存为自建地图）；名字与内置地图重名时拒绝（防覆盖混淆）

**范围：** `MapData`、`DefaultConfig`、`ModConfig`（+测试）、`MapManager`、`GameManager`、`PartyCommand`、资源文件。**风险：**
- 内置地图删除不掉（设计如此，文档说明；想移除需改 mod 资源与配置后重新发布）
- 资源流读取签名差异（编译期暴露，javap 先核对）
- 内置地图名字与玩家自建重名冲突（save 时拒绝，见上）

**验收标准：**
- 删除 `run/config` 下全部地图文件与配置登记后，`/party map list` 仍显示内置地图
- `/party map choose <内置图>` → setarena → start 正常落地游玩
- remove 内置地图报"不可删除"
- `./gradlew compileJava test` 通过；`./gradlew build` 后 `unzip -l` 确认 nbt 在 jar 内
