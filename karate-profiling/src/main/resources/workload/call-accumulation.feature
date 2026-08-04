Feature: many sequential calls from one long scenario

  # Sixty sequential calls from a single scenario, repeated across many scenarios in ONE
  # suite. The call results are never bound to a variable - this is not about what a call
  # returns, but about what is kept after it returns.
  #
  # Each call produces a FeatureResult tree that the calling StepResult retains
  # (StepResult.callResults), which ScenarioResult.stepResults retains, which
  # SuiteResult.featureResults retains for the whole run. Nothing is released until the
  # suite ends, so the live set scales with total scenarios x calls-per-scenario rather
  # than with anything per-scenario.
  #
  # Scenario count comes from the harness so the same feature can be run at several
  # scales - the interesting question is how peak memory moves with it, not any single
  # number.

  Scenario Outline: call accumulation scenario <i>
    * def someVar = 'test-value'
    * def baseArray = karate.repeat(100, function(x){ return { id: x, name: 'record_' + x, email: someVar, status: someVar, attributes: { source: someVar, target: someVar } } })

    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')
    * karate.call('classpath:workload/call-accumulation-callee.feature')

    * def count = baseArray.length
    * match count == 100

    Examples:
      | karate.mapWithKey(karate.range(0, parseInt(karate.properties['profiling.scenarios'])), 'i') |
