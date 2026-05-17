const DAMAGE_ACTION_TYPES = new Set([
  'VILLAIN_DAMAGE_REPORTED',
  'VILLAIN_DAMAGE_DELEGATED',
])

const THREAT_ADDED_ACTION_TYPES = new Set([
  'THREAT_REPORTED',
  'THREAT_DELEGATED',
])

const THREAT_REMOVED_ACTION_TYPES = new Set([
  'THREAT_REMOVED',
  'THREAT_REMOVED_DELEGATED',
])

const DELEGATED_ACTION_TYPES = new Set([
  'VILLAIN_DAMAGE_DELEGATED',
  'THREAT_DELEGATED',
  'THREAT_REMOVED_DELEGATED',
])

const MANUAL_CORRECTION_ACTION_TYPES = new Set([
  'VILLAIN_HP_CORRECTED',
  'THREAT_CORRECTED',
])

const TIMER_ACTION_TYPES = new Set([
  'TIMER_STARTED',
  'TIMER_PAUSED',
  'TIMER_RESUMED',
  'TIMER_ADJUSTED',
])

const INTER_POD_CREATED_ACTION_TYPES = new Set(['INTER_POD_CARD_CREATED'])
const INTER_POD_DAMAGE_ACTION_TYPES = new Set(['INTER_POD_CARD_DAMAGED'])
const INTER_POD_THREAT_ACTION_TYPES = new Set(['INTER_POD_CARD_THREAT_REDUCED'])
const INTER_POD_STATUS_ACTION_TYPES = new Set(['INTER_POD_CARD_STATUS_UPDATED'])
const INTER_POD_DELETED_ACTION_TYPES = new Set(['INTER_POD_CARD_DELETED'])

const STATISTICS_ACTION_TYPES = new Set([
  'EVENT_STARTED',
  'EVENT_FINISHED',
  ...DAMAGE_ACTION_TYPES,
  ...THREAT_ADDED_ACTION_TYPES,
  ...THREAT_REMOVED_ACTION_TYPES,
  ...MANUAL_CORRECTION_ACTION_TYPES,
  ...TIMER_ACTION_TYPES,
  ...INTER_POD_CREATED_ACTION_TYPES,
  ...INTER_POD_DAMAGE_ACTION_TYPES,
  ...INTER_POD_THREAT_ACTION_TYPES,
  ...INTER_POD_STATUS_ACTION_TYPES,
  ...INTER_POD_DELETED_ACTION_TYPES,
])

const TIMER_COUNTER_FIELDS = Object.freeze({
  TIMER_STARTED: 'timerStarts',
  TIMER_PAUSED: 'timerPauses',
  TIMER_RESUMED: 'timerResumes',
  TIMER_ADJUSTED: 'timerAdjustments',
})

