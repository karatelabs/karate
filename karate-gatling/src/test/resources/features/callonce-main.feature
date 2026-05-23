Feature: callOnce main — uses callonce on the same helper

  Background:
    * def helper = callonce read('classpath:features/callsingle-helper.feature')

  Scenario: invoke
    * match helper.n == '#number'
