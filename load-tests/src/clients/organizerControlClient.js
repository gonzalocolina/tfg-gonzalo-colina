import { summarizeSnapshot } from '../assertions/snapshotHash.js'
import { nowMs } from '../runtime/clock.js'
import { createHttpClient, storeAuth } from '../runtime/httpClient.js'
import { SessionStorageShim } from '../runtime/sessionStorageShim.js'

export class OrganizerControlClient {
  constructor({ baseUrl, metrics, clientId = 'organizer-control' }) {
    this.baseUrl = baseUrl
    this.metrics = metrics
    this.clientId = clientId
    this.sessionStorage = new SessionStorageShim()
    this.http = createHttpClient({
      baseUrl,
      sessionStorage: this.sessionStorage,
      onUnauthorized: (error) => {
        this.metrics.recordError({
          scope: 'auth',
          clientId: this.clientId,
          message: error.message,
        })
      },
    })
  }

  async login(username, password) {
    const startedAt = nowMs()
    this.metrics.upsertClientSession(this.clientId, {
      loginStart: new Date(startedAt).toISOString(),
      finalStatus: 'logging-in',
    })

    const response = await this.http.post('/auth/organizer/login', { username, password })
    const finishedAt = nowMs()
    storeAuth(this.sessionStorage, response.data)

    this.metrics.upsertClientSession(this.clientId, {
      loginEnd: new Date(finishedAt).toISOString(),
      loginLatencyMs: finishedAt - startedAt,
      finalStatus: 'authenticated',
    })

    return response.data
  }

  async getVillains() {
    const response = await this.http.get('/villains')
    return response.data
  }

  async createEvent(payload) {
    const response = await this.http.post('/events', payload)
    return response.data
  }

  async createPod(eventId, payload) {
    const response = await this.http.post(`/events/${eventId}/pods`, payload)
    return response.data
  }

  async createGroup(eventId, podId, payload) {
    const response = await this.http.post(`/events/${eventId}/pods/${podId}/groups`, payload)
    return response.data
  }

  async getEventStructure(eventId) {
    const response = await this.http.get(`/events/${eventId}/structure`)
    return response.data
  }

  async startGame(eventId) {
    const response = await this.http.post(`/events/${eventId}/game-state/start`)
    return response.data
  }

  async startTimer(eventId) {
    const response = await this.http.post(`/events/${eventId}/game-state/timer/start`)
    return response.data
  }

  async getGameState(eventId) {
    const response = await this.http.get(`/events/${eventId}/game-state`)
    return response.data
  }

  async updateThreat(eventId, value, justification) {
    const response = await this.http.patch(`/events/${eventId}/game-state/threat`, {
      value,
      justification: justification || undefined,
    })
    return response.data
  }

  async updateVillainHp(eventId, value, justification) {
    const response = await this.http.patch(`/events/${eventId}/game-state/villain-hp`, {
      value,
      justification: justification || undefined,
    })
    return response.data
  }

  summarizeSnapshot(snapshot) {
    return summarizeSnapshot(snapshot)
  }
}
