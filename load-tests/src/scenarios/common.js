import { authenticateOrganizer } from '../bootstrap/organizerAuth.js'
import { bootstrapEvent } from '../bootstrap/eventBootstrap.js'
import { fetchResolvedStructure } from '../bootstrap/fetchStructure.js'
import { bootstrapGameStart } from '../bootstrap/startGame.js'
import { bootstrapStructure } from '../bootstrap/structureBootstrap.js'
import {
  assertCompleteFanout,
  buildIncompleteFanoutMessage,
  summarizeFanout,
  waitForCompleteFanout,
} from '../assertions/consistency.js'
import { hashSnapshot } from '../assertions/snapshotHash.js'
import { OrganizerControlClient } from '../clients/organizerControlClient.js'
import { VirtualRepresentativeClient } from '../clients/virtualRepresentativeClient.js'
import { nowMs, sleep } from '../runtime/clock.js'

function finalizeFanoutObservation({
  metrics,
  opId,
  result,
  label,
  strict = true,
  scope = 'fanout',
  operationType,
  waveId,
  actorClientId,
  actorGroupId,
}) {
  const summary = summarizeFanout(result)
  const message = result.complete ? '' : buildIncompleteFanoutMessage(label, result.missingClientIds)

  metrics.completeOperation(opId, {
    fanoutComplete: result.complete,
    fanoutDeliveredClients: summary.deliveredClients,
    fanoutMissingCount: result.missingClientIds.length,
    fanoutMinLatencyMs: summary.minFanoutLatencyMs,
    fanoutMaxLatencyMs: summary.maxFanoutLatencyMs,
    fanoutFailureMessage: message,
  })
  metrics.linkDeliveriesToOperation(opId, [...result.matches.values()].map((match) => match.messageId))

  if (!result.complete) {
    if (strict) {
      assertCompleteFanout(result, label)
    }

    metrics.recordError({
      scope,
      waveId,
      operationType,
      actorClientId,
      actorGroupId,
      message,
      missingClientCount: result.missingClientIds.length,
      missingClientIdsSample: result.missingClientIds.slice(0, 12),
    })
  }

  return {
    fanout: summary,
    fanoutComplete: result.complete,
    missingClientIds: result.missingClientIds,
  }
}

export async function prepareEnvironment({ config, metrics }) {
  const organizer = new OrganizerControlClient({
    baseUrl: config.baseUrl,
    metrics,
  })

  await authenticateOrganizer({ organizer, config })

  const { event, selectedVillain } = await bootstrapEvent({ organizer, config })
  await bootstrapStructure({ organizer, eventId: event.id, config })
  const { structure, groups } = await fetchResolvedStructure({ organizer, eventId: event.id })
  await bootstrapGameStart({ organizer, eventId: event.id, startTimer: config.startTimer })

  return {
    organizer,
    event,
    structure,
    groups,
    selectedVillain,
  }
}

export async function connectClientsInRamp({ config, metrics, eventId, groups }) {
  const selectedGroups = groups.slice(0, config.clients)
  const clients = selectedGroups.map((group, index) => new VirtualRepresentativeClient({
    clientId: `client-${String(index + 1).padStart(3, '0')}`,
    baseUrl: config.baseUrl,
    eventId,
    group,
    metrics,
  }))

  const batchSize = Math.max(1, config.rampStep)

  for (let index = 0; index < clients.length; index += batchSize) {
    const batch = clients.slice(index, index + batchSize)
    await Promise.all(batch.map(async (client) => {
      await client.loginByRepresentativeCode()
      await client.connectRealtime()
    }))

    if (config.rampIntervalMs > 0 && index + batchSize < clients.length) {
      await sleep(config.rampIntervalMs)
    }
  }

  return clients
}

