import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const data = error.response?.data
    let msg = '请求失败'
    if (data) {
      if (typeof data === 'string') msg = data
      else msg = data.error || data.message || JSON.stringify(data)
    } else if (error.message) {
      msg = error.message
    }
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
