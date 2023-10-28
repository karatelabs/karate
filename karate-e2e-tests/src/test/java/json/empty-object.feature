Feature:

  Scenario: empty object should fail:
    * def response = {}
    * match response.id == '#null'
    * match response.time == '#present'