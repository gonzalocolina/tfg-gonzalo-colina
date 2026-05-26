import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
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
  if (globalThis.FormData !== undefined && config.data instanceof globalThis.FormData) {
    delete config.headers['Content-Type']
  }
  const raw = globalThis.sessionStorage?.getItem('auth')
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
      globalThis.sessionStorage?.removeItem('auth')
      if (!isAuthRequest(err.config?.url)) {
        globalThis.location?.replace(resolveAppPath('/login'))
      }
    }
    return Promise.reject(err)
  },
)

export default http
