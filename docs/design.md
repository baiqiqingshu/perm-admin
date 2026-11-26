# perm-admin 实施文档

本地权限管理工具：通过一份 JSON 配置接入多个项目的权限表，支持跨库查询用户权限、批量删除、批量新增，所有写操作先预览 SQL（按数据源+表分组）再确认执行。

## 目录结构

```
perm-admin/
  backend/            # Spring Boot 3.5 + JdbcTemplate 动态多数据源
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
- `permissions`/`scopes` 的 `label` 是跨表统一的显示名；同一 label 在不同表可对应不同 `value`，列表筛选按 label 进行（后端把每个 label 翻译成各表实际 value 过滤；多选为 OR，某表不支持的 label 忽略，全部不支持则跳过该表）。
- 密码不出现在 GET /api/config 响应中（返回 `***`）。

## 后端 API（端口 18917）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/config | 配置树（密码掩码） |
| POST | /api/config/reload | 从磁盘重载配置 |
| POST | /api/permissions/query | 查询权限列表 |
| POST | /api/sql/preview | 生成待执行 SQL 计划（insert/delete） |
| POST | /api/sql/execute | 执行 SQL 计划（每组一事务） |

### POST /api/permissions/query
请求（多选契约）：
```json
{"projects":["demo"],"environments":["dev"],"databases":["demo-db"],"tableCodes":["user_perm"],
 "permissionLabels":["查询","编辑"],"scopeLabels":["全部数据"],"keyword":"zhang"}
```
所有字段可选；`projects/environments/databases/tableCodes/permissionLabels/scopeLabels` 均为数组，空数组或缺省表示不过滤。仍兼容旧单值字段：`project/environment/database/tableCode/permissionLabel/scopeLabel`；同一级旧字段存在时优先。权限/范围多选为 OR 语义，后端逐表把 label 翻译为实际 value 后用 `IN` 查询，某表不支持的 label 忽略，全部不支持则跳过该表。返回打平的行列表（跨表/跨库合并，不限行数；大结果集由前端虚拟滚动表格渲染）：
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

## 前端页面（vite dev 5173，/api 代理到 18917）

单页 + 两个 tab：

1. **权限查询**
   - 筛选栏：项目/环境/数据库/权限表（四级均多选、可搜索，上级为空表示全部且下级选项随上级收敛）、权限显示名/数据范围显示名（多选）、关键字。
   - 表格列：勾选框、序号、工号、用户名称、项目、环境、数据库、权限表、数据范围(显示名+值)、权限(显示名+值)、操作(删除)。
   - 结果表为 `el-table-v2` 虚拟滚动（只渲染可视区行，大结果集不卡顿），勾选/序号列固定左侧、操作列固定右侧；列宽按内容预设并自动填满宽屏，表头右缘可拖动调整（双击恢复）。
   - 表头提供排序/筛选按钮：排序加入上方排序键，可切换方向、前移/后移、删除；筛选基于当前查询结果去重生成，支持搜索、勾选/取消、多选、全选当前搜索、清空。
   - 批量勾选/取消 → 批量删除；行内删除 → 同样走预览。
   - 删除 → SQL 预览弹窗（按数据源+表分栏 el-tabs/collapse 展示 SQL 列表）→ 确定执行 → 结果提示 → 刷新。
2. **新增权限**
   - 批量人员输入：文本域每行 `工号` 或 `工号,姓名`。
   - 目标范围四级均多选；勾选权限（显示名）+ 数据范围（显示名），均为多选。
   - 各多选下拉头部提供全选（跳过禁用项）/清空/已选计数，便于选项较多时快速添加。
   - 确定 → 同一预览弹窗 → 执行。
3. 顶部小入口：查看当前配置树（抽屉只读展示）。

## 技术栈与约定

- JDK 26 运行，`maven.compiler.release=17`；Maven 3.9.16 在 `D:\WorkProject\.tools\apache-maven-3.9.16`（阿里云镜像已配），构建命令：
  `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-26.0.1.8-hotspot" D:/WorkProject/.tools/apache-maven-3.9.16/bin/mvn -f backend/pom.xml <goal>`
- Spring Boot 3.5.7；动态查询全部走 JdbcTemplate/原生 JDBC；驱动：mysql-connector-j、postgresql、mssql-jdbc、ojdbc11、H2、sqlite-jdbc。
- 动态数据源：按 database.code 懒建 HikariDataSource 缓存；autoInit 库启动时建表+灌演示数据。
- 不用 Lombok；DTO 用 record/简单类。
- 配置文件解析顺序：`PERM_CONFIG` 环境变量 > `perm.config` 属性（`-D`/`--perm.config=`）> `./permissions.json` > `./config/permissions.json` > `../config/permissions.json`（相对工作目录）> jar 内置 `classpath:/permissions.json` 兜底。
- 前端 .npmrc 使用 `https://registry.npmmirror.com`。
- 原型阶段不写测试代码。

