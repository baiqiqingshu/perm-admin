# 迭代2：批量新增权限重构 + 前端重设计

## 后端改动

### POST /api/sql/preview —— insert 新契约（delete 不变）

请求：
```json
{
  "type": "insert",
  "targets": {
    "projects": ["demo"],        // 可选，空/缺省=全部
    "environments": ["dev"],      // 可选
    "databases": ["erp-dev-db"],  // 可选
    "tableCodes": ["user_perm"]   // 可选
  },
  "persons": [{"id": "u001", "name": "张三"}],
  "permissionLabels": ["查询", "编辑"],
  "scopeLabels": ["全部数据"]
}
```

解析与生成规则：
1. 目标表 = 配置树中匹配 targets 过滤的全部 (database, permissionTable) 对（targets 某级为空表示该级不筛选）。
2. 对每张目标表，把 permissionLabels/scopeLabels 按该表配置翻译成实际 value：
   - 某 label 在该表无对应配置 → 该表跳过该 label，记入 `unsupported` 返回（不报错）。
   - 兼容旧契约：若仍传 `permissions`/`scopes`（裸 value），按该表原始值直接使用。
3. **去重**：对每张表先查已有三元组 `SELECT personF, scopeF, permF FROM t WHERE personF IN (...)`，(personId, scopeVal, permVal) 已存在的组合跳过，记入 `skipped`；不生成对应 INSERT。personName 不参与去重判断（唯一性=工号×范围×权限）。
4. 其余（笛卡尔积、otherFields 占位符、按 db+table 分组）同前。

响应在原有 `groups` 基础上增加：
```json
{
  "groups": [...],
  "skipped": [{"dbCode":"erp-dev-db","tableName":"role_grant","personId":"u001",
               "permissionLabel":"查询","scopeLabel":"全部数据"}],
  "unsupported": [{"dbCode":"erp-prod-db","tableName":"sys_permission",
                   "type":"permission","label":"删除"}]
}
```

## 前端改动

### 新增权限页重构（PermissionAdd）
- **人员**：textarea，每行 `工号` 或 `工号,姓名`（兼容中文逗号、去重）。
- **目标范围**：6 个 `el-select multiple filterable`：项目、环境、数据库、权限表（额外补充项）、权限（显示名）、数据范围（显示名）。选项来自 /api/config 树；留空=全部；下方实时摘要"将写入 X 个项目 · Y 个环境 · Z 个库 · N 张表"。
- **级联约束**：环境选项受已选项目过滤、库受已选环境过滤、表受已选库过滤（上级为空时下级显示全量可选项）。
- **自动取消勾选**：勾选权限/范围 label 后，剔除无法满足全部已选 label 的目标——权限表需同时支持所有已选 permissionLabels 与 scopeLabels 才保留；其上级库/环境/项目同理。即"某项目没有该权限→自动取消勾选该项目/环境/库/表"。权限或范围清空时恢复全量可选。
- **预览**：POST 新 insert 契约 → SqlPreviewDialog 展示 groups；顶部展示 `skipped`（已存在将跳过 N 条，可展开明细）与 `unsupported`（部分表不支持 label 的警告）→ 执行。
- 批量提交后端时 persons 全量提交（预览/执行一致）。

### 全局重设计
按 `.agents/skills/impeccable` 与 `.agents/skills/ui-ux-pro-max`（先读各自 SKILL.md 再按其指引读 references）执行，用户审美要求优先：
- 现代质感：柔和弥散阴影、卡片悬停上浮；渐变仅用于品牌区/聚焦态/导航点缀；**按钮不用渐变**；磨砂玻璃顶栏；中文文案、SVG 图标、圆角卡片按钮；现代配色；隐藏组件（el-select 下拉、tag、tooltip、dialog、drawer、message、pagination）同样设计到位；减少"AI感"（避免滥用渐变/紫蓝大渐变标题/emoji装饰）。
- 权限查询页同步重构：筛选区卡片化、表格斑马纹/hover、操作按钮规范、errors 提示条美观。
- 保留全部现有功能，可微调按钮与布局（含删除按钮形态）。