function parseNumericValue(value) {
  if (value === null || value === undefined || value === '') {
    return null
  }

  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function decrease(previousValue, newValue) {
  if (previousValue === null || newValue === null) {
    return 0
  }

  return Math.max(0, previousValue - newValue)
}

function increase(previousValue, newValue) {
  if (previousValue === null || newValue === null) {
    return 0
  }

  return Math.max(0, newValue - previousValue)
}

function compareIdentifiers(left, right) {
  return left.identifier.localeCompare(right.identifier, 'es', {
    numeric: true,
    sensitivity: 'base',
  })
}

function sortByMetric(metric) {
  return (left, right) => {
    const diff = (right[metric] || 0) - (left[metric] || 0)
    if (diff !== 0) {
      return diff
    }

    return compareIdentifiers(left, right)
  }
}

function buildGroupStats(group = {}) {
  return {
    id: group.id ?? null,
    identifier: group.identifier || 'Mesa desconocida',
    podName: group.podName || 'Sin pod asignado',
    playerCount: group.playerCount || 0,
    damageToVillain: 0,
    threatAdded: 0,
    threatRemoved: 0,
    threatNet: 0,
    activityCount: 0,
    delegatedActionCount: 0,
    interPodCreated: 0,
    interPodDamage: 0,
    interPodThreatReduced: 0,
    interPodStatusUpdates: 0,
    interPodDeleted: 0,
    interPodActionCount: 0,
    firstContributionAt: null,
    finalBlow: false,
  }
}

function mergeGroupMetadata(target, source = {}) {
  if (target.id == null && source.id != null) {
    target.id = source.id
  }
  if ((!target.podName || target.podName === 'Sin pod asignado') && source.podName) {
    target.podName = source.podName
  }
  if (!target.playerCount && source.playerCount) {
    target.playerCount = source.playerCount
  }
}

function flattenGroups(structure) {
  const groups = []

  for (const pod of structure?.pods || []) {
    for (const group of pod.groups || []) {
      groups.push({
        id: group.id,
        identifier: group.identifier,
        podName: pod.name,
        playerCount: group.playerCount,
      })
    }
  }

  for (const group of structure?.unassignedGroups || []) {
    groups.push({
      id: group.id,
      identifier: group.identifier,
      podName: 'Pendiente de asignación',
      playerCount: group.playerCount,
    })
  }

  return groups
}

function ensureGroup(groupStatsByIdentifier, identifier, metadata = {}) {
  if (!identifier) {
    return null
  }

  const existingGroup = groupStatsByIdentifier.get(identifier)
  if (existingGroup) {
    mergeGroupMetadata(existingGroup, metadata)
    return existingGroup
  }

  const nextGroup = buildGroupStats({
    identifier,
    ...metadata,
  })
  groupStatsByIdentifier.set(identifier, nextGroup)
  return nextGroup
}

function maybeRegisterFirstContribution(group, timestamp) {
  if (!group || !timestamp || group.firstContributionAt) {
    return
  }

  group.firstContributionAt = timestamp
}

function buildTimelineEntry(log, previousValue, newValue, effectiveChange) {
  return {
    id: log.id,
    timestamp: log.timestamp,
    actor: log.actor,
    groupIdentifier: log.groupIdentifier,
    actionType: log.actionType,
    previousValue: log.previousValue,
    newValue: log.newValue,
    notes: log.notes,
    effectiveChange,
    numericPreviousValue: previousValue,
    numericNewValue: newValue,
  }
}

function createOperationalSummary() {
  return {
    delegatedActions: 0,
    manualCorrections: 0,
    timerStarts: 0,
    timerPauses: 0,
    timerResumes: 0,
    timerAdjustments: 0,
    timerNetAdjustmentSeconds: 0,
  }
}

function createInterPodSummary() {
  return {
    created: 0,
    damageActions: 0,
    damageTotal: 0,
    threatReducedActions: 0,
    threatReducedTotal: 0,
    statusUpdates: 0,
    deleted: 0,
  }
}

function registerContribution(context, group, timestamp, effectiveChange, allowZeroChange = false) {
  if (!group) {
    return
  }

  const shouldRegister = allowZeroChange || effectiveChange > 0
  if (!shouldRegister) {
    return
  }

  maybeRegisterFirstContribution(group, timestamp)
  if (!context.firstContributionGroupIdentifier) {
    context.firstContributionGroupIdentifier = group.identifier
  }
}

function applyCommonCounters(context, log, group, previousValue, newValue) {
  if (DELEGATED_ACTION_TYPES.has(log.actionType)) {
    context.operationalSummary.delegatedActions += 1
    if (group) {
      group.delegatedActionCount += 1
    }
  }

  if (MANUAL_CORRECTION_ACTION_TYPES.has(log.actionType)) {
    context.operationalSummary.manualCorrections += 1
  }

  const timerCounterField = TIMER_COUNTER_FIELDS[log.actionType]
  if (timerCounterField) {
    context.operationalSummary[timerCounterField] += 1
  }

  if (log.actionType === 'TIMER_ADJUSTED' && previousValue !== null && newValue !== null) {
    context.operationalSummary.timerNetAdjustmentSeconds += newValue - previousValue
  }
}

function handleDamage(context, group, log, previousValue, newValue) {
  const effectiveChange = decrease(previousValue, newValue)

  if (group) {
    group.damageToVillain += effectiveChange
  }

  registerContribution(context, group, log.timestamp, effectiveChange)

  if (group && newValue === 0 && effectiveChange > 0) {
    group.finalBlow = true
    context.finalBlowGroupIdentifier = group.identifier
  }

  return effectiveChange
}

function handleThreatAdded(context, group, log, previousValue, newValue) {
  const effectiveChange = increase(previousValue, newValue)

  if (group) {
    group.threatAdded += effectiveChange
  }

  registerContribution(context, group, log.timestamp, effectiveChange)
  return effectiveChange
}

function handleThreatRemoved(context, group, log, previousValue, newValue) {
  const effectiveChange = decrease(previousValue, newValue)

  if (group) {
    group.threatRemoved += effectiveChange
  }

  registerContribution(context, group, log.timestamp, effectiveChange)
  return effectiveChange
}

function handleInterPodCreated(context, group, log) {
  context.interPodSummary.created += 1

  if (group) {
    group.interPodCreated += 1
    group.interPodActionCount += 1
  }

  registerContribution(context, group, log.timestamp, 0, true)
  return 0
}

function handleInterPodDamage(context, group, log, previousValue, newValue) {
  const effectiveChange = decrease(previousValue, newValue)
  context.interPodSummary.damageActions += 1
  context.interPodSummary.damageTotal += effectiveChange

  if (group) {
    group.interPodDamage += effectiveChange
    group.interPodActionCount += 1
  }

  registerContribution(context, group, log.timestamp, effectiveChange)
  return effectiveChange
}

function handleInterPodThreatReduction(context, group, log, previousValue, newValue) {
  const effectiveChange = decrease(previousValue, newValue)
  context.interPodSummary.threatReducedActions += 1
  context.interPodSummary.threatReducedTotal += effectiveChange

  if (group) {
    group.interPodThreatReduced += effectiveChange
    group.interPodActionCount += 1
  }

  registerContribution(context, group, log.timestamp, effectiveChange)
  return effectiveChange
}

function handleInterPodStatusUpdate(context, group, log) {
  context.interPodSummary.statusUpdates += 1

  if (group) {
    group.interPodStatusUpdates += 1
    group.interPodActionCount += 1
  }

  registerContribution(context, group, log.timestamp, 0, true)
  return 0
}

function handleInterPodDeleted(context, group, log) {
  context.interPodSummary.deleted += 1

  if (group) {
    group.interPodDeleted += 1
    group.interPodActionCount += 1
  }

  registerContribution(context, group, log.timestamp, 0, true)
  return 0
}

const LOG_HANDLERS = Object.freeze({
  VILLAIN_DAMAGE_REPORTED: handleDamage,
  VILLAIN_DAMAGE_DELEGATED: handleDamage,
  THREAT_REPORTED: handleThreatAdded,
  THREAT_DELEGATED: handleThreatAdded,
  THREAT_REMOVED: handleThreatRemoved,
  THREAT_REMOVED_DELEGATED: handleThreatRemoved,
  INTER_POD_CARD_CREATED: handleInterPodCreated,
  INTER_POD_CARD_DAMAGED: handleInterPodDamage,
  INTER_POD_CARD_THREAT_REDUCED: handleInterPodThreatReduction,
  INTER_POD_CARD_STATUS_UPDATED: handleInterPodStatusUpdate,
  INTER_POD_CARD_DELETED: handleInterPodDeleted,
})

function processLog(context, log) {
  const previousValue = parseNumericValue(log.previousValue)
  const newValue = parseNumericValue(log.newValue)
  const group = ensureGroup(context.groupStatsByIdentifier, log.groupIdentifier)

  if (group) {
    group.activityCount += 1
  }

  applyCommonCounters(context, log, group, previousValue, newValue)

  const handler = LOG_HANDLERS[log.actionType]
  const effectiveChange = handler
    ? handler(context, group, log, previousValue, newValue)
    : 0

  context.timelineEntries.push(buildTimelineEntry(log, previousValue, newValue, effectiveChange))
}

function buildRankings(groups) {
  return {
    damage: [...groups].sort(sortByMetric('damageToVillain')),
    threatAdded: [...groups].sort(sortByMetric('threatAdded')),
    threatRemoved: [...groups].sort(sortByMetric('threatRemoved')),
    activity: [...groups].sort(sortByMetric('activityCount')),
  }
}

function buildResult(context, relevantLogs, structure, finalSummary) {
  const groups = [...context.groupStatsByIdentifier.values()]
    .map((group) => ({
      ...group,
      threatNet: group.threatAdded - group.threatRemoved,
    }))
    .sort(sortByMetric('damageToVillain'))

  const activeGroups = groups.filter((group) => group.activityCount > 0)
  const rankings = buildRankings(groups)

  const finalBlowGroup = context.finalBlowGroupIdentifier
    ? context.groupStatsByIdentifier.get(context.finalBlowGroupIdentifier) || null
    : null
  const firstContributionGroup = context.firstContributionGroupIdentifier
    ? context.groupStatsByIdentifier.get(context.firstContributionGroupIdentifier) || null
    : null

  return {
    groups,
    relevantLogs,
    timelineEntries: context.timelineEntries,
    rankings,
    highlights: {
      topDamageGroup: rankings.damage.find((group) => group.damageToVillain > 0) || null,
      topThreatAddedGroup: rankings.threatAdded.find((group) => group.threatAdded > 0) || null,
      topThreatRemovedGroup: rankings.threatRemoved.find((group) => group.threatRemoved > 0) || null,
      mostActiveGroup: rankings.activity.find((group) => group.activityCount > 0) || null,
      finalBlowGroup,
      firstContributionGroup,
    },
    totals: {
      actionCount: relevantLogs.length,
      activeGroupsCount: activeGroups.length,
      totalGroupsCount: groups.length,
      configuredPlayerCount: structure?.configuredPlayerCount || 0,
      totalReportedVillainDamage: finalSummary?.totalReportedVillainDamage ?? 0,
      finalThreat: finalSummary?.finalThreat ?? 0,
    },
    operationalSummary: context.operationalSummary,
    interPodSummary: context.interPodSummary,
  }
}

export function buildEventStatistics({ logs = [], structure = null, finalSummary = null } = {}) {
  const groupStatsByIdentifier = new Map()
  const flattenedGroups = flattenGroups(structure)

  for (const group of flattenedGroups) {
    groupStatsByIdentifier.set(group.identifier, buildGroupStats(group))
  }

  const relevantLogs = logs
    .filter((log) => STATISTICS_ACTION_TYPES.has(log.actionType))
    .sort((left, right) => new Date(left.timestamp) - new Date(right.timestamp))

  const context = {
    groupStatsByIdentifier,
    operationalSummary: createOperationalSummary(),
    interPodSummary: createInterPodSummary(),
    timelineEntries: [],
    firstContributionGroupIdentifier: null,
    finalBlowGroupIdentifier: null,
  }

  for (const log of relevantLogs) {
    processLog(context, log)
  }

  return buildResult(context, relevantLogs, structure, finalSummary)
}
