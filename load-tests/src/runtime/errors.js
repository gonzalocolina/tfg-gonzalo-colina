export class HarnessError extends Error {
  constructor(message, details = {}) {
    super(message)
    this.name = this.constructor.name
    Object.assign(this, details)
  }
}

export class TimeoutError extends HarnessError {}

export class ScenarioAssertionError extends HarnessError {}
