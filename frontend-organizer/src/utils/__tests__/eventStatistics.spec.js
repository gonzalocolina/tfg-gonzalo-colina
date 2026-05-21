import { describe, expect, it } from 'vitest'
import { buildEventStatistics } from '@/utils/eventStatistics'

function buildStructure() {
  return {
    pods: [
      {
        name: 'Pod Norte',
        groups: [
          { identifier: 'Mesa Alfa' },
          { identifier: 'Mesa Beta' },
        ],
      },
    ],
  }
}

function buildFinalSummary() {
  return {
    result: 'PLAYERS_VICTORY',
    cause: 'VILLAIN_HP_ZERO',
    durationSeconds: 4200,
    totalReportedVillainDamage: 40,
    finalThreat: 2,
  }
}

describe('buildEventStatistics', () => {
  it('aggregates per-group contributions and operational counters from gameplay logs', () => {
    const statistics = buildEventStatistics({
      structure: buildStructure(),
      finalSummary: buildFinalSummary(),
      gameState: {
        villainHpMax: 40,
      },
      logs: [
        {
          id: 1,
          timestamp: '2026-05-18T10:00:00Z',
          actor: 'organizer',
          groupIdentifier: null,
          actionType: 'EVENT_STARTED',
          previousValue: null,
          newValue: 'IN_PROGRESS',
          notes: 'Partida iniciada manualmente.',
        },
        {
          id: 2,
          timestamp: '2026-05-18T10:01:00Z',
          actor: 'rep-a',
          groupIdentifier: 'Mesa Alfa',
          actionType: 'VILLAIN_DAMAGE_REPORTED',
          previousValue: '40',
          newValue: '34',
          notes: 'Daño directo.',
        },
        {
          id: 3,
          timestamp: '2026-05-18T10:02:00Z',
          actor: 'rep-b',
          groupIdentifier: 'Mesa Beta',
          actionType: 'THREAT_REPORTED',
          previousValue: '0',
          newValue: '3',
          notes: 'Amenaza añadida.',
        },
        {
          id: 4,
          timestamp: '2026-05-18T10:03:00Z',
          actor: 'coorg',
          groupIdentifier: 'Mesa Alfa',
          actionType: 'THREAT_REMOVED_DELEGATED',
          previousValue: '3',
          newValue: '1',
          notes: 'Amenaza retirada.',
        },
        {
          id: 5,
          timestamp: '2026-05-18T10:04:00Z',
          actor: 'coorg',
          groupIdentifier: 'Mesa Beta',
          actionType: 'VILLAIN_DAMAGE_DELEGATED',
          previousValue: '34',
          newValue: '30',
          notes: 'Daño delegado.',
        },
        {
          id: 6,
          timestamp: '2026-05-18T10:05:00Z',
          actor: 'rep-a',
          groupIdentifier: 'Mesa Alfa',
          actionType: 'INTER_POD_CARD_CREATED',
          previousValue: null,
          newValue: 'Plan de refuerzo',
          notes: 'Carta creada.',
        },
        {
          id: 7,
          timestamp: '2026-05-18T10:06:00Z',
          actor: 'rep-b',
          groupIdentifier: 'Mesa Beta',
          actionType: 'INTER_POD_CARD_THREAT_REDUCED',
          previousValue: '4',
          newValue: '1',
          notes: 'Plan inter-pod contenido.',
        },
        {
          id: 8,
          timestamp: '2026-05-18T10:07:00Z',
          actor: 'coorg',
          groupIdentifier: null,
          actionType: 'TIMER_PAUSED',
          previousValue: null,
          newValue: '1200',
          notes: 'Temporizador pausado.',
        },
        {
          id: 9,
          timestamp: '2026-05-18T10:08:00Z',
          actor: 'rep-a',
          groupIdentifier: 'Mesa Alfa',
          actionType: 'VILLAIN_DAMAGE_REPORTED',
          previousValue: '30',
          newValue: '0',
          notes: 'Golpe final.',
        },
        {
          id: 10,
          timestamp: '2026-05-18T10:09:00Z',
          actor: null,
          groupIdentifier: null,
          actionType: 'EVENT_FINISHED',
          previousValue: null,
          newValue: 'PLAYERS_VICTORY',
          notes: 'Partida finalizada por VILLAIN_HP_ZERO',
        },
      ],
    })

    expect(statistics.totals.actionCount).toBe(10)
    expect(statistics.totals.activeGroupsCount).toBe(2)
    expect(statistics.operationalSummary.delegatedActions).toBe(2)
    expect(statistics.operationalSummary.timerPauses).toBe(1)
    expect(statistics.interPodSummary.created).toBe(1)
    expect(statistics.interPodSummary.threatReducedTotal).toBe(3)

    expect(statistics.highlights.topDamageGroup?.identifier).toBe('Mesa Alfa')
    expect(statistics.highlights.firstContributionGroup?.identifier).toBe('Mesa Alfa')
    expect(statistics.highlights.finalBlowGroup?.identifier).toBe('Mesa Alfa')

    const alfa = statistics.groups.find((group) => group.identifier === 'Mesa Alfa')
    const beta = statistics.groups.find((group) => group.identifier === 'Mesa Beta')

    expect(alfa?.damageToVillain).toBe(36)
    expect(alfa?.threatRemoved).toBe(2)
    expect(alfa?.interPodCreated).toBe(1)
    expect(alfa?.finalBlow).toBe(true)

    expect(beta?.damageToVillain).toBe(4)
    expect(beta?.threatAdded).toBe(3)
    expect(beta?.interPodThreatReduced).toBe(3)
    expect(beta?.delegatedActionCount).toBe(1)

    expect(statistics.progression.maxDamage).toBe(40)
    expect(statistics.progression.maxThreat).toBe(3)
    expect(statistics.progression.series.map((point) => ({
      actionType: point.actionType,
      villainDamage: point.villainDamage,
      threat: point.threat,
    }))).toEqual([
      {
        actionType: 'EVENT_STARTED',
        villainDamage: 0,
        threat: 0,
      },
      {
        actionType: 'VILLAIN_DAMAGE_REPORTED',
        villainDamage: 6,
        threat: 0,
      },
      {
        actionType: 'THREAT_REPORTED',
        villainDamage: 6,
        threat: 3,
      },
      {
        actionType: 'THREAT_REMOVED_DELEGATED',
        villainDamage: 6,
        threat: 1,
      },
      {
        actionType: 'VILLAIN_DAMAGE_DELEGATED',
        villainDamage: 10,
        threat: 1,
      },
      {
        actionType: 'VILLAIN_DAMAGE_REPORTED',
        villainDamage: 40,
        threat: 1,
      },
      {
        actionType: 'EVENT_FINISHED',
        villainDamage: 40,
        threat: 1,
      },
    ])
  })

  it('tracks manual corrections in the temporal progression', () => {
    const statistics = buildEventStatistics({
      structure: buildStructure(),
      finalSummary: buildFinalSummary(),
      gameState: {
        villainHpMax: 40,
      },
      logs: [
        {
          id: 1,
          timestamp: '2026-05-18T10:00:00Z',
          actor: 'organizer',
          groupIdentifier: null,
          actionType: 'EVENT_STARTED',
          previousValue: null,
          newValue: 'IN_PROGRESS',
          notes: 'Partida iniciada manualmente.',
        },
        {
          id: 2,
          timestamp: '2026-05-18T10:01:00Z',
          actor: 'rep-a',
          groupIdentifier: 'Mesa Alfa',
          actionType: 'VILLAIN_DAMAGE_REPORTED',
          previousValue: '40',
          newValue: '30',
          notes: 'Daño fuerte.',
        },
        {
          id: 3,
          timestamp: '2026-05-18T10:02:00Z',
          actor: 'rep-b',
          groupIdentifier: 'Mesa Beta',
          actionType: 'THREAT_REPORTED',
          previousValue: '0',
          newValue: '4',
          notes: 'Amenaza añadida.',
        },
        {
          id: 4,
          timestamp: '2026-05-18T10:03:00Z',
          actor: 'coorg',
          groupIdentifier: null,
          actionType: 'VILLAIN_HP_CORRECTED',
          previousValue: '30',
          newValue: '32',
          notes: 'Corrección manual de HP.',
        },
        {
          id: 5,
          timestamp: '2026-05-18T10:04:00Z',
          actor: 'coorg',
          groupIdentifier: null,
          actionType: 'THREAT_CORRECTED',
          previousValue: '4',
          newValue: '1',
          notes: 'Corrección manual de amenaza.',
        },
        {
          id: 6,
          timestamp: '2026-05-18T10:05:00Z',
          actor: null,
          groupIdentifier: null,
          actionType: 'EVENT_FINISHED',
          previousValue: null,
          newValue: 'PLAYERS_VICTORY',
          notes: 'Partida finalizada.',
        },
      ],
    })

    expect(statistics.progression.series.map((point) => ({
      actionType: point.actionType,
      villainDamage: point.villainDamage,
      threat: point.threat,
    }))).toEqual([
      {
        actionType: 'EVENT_STARTED',
        villainDamage: 0,
        threat: 0,
      },
      {
        actionType: 'VILLAIN_DAMAGE_REPORTED',
        villainDamage: 10,
        threat: 0,
      },
      {
        actionType: 'THREAT_REPORTED',
        villainDamage: 10,
        threat: 4,
      },
      {
        actionType: 'VILLAIN_HP_CORRECTED',
        villainDamage: 8,
        threat: 4,
      },
      {
        actionType: 'THREAT_CORRECTED',
        villainDamage: 8,
        threat: 1,
      },
      {
        actionType: 'EVENT_FINISHED',
        villainDamage: 8,
        threat: 1,
      },
    ])
  })

  it('discounts reverted logs from per-group totals and progression', () => {
    const statistics = buildEventStatistics({
      structure: buildStructure(),
      finalSummary: buildFinalSummary(),
      gameState: {
        villainHpMax: 40,
      },
      logs: [
        {
          id: 1,
          timestamp: '2026-05-18T10:00:00Z',
          actor: 'organizer',
          groupIdentifier: null,
          actionType: 'EVENT_STARTED',
          previousValue: null,
          newValue: 'IN_PROGRESS',
          notes: 'Partida iniciada manualmente.',
        },
        {
          id: 2,
          timestamp: '2026-05-18T10:01:00Z',
          actor: 'rep-a',
          groupIdentifier: 'Mesa Alfa',
          actionType: 'VILLAIN_DAMAGE_REPORTED',
          previousValue: '40',
          newValue: '35',
          notes: 'Daño incorrecto.',
        },
        {
          id: 3,
          timestamp: '2026-05-18T10:02:00Z',
          actor: 'rep-a',
          groupIdentifier: 'Mesa Alfa',
          actionType: 'THREAT_REPORTED',
          previousValue: '0',
          newValue: '2',
          notes: 'Amenaza incorrecta.',
        },
        {
          id: 4,
          timestamp: '2026-05-18T10:03:00Z',
          actor: 'organizer',
          groupIdentifier: 'Mesa Alfa',
          actionType: 'VILLAIN_DAMAGE_REVERTED',
          revertedLogId: 2,
          previousValue: '35',
          newValue: '40',
          notes: 'Reversión del daño.',
        },
        {
          id: 5,
          timestamp: '2026-05-18T10:04:00Z',
          actor: 'organizer',
          groupIdentifier: 'Mesa Alfa',
          actionType: 'THREAT_ADDITION_REVERTED',
          revertedLogId: 3,
          previousValue: '2',
          newValue: '0',
          notes: 'Reversión de la amenaza.',
        },
      ],
    })

    const alfa = statistics.groups.find((group) => group.identifier === 'Mesa Alfa')

    expect(alfa?.damageToVillain).toBe(0)
    expect(alfa?.threatAdded).toBe(0)
    expect(statistics.progression.series.map((point) => ({
      actionType: point.actionType,
      villainDamage: point.villainDamage,
      threat: point.threat,
    }))).toEqual([
      {
        actionType: 'EVENT_STARTED',
        villainDamage: 0,
        threat: 0,
      },
      {
        actionType: 'VILLAIN_DAMAGE_REPORTED',
        villainDamage: 5,
        threat: 0,
      },
      {
        actionType: 'THREAT_REPORTED',
        villainDamage: 5,
        threat: 2,
      },
      {
        actionType: 'VILLAIN_DAMAGE_REVERTED',
        villainDamage: 0,
        threat: 2,
      },
      {
        actionType: 'THREAT_ADDITION_REVERTED',
        villainDamage: 0,
        threat: 0,
      },
    ])
  })

  it('ignores setup-only logs when counting gameplay actions', () => {
    const statistics = buildEventStatistics({
      structure: buildStructure(),
      finalSummary: buildFinalSummary(),
      logs: [
        {
          id: 1,
          timestamp: '2026-05-18T09:00:00Z',
          actor: 'organizer',
          groupIdentifier: 'Mesa Alfa',
          actionType: 'GROUP_CREATED',
          previousValue: null,
          newValue: 'Mesa Alfa',
          notes: 'Mesa creada.',
        },
        {
          id: 2,
          timestamp: '2026-05-18T10:00:00Z',
          actor: 'rep-a',
          groupIdentifier: 'Mesa Alfa',
          actionType: 'VILLAIN_DAMAGE_REPORTED',
          previousValue: '2',
          newValue: '0',
          notes: 'Daño final.',
        },
      ],
    })

    expect(statistics.totals.actionCount).toBe(1)
    expect(statistics.highlights.finalBlowGroup?.identifier).toBe('Mesa Alfa')
    expect(statistics.groups.find((group) => group.identifier === 'Mesa Alfa')?.activityCount).toBe(1)
  })
})
