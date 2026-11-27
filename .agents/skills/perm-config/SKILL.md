---
name: perm-config
description: "为 perm-admin 项目接入/修改权限表配置时使用：当用户要把新项目/新环境/新数据库/新权限表接入权限 JSON 配置，或修改已有权限表配置（字段映射、label、连接信息、取值枚举）时，按本 skill 的「探查→枚举值→生成→合并→重载验证」五步流程操作。"
---

# perm-config：权限表配置接入

把业务系统的权限表接入 perm-admin 的标准流程。配置唯一事实来源是权限 JSON：源码仓库通常是 `config/permissions.json`，便携发行包通常是 exe 旁的 `permissions.json`（schema 定义见 `docs/design.md`「配置 JSON Schema」一节）。

## 何时使用

- 用户说"把项目 X 的权限表接入 perm-admin"、"加一张权限表"、"加一套环境/库"。
- 修改现有配置：改四映射、改 label、改 jdbcUrl/账号、增减 scopes/permissions 取值。
- 不适用：改后端/前端代码、纯执行 SQL——那些不走配置。

## 配置 Schema 速查

四级树：**项目 → 环境 → 数据库 → 权限表**。完整结构（注释版）：

```json
{
  "projects": [
    {
      "code": "demo",                          // 项目 code，建议 kebab-case/snake_case，项目内唯一
      "name": "演示项目",
      "environments": [
        {
          "code": "dev",                       // 环境 code（dev/test/prod...），项目内唯一
          "name": "开发环境",
          "databases": [
            {
              "code": "demo-dev-db",           // 数据库 code，【全局唯一】（后端按 dbCode 定位数据源）
              "name": "演示库-开发",
              "jdbcUrl": "jdbc:h2:file:./data/demo-dev",
              "username": "sa",                // SQLite 这类无账号库可省略
              "password": "",
              "driverClassName": "org.h2.Driver", // 可选，默认按 jdbcUrl 自动识别
              "autoInit": true,                // 可选，仅本地演示库：启动建表+灌数据。真实库别开
              "permissionTables": [
                {
                  "code": "user_perm",         // 表 code，库内唯一
                  "name": "用户权限表",
                  "tableName": "t_user_permission", // 物理表名
                  "fields": {                  // 四映射：配置值 = 物理列名
                    "personId":   "user_no",   // 工号列（必填）
                    "personName": "user_name", // 姓名列（可空列就配 null）
                    "scope":      "data_scope",// 数据范围列
                    "permission": "perm_type"  // 权限列
                  },
                  "otherFields": {             // insert 时四映射之外的填充列
                    "create_by": "perm-tool",  //   字面量原样写入
                    "create_time": "${now}",   //   占位符：${now} 当前时间戳字符串
                    "remark": "${personId}"    //   ${personId} ${personName} 取当行人员值
                  },
                  "scopes": [                  // 范围可选值：存储值 + 显示名
                    {"value": "ALL",  "label": "全部数据"},
                    {"value": "DEPT", "label": "本部门"},
                    {"value": "SELF", "label": "仅本人"}
                  ],
                  "permissions": [             // 权限可选值：存储值 + 显示名
                    {"value": "R", "label": "查询"},
                    {"value": "W", "label": "编辑"}
                  ]
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

要点：

- `scopes`/`permissions` 的 **label 跨表统一语义**：列表筛选按 label，后端把 label 翻译成各表实际 value；某表没有该 label 就跳过该表。同一 label 在不同表可对应不同 value。
- `value` 一律写字符串——库里存数字也写 `"1"`、`"30"`。
- `password` 在 `GET /api/config` 返回 `***`，但配置文件本身是明文，注意别外泄。

## 接入工作流（五步）

### Step 1 探查：弄清目标表结构

优先级：**用户提供的 DDL/截图 > 直接查库**。能查库时用对应方言拉列清单：

```sql
-- MySQL
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = '<库名>' AND table_name = '<表名>'
ORDER BY ordinal_position;

