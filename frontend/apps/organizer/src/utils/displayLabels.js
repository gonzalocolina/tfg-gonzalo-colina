const EVENT_STATUS_LABELS = Object.freeze({
  DRAFT: 'Borrador',
  IN_PROGRESS: 'En curso',
  FINISHED: 'Finalizado',
})

const EVENT_STATUS_BADGE_CLASSES = Object.freeze({
  DRAFT: 'badge-draft',
  IN_PROGRESS: 'badge-in-progress',
  FINISHED: 'badge-finished',
})

const ROLE_LABELS = Object.freeze({
  ORGANIZER: 'Organizador principal',
  CO_ORGANIZER: 'Co-organizador',
})

const RESPONSIBILITY_LABELS = Object.freeze({
  VILLAIN_HP: 'Vida del villano',
  THREAT: 'Amenaza',
  TIMER: 'Temporizador',
})

const RULE_TYPE_LABELS = Object.freeze({
  MULTIPLIER: 'Multiplicador',
  FIXED_VALUE: 'Valor fijo',
})

const INTER_POD_HP_RULE_LABELS = Object.freeze({
  MULTIPLIER: 'Por mesa (× nº de mesas del nodo)',
  FIXED_VALUE: 'HP fijo',
})

const INTER_POD_THREAT_RULE_LABELS = Object.freeze({
  MULTIPLIER: 'Por mesa (× nº de mesas del nodo)',
  FIXED_VALUE: 'Amenaza fija',
})

const CATALOG_TYPE_LABELS = Object.freeze({
  PREDEFINED: 'Predefinido',
  CUSTOM: 'Personalizado',
})

const CARD_CATEGORY_LABELS = Object.freeze({
  PLAN: 'Plan secundario',
  ESBIRRO: 'Esbirro',
})

const CARD_STATUS_LABELS = Object.freeze({
  OCULTA: 'Oculta',
  REVELADA: 'Revelada',
  DERROTADA: 'Derrotada',
})

const TIMER_STATUS_LABELS = Object.freeze({
  NOT_CONFIGURED: 'Sin temporizador',
  READY: 'Listo',
  RUNNING: 'En marcha',
  PAUSED: 'Pausado',
})

const LOG_ACTION_LABELS = Object.freeze({
  EVENT_CREATED: 'Evento creado',
  EVENT_UPDATED: 'Evento actualizado',
  EVENT_STARTED: 'Partida iniciada',
  EVENT_DELETED: 'Evento eliminado',
  CO_ORGANIZER_INVITATION_SENT: 'Invitación a co-organizador enviada',
  CO_ORGANIZER_INVITATION_ACCEPTED: 'Invitación de co-organizador aceptada',
  CO_ORGANIZER_INVITATION_REJECTED: 'Invitación de co-organizador rechazada',
  CO_ORGANIZER_INVITATION_REVOKED: 'Invitación de co-organizador revocada',
  GROUP_CREATED: 'Mesa creada',
  GROUP_UPDATED: 'Mesa actualizada',
  GROUP_DELETED: 'Mesa eliminada',
  POD_CREATED: 'Nodo creado',
  POD_UPDATED: 'Nodo actualizado',
  VILLAIN_DAMAGE_REPORTED: 'Daño al villano reportado',
  VILLAIN_DAMAGE_DELEGATED: 'Daño al villano delegado',
  THREAT_REPORTED: 'Amenaza reportada',
  THREAT_DELEGATED: 'Amenaza delegada',
  THREAT_REMOVED: 'Amenaza retirada',
  THREAT_REMOVED_DELEGATED: 'Retirada de amenaza delegada',
  VILLAIN_DAMAGE_REVERTED: 'Reversión de daño al villano',
  THREAT_ADDITION_REVERTED: 'Reversión de amenaza añadida',
  THREAT_REMOVAL_REVERTED: 'Reversión de amenaza retirada',
  VILLAIN_HP_CORRECTED: 'HP del villano corregido',
  THREAT_CORRECTED: 'Amenaza corregida',
  TIMER_STARTED: 'Temporizador iniciado',
  TIMER_PAUSED: 'Temporizador pausado',
  TIMER_RESUMED: 'Temporizador reanudado',
  TIMER_ADJUSTED: 'Temporizador ajustado',
  EVENT_REOPENED: 'Partida reabierta',
  INTER_POD_CARD_CREATED: 'Carta inter-nodo creada',
  INTER_POD_CARD_DAMAGED: 'Daño en carta inter-nodo',
  INTER_POD_CARD_THREAT_REDUCED: 'Amenaza reducida en carta inter-nodo',
  INTER_POD_CARD_THREAT_UPDATED: 'Amenaza actualizada en carta inter-nodo',
  INTER_POD_CARD_STATUS_UPDATED: 'Estado de carta inter-nodo actualizado',
  INTER_POD_CARD_DELETED: 'Carta inter-nodo eliminada',
  EVENT_FINISHED: 'Partida finalizada',
})

