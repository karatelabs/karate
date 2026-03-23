Feature: Config functions in parallel

  # Tests that functions defined in karate-config.js work correctly
  # across parallel scenarios

  Scenario: config function test 1
    * match sharedFunction('one') == 'hello one'
    * match configData.name == 'fromConfig'

  Scenario: config function test 2
    * match sharedFunction('two') == 'hello two'
    * match configData.name == 'fromConfig'

  Scenario: config function test 3
    * match sharedFunction('three') == 'hello three'
    * match configData.name == 'fromConfig'

  Scenario: config function test 4
    * match sharedFunction('four') == 'hello four'
    * match configData.name == 'fromConfig'
