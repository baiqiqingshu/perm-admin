# iter5：不限量查询 + 虚拟滚动结果表格

## 目标

1. 去掉后端查询结果每表 500 行的人为限制，让查询结果完整反映筛选条件。
2. 查询结果表格改为虚拟滚动，避免大结果集一次性渲染大量 DOM 导致页面卡死。
3. 保留并迁移现有表格能力：批量勾选、序号、排序键、表头筛选、行内删除、批量删除、列宽拖动。
4. 美化表格视觉并优化初始列宽，让常见信息在宽屏下更少截断、层级更清晰。

## 现状约束

- 后端 `PermissionService.queryTable` 目前固定追加 `LIMIT 500`。
- 前端 `PermissionQuery.vue` 使用 `el-table`，大数据量时所有行都会渲染。
- Element Plus 2.14.8 已内置 `el-table-v2` 与 `el-auto-resizer`，无需新增依赖。
- `el-table-v2` 官方当前没有交付内置列宽拖动能力，需要在自定义表头中实现拖拽手柄并更新列配置。
- `el-table-v2` 没有 `el-table` 的 selection 列 API，需要用自定义 header/cell + 行 key 实现选择状态。

## 实施方案

### A. 后端查询

- 删除 `MAX_ROWS_PER_TABLE` 与 SQL 末尾 `LIMIT`。
- 查询仍按权限表逐表执行并合并返回，筛选、label 翻译、错误收集逻辑不变。
- 不新增服务端分页契约；本轮目标是完整结果 + 前端虚拟渲染。
- 保持响应结构 `{ rows, errors }` 不变，避免影响既有前端与外部调用。

### B. 虚拟滚动结果表格

- `PermissionQuery.vue` 的结果表格从 `el-table` 迁移到 `el-table-v2`。
- 使用 `el-auto-resizer` 提供表格宽高；表格区域设置合理固定/自适应高度，内部垂直滚动。
- 每条查询结果在前端生成稳定 `_rowKey`（项目/环境/数据库/表/人员/范围/权限组合），作为虚拟表格 rowKey 与选择状态 key。
- 列模型改为数据驱动：
  - selection：固定左侧，44px 左右；header 为全选 checkbox。
  - index：固定左侧，56px 左右，展示当前排序后的序号。
  - 数据列：工号、用户名称、项目、环境、数据库、权限表、数据范围、权限。
  - operation：固定右侧，80px 左右，行内删除。
- 数据列设置更合理的初始宽度、最小/最大宽度与 flexGrow；宽屏下自动填满表格，窄屏横向滚动。
- 通过自定义 header-cell/cell slot 迁移现有显示逻辑：权限 tag、数据范围 label+value、空值 `—`、删除按钮。

### C. 排序、筛选、列宽拖动

- 保留 `rows -> filteredRows -> visibleRows` 数据流和排序键编辑器。
- 表头排序/筛选按钮迁移到 `el-table-v2` 自定义 header-cell；交互契约不变。
- 筛选弹层仍基于接口原始 rows 生成列内去重选项，列间 AND、列内 OR。
- 在数据列表头右缘实现 resize 手柄：
  - pointerdown 记录初始 x 与列宽；document pointermove 更新列宽；pointerup 清理监听。
  - 设置最小/最大宽度，避免拖成不可用大小。
  - selection/index/operation 不可拖动。
  - 拖动时给出全局 col-resize 光标和清晰手柄状态。

### D. 表格视觉优化

方向：企业级高密度数据网格，克制、清晰、可扫描，不做营销式装饰。

- 表格容器使用细边框 + 圆角 + 轻阴影，和面板卡片融合。
- 表头浅色底、12px 半粗体、较高对比；吸顶行为由虚拟表格自身固定 header 承担。
- 行高约 40px，单元格 13px，数字/编号使用 tabular-nums。
- 斑马纹、hover 行、选中行使用非常轻的冷色/品牌色层次，避免过重色块。
- 单元格分隔线轻量化，外边框闭合，固定列阴影保持克制。
- 数据范围以 label 为主、value 弱化；权限使用小型 tag + 弱化 value。
- 空态单独设计：图标/标题/提示，告知调整筛选后重新查询。
- resize 手柄默认细线，hover/dragging 时品牌色加粗。
- 保持键盘 focus-visible、按钮 disabled/hover 状态可用。

### E. 文档、版本与发布

