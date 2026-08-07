Feature: the lower equivalence control — the same two calls, checking what plain Gatling checks

  Paired with gatling-http.feature, which does the same POST and GET and then a structural
  match on all three fields plus the id round-trip. This one extracts a single value instead,
  which is what the plain-Gatling reference does.

  The pair exists because the parity number assumes both arms do equivalent work, and they do
  not: Karate's match is richer. Lowering Karate to the reference's level and raising the
  reference to Karate's level bracket the like-for-like cost from both sides. Neither variant
  alone can, because either one on its own only says which direction the gap moves.

  Keep the two files identical apart from the final check. Any other difference — a different
  body, a different path, an extra step — lands in the measurement as if it were assertion cost.

  Scenario: create a cat and read it back, checking one field
    * url mockUrl

    Given path 'cats'
    And request { name: 'Billie', age: 5 }
    When method post
    Then status 201
    * def catId = response.id

    Given path 'cats', catId
    When method get
    Then status 200
    And match response.name == 'Billie'
