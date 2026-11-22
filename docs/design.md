# perm-admin 实施文档

本地权限管理工具：通过一份 JSON 配置接入多个项目的权限表，支持跨库查询用户权限、批量删除、批量新增，所有写操作先预览 SQL（按数据源+表分组）再确认执行。

## 目录结构

```
perm-admin/
  backend/            # Spring Boot 3.5 + MyBatis Plus + 动态多数据源
  frontend/           # Vite + Vue3 + Element Plus
  config/permissions.json   # 权限配置（唯一事实来源）
  docs/
```

## 配置 JSON Schema（config/permissions.json）

```json
{
  "projects": [
    {
      "code": "demo",
      "name": "演示项目",
      "environments": [
        {
          "code": "dev",
          "name": "开发环境",
          "databases": [
            {
              "code": "demo-db",
              "name": "演示库",
              "jdbcUrl": "jdbc:h2:file:./data/demo",
              "username": "sa",
              "password": "",
              "driverClassName": "org.h2.Driver",       // 可选，默认按 jdbcUrl 自动识别
              "autoInit": true,                          // 可选：启动时建表+灌演示数据（仅本地demo用）
              "permissionTables": [
                {
                  "code": "user_perm",
                  "name": "用户权限表",
                  "tableName": "t_user_permission",
                  "fields": {
                    "personId":   "user_no",      // 人员字段（工号）
                    "personName": "user_name",    // 人员名称字段（可空）
                    "scope":      "data_scope",   // 数据访问范围字段
                    "permission": "perm_type"     // 权限字段
                  },
                  "otherFields": {                     // 插入时其余字段填充
                    "create_by": "perm-tool",
                    "create_time": "${now}",
                    "remark": "${personId}"            // 支持占位符 ${personId} ${personName} ${now}
                  },
                  "scopes": [                          // 数据访问范围可选值：存储值+显示名
                    {"value": "ALL",  "label": "全部数据"},
                    {"value": "DEPT", "label": "本部门"}
                  ],
                  "permissions": [                     // 权限字段可选值：存储值+显示名
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

说明：
- 项目→环境→数据库→权限表四级树。一般一个环境一个库，但配置允许数组。
- `permissions`/`scopes` 的 `label` 是跨表统一的显示名；同一 label 在不同表可对应不同 `value`，列表筛选按 label 进行（后端把 label 翻译成各表实际 value 过滤；某表无此 label 则跳过该表）。
- 密码不出现在 GET /api/config 响应中（返回 `***`）。

## 后端 API（端口 8080）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/config | 配置树（密码掩码） |
| POST | /api/config/reload | 从磁盘重载配置 |
| POST | /api/permissions/query | 查询权限列表 |
| POST | /api/sql/preview | 生成待执行 SQL 计划（insert/delete） |
| POST | /api/sql/execute | 执行 SQL 计划（每组一事务） |

### POST /api/permissions/query
请求：
```json
{"project":"demo","environment":"dev","database":"demo-db","tableCode":"user_perm",
 "permissionLabel":"查询","scopeLabel":"全部数据","keyword":"zhang"}
```
所有字段可选；返回打平的行列表（跨表/跨库合并，每表最多 500 行）：
```json
{"rows":[{
  "project":"demo","projectName":"演示项目","environment":"dev","environmentName":"开发环境",
  "database":"demo-db","databaseName":"演示库","tableCode":"user_perm","tableName":"t_user_permission",
  "personId":"u001","personName":"张三",
  "scopeValue":"ALL","scopeLabel":"全部数据",
  "permissionValue":"R","permissionLabel":"查询"
}]}
```
- `keyword` 对工号、姓名做 LIKE。
- 行唯一性约定：一个用户 × 一个数据范围 × 一个权限 = 一条记录。

### POST /api/sql/preview
删除（行来自 query 结果回传）：
```json
{"type":"delete","items":[{"project":"demo","environment":"dev","database":"demo-db",
  "tableCode":"user_perm","personId":"u001","personName":"张三",
  "scopeValue":"ALL","permissionValue":"R"}]}
```
新增（笛卡尔积：人员 × 权限 × 范围）：
```json
{"type":"insert","project":"demo","environment":"dev","database":"demo-db","tableCode":"user_perm",
 "persons":[{"id":"u009","name":"王五"}],
 "permissions":["R","W"],"scopes":["ALL"]}
