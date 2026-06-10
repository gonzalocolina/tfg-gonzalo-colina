import { execFile } from 'node:child_process'
import { once } from 'node:events'
import { createWriteStream } from 'node:fs'
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

function toCsvRow(row, columns) {
  return columns.map((column) => quoteCsv(row[column])).join(',')
}

function percentileFromSorted(sorted, ratio) {
  if (!sorted.length) return null
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

  filtered.sort((left, right) => left - right)

  return {
    count: filtered.length,
    min: filtered[0],
    p50: percentileFromSorted(filtered, 0.5),
    p95: percentileFromSorted(filtered, 0.95),
    p99: percentileFromSorted(filtered, 0.99),
    max: filtered[filtered.length - 1],
  }
}

async function writeCsvFile(filePath, rows, columns, chunkSize = 5000) {
  const stream = createWriteStream(filePath, { encoding: 'utf8' })
  const handleError = (error) => {
    stream.destroy(error)
  }

  try {
    stream.write(`${columns.join(',')}\n`)

    for (let index = 0; index < rows.length; index += chunkSize) {
      const chunkRows = rows.slice(index, index + chunkSize)
      const chunk = chunkRows.map((row) => toCsvRow(row, columns)).join('\n')
      const suffix = index + chunkSize < rows.length ? '\n' : ''

      if (!stream.write(chunk + suffix)) {
        await once(stream, 'drain')
      }
    }

    stream.end()
    await once(stream, 'finish')
  } catch (error) {
    handleError(error)
    throw error
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
    this.operationIndex = new Map()
    this.deliveries = []
    this.deliveryIndex = new Map()
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
        eventCode: '',
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
    const operationRecord = {
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
      fanoutComplete: '',
      fanoutDeliveredClients: '',
      fanoutMissingCount: '',
      fanoutMinLatencyMs: '',
      fanoutMaxLatencyMs: '',
      fanoutFailureMessage: '',
      timeoutFlag: false,
      failureMessage: '',
      ...record,
    }

    this.operationIndex.set(opId, this.operations.length)
    this.operations.push(operationRecord)
    return opId
  }

  completeOperation(opId, patch) {
    const operationIndex = this.operationIndex.get(opId)
    if (operationIndex == null) return null
    const target = this.operations[operationIndex]
    if (!target) return null
    Object.assign(target, patch)
    return target
  }

  recordDelivery(record) {
    const messageId = `msg-${String(++this.deliveryCounter).padStart(6, '0')}`
    const deliveryRecord = {
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
    }

    this.deliveryIndex.set(messageId, this.deliveries.length)
    this.deliveries.push(deliveryRecord)
    return messageId
  }

  linkDeliveriesToOperation(opId, messageIds) {
    for (const messageId of messageIds) {
      const deliveryIndex = this.deliveryIndex.get(messageId)
      if (deliveryIndex == null) {
        continue
      }

      const delivery = this.deliveries[deliveryIndex]
      if (delivery) {
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
    const operationsWithFanout = this.operations.filter((operation) => typeof operation.fanoutComplete === 'boolean')
    const incompleteFanoutOperations = operationsWithFanout.filter((operation) => operation.fanoutComplete === false)
    const completeFanoutOperations = operationsWithFanout.filter((operation) => operation.fanoutComplete === true)
    const maxMissingClients = incompleteFanoutOperations.reduce(
      (currentMax, operation) => Math.max(currentMax, Number(operation.fanoutMissingCount) || 0),
      0,
    )

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
      fanoutQuality: {
        observedOperations: operationsWithFanout.length,
        completeOperations: completeFanoutOperations.length,
        incompleteOperations: incompleteFanoutOperations.length,
        maxMissingClients,
      },
      result,
    }
  }

  async finalize({ status, scenarioDescription, result, failureMessage = null }) {
    const manifest = this.buildManifest(status, scenarioDescription)
    const summary = this.buildSummary(status, scenarioDescription, result, failureMessage)

    await writeFile(path.join(this.runDir, 'manifest.json'), JSON.stringify(manifest, null, 2))
    await writeFile(path.join(this.runDir, 'summary.json'), JSON.stringify(summary, null, 2))
    await writeCsvFile(
      path.join(this.runDir, 'client-sessions.csv'),
      this.clientSessions,
      [
        'clientId',
        'groupId',
        'groupLabel',
        'eventCode',
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
      ],
    )
    await writeCsvFile(
      path.join(this.runDir, 'operations.csv'),
      this.operations,
      [
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
        'fanoutComplete',
        'fanoutDeliveredClients',
        'fanoutMissingCount',
        'fanoutMinLatencyMs',
        'fanoutMaxLatencyMs',
        'fanoutFailureMessage',
        'timeoutFlag',
        'failureMessage',
      ],
    )
    await writeCsvFile(
      path.join(this.runDir, 'deliveries.csv'),
      this.deliveries,
      [
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
      ],
    )
    await writeCsvFile(
      path.join(this.runDir, 'resource-samples.csv'),
      this.resourceSamples,
      [
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
      ],
    )

    if (this.errors.length) {
      await writeFile(path.join(this.runDir, 'errors.json'), JSON.stringify(this.errors, null, 2))
    }
  }
}
