Feature: Every kind of user output, one HTTP call, then a failure

  Everything here lands in the report buffer at INFO — print, karate.log(), karate.logger.info()
  and the HTTP block — so a replay of this feature is the check that none of them is dropped.

  Scenario: print, karate.log and karate.logger, then fail after a real request
    * print 'PRINTED-MARKER'
    * karate.log('KLOG-MARKER')
    * karate.logger.info('LOGGER-MARKER')
    Given url baseUrl
    And path 'cats'
    And request { name: '#(__gatling.name)' }
    When method post
    Then status 201
    * match response.name == 'WRONG_NAME_THAT_WILL_NEVER_MATCH'
