Feature: test that works un-changed for http as well as in-process servlet / mock-http

Scenario: get hello

  When url demoBaseUrl
  And path 'hello'
  When method get
  Then status 200
  And match response == 'hello world'

Scenario: post cat

    When url demoBaseUrl
    And path 'hello'
    And request { name: 'Billie' }
    When method post
    Then status 200
    And match response == { success: true }

## Uncomment below to test #705 fix in intellij.

#Scenario: post cat again to check only this log is seen.
#
#    When url demoBaseUrl
#    And path 'hello'
#    And request { name: 'Billie' }
#    When method post
#    Then status 200
#    And match response == { success: true }
#
#Scenario: post cat fail
#
#    When url demoBaseUrl
#    And path 'hello'
#    And request { name: 'john' }
#    When method post
#    Then status 200
#    And match response == { success: false }
#
#
#Scenario: get hello again
#
#    When url demoBaseUrl
#    And path 'hello'
#    When method get
#    Then status 200
#    And match response == 'hello world'
#
#  Scenario: post random data
#
#    When url demoBaseUrl
#    And path 'hello'
#    And request { name: 65 }
#    When method post
#    Then status 200
#    And match response == { success: true }
#
#
#  Scenario: post cat fail again
#
#    When url demoBaseUrl
#    And path 'hello'
#    And request { name: 'pauline' }
#    When method post
#    Then status 200
#    And match response == { success: false }
#
#  Scenario: get hello again 2
#
#    When url demoBaseUrl
#    And path 'hello'
#    When method get
#    Then status 200
#    And match response == 'hello world'
#
#  Scenario: post random data 2
#
#    When url demoBaseUrl
#    And path 'hello'
#    And request { name: 65 }
#    When method post
#    Then status 200
#    And match response == { success: true }