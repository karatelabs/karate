Feature: Mock API returning text/plain;charset=UTF-8

  Scenario: pathMatches('/config') && methodIs('get')
    * def responseStatus = 200
    * string response = "[agent]\n\tinterval = \"1s\"\n\tround_interval = true\n\n[[processors.date]]\n\torder=1"