export async function runOrganizerThreatPulse({ organizer, clients, eventId, value, metrics, timeoutMs, waveId }) {
  const cursors = new Map(clients.map((client) => [client.clientId, client.getMessageCursor()]))
  const sentAtMs = nowMs()
  const opId = metrics.recordOperationStart({
    waveId,
    actorClientId: organizer.clientId,
    actorGroupId: '',
    operationType: 'MANUAL_THREAT_UPDATE',
    endpoint: `/events/${eventId}/game-state/threat`,
    requestPayloadSummary: JSON.stringify({ value }),
    sentAt: new Date(sentAtMs).toISOString(),
  })

  try {
    const response = await organizer.updateThreat(eventId, value, `Pulse ${waveId}`)
    const finishedAtMs = nowMs()
    const snapshot = response.snapshot || null

    metrics.completeOperation(opId, {
      httpResponseAt: new Date(finishedAtMs).toISOString(),
      httpLatencyMs: finishedAtMs - sentAtMs,
      httpStatus: 200,
      responseSnapshotHash: hashSnapshot(snapshot),
      responseSnapshotVillainHp: snapshot?.villainHpCurrent ?? null,
      responseSnapshotThreat: snapshot?.schemeThreatCurrent ?? null,
      timeoutFlag: false,
    })

    const fanout = await waitForCompleteFanout({
      clients,
      cursors,
      timeoutMs,
      predicate: (message) =>
        message.topic.endsWith('/game-state') &&
        message.payloadOperation === 'MANUAL_THREAT_UPDATE' &&
        message.snapshotThreat === snapshot?.schemeThreatCurrent,
    })

    return {
      opId,
      response,
      ...finalizeFanoutObservation({
        metrics,
        opId,
        result: fanout,
        label: `Pulse organizer ${waveId}`,
        operationType: 'MANUAL_THREAT_UPDATE',
        waveId,
        actorClientId: organizer.clientId,
        actorGroupId: '',
      }),
    }
  } catch (error) {
    const finishedAtMs = nowMs()
    metrics.completeOperation(opId, {
      httpResponseAt: new Date(finishedAtMs).toISOString(),
      httpLatencyMs: finishedAtMs - sentAtMs,
      httpStatus: error.response?.status ?? 0,
      timeoutFlag: error.name === 'TimeoutError',
      failureMessage: error.message,
    })
    throw error
  }
}

export async function runOrganizerVillainHpPulse({ organizer, clients, eventId, value, metrics, timeoutMs, waveId }) {
  const cursors = new Map(clients.map((client) => [client.clientId, client.getMessageCursor()]))
  const sentAtMs = nowMs()
  const opId = metrics.recordOperationStart({
    waveId,
    actorClientId: organizer.clientId,
    actorGroupId: '',
    operationType: 'MANUAL_VILLAIN_HP_UPDATE',
    endpoint: `/events/${eventId}/game-state/villain-hp`,
    requestPayloadSummary: JSON.stringify({ value }),
    sentAt: new Date(sentAtMs).toISOString(),
  })

  try {
    const response = await organizer.updateVillainHp(eventId, value, `Pulse ${waveId}`)
    const finishedAtMs = nowMs()
    const snapshot = response.snapshot || null

    metrics.completeOperation(opId, {
      httpResponseAt: new Date(finishedAtMs).toISOString(),
      httpLatencyMs: finishedAtMs - sentAtMs,
      httpStatus: 200,
      responseSnapshotHash: hashSnapshot(snapshot),
      responseSnapshotVillainHp: snapshot?.villainHpCurrent ?? null,
      responseSnapshotThreat: snapshot?.schemeThreatCurrent ?? null,
      timeoutFlag: false,
    })

    const fanout = await waitForCompleteFanout({
      clients,
      cursors,
      timeoutMs,
      predicate: (message) =>
        message.topic.endsWith('/game-state') &&
        message.payloadOperation === 'MANUAL_VILLAIN_HP_UPDATE' &&
        message.snapshotVillainHp === snapshot?.villainHpCurrent,
    })

    return {
      opId,
      response,
      ...finalizeFanoutObservation({
        metrics,
        opId,
        result: fanout,
        label: `Pulse organizer HP ${waveId}`,
        operationType: 'MANUAL_VILLAIN_HP_UPDATE',
        waveId,
        actorClientId: organizer.clientId,
        actorGroupId: '',
      }),
    }
  } catch (error) {
    const finishedAtMs = nowMs()
    metrics.completeOperation(opId, {
      httpResponseAt: new Date(finishedAtMs).toISOString(),
      httpLatencyMs: finishedAtMs - sentAtMs,
      httpStatus: error.response?.status ?? 0,
      timeoutFlag: error.name === 'TimeoutError',
      failureMessage: error.message,
    })
    throw error
  }
}

