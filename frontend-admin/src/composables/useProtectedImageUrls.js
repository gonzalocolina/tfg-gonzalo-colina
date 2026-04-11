import { onUnmounted, reactive } from 'vue'

function readAccessToken(sessionStorageKey) {
  const raw = sessionStorage.getItem(sessionStorageKey)
  if (!raw) return null

  try {
    return JSON.parse(raw)?.accessToken || null
  } catch {
    return null
  }
}

export function useProtectedImageUrls(sessionStorageKey) {
  const protectedImageUrls = reactive({})
  const objectUrlsById = new Map()
  const sourceUrlsById = new Map()
  const requestedUrlsById = new Map()
  const inflightLoadsById = new Map()

  function revokeImage(id) {
    const normalizedId = String(id)
    const previousObjectUrl = objectUrlsById.get(normalizedId)
    if (previousObjectUrl) {
      URL.revokeObjectURL(previousObjectUrl)
      objectUrlsById.delete(normalizedId)
    }

    sourceUrlsById.delete(normalizedId)
    requestedUrlsById.delete(normalizedId)
    inflightLoadsById.delete(normalizedId)
    delete protectedImageUrls[normalizedId]
  }

  async function loadImage(entry, { force = false } = {}) {
    const normalizedId = entry?.id == null ? null : String(entry.id)
    const sourceUrl = entry?.url || null

    if (!normalizedId) return null
    if (!sourceUrl) {
      revokeImage(normalizedId)
      return null
    }

    if (!force && sourceUrlsById.get(normalizedId) === sourceUrl && protectedImageUrls[normalizedId]) {
      return protectedImageUrls[normalizedId]
    }

    if (inflightLoadsById.has(normalizedId)) {
      return inflightLoadsById.get(normalizedId)
    }

    const accessToken = readAccessToken(sessionStorageKey)
    if (!accessToken) {
      revokeImage(normalizedId)
      return null
    }

    requestedUrlsById.set(normalizedId, sourceUrl)

    const request = fetch(sourceUrl, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`No se pudo cargar la imagen protegida (${response.status}).`)
        }

        const objectUrl = URL.createObjectURL(await response.blob())
        if (requestedUrlsById.get(normalizedId) !== sourceUrl) {
          URL.revokeObjectURL(objectUrl)
          return null
        }

        const previousObjectUrl = objectUrlsById.get(normalizedId)
        if (previousObjectUrl) {
          URL.revokeObjectURL(previousObjectUrl)
        }

        objectUrlsById.set(normalizedId, objectUrl)
        sourceUrlsById.set(normalizedId, sourceUrl)
        protectedImageUrls[normalizedId] = objectUrl
        return objectUrl
      })
      .catch(() => {
        revokeImage(normalizedId)
        return null
      })
      .finally(() => {
        inflightLoadsById.delete(normalizedId)
      })

    inflightLoadsById.set(normalizedId, request)
    return request
  }

  async function syncImageUrls(entries, options = {}) {
    const normalizedEntries = Array.isArray(entries) ? entries : []
    const nextIds = new Set(normalizedEntries.filter((entry) => entry?.id != null).map((entry) => String(entry.id)))

    for (const currentId of Array.from(sourceUrlsById.keys())) {
      if (!nextIds.has(currentId)) {
        revokeImage(currentId)
      }
    }

    await Promise.all(normalizedEntries.map((entry) => loadImage(entry, options)))
  }

  function getProtectedImageUrl(id) {
    if (id == null) return null
    return protectedImageUrls[String(id)] || null
  }

  function revokeAll() {
    for (const currentId of Array.from(new Set([
      ...objectUrlsById.keys(),
      ...sourceUrlsById.keys(),
      ...requestedUrlsById.keys(),
    ]))) {
      revokeImage(currentId)
    }
  }

  onUnmounted(() => {
    revokeAll()
  })

  return {
    protectedImageUrls,
    getProtectedImageUrl,
    syncImageUrls,
    revokeImage,
    revokeAll,
  }
}
