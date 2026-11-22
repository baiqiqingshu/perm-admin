import { reactive } from 'vue'
import { getConfig } from '../api'

/** 全局共享的配置树状态（/api/config） */
export const configState = reactive({
  loaded: false,
  loading: false,
  projects: []
})

export async function loadConfig(force = false) {
  if (configState.loading) return
  if (configState.loaded && !force) return
  configState.loading = true
  try {
    const data = await getConfig()
    configState.projects = (data && data.projects) || []
    configState.loaded = true
  } finally {
    configState.loading = false
  }
}

/** 遍历配置树所有权限表 */
export function eachTable(fn) {
  for (const p of configState.projects || []) {
    for (const e of p.environments || []) {
      for (const d of e.databases || []) {
        for (const t of d.permissionTables || []) {
          fn(t, { project: p, environment: e, database: d })
        }
      }
    }
  }
}

/**
 * 打平配置树为 (database, permissionTable) 目标条目列表。
 * 每条带完整祖先级 code/name + 该表支持的权限/范围 label 集合，
 * 供新增权限页的级联过滤与"全部已选 label 可满足"判定使用。
 */
export function collectTableEntries() {
  const list = []
  eachTable((t, { project: p, environment: e, database: d }) => {
    list.push({
      project: p.code,
      projectName: p.name,
      env: e.code,
      envName: e.name,
      db: d.code,
      dbName: d.name,
      tableCode: t.code,
      tableName: t.tableName,
      tableTitle: t.name,
      permLabels: new Set((t.permissions || []).map((o) => o.label)),
      scopeLabels: new Set((t.scopes || []).map((o) => o.label))
    })
  })
  return list
}

/** 全配置去重的权限显示名 */
export function collectPermissionLabels() {
  const set = new Set()
  eachTable((t) => (t.permissions || []).forEach((p) => set.add(p.label)))
  return [...set]
}

/** 全配置去重的数据范围显示名 */
export function collectScopeLabels() {
  const set = new Set()
  eachTable((t) => (t.scopes || []).forEach((s) => set.add(s.label)))
  return [...set]
}
