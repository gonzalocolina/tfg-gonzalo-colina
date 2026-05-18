import { nowMs, sleep } from '../runtime/clock.js'
import { ResourceSampler } from '../runtime/resourceSampler.js'
import {
  connectClientsInRamp,
  prepareEnvironment,
  runRepresentativeDamagePulse,
  runRepresentativeThreatPulse,
} from './common.js'

function isThreatIteration(iterationIndex) {
  return iterationIndex % 10 >= 7
}

export const nominal100Scenario = {
  name: 'nominal-100-10writers',
  description: 'Carga nominal con 100 clientes conectados, 10 escritores y una operación por segundo.',
  async run({ config, metrics }) {
    const sampler = new ResourceSampler({ metrics, intervalMs: 1000 })
    const cleanupClients = []

    try {
      sampler.start()
      const environment = await prepareEnvironment({ config, metrics })
      const clients = await connectClientsInRamp({
        config,
        metrics,
        eventId: environment.event.id,
        groups: environment.groups,
      })
      cleanupClients.push(...clients)

      const writers = clients.slice(0, Math.max(1, config.writerCount))
      const operations = []
      const startedAtMs = nowMs()
      let iteration = 0

      while (nowMs() - startedAtMs < config.holdMs) {
        const actorClient = writers[iteration % writers.length]
        const waveId = `nominal-${String(iteration + 1).padStart(4, '0')}`
        const threatIteration = isThreatIteration(iteration)
        const operationStartedAtMs = nowMs()

        const result = threatIteration
          ? await runRepresentativeThreatPulse({
              actorClient,
              clients,
              metrics,
              threat: config.threatValue,
              timeoutMs: config.deliveryTimeoutMs,
              waveId,
            })
          : await runRepresentativeDamagePulse({
              actorClient,
              clients,
              metrics,
              damage: config.damageValue,
              timeoutMs: config.deliveryTimeoutMs,
              waveId,
            })

        operations.push({
          iteration: iteration + 1,
          waveId,
          actorClientId: actorClient.clientId,
          actorGroupId: actorClient.group.id,
          operationType: threatIteration ? 'REPORT_THREAT' : 'REPORT_VILLAIN_DAMAGE',
          fanout: result.fanout,
          snapshotVillainHp: result.response.snapshot?.villainHpCurrent ?? null,
          snapshotThreat: result.response.snapshot?.schemeThreatCurrent ?? null,
        })

        iteration += 1

        const elapsedOperationMs = nowMs() - operationStartedAtMs
        const remainingDelayMs = Math.max(0, config.operationIntervalMs - elapsedOperationMs)
        if (remainingDelayMs > 0) {
          await sleep(remainingDelayMs)
        }
      }

      return {
        eventId: environment.event.id,
        villainId: environment.selectedVillain.id,
        clientsConnected: clients.length,
        writersConnected: writers.length,
        totalOperations: operations.length,
        firstOperations: operations.slice(0, 5),
        lastOperation: operations.at(-1) || null,
      }
    } finally {
      sampler.stop()
      await Promise.all(cleanupClients.map((client) => client.disconnect().catch(() => {})))
    }
  },
}
