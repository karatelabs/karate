Feature:

  Scenario: recommended to use should not fail:
    * def response = ''
    * match response == { id: '#null', time: '#present' }