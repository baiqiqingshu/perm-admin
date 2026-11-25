# iter4 查询表格交互与批量选择增强实施文档

## 目标

本次迭代只做原型功能增强，不新增测试代码：

1. 查询结果列表表头支持拖动调整列宽。
2. 页面整体宽度加宽，提升宽屏利用率。
3. 查询结果列表表头提供排序、筛选按钮：
   - 点击排序按钮后把字段加入上方排序键；重复点击切换升/降序。
   - 上方排序键支持删除、前移/后移调整优先级。
   - 表头筛选基于当前查询结果生成可选项，支持搜索、勾选/取消、多选、全选/清空。
4. 查询页顶部「筛选条件」全部改为多选（项目/环境/数据库/权限表/权限显示名/数据范围）。
5. 新增权限页的多选下拉提供「全选」能力，便于选项较多时快速添加。

## 后端契约调整

`POST /api/permissions/query` 保持向后兼容：

- 旧字段：`project` / `environment` / `database` / `tableCode` / `permissionLabel` / `scopeLabel` 仍可按单值传入。
- 新字段：`projects` / `environments` / `databases` / `tableCodes` / `permissionLabels` / `scopeLabels` 均为字符串数组；空数组或缺省表示不过滤。
- 同一级旧字段存在时优先按旧字段单值处理；否则使用新数组字段。
- 权限/范围多选按 OR 语义查询：每个 label 先按当前表配置翻译为实际 value，表内不支持的 label 忽略；全部不支持则跳过该表。SQL 使用 `IN (?, ...)`。
- `keyword` 行为不变。

## 前端实施拆分

### A. 查询页与顶部筛选（PermissionQuery.vue + TableCascade.vue）

- `TableCascade` 的 v-model 改为 `{ projects: [], environments: [], databases: [], tableCodes: [] }`。
- 四级下拉均 `multiple + filterable + clearable + collapse-tags`；上级为空表示全部，下级仍可选择（选项按上级已选收敛）。
- 权限显示名/数据范围改为多选数组，查询时发送新契约数组字段。
- 重置时清空所有数组、排序键和表头筛选。

### B. 查询结果表格（PermissionQuery.vue）

- `el-table` 开启 `border`；数据列显式设置初始 `width` 和 `resizable`，选择/序号/操作列禁止拖动。
- 用列定义驱动数据列渲染，自定义 header：列名 + 排序按钮 + 筛选按钮。
- 数据流：`rows`（接口原始结果）→ `filteredRows`（表头筛选）→ `visibleRows`（多键排序）。
- 排序键面板：显示顺序、字段、升降序切换、前移/后移、删除、清空。
- 表头筛选：每列从 `rows` 去重生成选项；弹层内提供搜索框、全选当前搜索、清空、勾选列表；筛选激活时在按钮上显示数量。
- 显示型列使用一致的值键：`scope` = `scopeLabel（scopeValue）`，`permission` = `permissionLabel（permissionValue）`。

### C. 新增权限页批量全选（PermissionAdd.vue，必要时新增组件）

- 目标范围四级多选、权限显示名、多选数据范围的下拉 header 增加：
  - 全选当前可选（跳过 disabled）
  - 清空
  - 已选数量提示
- 不改动后端 insert 契约。

### D. 页面宽度（App.vue / PermissionAdd.vue / 必要的全局样式）

- 顶栏与主内容从 `1240px` 提升到宽屏流体布局，最大宽度约 `1680px`；小屏保持现有响应式。
- 新增权限页取消 `900px` 的窄宽度限制，与主布局对齐但保留合理表单宽度。

## 验证

- `cd frontend && npm run build`
- `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-26.0.1.8-hotspot" "D:/WorkProject/.tools/apache-maven-3.9.16/bin/mvn" -f backend/pom.xml -DskipTests package`
- 原型阶段不写测试；用构建结果和必要的手工/API smoke 验证。

## 实施结果与验证

已完成全部计划项：

- 查询页顶部四级筛选、权限显示名、数据范围均改为多选；后端 `/api/permissions/query` 已支持数组契约并兼容旧单值字段。
- 查询结果表格启用原生列宽拖动；数据列表头提供排序/筛选按钮。排序键支持方向切换、前移/后移、删除、清空；表头筛选支持搜索、多选、勾选/取消、全选当前搜索、清空。
- 页面主体宽屏最大内容宽度调整为 1680px，顶栏与主内容左右边界对齐；新增权限页移除 900px 窄宽度限制并按宽度使用 4/2/1 列网格。
- 新增权限页所有多选下拉提供全选（跳过禁用项）、清空、已选/可选计数。
- 版本号升级为 1.0.2；Windows 打包脚本 exe 元数据版本默认读取 `backend/pom.xml`，并明确选择对应版本 jar，避免 target 中旧版本 jar 被误打包。

验证结果：

- `cd frontend && npm run build`：通过。
- `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-26.0.1.8-hotspot" "D:/WorkProject/.tools/apache-maven-3.9.16/bin/mvn" -f backend/pom.xml -DskipTests package`：通过。
- 集成冒烟（隔离 demo 数据 + jar 18918）：`/api/config` 200；`POST /api/permissions/query` `{}` 57 行；数组契约多项目/多表/多权限/多范围 18 行；多环境数组 32 行；旧单值契约兼容通过。
- Chrome headless CDP 页面冒烟：查询 57 行；列宽拖动 120px→165px；排序键新增/前移/后移/删除通过；表头筛选搜索 ERP 后 33/57 行且可清空；新增权限页全选跳过 disabled、清空通过；无浏览器 console 错误/网络错误。

已知限制：表头排序/筛选只作用于当前查询返回的客户端结果（仍受后端每表 500 行限制）；新增权限页「全选」选中当前可选全集，不随下拉过滤输入临时收窄。零长度 body 调 `/api/permissions/query` 仍返回 400（前端总是发送 `{}`，不影响使用）；未清理 target 时 jar 可能残留旧 hash 静态资源（不影响 index 指向的最新资源，后续可让打包流水线 clean）。
