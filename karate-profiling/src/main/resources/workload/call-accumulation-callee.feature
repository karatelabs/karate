Feature: callee for the call-accumulation workload

  # Stands in for a shared "common API" feature that a real suite calls dozens of times
  # per scenario. Every one of these executions produces a FeatureResult tree that the
  # calling StepResult holds on to.

  Scenario:
    * def someVar = 'test-value'
    * def resultPayload =
    """
    {
      "meta": {
        "page": { "size": "#(someVar)", "number": "#(someVar)" },
        "filters": { "status": "#(someVar)", "type": "#(someVar)" }
      },
      "data": {
        "id": "#(someVar)",
        "attributes": { "name": "#(someVar)", "value": "#(someVar)" }
      }
    }
    """
