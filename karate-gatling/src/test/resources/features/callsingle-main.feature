Feature: callSingle main — uses karate.callSingle on the helper

  Scenario: invoke
    * def helper = karate.callSingle('classpath:features/callsingle-helper.feature')
