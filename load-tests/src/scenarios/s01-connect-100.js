import { sleep } from '../runtime/clock.js'
import { ResourceSampler } from '../runtime/resourceSampler.js'
import { connectClientsInRamp, prepareEnvironment, runOrganizerThreatPulse } from './common.js'

export const connect100Scenario = {
  name: 'connect-100',
  description: 'Conexión masiva de 100 clientes con rampa y pulsos manuales del organizer.',
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
        eventCode: environment.event.eventCode,
        groups: environment.groups,
      })
      cleanupClients.push(...clients)

      const pulses = []
      for (let index = 0; index < config.pulseValues.length; index += 1) {
        const value = config.pulseValues[index]
        const pulse = await runOrganizerThreatPulse({
          organizer: environment.organizer,
          clients,
          eventId: environment.event.id,
          value,
          metrics,
          timeoutMs: config.deliveryTimeoutMs,
          waveId: `organizer-pulse-${index + 1}`,
        })
        pulses.push(pulse)

        if (config.pulseIntervalMs > 0 && index < config.pulseValues.length - 1) {
          await sleep(config.pulseIntervalMs)
        }
      }

      const occupiedMs = Math.max(0, (config.pulseValues.length - 1) * config.pulseIntervalMs)
      const remainingHoldMs = Math.max(0, config.holdMs - occupiedMs)
      if (remainingHoldMs > 0) {
        await sleep(remainingHoldMs)
      }

      return {
        eventId: environment.event.id,
        villainId: environment.selectedVillain.id,
        clientsConnected: clients.length,
        pulses,
      }
    } finally {
      sampler.stop()
      await Promise.all(cleanupClients.map((client) => client.disconnect().catch(() => {})))
    }
  },
}
