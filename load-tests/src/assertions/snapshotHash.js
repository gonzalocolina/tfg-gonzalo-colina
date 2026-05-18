import crypto from 'node:crypto'

function normalizeSnapshot(snapshot = null) {
  if (!snapshot) return null

  return {
    eventId: snapshot.eventId ?? null,
    eventStatus: snapshot.eventStatus ?? null,
    eventResult: snapshot.eventResult ?? null,
    villainHpCurrent: snapshot.villainHpCurrent ?? null,
    villainHpMax: snapshot.villainHpMax ?? null,
    schemeThreatCurrent: snapshot.schemeThreatCurrent ?? null,
    schemeThreatTarget: snapshot.schemeThreatTarget ?? null,
    timerRemainingSeconds: snapshot.timerRemainingSeconds ?? null,
    timerStatus: snapshot.timerStatus ?? null,
    totalReportedVillainDamage: snapshot.totalReportedVillainDamage ?? null,
  }
}

export function hashSnapshot(snapshot) {
  const normalized = normalizeSnapshot(snapshot)
  if (!normalized) return ''
  return crypto.createHash('sha1').update(JSON.stringify(normalized)).digest('hex')
}

export function summarizeSnapshot(snapshot) {
  return {
    hash: hashSnapshot(snapshot),
    villainHpCurrent: snapshot?.villainHpCurrent ?? null,
    schemeThreatCurrent: snapshot?.schemeThreatCurrent ?? null,
    timerRemainingSeconds: snapshot?.timerRemainingSeconds ?? null,
    eventStatus: snapshot?.eventStatus ?? null,
  }
}
