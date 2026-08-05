Feature: one virtual user's realistic work — create, then read back

  Deliberately ordinary: a small JSON body, a captured id, and a structural match on the
  way back. The plain-Gatling variant it is compared against does the same two calls with
  a JSONPath check, so the difference between them is what Karate's richer handling of a
  response costs, not a difference in how much work was asked for.

  Scenario: create a cat and read it back
    * url mockUrl

    Given path 'cats'
    And request { name: 'Billie', age: 5 }
    When method post
    Then status 201
    * def catId = response.id

    Given path 'cats', catId
    When method get
    Then status 200
    And match response == { id: '#(catId)', name: 'Billie', age: 5 }