## 运行方式

```bash
# 后端（18917）：工作目录必须是 backend/（数据文件 ./data 与默认配置路径都相对它）
cd backend && java -jar target/perm-admin-1.0.3.jar
# 重新构建（frontend/dist 会被打进 jar 的 /static，18917 同源 serve 页面）：
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-26.0.1.8-hotspot" \
  "D:/WorkProject/.tools/apache-maven-3.9.16/bin/mvn" -f backend/pom.xml -DskipTests package

# 前端（5173，/api 代理到 18917；仅开发调试页面时需要，日常使用可只跑 jar 开 18917）
cd frontend && npm install && npm run dev
```

### Windows 单 exe 打包与分发

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-exe.ps1
```

流水线：`npm run build` → `mvn package` → `jpackage` app-image（jlink 裁剪运行时，目标机零依赖）→ Enigma Virtual Box 打便携单 exe（未装 EVB 时降级 7z SFX，再缺则只出目录形态）。可选参数：`-SkipFrontend`（dist 已最新）、`-AppImageOnly`（只到目录形态）、`-AppVersion`（覆盖 exe 元数据版本，默认读取 backend/pom.xml）。

产物（`release/`）：
- `perm-admin.exe` —— 便携单 exe（~81MB；**文件名不可改**，jpackage 启动器按自身文件名找 `app\<name>.cfg`）
- `perm-admin\` —— app-image 目录形态，内含同名启动器，可整目录分发
- `permissions.json` —— 外置默认配置，与 exe 同目录即生效（不存在则用 jar 内置兜底配置）

目标机用法：`perm-admin.exe` + `permissions.json` 放同一目录双击；控制台窗口保持打开即服务运行，关闭即停止；启动后自动尝试打开浏览器（`-Dperm.openBrowser`，非交互桌面环境失败时手动访问 `http://localhost:18917`）。
运行时参数可追加：`perm-admin.exe --server.port=9090 --perm.config=D:\conf\x.json`。

demo 配置自带两个 H2 演示库、erp 项目自带三个 SQLite 演示库（`./data/` 相对 exe 所在目录，autoInit 自动建库建表灌数），均无需外部数据库。

## 已实现要点

- 四级配置树（项目→环境→数据库→权限表），字段映射/otherFields 占位符（`${personId}` `${personName}` `${now}`）。
- 权限/数据范围按**显示名**筛选，后端按各表配置翻译成实际存储值（已验证 "查询" 同时命中 R/Q）。
- 查询跨库打平返回；单表失败收集进 `errors` 不拖垮整体。
- 删除/新增统一走"预览 SQL → 按数据源+表分组 → 逐组事务执行"；页面展示插值 SQL，执行走 prepared+params。
- 新增为 人员×权限×范围 笛卡尔积；不带 id。
- 新增支持 targets 四级多选目标（空=全部）+ 按 label 翻译 + 已有记录去重跳过（skipped/unsupported/errors 明细），详见 iter2-batch-add-redesign.md。
- 批量执行结果统一确认弹窗（成功/失败/跳过/不支持分区），详见 iter3-execute-result.md。
- 查询筛选四级与权限/范围均支持多选数组契约；查询结果表格支持列宽拖动、表头多键排序与结果集筛选（iter5 起为虚拟滚动表格、查询不再限制每表 500 行，详见 iter5-unlimited-virtual-table.md）；新增权限页多选支持一键全选，详见 iter4-query-table-enhancements.md。
- 配置热重载：POST /api/config/reload；`.agents/skills/perm-config` skill + `scripts/add-perm-table.mjs` 辅助 agent 接入新表配置。

## 已知限制（原型）

- 查询不分页、不限行数（已移除 `LIMIT`，SQL 对各方言通用）；超大结果集的网络传输与浏览器内存占用随行数增长，渲染由虚拟滚动保证流畅。
- autoInit 建表语法适配 H2/MySQL/SQLite（`AUTOINCREMENT`），仅用于内置演示。
- `${now}` 以时间戳字符串传参，严格类型列可能需 CAST。
- `database.code` 必须全局唯一（execute 按 dbCode 定位数据源）。
- 删除按 personId+scope+permission(+personName) 条件匹配，与该唯一性约定一致。

