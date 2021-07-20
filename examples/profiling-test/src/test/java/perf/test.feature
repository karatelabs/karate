Feature:

  Background:
    * def backgroundData = callonce read('called.feature')
    * url karate.properties['mock.server.url']

  Scenario:
    * path 'test'
    * method get
    * match response == { success: true }
    * match backgroundData contains { var1: { foo: 'bar' } }
    * def scenarioData = karate.call('called.feature')
    * match scenarioData contains { var2: { baz: 'ban' } }
