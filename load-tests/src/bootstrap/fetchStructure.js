export function flattenStructureGroups(structure) {
  const assigned = (structure?.pods || []).flatMap((pod) =>
    (pod.groups || []).map((group) => ({
      ...group,
      podName: pod.name,
    })),
  )

  const unassigned = (structure?.unassignedGroups || []).map((group) => ({
    ...group,
    podName: null,
  }))

  return [...assigned, ...unassigned]
}

export async function fetchResolvedStructure({ organizer, eventId }) {
  const structure = await organizer.getEventStructure(eventId)
  return {
    structure,
    groups: flattenStructureGroups(structure),
  }
}
