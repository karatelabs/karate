@ignore
Feature: even java interop performance test reports are possible

  Background:
    * def Utils = Java.type('mock.MockUtils')

  Scenario: fifty
    * def payload = { sleep: 50 }
    * def response = Utils.myRpc(payload, karate)
    * match response == { success: true }

  Scenario: seventy five
    * def payload = { sleep: 75 }
    * def response = Utils.myRpc(payload, karate)
    # this is deliberately set up to fail
    * match response == { success: false }

  Scenario: hundred
    * def payload = { sleep: 100 }
    * def response = Utils.myRpc(payload, karate)
    * match response == { success: true }
