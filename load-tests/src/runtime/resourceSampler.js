import os from 'node:os'
import { nowMs } from './clock.js'

function cloneCpuTimes() {
  return os.cpus().map((cpu) => ({ ...cpu.times }))
}

function aggregateCpuDiff(previous, current) {
  let idle = 0
  let total = 0

  for (let index = 0; index < current.length; index += 1) {
    const prev = previous[index]
    const next = current[index]

    if (!prev || !next) continue

    const idleDiff = next.idle - prev.idle
    const totalDiff =
      (next.user - prev.user) +
      (next.nice - prev.nice) +
      (next.sys - prev.sys) +
      (next.idle - prev.idle) +
      (next.irq - prev.irq)

    idle += idleDiff
    total += totalDiff
  }

  return { idle, total }
}

function round(value) {
  return value == null ? null : Number(value.toFixed(2))
}

export class ResourceSampler {
  constructor({ metrics, intervalMs = 1000 }) {
    this.metrics = metrics
    this.intervalMs = intervalMs
    this.intervalId = null
    this.previousTimestamp = nowMs()
    this.previousProcessUsage = process.cpuUsage()
    this.previousCpuTimes = cloneCpuTimes()
  }

  sample() {
    const currentTimestamp = nowMs()
    const elapsedMs = currentTimestamp - this.previousTimestamp
    const currentProcessUsage = process.cpuUsage()
    const currentCpuTimes = cloneCpuTimes()

    const processDeltaMicros =
      currentProcessUsage.user - this.previousProcessUsage.user +
      currentProcessUsage.system - this.previousProcessUsage.system

    const cpuCount = Math.max(currentCpuTimes.length, 1)
    const nodeCpuPct = elapsedMs > 0
      ? (processDeltaMicros / 1000 / (elapsedMs * cpuCount)) * 100
      : null

    const { idle, total } = aggregateCpuDiff(this.previousCpuTimes, currentCpuTimes)
    const systemCpuPct = total > 0 ? ((total - idle) / total) * 100 : null
    const memory = process.memoryUsage()

    this.metrics.recordResourceSample({
      timestamp: new Date(currentTimestamp).toISOString(),
      backendCpuPct: null,
      backendRssMb: null,
      backendHeapUsedMb: null,
      backendThreadCount: null,
      nodeCpuPct: round(nodeCpuPct),
      nodeRssMb: round(memory.rss / 1024 / 1024),
      nodeHeapUsedMb: round(memory.heapUsed / 1024 / 1024),
      systemCpuPct: round(systemCpuPct),
      systemMemoryUsedMb: round((os.totalmem() - os.freemem()) / 1024 / 1024),
    })

    this.previousTimestamp = currentTimestamp
    this.previousProcessUsage = currentProcessUsage
    this.previousCpuTimes = currentCpuTimes
  }

  start() {
    if (this.intervalId) return
    this.intervalId = setInterval(() => this.sample(), this.intervalMs)
    this.intervalId.unref?.()
  }

  stop() {
    if (!this.intervalId) return
    clearInterval(this.intervalId)
    this.intervalId = null
  }
}
