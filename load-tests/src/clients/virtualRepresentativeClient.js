import { hashSnapshot } from '../assertions/snapshotHash.js'
import { nowMs, toUnixMs } from '../runtime/clock.js'
import { createHttpClient, storeAuth } from '../runtime/httpClient.js'
import { SessionStorageShim } from '../runtime/sessionStorageShim.js'
import { RealtimeWsClient } from '../runtime/wsClient.js'

export class VirtualRepresentativeClient {
  constructor({ clientId, baseUrl, eventId, group, metrics }) {
    this.clientId = clientId
    this.baseUrl = baseUrl
    this.eventId = eventId
    this.group = group
    this.metrics = metrics
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
    this.ws = new RealtimeWsClient({
      baseUrl,
      eventId,
    })
    this.localMessageCounter = 0
    this.messages = []
    this.disconnectCount = 0
    this.reconnectCount = 0
    this.lastSnapshot = null

    this.ws.on('message', (message) => this.handleRealtimeMessage(message))
    this.ws.on('disconnect', () => {
      this.disconnectCount += 1
      this.metrics.upsertClientSession(this.clientId, {
        disconnectCount: this.disconnectCount,
      })
    })
    this.ws.on('connect', () => {
      if (this.messages.length > 0) {
        this.reconnectCount += 1
      }
      this.metrics.upsertClientSession(this.clientId, {
        reconnectCount: this.reconnectCount,
      })
    })
  }

  async loginByRepresentativeCode() {
    const startedAt = nowMs()
    this.metrics.upsertClientSession(this.clientId, {
      groupId: this.group.id,
      groupLabel: this.group.identifier,
      representativeCode: this.group.representativeCode,
      loginStart: new Date(startedAt).toISOString(),
      finalStatus: 'logging-in',
    })

    const response = await this.http.post('/auth/group/login-by-code', {
      code: this.group.representativeCode,
    })

    const finishedAt = nowMs()
    storeAuth(this.sessionStorage, response.data)

    this.metrics.upsertClientSession(this.clientId, {
      loginEnd: new Date(finishedAt).toISOString(),
      loginLatencyMs: finishedAt - startedAt,
      finalStatus: 'authenticated',
    })

    return response.data
  }

  async connectRealtime() {
    const startedAt = nowMs()
    this.metrics.upsertClientSession(this.clientId, {
      wsConnectStart: new Date(startedAt).toISOString(),
      finalStatus: 'connecting-ws',
    })

    await this.ws.connect()

    const finishedAt = nowMs()
    this.metrics.upsertClientSession(this.clientId, {
      wsConnectEnd: new Date(finishedAt).toISOString(),
      wsConnectLatencyMs: finishedAt - startedAt,
      subscriptionsReadyAt: new Date(finishedAt).toISOString(),
      finalStatus: 'connected',
    })
  }

  handleRealtimeMessage(message) {
    const payloadEmittedAt = toUnixMs(message.payload?.emittedAt)
    const snapshot = message.payload?.snapshot || null
    const entry = {
      clientId: this.clientId,
      localMessageId: ++this.localMessageCounter,
      topic: message.topic,
      receivedAt: new Date(message.receivedAt).toISOString(),
      receivedAtMs: message.receivedAt,
      payloadEmittedAt: payloadEmittedAt ? new Date(payloadEmittedAt).toISOString() : '',
      payloadEmittedAtMs: payloadEmittedAt,
      fanoutLatencyMs: payloadEmittedAt == null ? null : message.receivedAt - payloadEmittedAt,
      payloadOperation: message.payload?.operation || '',
      snapshotHash: hashSnapshot(snapshot),
      snapshotVillainHp: snapshot?.villainHpCurrent ?? null,
      snapshotThreat: snapshot?.schemeThreatCurrent ?? null,
      eventFinished: Boolean(message.payload?.eventFinished),
      isDuplicate: false,
      isOutOfOrder: false,
      payload: message.payload,
    }

    entry.messageId = this.metrics.recordDelivery({
      clientId: entry.clientId,
      topic: entry.topic,
      receivedAt: entry.receivedAt,
      payloadEmittedAt: entry.payloadEmittedAt,
      fanoutLatencyMs: entry.fanoutLatencyMs,
      payloadOperation: entry.payloadOperation,
      snapshotHash: entry.snapshotHash,
      snapshotVillainHp: entry.snapshotVillainHp,
      snapshotThreat: entry.snapshotThreat,
      eventFinished: entry.eventFinished,
      isDuplicate: entry.isDuplicate,
      isOutOfOrder: entry.isOutOfOrder,
    })

    if (snapshot) {
      this.lastSnapshot = snapshot
    }

    this.messages.push(entry)
  }

  getMessageCursor() {
    return this.localMessageCounter
  }

  findMessageAfter(cursor, predicate) {
    return this.messages.find((message) => message.localMessageId > cursor && predicate(message)) || null
  }

  async reportVillainDamage(damage, { waveId = '' } = {}) {
    return this.performMutation({
      waveId,
      operationType: 'REPORT_VILLAIN_DAMAGE',
      endpoint: `/events/${this.eventId}/game-state/villain-damage`,
      payload: {
        representativeCode: this.group.representativeCode,
        damage,
      },
    })
  }

  async reportThreat(threat, { waveId = '' } = {}) {
    return this.performMutation({
      waveId,
      operationType: 'REPORT_THREAT',
      endpoint: `/events/${this.eventId}/game-state/threat`,
      payload: {
        representativeCode: this.group.representativeCode,
        threat,
      },
    })
  }

  async removeThreat(threat, { waveId = '' } = {}) {
    return this.performMutation({
      waveId,
      operationType: 'REMOVE_THREAT',
      endpoint: `/events/${this.eventId}/game-state/threat/remove`,
      payload: {
        representativeCode: this.group.representativeCode,
        threat,
      },
    })
  }

  async performMutation({ waveId, operationType, endpoint, payload }) {
    const sentAtMs = nowMs()
    const opId = this.metrics.recordOperationStart({
      waveId,
      actorClientId: this.clientId,
      actorGroupId: this.group.id,
      operationType,
      endpoint,
      requestPayloadSummary: JSON.stringify(payload),
      sentAt: new Date(sentAtMs).toISOString(),
    })

    try {
      const response = await this.http.post(endpoint, payload)
      const finishedAtMs = nowMs()
      const snapshot = response.data?.snapshot || null

      this.metrics.completeOperation(opId, {
        httpResponseAt: new Date(finishedAtMs).toISOString(),
        httpLatencyMs: finishedAtMs - sentAtMs,
        httpStatus: response.status,
        responseSnapshotHash: hashSnapshot(snapshot),
        responseSnapshotVillainHp: snapshot?.villainHpCurrent ?? null,
        responseSnapshotThreat: snapshot?.schemeThreatCurrent ?? null,
        timeoutFlag: false,
      })

      return {
        opId,
        sentAtMs,
        response: response.data,
      }
    } catch (error) {
      const finishedAtMs = nowMs()
      this.metrics.completeOperation(opId, {
        httpResponseAt: new Date(finishedAtMs).toISOString(),
        httpLatencyMs: finishedAtMs - sentAtMs,
        httpStatus: error.response?.status ?? 0,
        timeoutFlag: false,
        failureMessage: error.message,
      })
      throw error
    }
  }

  async disconnect() {
    await this.ws.disconnect()
    this.metrics.upsertClientSession(this.clientId, {
      finalStatus: 'disconnected',
    })
  }
}
