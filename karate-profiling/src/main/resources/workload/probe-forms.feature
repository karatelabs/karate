Feature: which call form returns the caller's scope?

  Scenario:
    * def base = read('classpath:workload/base-payload.json')
    # form 1: JS api, no args
    * def viaJs = karate.call('classpath:workload/probe-callee.feature')
    # form 2: gherkin keyword via read(), no args
    * def viaGherkin = call read('classpath:workload/probe-callee.feature')
    # form 3: gherkin keyword with an args object (the isolated-scope control)
    * def viaGherkinArgs = call read('classpath:workload/probe-callee.feature') { seed: 1 }
    # form 4: JS api with args
    * def viaJsArgs = karate.call('classpath:workload/probe-callee.feature', { seed: 1 })
