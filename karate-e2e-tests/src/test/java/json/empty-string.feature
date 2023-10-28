Feature:

  Scenario: empty reponse should fail:
    * def response = ''
    * match response.time == '#present'
    * match response.id == '#null'