import axios from 'axios'

export const AUTH_STORAGE_KEY = 'auth'

function isAuthRequest(url) {
  return typeof url === 'string' && url.startsWith('/auth/')
}

function deleteContentTypeHeader(headers) {
  if (!headers) return
  if (typeof headers.delete === 'function') {
    headers.delete('Content-Type')
    return
  }
  delete headers['Content-Type']
}

export function readStoredAuth(sessionStorage) {
  const raw = sessionStorage?.getItem(AUTH_STORAGE_KEY)
  if (!raw) return null

  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function storeAuth(sessionStorage, authPayload) {
  sessionStorage?.setItem(AUTH_STORAGE_KEY, JSON.stringify(authPayload))
}

export function clearAuth(sessionStorage) {
  sessionStorage?.removeItem(AUTH_STORAGE_KEY)
}

export function createHttpClient({ baseUrl, sessionStorage, onUnauthorized }) {
  const client = axios.create({
    baseURL: `${baseUrl}/api`,
    headers: { 'Content-Type': 'application/json' },
  })

  client.interceptors.request.use((config) => {
    if (globalThis.FormData !== undefined && config.data instanceof globalThis.FormData) {
      deleteContentTypeHeader(config.headers)
    }

    const auth = readStoredAuth(sessionStorage)
    if (auth?.accessToken && !isAuthRequest(config.url)) {
      config.headers = config.headers || {}
      config.headers.Authorization = `Bearer ${auth.accessToken}`
    }

    return config
  })

  client.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error.response?.status === 401) {
        clearAuth(sessionStorage)
        onUnauthorized?.(error)
      }

      return Promise.reject(error)
    },
  )

  return client
}
