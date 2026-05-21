export async function bootstrapGameStart({ organizer, eventId, startTimer }) {
  const startResult = await organizer.startGame(eventId)
  let timerResult = null

  if (startTimer) {
    timerResult = await organizer.startTimer(eventId)
  }

  return { startResult, timerResult }
}
