Feature: simple mock

  Scenario: pathMatches('/json_order') && methodIs('post')
    * def response = {"tango":"Alice","foxtrot":"0.0.0.0","sierra":"Bob"}