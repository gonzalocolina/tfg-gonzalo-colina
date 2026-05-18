import { ResourceSampler } from '../runtime/resourceSampler.js'
import { connectClientsInRamp, prepareEnvironment, runOrganizerThreatPulse, runRepresentativeDamagePulse } from './common.js'

export const smokeScenario = {
  name: 'smoke',
  description: 'Smoke test corto: bootstrap completo, login de clientes, realtime y fan-out básico.',
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

      const organizerPulse = await runOrganizerThreatPulse({
        organizer: environment.organizer,
        clients,
        eventId: environment.event.id,
        value: 1,
        metrics,
        timeoutMs: config.deliveryTimeoutMs,
        waveId: 'organizer-pulse-1',
      })

      const representativePulse = await runRepresentativeDamagePulse({
        actorClient: clients[0],
        clients,
        metrics,
        damage: 1,
        timeoutMs: config.deliveryTimeoutMs,
        waveId: 'rep-damage-1',
      })

      return {
        eventId: environment.event.id,
        villainId: environment.selectedVillain.id,
        clientsConnected: clients.length,
        pulses: [organizerPulse, representativePulse],
      }
    } finally {
      sampler.stop()
      await Promise.all(cleanupClients.map((client) => client.disconnect().catch(() => {})))
    }
  },
}
