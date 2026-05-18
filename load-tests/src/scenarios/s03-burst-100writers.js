import { assertCompleteFanout, summarizeFanout, waitForCompleteFanout } from '../assertions/consistency.js'
import { hashSnapshot } from '../assertions/snapshotHash.js'
import { ScenarioAssertionError } from '../runtime/errors.js'
import { nowMs, sleep } from '../runtime/clock.js'
import { ResourceSampler } from '../runtime/resourceSampler.js'
import {
  connectClientsInRamp,
  prepareEnvironment,
  runOrganizerThreatPulse,
  runOrganizerVillainHpPulse,
} from './common.js'

function splitBurstWriters(clients) {
  const midpoint = Math.floor(clients.length / 2)
  return {
    damageClients: clients.slice(0, midpoint),
    threatClients: clients.slice(midpoint),
  }
}

function maxReceivedAtMs(matches) {
  return Math.max(...[...matches.values()].map((match) => match.receivedAtMs))
}

function findHttpLatencyMs(metrics, opId) {
  const operation = metrics.operations.find((candidate) => candidate.opId === opId)
  const value = Number(operation?.httpLatencyMs)
  return Number.isFinite(value) ? value : null
}

function summarizeOperationLatencies(values) {
  const filtered = values.filter((value) => Number.isFinite(value))
  if (!filtered.length) {
    return {
      min: null,
      max: null,
      avg: null,
    }
  }

  const total = filtered.reduce((sum, value) => sum + value, 0)
  return {
    min: Math.min(...filtered),
    max: Math.max(...filtered),
    avg: Number((total / filtered.length).toFixed(2)),
  }
}

function ensureWaveIsSafe(snapshot, damageWriterCount, threatWriterCount, config, waveId) {
  const expectedVillainHp = snapshot.villainHpCurrent - (damageWriterCount * config.damageValue)
  if (expectedVillainHp <= 0) {
    throw new ScenarioAssertionError(
      `${waveId}: la ráfaga dejaría el HP del villano en ${expectedVillainHp}. Ajusta damage-value o reduce escritores de daño.`,
    )
  }

  if (snapshot.schemeThreatTarget > 0) {
    const expectedThreat = snapshot.schemeThreatCurrent + (threatWriterCount * config.threatValue)
    if (expectedThreat >= snapshot.schemeThreatTarget) {
      throw new ScenarioAssertionError(
        `${waveId}: la ráfaga llevaría la amenaza a ${expectedThreat}, alcanzando o superando el umbral ${snapshot.schemeThreatTarget}.`,
      )
    }
  }
}

async function restoreBaselineAfterWave({ organizer, clients, eventId, metrics, timeoutMs, baselineState, waveId }) {
  const resetOperations = []

  resetOperations.push(await runOrganizerVillainHpPulse({
    organizer,
    clients,
    eventId,
    value: baselineState.villainHpCurrent,
    metrics,
    timeoutMs,
    waveId: `${waveId}-reset-hp`,
  }))

  resetOperations.push(await runOrganizerThreatPulse({
    organizer,
    clients,
    eventId,
    value: baselineState.schemeThreatCurrent,
    metrics,
    timeoutMs,
    waveId: `${waveId}-reset-threat`,
  }))

  const restoredSnapshot = await organizer.getGameState(eventId)
  if (
    restoredSnapshot.villainHpCurrent !== baselineState.villainHpCurrent
    || restoredSnapshot.schemeThreatCurrent !== baselineState.schemeThreatCurrent
  ) {
    throw new ScenarioAssertionError(
      `${waveId}: el organizer no consiguió restaurar la línea base (${baselineState.villainHpCurrent}/${baselineState.schemeThreatCurrent}).`,
      {
        restoredSnapshot,
        baselineState,
      },
    )
  }

  return {
    operations: resetOperations.map((operation) => ({
      opId: operation.opId,
      fanout: operation.fanout,
      snapshotVillainHp: operation.response.snapshot?.villainHpCurrent ?? null,
      snapshotThreat: operation.response.snapshot?.schemeThreatCurrent ?? null,
    })),
    restoredSnapshotVillainHp: restoredSnapshot.villainHpCurrent ?? null,
    restoredSnapshotThreat: restoredSnapshot.schemeThreatCurrent ?? null,
  }
}

