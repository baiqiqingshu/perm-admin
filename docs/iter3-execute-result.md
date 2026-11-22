# 迭代3：批量执行结果统一确认

## 需求
批量新增/批量删除的执行完成提示从简单 toast 改为：后端全部执行完成后，把「成功哪些、失败哪些、跳过哪些」统一返回并给用户一个确认界面。

## 后端
POST /api/sql/execute 已是同步一次返回：
```json
{"results":[{"key":"db|table","success":true,"rows":2,"error":null}]}
```
信息足够（配合 preview 返回的 groups 元数据可展示 dbName/tableName）。**无需改动**；若前端需要，执行响应体即为最终结果。

## 前端
新增 `ExecuteResultDialog` 组件，替换原来逐组 ElMessage 的提示方式：

- 触发时机：preview 弹窗点「确定执行」→ 调 /api/sql/execute → **执行请求全部结束后**弹出结果确认。
- 内容：
  - 顶部汇总卡片：`共提交 N 条 · 成功 X 条 · 失败 Y 组 · 跳过 Z 条`（跳过=preview 返回的 skipped 条数；另有 unsupported M 项不支持时一并提示）。
  - 明细区（分栏或分组列表）：
    - **成功**：每组 `dbName (tableName)` + 成功条数 rows + 绿色标识；组内可按 statement.sql 展开（可省略，优先汇总）。
    - **失败**：每组 `dbName (tableName)` + error 信息 + 红色标识。
    - **跳过**：preview.skipped 明细（工号/权限label/范围label/表名）；preview.unsupported 明细（表/type/label）。
  - 底部「确定」确认按钮；结果未确认前不自动关闭。
- 删除流程：结果确认后刷新查询列表；新增流程：确认后按原逻辑重置表单。
- 执行中保持 loading，禁用重复提交；execute 网络层失败（非 200）按失败组同等展示而非静默 toast。
- 样式沿用现有设计系统（iter2 的 tokens/组件风格），保持磨砂卡片质感。
