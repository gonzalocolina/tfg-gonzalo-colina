import { sleep } from '../runtime/clock.js'
import { ScenarioAssertionError } from '../runtime/errors.js'

export async function waitForCompleteFanout({ clients, cursors, predicate, timeoutMs, pollIntervalMs = 25 }) {
  const deadline = Date.now() + timeoutMs
  const matches = new Map()
  const pendingClients = new Map(clients.map((client) => [client.clientId, client]))

  while (Date.now() < deadline && pendingClients.size > 0) {
    for (const [clientId, client] of pendingClients) {
      const cursor = cursors.get(clientId) ?? 0
      const match = client.findMessageAfter(cursor, predicate)
      if (match) {
        matches.set(clientId, match)
        pendingClients.delete(clientId)
      }
    }

    if (pendingClients.size === 0) {
      return {
        complete: true,
        matches,
        missingClientIds: [],
      }
    }

    await sleep(pollIntervalMs)
  }

  return {
    complete: false,
    matches,
    missingClientIds: [...pendingClients.keys()],
  }
}

export function buildIncompleteFanoutMessage(label, missingClientIds, sampleSize = 12) {
  const sample = missingClientIds.slice(0, sampleSize)
  const suffix = missingClientIds.length > sample.length
    ? `, ... (${missingClientIds.length} clientes en total)`
    : ''

  return `${label}: no todos los clientes recibieron la actualización. Faltan ${sample.join(', ')}${suffix}`
}

export function assertCompleteFanout(result, label) {
  if (result.complete) return

  throw new ScenarioAssertionError(
    buildIncompleteFanoutMessage(label, result.missingClientIds),
    {
      missingClientIds: result.missingClientIds,
    },
  )
}

export function summarizeFanout(result) {
  const deliveries = [...result.matches.values()]
  const latencies = deliveries
    .map((delivery) => delivery.fanoutLatencyMs)
    .filter((value) => Number.isFinite(value))

  let minLatencyMs = null
  let maxLatencyMs = null

  for (const latency of latencies) {
    if (minLatencyMs == null || latency < minLatencyMs) {
      minLatencyMs = latency
    }

    if (maxLatencyMs == null || latency > maxLatencyMs) {
      maxLatencyMs = latency
    }
  }

  return {
    deliveredClients: deliveries.length,
    missingClientIds: result.missingClientIds,
    minFanoutLatencyMs: minLatencyMs,
    maxFanoutLatencyMs: maxLatencyMs,
  }
}