async function runBurstWave({ organizer, clients, eventId, config, metrics, baselineState, waveNumber }) {
  const waveId = `burst-wave-${String(waveNumber).padStart(2, '0')}`
  const { damageClients, threatClients } = splitBurstWriters(clients)

  if (!damageClients.length || !threatClients.length) {
    throw new ScenarioAssertionError(`${waveId}: se necesitan al menos dos clientes para repartir daño y amenaza.`)
  }

  const currentSnapshot = await organizer.getGameState(eventId)
  if (
    currentSnapshot.villainHpCurrent !== baselineState.villainHpCurrent
    || currentSnapshot.schemeThreatCurrent !== baselineState.schemeThreatCurrent
  ) {
    throw new ScenarioAssertionError(
      `${waveId}: el estado previo no coincide con la línea base esperada (${baselineState.villainHpCurrent}/${baselineState.schemeThreatCurrent}).`,
      {
        currentSnapshot,
        baselineState,
      },
    )
  }

  ensureWaveIsSafe(currentSnapshot, damageClients.length, threatClients.length, config, waveId)

  const expectedVillainHp = currentSnapshot.villainHpCurrent - (damageClients.length * config.damageValue)
  const expectedThreat = currentSnapshot.schemeThreatCurrent + (threatClients.length * config.threatValue)
  const cursors = new Map(clients.map((client) => [client.clientId, client.getMessageCursor()]))
  const waveStartedAtMs = nowMs()
  const syntheticWaveOpId = metrics.recordOperationStart({
    waveId,
    actorClientId: organizer.clientId,
    actorGroupId: '',
    operationType: 'BURST_WAVE',
    endpoint: `/events/${eventId}/game-state/burst`,
    requestPayloadSummary: JSON.stringify({
      damageWriters: damageClients.length,
      threatWriters: threatClients.length,
      damageValue: config.damageValue,
      threatValue: config.threatValue,
    }),
    sentAt: new Date(waveStartedAtMs).toISOString(),
  })

  const burstOperations = [
    ...damageClients.map((actorClient) => ({
      actorClient,
      operationType: 'REPORT_VILLAIN_DAMAGE',
      execute: () => actorClient.reportVillainDamage(config.damageValue, { waveId }),
    })),
    ...threatClients.map((actorClient) => ({
      actorClient,
      operationType: 'REPORT_THREAT',
      execute: () => actorClient.reportThreat(config.threatValue, { waveId }),
    })),
  ]

  const settled = await Promise.allSettled(burstOperations.map((operation) => operation.execute()))
  const requestsSettledAtMs = nowMs()
  const failures = settled.flatMap((result, index) => {
    if (result.status === 'fulfilled') return []
    return [{
      actorClientId: burstOperations[index].actorClient.clientId,
      operationType: burstOperations[index].operationType,
      message: result.reason?.message || String(result.reason),
    }]
  })

  if (failures.length) {
    metrics.completeOperation(syntheticWaveOpId, {
      httpResponseAt: new Date(requestsSettledAtMs).toISOString(),
      httpLatencyMs: undefined,
      httpStatus: 500,
      responseSnapshotHash: '',
      responseSnapshotVillainHp: currentSnapshot.villainHpCurrent ?? null,
      responseSnapshotThreat: currentSnapshot.schemeThreatCurrent ?? null,
      timeoutFlag: false,
      failureMessage: `Fallaron ${failures.length} operaciones en ${waveId}.`,
    })

    throw new ScenarioAssertionError(
      `${waveId}: fallaron ${failures.length} operaciones concurrentes. Primera: ${failures[0].actorClientId} (${failures[0].operationType}) -> ${failures[0].message}`,
      { failures },
    )
  }

  const persistedSnapshot = await organizer.getGameState(eventId)

  const fanout = await waitForCompleteFanout({
    clients,
    cursors,
    timeoutMs: config.deliveryTimeoutMs,
    predicate: (message) =>
      message.topic.endsWith('/game-state')
      && (message.payloadOperation === 'REPORT_VILLAIN_DAMAGE' || message.payloadOperation === 'REPORT_THREAT')
      && message.snapshotVillainHp === persistedSnapshot.villainHpCurrent
      && message.snapshotThreat === persistedSnapshot.schemeThreatCurrent,
  })

  assertCompleteFanout(fanout, `Ráfaga ${waveId}`)
  metrics.linkDeliveriesToOperation(syntheticWaveOpId, [...fanout.matches.values()].map((match) => match.messageId))

  metrics.completeOperation(syntheticWaveOpId, {
    httpResponseAt: new Date(requestsSettledAtMs).toISOString(),
    httpLatencyMs: undefined,
    httpStatus: 200,
    responseSnapshotHash: hashSnapshot(persistedSnapshot),
    responseSnapshotVillainHp: persistedSnapshot.villainHpCurrent ?? null,
    responseSnapshotThreat: persistedSnapshot.schemeThreatCurrent ?? null,
    timeoutFlag: false,
  })

  if (
    persistedSnapshot.villainHpCurrent !== expectedVillainHp
    || persistedSnapshot.schemeThreatCurrent !== expectedThreat
  ) {
    throw new ScenarioAssertionError(
      `${waveId}: la ráfaga convergió a ${persistedSnapshot.villainHpCurrent}/${persistedSnapshot.schemeThreatCurrent}, pero el acumulado esperado era ${expectedVillainHp}/${expectedThreat}. Posible pérdida de actualizaciones concurrentes.`,
      {
        persistedSnapshot,
        expectedVillainHp,
        expectedThreat,
      },
    )
  }

  const convergenceAtMs = maxReceivedAtMs(fanout.matches)
  const operationLatencies = settled
    .filter((result) => result.status === 'fulfilled')
    .map((result) => findHttpLatencyMs(metrics, result.value.opId))

  return {
    waveId,
    damageWriters: damageClients.length,
    threatWriters: threatClients.length,
    operationsDispatched: burstOperations.length,
    requestsSettledMs: requestsSettledAtMs - waveStartedAtMs,
    convergenceMs: convergenceAtMs - waveStartedAtMs,
    operationHttpMs: summarizeOperationLatencies(operationLatencies),
    fanout: summarizeFanout(fanout),
    finalSnapshotVillainHp: persistedSnapshot.villainHpCurrent ?? null,
    finalSnapshotThreat: persistedSnapshot.schemeThreatCurrent ?? null,
    syntheticWaveOpId,
  }
}

