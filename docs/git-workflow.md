
# Git 协作规范（学习重点）

本项目是 git 项目管理的练习场。规范从简，但必须执行。

## 分支模型

- `main`：只放可运行、已验证的代码。**禁止直接提交**。
- 功能分支：每个功能/修复一个分支，命名 `feat/<功能名>` 或 `fix/<问题名>`，例如 `feat/task-system`、`fix/arena-wall-height`。
- 流程：切分支 → 小步提交 → 验证 → 合并回 main → 删分支。

## 提交规范（Conventional Commits）

```
<type>(<scope>): <简短描述>
```

type：`feat`（新功能）/ `fix`（修复）/ `docs`（文档）/ `refactor`（重构）/ `test`（测试）/ `chore`（构建与杂项）。

要求：
- 描述用中文，说明**做了什么**。
- 一次提交只做一件事。
- 提交信息中禁止出现开发进度词（FIXED、Step、Phase 等）与 AI 工具名称。

示例：

```
feat(task): 新增任务分配器与冲突约束
fix(arena): 修正竞技场围墙高度为 3 格
docs(git): 补充分支与提交规范
```

## 常用命令速查

| 命令 | 作用 |
| --- | --- |
| `git init` | 初始化仓库 |
| `git status` | 查看改动状态 |
| `git diff` | 查看具体改动 |
| `git add <文件>` | 暂存改动 |
| `git commit -m "..."` | 提交 |
| `git log --oneline` | 查看提交历史 |
| `git branch` / `git checkout -b <名>` | 查看分支 / 新建并切换分支 |
| `git merge <分支>` | 合并分支 |
| `git reset --hard HEAD` | 丢弃未提交改动（危险，谨慎） |
| `git revert <提交>` | 撤销某次已提交改动（安全） |

## 合并流程

1. 在功能分支上完成开发并通过验证。
2. 切回 main：`git checkout main`。
3. 合并：`git merge feat/<功能名>`。
4. 确认无误后删除分支：`git branch -d feat/<功能名>`。

> 进阶：后续可学习 `git rebase`、`git stash`、远程仓库（GitHub）与 PR 流程，按需推进，不提前铺开。

## 回滚原则

- 未提交的改动：`git checkout -- <文件>`（丢弃）或 `git stash`（暂存）。
- 已提交的改动：优先 `git revert`（保留历史），避免 `git reset --hard`。