- 实施完成后同步 `docs/design.md`：去掉“每表最多 500 行”说明，补充虚拟滚动表格说明。
- `backend/pom.xml` 升级到下一发布版本（预计 1.0.3）；`AGENTS.md`/运行说明中的 jar 名同步。
- 使用 `scripts/build-exe.ps1` 重新打包 exe，发布后上传 `perm-admin.exe` 与 `permissions.json`。

## 任务拆分

1. 后端 worker：只改 `backend/src/main/java/com/perm/admin/service/PermissionService.java`（必要时可读配置/文档），移除 500 行限制并验证后端编译。
2. 前端 worker：只改 `frontend/src/views/PermissionQuery.vue`（必要时可读 style/App/package/node_modules），完成虚拟表格、选择、排序筛选迁移、列宽拖动与表格美化，并验证前端 build。
3. 集成冒烟 worker（实现完成后启动）：构建前后端，使用隔离配置启动服务，验证 API 不再 LIMIT 500、虚拟表格 DOM 行数受限、排序/筛选/列宽/选择仍可用、console 无错误。

## 验证

- `cd frontend && npm run build`
- `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-26.0.1.8-hotspot" "D:/WorkProject/.tools/apache-maven-3.9.16/bin/mvn" -f backend/pom.xml -DskipTests package`
- API smoke：查询返回不再因单表 500 行截断。
- UI smoke：查询结果渲染、排序、筛选、列宽拖动、全选/单选、行内/批量删除预览、虚拟 DOM 行数远小于总行数。

## 已知风险与处理

- 大结果集仍会产生较大的网络响应和内存占用；这是“完整查询”的必然代价，本轮通过虚拟滚动解决 DOM 渲染瓶颈，不引入分页契约。
- `el-table-v2` 无原生列宽拖动；必须自实现并做浏览器冒烟。
- 多表合并后 rowKey 必须包含来源层级，否则跨表同用户/权限/范围会冲突。

## 实施结果与验证

- 后端：已删除 `MAX_ROWS_PER_TABLE` 与 `LIMIT`，查询不分页不限行；SQL 不再含方言相关的 LIMIT 语法。
- 前端：结果表迁移为 `el-auto-resizer` + `el-table-v2`（fixed 模式）。勾选/序号列固定左、操作列固定右；8 个数据列按内容设初始宽度与 min/max，剩余宽度按 flexGrow 比例分给未拖动过的列以填满宽屏，窄屏横向滚动。
- 行 key：`project|environment|database|tableCode|personId|scopeValue|permissionValue` 各字段 `encodeURIComponent` 后拼接，重复时追加 `#n`（不能含引号，否则 table-v2 hover 拼接属性选择器会报错）。
- 选择：Set 存行 key，跨排序/筛选保持；表头全选作用于当前可见（筛选后）结果；批量删除只取可见的已选行；新查询/执行完成后清空。
- 列宽拖动：数据列表头右缘 8px 手柄，pointer 事件拖动，min 100、max 720，双击恢复默认。
- 冒烟（隔离配置 + 5500 行 SQLite 大表，共 5557 行，1680x1000 与 1280x800）：API 返回全部行；虚拟 DOM 行数 36–57；滚动到底可见末行；排序、表头筛选、列宽拖动、全选/单选、批量与行内删除预览（全选 5557 条生成预览 < 400ms）均通过；固定列 hover 同步；页面无外层滚动条；筛选弹层无横向滚动；console 0 错误。
- 已知：被表头筛选隐藏的已选行保持选中状态，但不会参与批量删除。

## 补充：表格高度自适应（v1.0.4）

- 原 `.result-grid` 高度 `clamp(420px, calc(100vh - 375px), 760px)` 的 760px 上限导致大屏下方空白。
- 改为 JS 动态计算：`max(320, innerHeight - 表格顶部文档位置 - 64)`，64 = 卡片内边距 + 页面底部留白；在 window resize、筛选卡片尺寸变化（ResizeObserver）、排序键/错误提示数量变化时通过 requestAnimationFrame 重算，隐藏 tab 时跳过。
- 验证（1920x1080 / 2560x1300 / 1680x1000 / 1280x800 / 1366x768）：底部间距恒为 64px，页面无外层滚动；加排序键表格同步变矮，清空恢复；同页 resize 与切 tab 往返高度正确；2560 宽屏可视行由 17 增至 22；console 0 错误。
