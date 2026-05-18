import { execFile } from 'node:child_process'
import { mkdir, writeFile } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import { promisify } from 'node:util'
import { timestampSlug } from '../runtime/clock.js'

const execFileAsync = promisify(execFile)

function quoteCsv(value) {
  if (value == null) return ''
  const serialized = String(value)
  if (!/[",\n]/.test(serialized)) return serialized
  return `"${serialized.replaceAll('"', '""')}"`
}

function toCsv(rows, columns) {
  const header = columns.join(',')
  const lines = rows.map((row) => columns.map((column) => quoteCsv(row[column])).join(','))
  return `${header}\n${lines.join('\n')}`
}

function percentile(values, ratio) {
  if (!values.length) return null
  const sorted = [...values].sort((left, right) => left - right)
  const index = Math.min(sorted.length - 1, Math.max(0, Math.ceil(sorted.length * ratio) - 1))
  return sorted[index]
}

function latencyStats(values) {
  const filtered = values.filter((value) => Number.isFinite(value))
  if (!filtered.length) {
    return {
      count: 0,
      min: null,
      p50: null,
      p95: null,
      p99: null,
      max: null,
    }
  }

  return {
    count: filtered.length,
    min: Math.min(...filtered),
    p50: percentile(filtered, 0.5),
    p95: percentile(filtered, 0.95),
    p99: percentile(filtered, 0.99),
    max: Math.max(...filtered),
  }
}

function sanitizeSegment(value) {
  return String(value || '')
    .trim()
    .replace(/[^a-zA-Z0-9-_]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
}

async function getGitCommit(cwd) {
  try {
    const { stdout } = await execFileAsync('git', ['rev-parse', 'HEAD'], { cwd })
    return stdout.trim()
  } catch {
    return null
  }
}

export class MetricsCollector {
  static async create(config) {
    const collector = new MetricsCollector(config)
    collector.gitCommit = await getGitCommit(config.projectRoot)
    collector.runId = [
      timestampSlug(),
      sanitizeSegment(config.scenarioName),
      sanitizeSegment(config.runLabel),
    ]
      .filter(Boolean)
      .join('-')
    collector.runDir = path.join(config.resultsDir, collector.runId)
    return collector
  }

  constructor(config) {
    this.config = config
    this.scenarioName = config.scenarioName
    this.runId = null
    this.runDir = null
    this.gitCommit = null
    this.clientSessions = []
    this.clientSessionIndex = new Map()
    this.operations = []
    this.deliveries = []
    this.resourceSamples = []
    this.errors = []
    this.operationCounter = 0
    this.deliveryCounter = 0
  }

  async initialize() {
    await mkdir(this.runDir, { recursive: true })
  }

  upsertClientSession(clientId, patch) {
    if (!this.clientSessionIndex.has(clientId)) {
      const base = {
        clientId,
        groupId: '',
        groupLabel: '',
        representativeCode: '',
        loginStart: '',
        loginEnd: '',
        loginLatencyMs: '',
        wsConnectStart: '',
        wsConnectEnd: '',
        wsConnectLatencyMs: '',
        subscriptionsReadyAt: '',
        disconnectCount: 0,
        reconnectCount: 0,
        finalStatus: '',
      }
      this.clientSessionIndex.set(clientId, this.clientSessions.length)
      this.clientSessions.push(base)
    }

    const target = this.clientSessions[this.clientSessionIndex.get(clientId)]
    Object.assign(target, patch)
    return target
  }

  recordOperationStart(record) {
    const opId = `op-${String(++this.operationCounter).padStart(5, '0')}`
    this.operations.push({
      opId,
      scenario: this.scenarioName,
      waveId: '',
      actorClientId: '',
      actorGroupId: '',
      operationType: '',
      endpoint: '',
      requestPayloadSummary: '',
      sentAt: '',
      httpResponseAt: '',
      httpLatencyMs: '',
      httpStatus: '',
      responseSnapshotHash: '',
      responseSnapshotVillainHp: '',
      responseSnapshotThreat: '',
      timeoutFlag: false,
      failureMessage: '',
      ...record,
      opId,
    })
    return opId
  }

  completeOperation(opId, patch) {
    const target = this.operations.find((operation) => operation.opId === opId)
    if (!target) return null
    Object.assign(target, patch)
    return target
  }

  recordDelivery(record) {
    const messageId = `msg-${String(++this.deliveryCounter).padStart(6, '0')}`
    this.deliveries.push({
      messageId,
      opId: '',
      clientId: '',
      topic: '',
      receivedAt: '',
      payloadEmittedAt: '',
      fanoutLatencyMs: '',
      payloadOperation: '',
      snapshotHash: '',
      snapshotVillainHp: '',
      snapshotThreat: '',
      eventFinished: false,
      isDuplicate: false,
      isOutOfOrder: false,
      ...record,
      messageId,
    })
    return messageId
  }

  linkDeliveriesToOperation(opId, messageIds) {
    const expected = new Set(messageIds)
    for (const delivery of this.deliveries) {
      if (expected.has(delivery.messageId)) {
        delivery.opId = opId
      }
    }
  }

  recordResourceSample(record) {
    this.resourceSamples.push(record)
  }

  recordError(record) {
    this.errors.push({
      timestamp: new Date().toISOString(),
      ...record,
    })
  }

  buildManifest(status, scenarioDescription) {
    return {
      runId: this.runId,
      scenarioName: this.scenarioName,
      scenarioDescription,
      status,
      generatedAt: new Date().toISOString(),
      gitCommit: this.gitCommit,
      host: os.hostname(),
      platform: process.platform,
      arch: process.arch,
      nodeVersion: process.version,
      config: {
        baseUrl: this.config.baseUrl,
        clients: this.config.clients,
        podCount: this.config.podCount,
        groupsPerPod: this.config.groupsPerPod,
        playersPerGroup: this.config.playersPerGroup,
        timeLimitMinutes: this.config.timeLimitMinutes,
        startTimer: this.config.startTimer,
        rampStep: this.config.rampStep,
        rampIntervalMs: this.config.rampIntervalMs,
        holdMs: this.config.holdMs,
        deliveryTimeoutMs: this.config.deliveryTimeoutMs,
        writerCount: this.config.writerCount,
        operationIntervalMs: this.config.operationIntervalMs,
        damageValue: this.config.damageValue,
        threatValue: this.config.threatValue,
        waveCount: this.config.waveCount,
        waveCooldownMs: this.config.waveCooldownMs,
        pulseIntervalMs: this.config.pulseIntervalMs,
        pulseValues: this.config.pulseValues,
      },
    }
  }

  buildSummary(status, scenarioDescription, result, failureMessage) {
    const loginLatencies = this.clientSessions.map((session) => Number(session.loginLatencyMs))
    const wsLatencies = this.clientSessions.map((session) => Number(session.wsConnectLatencyMs))
    const httpLatencies = this.operations.map((operation) => Number(operation.httpLatencyMs))
    const fanoutLatencies = this.deliveries
      .filter((delivery) => delivery.opId)
      .map((delivery) => Number(delivery.fanoutLatencyMs))

    return {
      runId: this.runId,
      scenarioName: this.scenarioName,
      scenarioDescription,
      status,
      generatedAt: new Date().toISOString(),
      failureMessage: failureMessage || null,
      totals: {
        clientSessions: this.clientSessions.length,
        operations: this.operations.length,
        deliveries: this.deliveries.length,
        linkedDeliveries: this.deliveries.filter((delivery) => delivery.opId).length,
        errors: this.errors.length,
      },
      latencies: {
        loginMs: latencyStats(loginLatencies),
        websocketConnectMs: latencyStats(wsLatencies),
        httpMs: latencyStats(httpLatencies),
        fanoutMs: latencyStats(fanoutLatencies),
      },
      result,
    }
  }

  async finalize({ status, scenarioDescription, result, failureMessage = null }) {
    const manifest = this.buildManifest(status, scenarioDescription)
    const summary = this.buildSummary(status, scenarioDescription, result, failureMessage)

    await writeFile(path.join(this.runDir, 'manifest.json'), JSON.stringify(manifest, null, 2))
    await writeFile(path.join(this.runDir, 'summary.json'), JSON.stringify(summary, null, 2))
    await writeFile(
      path.join(this.runDir, 'client-sessions.csv'),
      toCsv(this.clientSessions, [
        'clientId',
        'groupId',
        'groupLabel',
        'representativeCode',
        'loginStart',
        'loginEnd',
        'loginLatencyMs',
        'wsConnectStart',
        'wsConnectEnd',
        'wsConnectLatencyMs',
        'subscriptionsReadyAt',
        'disconnectCount',
        'reconnectCount',
        'finalStatus',
      ]),
    )
    await writeFile(
      path.join(this.runDir, 'operations.csv'),
      toCsv(this.operations, [
        'opId',
        'scenario',
        'waveId',
        'actorClientId',
        'actorGroupId',
        'operationType',
        'endpoint',
        'requestPayloadSummary',
        'sentAt',
        'httpResponseAt',
        'httpLatencyMs',
        'httpStatus',
        'responseSnapshotHash',
        'responseSnapshotVillainHp',
        'responseSnapshotThreat',
        'timeoutFlag',
        'failureMessage',
      ]),
    )
    await writeFile(
      path.join(this.runDir, 'deliveries.csv'),
      toCsv(this.deliveries, [
        'messageId',
        'opId',
        'clientId',
        'topic',
        'receivedAt',
        'payloadEmittedAt',
        'fanoutLatencyMs',
        'payloadOperation',
        'snapshotHash',
        'snapshotVillainHp',
        'snapshotThreat',
        'eventFinished',
        'isDuplicate',
        'isOutOfOrder',
      ]),
    )
    await writeFile(
      path.join(this.runDir, 'resource-samples.csv'),
      toCsv(this.resourceSamples, [
        'timestamp',
        'backendCpuPct',
        'backendRssMb',
        'backendHeapUsedMb',
        'backendThreadCount',
        'nodeCpuPct',
        'nodeRssMb',
        'nodeHeapUsedMb',
        'systemCpuPct',
        'systemMemoryUsedMb',
      ]),
    )

    if (this.errors.length) {
      await writeFile(path.join(this.runDir, 'errors.json'), JSON.stringify(this.errors, null, 2))
    }
  }
}
