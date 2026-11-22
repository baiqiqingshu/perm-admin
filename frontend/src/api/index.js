import request from './request'

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
  return request.post('/sql/execute', payload)
}
