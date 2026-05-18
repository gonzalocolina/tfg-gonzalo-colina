export async function authenticateOrganizer({ organizer, config }) {
  return organizer.login(config.organizerUsername, config.organizerPassword)
}