```
统一响应（按 数据源+表 分组）：
```json
{"groups":[{
  "key":"demo-db|t_user_permission",
  "dbCode":"demo-db","dbName":"演示库","jdbcUrl":"jdbc:h2:file:./data/demo",
  "tableName":"t_user_permission",
  "statements":[{"sql":"DELETE FROM t_user_permission WHERE user_no='u001' AND data_scope='ALL' AND perm_type='R' AND user_name='张三'",
                 "prepared":"DELETE FROM t_user_permission WHERE user_no=? AND data_scope=? AND perm_type=? AND user_name=?",
                 "params":["u001","ALL","R","张三"]}]
}]}
```
- `sql` 仅用于页面展示（字面量已转义）；执行用 `prepared`+`params`。
- delete 条件：personId + scope + permission + personName（null 时生成 `col IS NULL`）。
- insert 列：映射字段 + otherFields；不带 id。

### POST /api/sql/execute
请求体 = preview 响应的 `{groups:[...]}`。每个 group 一个事务，失败回滚该组、继续其余组：
```json
{"results":[{"key":"demo-db|t_user_permission","success":true,"rows":3,"error":null}]}
```

## 前端页面（vite dev 5173，/api 代理到 8080）

单页 + 两个 tab：

1. **权限查询**
   - 筛选栏：项目/环境/数据库/权限表（级联）、权限显示名、数据范围显示名（全配置去重聚合）、关键字。
   - 表格列：勾选框、工号、用户名称、项目、环境、数据库、权限表、数据范围(显示名+值)、权限(显示名)、操作(删除)。
   - 批量勾选/取消 → 批量删除；行内删除 → 同样走预览。
   - 删除 → SQL 预览弹窗（按数据源+表分栏 el-tabs/collapse 展示 SQL 列表）→ 确定执行 → 结果提示 → 刷新。
2. **新增权限**
   - 批量人员输入：文本域每行 `工号` 或 `工号,姓名`。
   - 级联选 项目→环境→数据库→权限表；勾选权限（显示名）+ 数据范围（显示名），均为多选。
   - 确定 → 同一预览弹窗 → 执行。
3. 顶部小入口：查看当前配置树（抽屉只读展示）。

## 技术栈与约定

- JDK 26 运行，`maven.compiler.release=17`；Maven 3.9.16 在 `D:\WorkProject\.tools\apache-maven-3.9.16`（阿里云镜像已配），构建命令：
  `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-26.0.1.8-hotspot" D:/WorkProject/.tools/apache-maven-3.9.16/bin/mvn -f backend/pom.xml <goal>`
- Spring Boot 3.5.7；`mybatis-plus-spring-boot3-starter`（动态查询主要走 JdbcTemplate，MP 作主库/演示库 ORM）；驱动：mysql-connector-j、postgresql、mssql-jdbc、ojdbc11、H2、sqlite-jdbc。
- 动态数据源：按 database.code 懒建 HikariDataSource 缓存；演示库（autoInit）同时注册为 Spring 主 DataSource。
- 不用 Lombok；DTO 用 record/简单类。
- 配置文件路径：env `PERM_CONFIG` 优先，否则 `../config/permissions.json`（相对 backend 工作目录）。
- 前端 .npmrc 使用 `https://registry.npmmirror.com`。
- 原型阶段不写测试代码。

## 运行方式

```bash
# 后端（8080）：工作目录必须是 backend/（数据文件 ./data 与默认配置路径都相对它）
cd backend && java -jar target/perm-admin-1.0.0.jar
# 重新构建：
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-26.0.1.8-hotspot" \
  "D:/WorkProject/.tools/apache-maven-3.9.16/bin/mvn" -f backend/pom.xml -DskipTests package

# 前端（5173，/api 代理到 8080）
cd frontend && npm install && npm run dev
```

打开 http://localhost:5173 即可使用；demo 配置自带两个 H2 演示库、erp 项目自带三个 SQLite 演示库（`backend/data/sqlite/*.db`，autoInit 自动建库建表灌数），均无需外部数据库。

## 已实现要点（初版）

- 四级配置树（项目→环境→数据库→权限表），字段映射/otherFields 占位符（`${personId}` `${personName}` `${now}`）。
- 权限/数据范围按**显示名**筛选，后端按各表配置翻译成实际存储值（已验证 "查询" 同时命中 R/Q）。
- 查询跨库打平返回；单表失败收集进 `errors` 不拖垮整体。
- 删除/新增统一走"预览 SQL → 按数据源+表分组 → 逐组事务执行"；页面展示插值 SQL，执行走 prepared+params。
- 新增为 人员×权限×范围 笛卡尔积；不带 id。
- 新增支持 targets 四级多选目标（空=全部）+ 按 label 翻译 + 已有记录去重跳过（skipped/unsupported/errors 明细），详见 iter2-batch-add-redesign.md。
- 批量执行结果统一确认弹窗（成功/失败/跳过/不支持分区），详见 iter3-execute-result.md。
- 配置热重载：POST /api/config/reload；`.agents/skills/perm-config` skill + `scripts/add-perm-table.mjs` 辅助 agent 接入新表配置。

## 已知限制（原型）

- `LIMIT 500` 分页写法兼容 H2/MySQL/PG/SQLite；MSSQL/Oracle 需方言化。
- autoInit 建表语法适配 H2/MySQL/SQLite（`AUTOINCREMENT`），仅用于内置演示。
- `${now}` 以时间戳字符串传参，严格类型列可能需 CAST。
- `database.code` 必须全局唯一（execute 按 dbCode 定位数据源）。
- 删除按 personId+scope+permission(+personName) 条件匹配，与该唯一性约定一致。

