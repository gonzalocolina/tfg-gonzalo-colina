import { nowMs, sleep } from '../runtime/clock.js'
import { ResourceSampler } from '../runtime/resourceSampler.js'
import {
  connectClientsInRamp,
  prepareEnvironment,
  runRepresentativeDamagePulse,
  runRepresentativeRemoveThreatPulse,
} from './common.js'

const NOMINAL_DAMAGE_SHARE = 0.7
const NOMINAL_OPERATION_TYPES = {
  DAMAGE: 'REPORT_VILLAIN_DAMAGE',
  REMOVE_THREAT: 'REMOVE_THREAT',
}

function buildNominalWaveId(iteration) {
  return `nominal-${String(iteration).padStart(6, '0')}`
}

function getWriterInitialOffsetMs(writerIndex, writerCount, operationIntervalMs) {
  if (operationIntervalMs <= 0 || writerCount <= 1) {
    return 0
  }

  return Math.floor((writerIndex * operationIntervalMs) / writerCount)
}

function estimatePlannedOperationCount({ writerCount, holdMs, operationIntervalMs }) {
  if (writerCount <= 0 || holdMs <= 0) {
    return 0
  }

  if (operationIntervalMs <= 0) {
    return null
  }

  let totalOperations = 0

  for (let writerIndex = 0; writerIndex < writerCount; writerIndex += 1) {
    const initialOffsetMs = getWriterInitialOffsetMs(writerIndex, writerCount, operationIntervalMs)
    if (initialOffsetMs >= holdMs) {
      continue
    }

    totalOperations += Math.floor((holdMs - 1 - initialOffsetMs) / operationIntervalMs) + 1
  }

  return totalOperations
}

function buildDamageBudget(snapshot, damageValue) {
  if (damageValue <= 0) {
    return 0
  }

  const currentVillainHp = Number(snapshot?.villainHpCurrent)
  if (!Number.isFinite(currentVillainHp)) {
    return Number.MAX_SAFE_INTEGER
  }

  return Math.max(0, Math.floor((Math.max(0, currentVillainHp) - 1) / damageValue))
}

function distributeEvenly(itemCount, segmentCount) {
  if (segmentCount <= 0) {
    return []
  }

  const baseCount = Math.floor(itemCount / segmentCount)
  const remainder = itemCount % segmentCount

  return Array.from({ length: segmentCount }, (_, index) => baseCount + (index < remainder ? 1 : 0))
}

function buildNominalOperationPlan({ totalOperations, snapshot, damageValue }) {
  if (!Number.isFinite(totalOperations) || totalOperations <= 0) {
    return null
  }

  const damageBudget = buildDamageBudget(snapshot, damageValue)
  const damageCount = Math.min(totalOperations, Math.min(Math.floor(totalOperations * NOMINAL_DAMAGE_SHARE), damageBudget))
  const removeThreatCount = totalOperations - damageCount
  const damageSegments = distributeEvenly(damageCount, removeThreatCount + 1)
  const sequence = []

  for (let segmentIndex = 0; segmentIndex < damageSegments.length; segmentIndex += 1) {
    for (let damageIndex = 0; damageIndex < damageSegments[segmentIndex]; damageIndex += 1) {
      sequence.push(NOMINAL_OPERATION_TYPES.DAMAGE)
    }

    if (segmentIndex < removeThreatCount) {
      sequence.push(NOMINAL_OPERATION_TYPES.REMOVE_THREAT)
    }
  }

  return {
    sequence,
    counts: {
      damage: damageCount,
      removeThreat: removeThreatCount,
    },
  }
}

function resolveOperationType(iterationIndex, operationPlan) {
  if (operationPlan?.sequence?.[iterationIndex] != null) {
    return operationPlan.sequence[iterationIndex]
  }

  return iterationIndex % 10 >= 7
    ? NOMINAL_OPERATION_TYPES.REMOVE_THREAT
    : NOMINAL_OPERATION_TYPES.DAMAGE
}

function updateCounter(counter, key) {
  counter.set(key, (counter.get(key) ?? 0) + 1)
}

