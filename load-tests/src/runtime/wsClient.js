import { EventEmitter } from 'node:events'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { nowMs } from './clock.js'
import { TimeoutError } from './errors.js'

export class RealtimeWsClient extends EventEmitter {
  constructor({ baseUrl, eventId, reconnectDelay = 3000, connectTimeoutMs = 15000 }) {
    super()
    this.baseUrl = baseUrl
    this.eventId = eventId
    this.reconnectDelay = reconnectDelay
    this.connectTimeoutMs = connectTimeoutMs
    this.connected = false
    this.client = null
    this.connectPromise = null
    this.messages = []
    this.subscriptions = []
  }

  async connect() {
    if (this.connected) return
    if (this.connectPromise) return this.connectPromise

    this.connectPromise = new Promise((resolve, reject) => {
      let settled = false
      let timeoutId = null

      const resolveOnce = () => {
        if (settled) return
        settled = true
        clearTimeout(timeoutId)
        resolve()
      }

      const rejectOnce = (error) => {
        if (settled) return
        settled = true
        clearTimeout(timeoutId)
        reject(error)
      }

      this.client = new Client({
        webSocketFactory: () => new SockJS(`${this.baseUrl}/ws`),
        reconnectDelay: this.reconnectDelay,
        onConnect: () => {
          this.connected = true
          this.subscribeDefaultTopics()
          this.emit('connect')
          resolveOnce()
        },
        onDisconnect: () => {
          this.connected = false
          this.emit('disconnect')
        },
        onStompError: (frame) => {
          const error = new Error(`STOMP error: ${frame.headers?.message || 'sin detalle'}`)
          this.connected = false
          this.emit('stomp-error', error)
          rejectOnce(error)
        },
        onWebSocketError: (event) => {
          const error = new Error(`WebSocket error: ${event?.message || 'sin detalle'}`)
          this.connected = false
          this.emit('websocket-error', error)
          rejectOnce(error)
        },
        debug: () => {},
      })

      timeoutId = setTimeout(() => {
        rejectOnce(new TimeoutError(`Timeout conectando al websocket del evento ${this.eventId}`))
      }, this.connectTimeoutMs)

      this.client.activate()
    })
      .finally(() => {
        this.connectPromise = null
      })

    return this.connectPromise
  }

  subscribeDefaultTopics() {
    if (!this.client || !this.connected || this.subscriptions.length) return

    const topics = [
      `/topic/events/${this.eventId}/game-state`,
      `/topic/events/${this.eventId}/notifications`,
      `/topic/events/${this.eventId}/game-finished`,
    ]

    this.subscriptions = topics.map((topic) => this.client.subscribe(topic, (message) => {
      this.handleMessage(topic, message)
    }))
  }

  handleMessage(topic, message) {
    let payload = null
    try {
      payload = JSON.parse(message.body)
    } catch {
      payload = { rawBody: message.body }
    }

    const event = {
      topic,
      payload,
      receivedAt: nowMs(),
    }

    this.messages.push(event)
    this.emit('message', event)
    this.emit(topic, event)
  }

  async disconnect() {
    if (!this.client) return

    const activeClient = this.client
    this.client = null
    this.subscriptions = []
    this.connected = false
    await activeClient.deactivate()
  }
}
