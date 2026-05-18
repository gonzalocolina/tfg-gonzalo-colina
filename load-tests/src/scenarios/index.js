import { nominal100Scenario } from './s02-nominal-100-10writers.js'
import { burst100WritersScenario } from './s03-burst-100writers.js'
import { connect100Scenario } from './s01-connect-100.js'
import { smokeScenario } from './smoke.js'

export const scenarios = {
  [smokeScenario.name]: smokeScenario,
  [nominal100Scenario.name]: nominal100Scenario,
  [burst100WritersScenario.name]: burst100WritersScenario,
  [connect100Scenario.name]: connect100Scenario,
}