export const nominal100Scenario = {
  name: 'nominal-100-10writers',
  description: 'Carga nominal con clientes conectados y escritores activos emitiendo una operación por segundo cada uno, manteniendo la partida abierta sin abortar en el primer fan-out incompleto.',
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
      const initialSnapshot = await environment.organizer.getGameState(environment.event.id)
      const plannedOperationCount = estimatePlannedOperationCount({
        writerCount: writers.length,
        holdMs: config.holdMs,
        operationIntervalMs: config.operationIntervalMs,
      })
      const operationPlan = buildNominalOperationPlan({
        totalOperations: plannedOperationCount,
        snapshot: initialSnapshot,
        damageValue: config.damageValue,
      })
      const operations = []
      const operationMix = new Map()
      const missingClientFrequency = new Map()
      const incompleteFanoutSamples = []
      const startedAtMs = nowMs()
      const deadlineAtMs = startedAtMs + config.holdMs
      const operationIntervalMs = Math.max(0, config.operationIntervalMs)
      let dispatchedIterations = 0
      let nextProgressPct = 10
      const operationTasks = []

      const reportProgressIfDue = (force = false) => {
        if (config.holdMs <= 0) {
          return
        }

        const elapsedMs = force
          ? config.holdMs
          : Math.min(config.holdMs, Math.max(0, nowMs() - startedAtMs))

        while (nextProgressPct <= 100 && elapsedMs >= (config.holdMs * nextProgressPct) / 100) {
          const plannedText = plannedOperationCount == null
            ? `${dispatchedIterations}`
            : `${Math.min(dispatchedIterations, plannedOperationCount)}/${plannedOperationCount}`
          console.log(
            `[nominal-100-10writers] progreso ${String(nextProgressPct).padStart(3, ' ')}% · ${Math.round(elapsedMs / 1000)}s/${Math.round(config.holdMs / 1000)}s · operaciones emitidas ${plannedText}`,
          )
          nextProgressPct += 10
        }
      }

      const reserveIteration = () => {
        dispatchedIterations += 1
        return dispatchedIterations
      }

      const writerTasks = writers.map((actorClient, writerIndex) => (async () => {
        let nextScheduledAtMs = startedAtMs + getWriterInitialOffsetMs(writerIndex, writers.length, operationIntervalMs)

        while (nextScheduledAtMs < deadlineAtMs) {
          const waitMs = nextScheduledAtMs - nowMs()
          if (waitMs > 0) {
            await sleep(waitMs)
          }

          const iteration = reserveIteration()
          const waveId = buildNominalWaveId(iteration)
          const operationType = resolveOperationType(iteration - 1, operationPlan)
          reportProgressIfDue()

          const operationTask = (async () => {
            let result

            if (operationType === NOMINAL_OPERATION_TYPES.REMOVE_THREAT) {
              result = await runRepresentativeRemoveThreatPulse({
                actorClient,
                clients,
                metrics,
                threat: config.threatValue,
                timeoutMs: config.deliveryTimeoutMs,
                waveId,
                strictFanout: false,
              })
            } else {
              result = await runRepresentativeDamagePulse({
                actorClient,
                clients,
                metrics,
                damage: config.damageValue,
                timeoutMs: config.deliveryTimeoutMs,
                waveId,
                strictFanout: false,
              })
            }

            const operationRecord = {
              iteration,
              waveId,
              actorClientId: actorClient.clientId,
              actorGroupId: actorClient.group.id,
              operationType,
              fanout: result.fanout,
              fanoutComplete: result.fanoutComplete,
              fanoutMissingCount: result.missingClientIds.length,
              snapshotVillainHp: result.response.snapshot?.villainHpCurrent ?? null,
              snapshotThreat: result.response.snapshot?.schemeThreatCurrent ?? null,
            }

            updateCounter(operationMix, operationType)

            if (!result.fanoutComplete) {
              for (const clientId of result.missingClientIds) {
                updateCounter(missingClientFrequency, clientId)
              }

              if (incompleteFanoutSamples.length < 5) {
                incompleteFanoutSamples.push({
                  waveId,
                  operationType,
                  missingClientCount: result.missingClientIds.length,
                  missingClientIdsSample: result.missingClientIds.slice(0, 8),
                })
              }
            }

            operations.push(operationRecord)
            return operationRecord
          })().then(
            (value) => ({ status: 'fulfilled', value }),
            (reason) => ({ status: 'rejected', reason }),
          )

          operationTasks.push(operationTask)

          if (operationIntervalMs > 0) {
            nextScheduledAtMs += operationIntervalMs
          } else {
            nextScheduledAtMs = nowMs()
          }
        }
      })())

      await Promise.all(writerTasks)
      reportProgressIfDue(true)

      const settledOperationTasks = await Promise.all(operationTasks)
      const failedOperation = settledOperationTasks.find((task) => task.status === 'rejected')
      if (failedOperation) {
        throw failedOperation.reason
      }

      const orderedOperations = [...operations].sort((left, right) => left.iteration - right.iteration)
      const incompleteOperations = orderedOperations.filter((operation) => operation.fanoutComplete === false)
      const topMissingClients = [...missingClientFrequency.entries()]
        .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
        .slice(0, 10)
        .map(([clientId, missCount]) => ({ clientId, missCount }))

      return {
        eventId: environment.event.id,
        villainId: environment.selectedVillain.id,
        clientsConnected: clients.length,
        writersConnected: writers.length,
        totalOperations: orderedOperations.length,
        plannedOperations: plannedOperationCount,
        plannedOperationMix: operationPlan?.counts || null,
        executedOperationMix: Object.fromEntries([...operationMix.entries()].sort((left, right) => left[0].localeCompare(right[0]))),
        fanoutQuality: {
          completeOperations: orderedOperations.length - incompleteOperations.length,
          incompleteOperations: incompleteOperations.length,
          topMissingClients,
          sampleIncompleteOperations: incompleteFanoutSamples,
        },
        firstOperations: orderedOperations.slice(0, 5),
        lastOperation: orderedOperations.at(-1) || null,
      }
    } finally {
      sampler.stop()
      await Promise.all(cleanupClients.map((client) => client.disconnect().catch(() => {})))
    }
  },
}
