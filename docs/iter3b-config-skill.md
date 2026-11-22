# 迭代3b：新建 perm-config skill（帮助 agent 快速接入项目权限表）

## 目标
在项目 `.agents/skills/perm-config/` 创建一个新 skill：当用户说"把项目X的权限表接入perm-admin"时，agent 按 SKILL.md 指引完成：探查库表结构→判定字段映射→生成配置块→合并进 `config/permissions.json`→热重载→验证。

## skill 内容要求（SKILL.md，中文为主，含 frontmatter name/description）

1. **何时使用**：为 perm-admin 接入新项目/环境/库/权限表配置，或修改现有配置。
2. **配置 Schema 速查**：内嵌完整的 permissions.json 结构说明（与 docs/design.md 中 schema 一致，字段注释齐全：fields 四映射、otherFields 占位符 `${personId}/${personName}/${now}`、scopes/permissions 的 value+label）。
3. **接入工作流**（核心）：
   - Step1 探查：用可用手段查看目标表结构——优先级：用户提供 DDL/截图 > 直接查库（给出各库示例 SQL：`information_schema.columns` for MySQL、`sqlite_master`/`PRAGMA table_info` for SQLite、`pg_catalog` for PG）；确认哪列是工号、姓名、范围、权限。
   - Step2 取值枚举：`SELECT DISTINCT perm_col FROM t`、`SELECT DISTINCT scope_col FROM t` 拿实际存储值；与用户/业务确认每个值的显示名 label（label 跨表统一语义，如"查询"）。
   - Step3 生成配置块：给出现成 JSON 模板 + 字段命名惯例（code 用 kebab-case、database.code 必须全局唯一）；otherFields 填哪些列（非空列必须有值：租户/创建人/时间戳等）。
   - Step4 合并：编辑 `config/permissions.json`（项目已存在则往 environments/databases/permissionTables 追加），附 `scripts/add-perm-table.mjs` 用法：node 脚本按 cli 参数自动 merge 指定层级并做 JSON 校验，防手抖改坏。
   - Step5 生效：`POST /api/config/reload`（curl 示例）；Step6 验证：`POST /api/permissions/query` 该表能返回行、label 翻译正确。
4. **校验清单**：code 唯一性、jdbcUrl 连通性、四映射列真实存在、label 语义跨表一致（同义 label 不要起新名）、password 不泄露到日志/提交。
5. **常见坑**：personName 可空字段配置 null 或留空列；delete 唯一性=工号×范围×权限；数字存储值在 JSON 中仍写字符串；SQLite 需写绝对/相对 `./data` 路径且父目录存在；MSSQL/Oracle LIMIT 分页不支持（已知限制）。

## 配套脚本 `scripts/add-perm-table.mjs`（Node 无依赖）
用法：`node scripts/add-perm-table.mjs --config <path> --file <snippet.json>` 或 stdin 传入 snippet：
```json
{"project":{...}, "environment":{...}, "database":{...}, "table":{...}}
```
行为：读取主配置，按 code 查找/创建 project→environment→database 层级，把 table 追加到该 database.permissionTables（同 code 则替换）；检查 database.code 全局唯一冲突（不同库同名 code 报错）；写回格式化 JSON。SKILL.md 给出示例。

## 交付
`.agents/skills/perm-config/SKILL.md` + `scripts/add-perm-table.mjs`（自测 node 可跑通 merge）。
