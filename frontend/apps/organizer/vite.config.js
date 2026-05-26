import { fileURLToPath, URL } from 'node:url'

import { createAppConfig } from '../../shared/vite/createAppConfig.js'

const appRoot = fileURLToPath(new URL('.', import.meta.url))

export default createAppConfig({
  appRoot,
  appName: 'organizer',
  basePath: '/organizer/',
  port: 5173,
})
