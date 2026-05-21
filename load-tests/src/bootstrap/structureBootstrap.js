export async function bootstrapStructure({ organizer, eventId, config }) {
  const pods = []
  const groups = []
  let groupNumber = 1

  for (let podIndex = 0; podIndex < config.podCount; podIndex += 1) {
    const pod = await organizer.createPod(eventId, {
      name: `Pod ${podIndex + 1}`,
      sortOrder: podIndex,
    })

    pods.push(pod)

    for (let groupIndex = 0; groupIndex < config.groupsPerPod; groupIndex += 1) {
      if (groupNumber > config.expectedGroupCount) break

      const group = await organizer.createGroup(eventId, pod.id, {
        identifier: `Mesa ${String(groupNumber).padStart(3, '0')}`,
        playerCount: config.playersPerGroup,
      })

      groups.push(group)
      groupNumber += 1
    }
  }

  return { pods, groups }
}