export const LOG_ACTION_TYPES = Object.freeze([
  'EVENT_CREATED',
  'EVENT_UPDATED',
  'EVENT_STARTED',
  'EVENT_DELETED',
  'CO_ORGANIZER_INVITATION_SENT',
  'CO_ORGANIZER_INVITATION_ACCEPTED',
  'CO_ORGANIZER_INVITATION_REJECTED',
  'CO_ORGANIZER_INVITATION_REVOKED',
  'GROUP_CREATED',
  'GROUP_UPDATED',
  'GROUP_DELETED',
  'POD_CREATED',
  'POD_UPDATED',
  'VILLAIN_DAMAGE_REPORTED',
  'VILLAIN_DAMAGE_DELEGATED',
  'THREAT_REPORTED',
  'THREAT_DELEGATED',
  'THREAT_REMOVED',
  'THREAT_REMOVED_DELEGATED',
  'VILLAIN_DAMAGE_REVERTED',
  'THREAT_ADDITION_REVERTED',
  'THREAT_REMOVAL_REVERTED',
  'VILLAIN_HP_CORRECTED',
  'THREAT_CORRECTED',
  'TIMER_STARTED',
  'TIMER_PAUSED',
  'TIMER_RESUMED',
  'TIMER_ADJUSTED',
  'EVENT_REOPENED',
  'INTER_POD_CARD_CREATED',
  'INTER_POD_CARD_DAMAGED',
  'INTER_POD_CARD_THREAT_REDUCED',
  'INTER_POD_CARD_STATUS_UPDATED',
  'INTER_POD_CARD_DELETED',
  'EVENT_FINISHED',
])

function formatFromMap(value, map, fallback = '-') {
  if (!value) return fallback
  return map[value] || value
}

export function eventStatusBadgeClass(status) {
  return EVENT_STATUS_BADGE_CLASSES[status] || 'badge-draft'
}

export function formatEventStatus(status) {
  return formatFromMap(status, EVENT_STATUS_LABELS)
}

export function formatUserRole(role) {
  return formatFromMap(role, ROLE_LABELS)
}

export function formatResponsibility(responsibility) {
  return formatFromMap(responsibility, RESPONSIBILITY_LABELS)
}

export function formatResponsibilities(responsibilities = [], emptyLabel = '-') {
  if (!responsibilities.length) return emptyLabel
  return responsibilities.map((responsibility) => formatResponsibility(responsibility)).join(', ')
}

export function formatRuleType(ruleType) {
  return formatFromMap(ruleType, RULE_TYPE_LABELS)
}

export function formatInterPodRuleType(ruleType, category = 'ESBIRRO') {
  const labels = category === 'ESBIRRO' ? INTER_POD_HP_RULE_LABELS : INTER_POD_THREAT_RULE_LABELS
  return formatFromMap(ruleType, labels)
}

export function formatInterPodHpRuleType(ruleType) {
  return formatInterPodRuleType(ruleType, 'ESBIRRO')
}

export function formatInterPodThreatRuleType(ruleType) {
  return formatInterPodRuleType(ruleType, 'PLAN')
}

export function formatInterPodRuleTypeForDefinition(definition) {
  if (!definition) return '-'
  return formatInterPodRuleType(definition.ruleType || 'MULTIPLIER', definition.cardCategory)
}

/** @deprecated use formatInterPodRuleType */
export function formatInterPodValueRuleType(ruleType) {
  return formatInterPodHpRuleType(ruleType)
}

export function formatInterPodMetricSummary(definition) {
  if (!definition) return '-'
  const isMinion = definition.cardCategory === 'ESBIRRO'
  const value = definition.defaultMetricMax
  const metric = isMinion ? 'HP' : 'Amenaza'
  const ruleType = definition.ruleType
  if (ruleType === 'FIXED_VALUE') {
    return `${metric} fijo ${value}`
  }
  return `${metric} base ${value}/mesa`
}

export function interPodMetricValueLabel(category, ruleType) {
  const isMinion = category === 'ESBIRRO'
  const metric = isMinion ? 'HP' : 'Amenaza'
  if (ruleType === 'FIXED_VALUE') {
    return `${metric} total`
  }
  return `${metric} base por mesa`
}

export function formatCatalogItemType(type) {
  return formatFromMap(type, CATALOG_TYPE_LABELS)
}

export function formatCardCategory(category) {
  return formatFromMap(category, CARD_CATEGORY_LABELS)
}

export function formatCardStatus(status) {
  return formatFromMap(status, CARD_STATUS_LABELS)
}

export function formatTimerStatus(status) {
  return formatFromMap(status, TIMER_STATUS_LABELS)
}

export function formatLogActionType(actionType) {
  return formatFromMap(actionType, LOG_ACTION_LABELS)
}
