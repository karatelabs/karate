Feature: unbound scope capture

  # Identical to the bound variant, except each capture is released immediately.
  #
  # A bare karate.call() - no args object - runs the callee in the caller's scope and
  # returns that scope wholesale. Binding the result means capture N contains every
  # capture before it, so the cost of copying the result grows geometrically with the
  # number of captures rather than linearly with the number of calls.

  Scenario:
    * def base = read('classpath:workload/base-payload.json')
    * def cap1 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap1 = null
    * def cap2 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap2 = null
    * def cap3 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap3 = null
    * def cap4 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap4 = null
    * def cap5 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap5 = null
    * def cap6 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap6 = null
    * def cap7 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap7 = null
    * def cap8 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap8 = null
    * def cap9 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap9 = null
    * def cap10 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap10 = null
    * def cap11 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap11 = null
    * def cap12 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap12 = null
    * def cap13 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap13 = null

    # Trailing steps, identical in both variants, so anything that scales with step
    # count alone is held constant between them.
    * def total = base.records.length
    * assert total == 100
