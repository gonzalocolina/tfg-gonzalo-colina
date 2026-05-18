import process from 'node:process'
import { buildConfig, parseCliArgs, scenarioDefaults } from './config.js'
import { MetricsCollector } from './metrics/metricsCollector.js'
import { scenarios } from './scenarios/index.js'

function printHelp() {
  console.log(`Uso: node src/index.js [opciones]\n\nOpciones:\n  --scenario <nombre>         Escenario a ejecutar (${Object.keys(scenarioDefaults).join(', ')})\n  --clients <n>               Sobrescribe el número de clientes\n  --pods <n>                  Número de pods a crear\n  --groups-per-pod <n>        Número de grupos por pod\n  --ramp-step <n>             Tamaño de cada lote de conexión\n  --ramp-interval-ms <ms>     Pausa entre lotes de conexión\n  --hold-ms <ms>              Duración total o espera principal del escenario\n  --writer-count <n>          Número de escritores activos en escenarios nominales\n  --operation-interval-ms <ms> Intervalo entre operaciones nominales\n  --damage-value <n>          Valor usado para reportes de daño\n  --threat-value <n>          Valor usado para reportes de amenaza\n  --wave-count <n>            Número de olas en escenarios burst\n  --wave-cooldown-ms <ms>     Enfriamiento entre olas burst\n  --delivery-timeout-ms <ms>  Timeout para fan-out y convergencia\n  --start-timer               Fuerza arranque del temporizador\n  --no-start-timer            Desactiva arranque del temporizador\n  --base-url <url>            URL base del backend (por defecto http://localhost:8080)\n  --run-label <texto>         Etiqueta adicional para la carpeta de resultados\n  --dry-run                   Muestra la configuración resuelta y sale\n  --list-scenarios            Lista escenarios disponibles\n  --help                      Muestra esta ayuda\n`)
}

async function main() {
  const cliArgs = parseCliArgs(process.argv.slice(2))

  if (cliArgs.help) {
    printHelp()
    return
  }

  if (cliArgs['list-scenarios']) {
    console.log(Object.keys(scenarios).join('\n'))
    return
  }

  const config = buildConfig(cliArgs)
  const scenario = scenarios[config.scenarioName]

  if (!scenario) {
    throw new Error(`Escenario no implementado: ${config.scenarioName}`)
  }

  if (config.dryRun) {
    console.log(JSON.stringify(config, null, 2))
    return
  }

  const metrics = await MetricsCollector.create(config)

  try {
    await metrics.initialize()
    const result = await scenario.run({ config, metrics })
    await metrics.finalize({
      status: 'passed',
      scenarioDescription: scenario.description,
      result,
    })

    console.log(`Escenario '${config.scenarioName}' completado.`)
    console.log(`Resultados: ${metrics.runDir}`)
  } catch (error) {
    metrics.recordError({
      scope: 'run',
      message: error.message,
      stack: error.stack,
    })

    await metrics.finalize({
      status: 'failed',
      scenarioDescription: scenario.description,
      result: null,
      failureMessage: error.message,
    })

    console.error(error.stack || error.message)
    process.exitCode = 1
  }
}

main()
