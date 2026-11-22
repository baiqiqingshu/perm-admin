import request, { extractErrorMessage } from './request'

export { extractErrorMessage }

export function getConfig() {
  return request.get('/config')
}

export function reloadConfig() {
  return request.post('/config/reload')
}

export function queryPermissions(params) {
  return request.post('/permissions/query', params)
}

export function previewSql(payload) {
  return request.post('/sql/preview', payload)
}

export function executeSql(payload) {
  // 失败由 ExecuteResultDialog 统一展示，拦截器不再弹 toast
  return request.post('/sql/execute', payload, { silentError: true })
}
