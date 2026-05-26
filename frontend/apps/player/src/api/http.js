import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
})

function isAuthRequest(url) {
  return typeof url === 'string' && url.startsWith('/auth/')
}

function resolveAppPath(path) {
  const baseUrl = import.meta.env.BASE_URL || '/'
  const normalizedBase = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`
  const normalizedPath = path.startsWith('/') ? path.slice(1) : path

  return `${normalizedBase}${normalizedPath}`.replace(/\/+/g, '/')
}

http.interceptors.request.use((config) => {
  config.headers = config.headers || {}

  if (config.data instanceof FormData) {
    delete config.headers['Content-Type']
    delete config.headers['content-type']
  } else if (!config.headers['Content-Type'] && !config.headers['content-type']) {
    config.headers['Content-Type'] = 'application/json'
  }

  const raw = sessionStorage.getItem('auth-player')
  if (raw && !isAuthRequest(config.url)) {
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
      if (!isAuthRequest(err.config?.url)) {
        globalThis.location?.replace(resolveAppPath('/join'))
      }
    }
    return Promise.reject(err)
  },
)

export default http
