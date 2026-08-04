Feature: bound scope capture

  # Thirteen bare calls, each result BOUND and left in scope.
  #
  # A bare karate.call() - no args object - runs the callee in the caller's scope and
  # returns that scope wholesale. Binding the result means capture N contains every
  # capture before it, so the cost of copying the result grows geometrically with the
  # number of captures rather than linearly with the number of calls.

  Scenario:
    * def base = read('classpath:workload/base-payload.json')
    * def cap1 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap2 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap3 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap4 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap5 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap6 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap7 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap8 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap9 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap10 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap11 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap12 = karate.call('classpath:workload/scope-capture-common.feature')
    * def cap13 = karate.call('classpath:workload/scope-capture-common.feature')

    # Trailing steps, identical in both variants, so anything that scales with step
    # count alone is held constant between them.
    * def total = base.records.length
    * assert total == 100
