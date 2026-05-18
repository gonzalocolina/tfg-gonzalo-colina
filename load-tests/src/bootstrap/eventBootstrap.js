export async function bootstrapEvent({ organizer, config }) {
  const villains = await organizer.getVillains()
  if (!villains.length) {
    throw new Error('No hay villanos disponibles para crear el evento de prueba')
  }

  const selectedVillain = villains[0]
  const event = await organizer.createEvent({
    name: `${config.eventNamePrefix}-${Date.now()}`,
    configuredPlayerCount: config.configuredPlayerCount,
    timeLimitMinutes: config.timeLimitMinutes,
    villainDefinitionId: selectedVillain.id,
    interPodCardDefinitionIds: [],
  })

  return { event, selectedVillain }
}