export async function runRepresentativeDamagePulse({ actorClient, clients, metrics, damage, timeoutMs, waveId, strictFanout = true }) {
  const cursors = new Map(clients.map((client) => [client.clientId, client.getMessageCursor()]))
  const { opId, response } = await actorClient.reportVillainDamage(damage, { waveId })
  const snapshot = response.snapshot || null

  const fanout = await waitForCompleteFanout({
    clients,
    cursors,
    timeoutMs,
    predicate: (message) =>
      message.topic.endsWith('/game-state') &&
      message.payloadOperation === 'REPORT_VILLAIN_DAMAGE' &&
      message.snapshotVillainHp === snapshot?.villainHpCurrent,
  })

  return {
    opId,
    response,
    ...finalizeFanoutObservation({
      metrics,
      opId,
      result: fanout,
      label: `Daño representante ${waveId}`,
      strict: strictFanout,
      operationType: 'REPORT_VILLAIN_DAMAGE',
      waveId,
      actorClientId: actorClient.clientId,
      actorGroupId: actorClient.group.id,
    }),
  }
}

export async function runRepresentativeThreatPulse({ actorClient, clients, metrics, threat, timeoutMs, waveId, strictFanout = true }) {
  const cursors = new Map(clients.map((client) => [client.clientId, client.getMessageCursor()]))
  const { opId, response } = await actorClient.reportThreat(threat, { waveId })
  const snapshot = response.snapshot || null

  const fanout = await waitForCompleteFanout({
    clients,
    cursors,
    timeoutMs,
    predicate: (message) =>
      message.topic.endsWith('/game-state') &&
      message.payloadOperation === 'REPORT_THREAT' &&
      message.snapshotThreat === snapshot?.schemeThreatCurrent,
  })

  return {
    opId,
    response,
    ...finalizeFanoutObservation({
      metrics,
      opId,
      result: fanout,
      label: `Amenaza representante ${waveId}`,
      strict: strictFanout,
      operationType: 'REPORT_THREAT',
      waveId,
      actorClientId: actorClient.clientId,
      actorGroupId: actorClient.group.id,
    }),
  }
}

export async function runRepresentativeRemoveThreatPulse({ actorClient, clients, metrics, threat, timeoutMs, waveId, strictFanout = true }) {
  const cursors = new Map(clients.map((client) => [client.clientId, client.getMessageCursor()]))
  const { opId, response } = await actorClient.removeThreat(threat, { waveId })
  const snapshot = response.snapshot || null

  const fanout = await waitForCompleteFanout({
    clients,
    cursors,
    timeoutMs,
    predicate: (message) =>
      message.topic.endsWith('/game-state')
      && message.payloadOperation === 'REMOVE_THREAT'
      && message.snapshotThreat === snapshot?.schemeThreatCurrent,
  })

  return {
    opId,
    response,
    ...finalizeFanoutObservation({
      metrics,
      opId,
      result: fanout,
      label: `Retirada de amenaza representante ${waveId}`,
      strict: strictFanout,
      operationType: 'REMOVE_THREAT',
      waveId,
      actorClientId: actorClient.clientId,
      actorGroupId: actorClient.group.id,
    }),
  }
}