-- SQLite
PRAGMA table_info('<表名>');
SELECT sql FROM sqlite_master WHERE name = '<表名>';

-- PostgreSQL
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = '<表名>'
ORDER BY ordinal_position;
-- 或：SELECT a.attname, format_type(a.atttypid, a.atttypmod) FROM pg_catalog.pg_attribute a
--     JOIN pg_catalog.pg_class c ON a.attrelid = c.oid WHERE c.relname = '<表名>' AND a.attnum > 0;

-- MSSQL / SQL Server
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = '<表名>' ORDER BY ORDINAL_POSITION;
```

产出结论：哪列是 **工号**、哪列是 **姓名**（是否可空）、哪列是 **数据范围**、哪列是 **权限**；还有哪些 NOT NULL 列需要 `otherFields` 兜底（租户、创建人、时间戳、来源标识等）。

### Step 2 枚举值：拿实际存储值并定 label

```sql
SELECT DISTINCT <权限列> FROM <表名>;
SELECT DISTINCT <范围列> FROM <表名>;
```

拿真实存储值（可能是 `R/W/D`、`1/2/3`、`VIEW/EDIT` 各种风格），与业务确认每个值对应的显示名 label。

**Label 命名建议——一律复用现有语义，别造同义词**（筛选项按 label 聚合，新造"查看"会导致按"查询"筛不到这张表）：

| 标准权限 label | 现网已见过的存储值 |
|---|---|
| 查询 | `R`、`Q`、`VIEW`、`SEL`、`10` |
| 编辑 | `W`、`U`、`EDIT`、`UPD`、`20` |
| 删除 | `D`、`DEL` |
| 导出 | `X`、`EXP` |
| 审批 | `A`、`30` |

| 标准范围 label | 现网已见过的存储值 |
|---|---|
| 全部数据 | `ALL`、`GLOBAL`、`A`、`1` |
| 本部门 | `DEPT`、`ORG`、`D`、`2` |
| 仅本人 | `SELF`、`OWN`、`O`、`3` |
| 部分数据 | `PART` |

遇到表里有语义对不上的新值（如"只读"），先与用户确认是归入现有 label 还是确实需要新 label。

### Step 3 生成：写出 snippet 配置块

按下面模板生成（这就是合并脚本和手工编辑通用的格式）：

```json
{
  "project":     {"code": "crm", "name": "CRM系统"},
  "environment": {"code": "dev", "name": "开发环境"},
  "database":    {"code": "crm-dev-db", "name": "CRM开发库",
                  "jdbcUrl": "jdbc:mysql://localhost:3306/crm",
                  "username": "root", "password": "***"},
  "table": {
    "code": "user_perm", "name": "用户权限表", "tableName": "t_perm",
    "fields": {"personId": "emp_no", "personName": "emp_name",
               "scope": "scope_cd", "permission": "perm_cd"},
    "otherFields": {"tenant_id": "T001", "create_by": "perm-tool", "create_time": "${now}"},
    "scopes":      [{"value": "ALL", "label": "全部数据"}, {"value": "DEPT", "label": "本部门"}],
    "permissions": [{"value": "R", "label": "查询"}, {"value": "W", "label": "编辑"}]
  }
}
```

命名惯例：

- `code` 用 kebab-case 或 snake_case，与现网风格一致即可；`database.code` 惯例 `<project>-<env>-db`，**必须全局唯一**。
- `otherFields` 覆盖表里所有 NOT NULL 且无默认值的非映射列；`personName` 是可空列就配 `null`。
- 数字存储值在 JSON 里仍写字符串（`"value": "1"`）。

### Step 4 合并：写进权限配置

**优先用脚本** `scripts/add-perm-table.mjs`（Node ≥18 零依赖），自动按 code 查找/创建层级、做唯一性校验，防手抖改坏 JSON：

```bash
node scripts/add-perm-table.mjs --file snippet.json --dry-run   # 先 dry-run 看合并结果（打印不落盘）
node scripts/add-perm-table.mjs --file snippet.json             # 确认后写回（自动选择默认配置）
cat snippet.json | node scripts/add-perm-table.mjs              # 也支持 stdin 传 snippet
node scripts/add-perm-table.mjs --config /tmp/copy.json --file snippet.json  # 指定其他配置文件
```

默认配置路径：源码仓库是 `config/permissions.json`；便携发行包根目录通常只有 exe 旁的 `permissions.json`，脚本会自动回退使用它，也可显式传 `--config permissions.json`。

脚本行为：project→environment→database 按 code 找，找不到就创建；table 追加到 `database.permissionTables`，同 code 则替换并提示；`database.code` 撞到"不同库"（不同 project/env 组合或 jdbcUrl 不同）直接报错退出。

手工编辑也可以：哪级缺补哪级，注意保持 2 空格缩进、JSON 合法（改完可 `node -e "JSON.parse(require('fs').readFileSync('permissions.json'))"` 或替换为实际配置路径验证）。

### Step 5 重载 + 验证

```bash
# 热重载配置（后端默认 18917）
curl -X POST http://localhost:18917/api/config/reload

