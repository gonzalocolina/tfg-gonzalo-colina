import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
})

http.interceptors.request.use((config) => {
  config.headers = config.headers || {}

  if (config.data instanceof FormData) {
    delete config.headers['Content-Type']
    delete config.headers['content-type']
  } else if (!config.headers['Content-Type'] && !config.headers['content-type']) {
    config.headers['Content-Type'] = 'application/json'
  }

  const raw = sessionStorage.getItem('auth-player')
  if (raw) {
    try {
      const { accessToken } = JSON.parse(raw)
      if (accessToken) {
        config.headers.Authorization = `Bearer ${accessToken}`
      }
    } catch {
      /* ignore malformed data */
    }
  }
  return config
})

http.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      sessionStorage.removeItem('auth-player')
      globalThis.location?.replace('/join')
    }
    return Promise.reject(err)
  },
)

export default http
