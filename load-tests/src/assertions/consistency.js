import { sleep } from '../runtime/clock.js'
import { ScenarioAssertionError } from '../runtime/errors.js'

export async function waitForCompleteFanout({ clients, cursors, predicate, timeoutMs }) {
  const deadline = Date.now() + timeoutMs
  const matches = new Map()

  while (Date.now() < deadline) {
    for (const client of clients) {
      if (matches.has(client.clientId)) continue
      const cursor = cursors.get(client.clientId) ?? 0
      const match = client.findMessageAfter(cursor, predicate)
      if (match) {
        matches.set(client.clientId, match)
      }
    }

    if (matches.size === clients.length) {
      return {
        complete: true,
        matches,
        missingClientIds: [],
      }
    }

    await sleep(25)
  }

  return {
    complete: false,
    matches,
    missingClientIds: clients
      .filter((client) => !matches.has(client.clientId))
      .map((client) => client.clientId),
  }
}

export function assertCompleteFanout(result, label) {
  if (result.complete) return

  throw new ScenarioAssertionError(
    `${label}: no todos los clientes recibieron la actualización. Faltan ${result.missingClientIds.join(', ')}`,
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

  return {
    deliveredClients: deliveries.length,
    missingClientIds: result.missingClientIds,
    minFanoutLatencyMs: latencies.length ? Math.min(...latencies) : null,
    maxFanoutLatencyMs: latencies.length ? Math.max(...latencies) : null,
  }
}
