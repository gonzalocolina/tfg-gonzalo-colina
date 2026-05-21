import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const loadTestsDir = path.resolve(__dirname, '..')
const projectRoot = path.resolve(loadTestsDir, '..')
const resultsDir = path.join(loadTestsDir, 'results')

export const scenarioDefaults = {
  smoke: {
    clients: 5,
    podCount: 1,
    groupsPerPod: 5,
    playersPerGroup: 1,
    rampStep: 5,
    rampIntervalMs: 0,
    holdMs: 3000,
    deliveryTimeoutMs: 8000,
    startTimer: true,
    pulseValues: [1],
  },
  'nominal-100-10writers': {
    clients: 100,
    podCount: 10,
    groupsPerPod: 10,
    playersPerGroup: 1,
    rampStep: 10,
    rampIntervalMs: 5000,
    holdMs: 600000,
    deliveryTimeoutMs: 45000,
    startTimer: true,
    writerCount: 10,
    operationIntervalMs: 1000,
    damageValue: 1,
    threatValue: 1,
  },
  'burst-100writers': {
    clients: 100,
    podCount: 10,
    groupsPerPod: 10,
    playersPerGroup: 1,
    rampStep: 10,
    rampIntervalMs: 5000,
    holdMs: 0,
    deliveryTimeoutMs: 20000,
    startTimer: true,
    damageValue: 1,
    threatValue: 1,
    waveCount: 5,
    waveCooldownMs: 30000,
  },
  'connect-100': {
    clients: 100,
    podCount: 10,
    groupsPerPod: 10,
    playersPerGroup: 1,
    rampStep: 10,
    rampIntervalMs: 5000,
    holdMs: 60000,
    deliveryTimeoutMs: 15000,
    startTimer: true,
    pulseValues: [1, 2, 3, 4, 5],
    pulseIntervalMs: 10000,
  },
}

function parseBoolean(value, defaultValue = false) {
  if (value == null) return defaultValue
  if (typeof value === 'boolean') return value
  return ['1', 'true', 'yes', 'on'].includes(String(value).toLowerCase())
}

function parseNumber(value, fallback) {
  if (value == null || value === '') return fallback
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function trimTrailingSlash(value) {
  return value.replace(/\/+$/, '')
}

export function parseCliArgs(argv) {
  const parsed = {}

  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index]

    if (!token.startsWith('--')) continue

    const key = token.slice(2)
    const next = argv[index + 1]

    if (['help', 'dry-run', 'list-scenarios', 'start-timer'].includes(key)) {
      parsed[key] = true
      continue
    }

    if (key === 'no-start-timer') {
      parsed.startTimer = false
      continue
    }

    parsed[key] = next
    index += 1
  }

  return parsed
}

