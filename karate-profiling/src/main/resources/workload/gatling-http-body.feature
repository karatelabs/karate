Feature: the body-size tier — the same two calls against a response of a chosen size

  Identical to gatling-http.feature apart from one added field, which carries the padding
  that sets the body size. Kept as a separate file rather than a flag on that one: the
  unpadded arms produced every published figure, and adding a conditional field to them
  would change what the baseline measures.

  The match is CLOSED and includes the padding, which is the point. Karate compares the
  whole document; the plain reference reads three small fields and never touches the pad.
  That difference is the idiomatic form of each arm and is what the tier is measuring —
  whether the gap between them holds as the body grows, or widens with it.

  The pad is READ here, never generated. The simulation sets the property once, before any
  load; building a kilobyte string per scenario would put allocation in every iteration and
  land in the measurement as if it were response-handling cost.

  Read in this feature rather than in karate-config.js on purpose. That file is evaluated by
  every Karate workload, so a key added there would change the null pair and the 34-byte arms
  — baselines this tier has no business touching. The one extra property read costs the same
  at every body size, and the tier measures a SLOPE across sizes, so a constant offset in one
  arm cannot move the result.

  Scenario: create a padded cat and read it back
    * url mockUrl
    * def pad = karate.properties['karate.profiling.pad']

    Given path 'cats'
    And request { name: 'Billie', age: 5, pad: '#(pad)' }
    When method post
    Then status 201
    * def catId = response.id

    Given path 'cats', catId
    When method get
    Then status 200
    And match response == { id: '#(catId)', name: 'Billie', age: 5, pad: '#(pad)' }