# 验证 1：新表能查出数据
curl -X POST http://localhost:18917/api/permissions/query \
  -H "Content-Type: application/json" \
  -d '{"project":"<project>","environment":"<env>","database":"<db>","tableCode":"<table>"}'

# 验证 2：按 label 筛选能命中该表（label 翻译正确）
curl -X POST http://localhost:18917/api/permissions/query \
  -H "Content-Type: application/json" \
  -d '{"database":"<db>","tableCode":"<table>","permissionLabel":"查询","scopeLabel":"全部数据"}'
```

通过标准：返回 `rows` 里 `scopeLabel`/`permissionLabel` 是翻译后的中文显示名（不是原始 value）；按 label 筛选时新表也在结果中。

## 校验清单（合并前逐项过）

- [ ] `database.code` 全局唯一（脚本会强制校验）。
- [ ] `jdbcUrl` 能连通、`username`/`password` 正确（先拿客户端工具试连）。
- [ ] `fields` 四映射列名与物理表完全一致（大小写也一致），列真实存在。
- [ ] `otherFields` 覆盖了所有 NOT NULL 且无默认值的非映射列。
- [ ] `scopes`/`permissions` 的 `value` 与 `SELECT DISTINCT` 出来的存储值一一对应（字符串形式）；`label` 全部复用标准语义，无新造同义词。
- [ ] `password` 不打印到日志、不贴进 IM/issue；配置文件含明文密码，注意提交范围。
- [ ] 合并后文件仍是合法 JSON、2 空格缩进（用脚本合并自动保证）。

## 常见坑

- **personName 可空**：姓名列允许 NULL 时，`fields.personName` 配 `null`（或指向该可空列）；删除匹配对 NULL 会生成 `col IS NULL`。
- **删除唯一性**：一行 = 工号 × 范围 × 权限（+personName）。若目标表真实粒度更细（如同一组合有多行），删除会一次清掉多行，接入前先确认。
- **数字存储值写字符串**：库里存 `1/2/3`，JSON 里写 `"1"`/`"2"`/`"3"`，写数字会导致 label 翻译不匹配。
- **SQLite 路径**：`jdbc:sqlite:./data/...` 相对的是 **backend 工作目录**，且父目录必须存在；真实库建议先确认相对/绝对路径。
- **MSSQL/Oracle 分页**：查询用 `LIMIT 500` 写法，兼容 H2/MySQL/PG/SQLite，MSSQL/Oracle 是已知限制（需方言化，暂未支持）。
- **`${now}` 是字符串时间戳**：严格类型列（如 PG timestamptz 之外的类型）可能需要 CAST 或换字面量。
- **`autoInit` 只用于本地演示**：会建表灌数，真实库千万别开。
- **label 造同义词**："查看/读取/修改"这类新 label 会让该表在按"查询/编辑"筛选时漏掉——永远先查标准 label 表。