export const burst100WritersScenario = {
  name: 'burst-100writers',
  description: 'Ráfagas concurrentes con 100 escritores, validando convergencia por ola y restaurando línea base entre olas.',
  async run({ config, metrics }) {
    const sampler = new ResourceSampler({ metrics, intervalMs: 1000 })
    const cleanupClients = []

    try {
      if (config.waveCount < 1) {
        throw new ScenarioAssertionError('El escenario burst requiere al menos una ola (--wave-count >= 1).')
      }

      sampler.start()
      const environment = await prepareEnvironment({ config, metrics })
      const clients = await connectClientsInRamp({
        config,
        metrics,
        eventId: environment.event.id,
        groups: environment.groups,
      })
      cleanupClients.push(...clients)

      const baselineSnapshot = await environment.organizer.getGameState(environment.event.id)
      const baselineState = {
        villainHpCurrent: baselineSnapshot.villainHpCurrent ?? null,
        schemeThreatCurrent: baselineSnapshot.schemeThreatCurrent ?? null,
      }

      const waveResults = []
      for (let waveNumber = 1; waveNumber <= config.waveCount; waveNumber += 1) {
        const waveResult = await runBurstWave({
          organizer: environment.organizer,
          clients,
          eventId: environment.event.id,
          config,
          metrics,
          baselineState,
          waveNumber,
        })

        if (waveNumber < config.waveCount) {
          waveResult.reset = await restoreBaselineAfterWave({
            organizer: environment.organizer,
            clients,
            eventId: environment.event.id,
            metrics,
            timeoutMs: config.deliveryTimeoutMs,
            baselineState,
            waveId: waveResult.waveId,
          })

          if (config.waveCooldownMs > 0) {
            await sleep(config.waveCooldownMs)
          }
        }

        waveResults.push(waveResult)
      }

      return {
        eventId: environment.event.id,
        villainId: environment.selectedVillain.id,
        clientsConnected: clients.length,
        writersConnected: clients.length,
        baselineSnapshotVillainHp: baselineState.villainHpCurrent,
        baselineSnapshotThreat: baselineState.schemeThreatCurrent,
        totalBurstOperations: waveResults.reduce((sum, wave) => sum + wave.operationsDispatched, 0),
        wavesCompleted: waveResults.length,
        waveResults,
      }
    } finally {
      sampler.stop()
      await Promise.all(cleanupClients.map((client) => client.disconnect().catch(() => {})))
    }
  },
}
