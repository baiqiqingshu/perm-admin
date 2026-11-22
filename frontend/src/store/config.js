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