export function buildConfig(cliArgs = {}) {
  const scenarioName = cliArgs.scenario || process.env.LOAD_TEST_SCENARIO || 'smoke'
  const defaults = scenarioDefaults[scenarioName]

  if (!defaults) {
    throw new Error(`Escenario no soportado: ${scenarioName}`)
  }

  const baseUrl = trimTrailingSlash(cliArgs['base-url'] || process.env.LOAD_TEST_BASE_URL || 'http://localhost:8080')
  const clients = parseNumber(cliArgs.clients || process.env.LOAD_TEST_CLIENTS, defaults.clients)
  const groupsPerPod = parseNumber(cliArgs['groups-per-pod'] || process.env.LOAD_TEST_GROUPS_PER_POD, defaults.groupsPerPod)
  const podCount = parseNumber(
    cliArgs.pods || process.env.LOAD_TEST_PODS,
    defaults.podCount || Math.max(1, Math.ceil(clients / Math.max(groupsPerPod, 1))),
  )

  const config = {
    scenarioName,
    loadTestsDir,
    projectRoot,
    resultsDir,
    baseUrl,
    apiBaseUrl: `${baseUrl}/api`,
    wsUrl: `${baseUrl}/ws`,
    organizerUsername: cliArgs['organizer-username'] || process.env.LOAD_TEST_ORGANIZER_USERNAME || 'organizer',
    organizerPassword: cliArgs['organizer-password'] || process.env.LOAD_TEST_ORGANIZER_PASSWORD || 'organizer',
    eventNamePrefix: cliArgs['event-name-prefix'] || process.env.LOAD_TEST_EVENT_PREFIX || 'LoadTest',
    timeLimitMinutes: parseNumber(cliArgs['time-limit-minutes'] || process.env.LOAD_TEST_TIME_LIMIT, 180),
    clients,
    podCount,
    groupsPerPod,
    playersPerGroup: parseNumber(
      cliArgs['players-per-group'] || process.env.LOAD_TEST_PLAYERS_PER_GROUP,
      defaults.playersPerGroup,
    ),
    rampStep: parseNumber(cliArgs['ramp-step'] || process.env.LOAD_TEST_RAMP_STEP, defaults.rampStep),
    rampIntervalMs: parseNumber(
      cliArgs['ramp-interval-ms'] || process.env.LOAD_TEST_RAMP_INTERVAL_MS,
      defaults.rampIntervalMs,
    ),
    holdMs: parseNumber(cliArgs['hold-ms'] || process.env.LOAD_TEST_HOLD_MS, defaults.holdMs),
    deliveryTimeoutMs: parseNumber(
      cliArgs['delivery-timeout-ms'] || process.env.LOAD_TEST_DELIVERY_TIMEOUT_MS,
      defaults.deliveryTimeoutMs,
    ),
    writerCount: parseNumber(
      cliArgs['writer-count'] || process.env.LOAD_TEST_WRITER_COUNT,
      defaults.writerCount ?? 0,
    ),
    operationIntervalMs: parseNumber(
      cliArgs['operation-interval-ms'] || process.env.LOAD_TEST_OPERATION_INTERVAL_MS,
      defaults.operationIntervalMs ?? 0,
    ),
    damageValue: parseNumber(
      cliArgs['damage-value'] || process.env.LOAD_TEST_DAMAGE_VALUE,
      defaults.damageValue ?? 1,
    ),
    threatValue: parseNumber(
      cliArgs['threat-value'] || process.env.LOAD_TEST_THREAT_VALUE,
      defaults.threatValue ?? 1,
    ),
    waveCount: parseNumber(
      cliArgs['wave-count'] || process.env.LOAD_TEST_WAVE_COUNT,
      defaults.waveCount ?? 0,
    ),
    waveCooldownMs: parseNumber(
      cliArgs['wave-cooldown-ms'] || process.env.LOAD_TEST_WAVE_COOLDOWN_MS,
      defaults.waveCooldownMs ?? 0,
    ),
    pulseIntervalMs: parseNumber(
      cliArgs['pulse-interval-ms'] || process.env.LOAD_TEST_PULSE_INTERVAL_MS,
      defaults.pulseIntervalMs || 0,
    ),
    pulseValues: defaults.pulseValues,
    startTimer: cliArgs.startTimer === false
      ? false
      : parseBoolean(cliArgs['start-timer'] || process.env.LOAD_TEST_START_TIMER, defaults.startTimer),
    runLabel: cliArgs['run-label'] || process.env.LOAD_TEST_RUN_LABEL || '',
    dryRun: Boolean(cliArgs['dry-run']),
  }

  config.expectedGroupCount = config.podCount * config.groupsPerPod
  config.configuredPlayerCount = config.expectedGroupCount * config.playersPerGroup

  if (config.clients > config.expectedGroupCount) {
    throw new Error(
      `Se solicitan ${config.clients} clientes, pero la topología solo crea ${config.expectedGroupCount} grupos`,
    )
  }

  if (config.writerCount > config.clients) {
    throw new Error(
      `Se solicitan ${config.writerCount} escritores, pero solo habrá ${config.clients} clientes conectados`,
    )
  }

  return config
}
