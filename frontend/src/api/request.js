import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

/** 从 axios 错误中提取可读信息（与拦截器提示口径一致） */
export function extractErrorMessage(error) {
  const data = error?.response?.data
  if (data) {
    if (typeof data === 'string') return data
    return data.error || data.message || JSON.stringify(data)
  }
  return error?.message || '请求失败'
}

request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    // silentError：调用方自行接管错误展示（如执行结果弹窗），不再弹 toast
    if (!error.config?.silentError) {
      ElMessage.error(extractErrorMessage(error))
    }
    return Promise.reject(error)
  }
)

export default request
