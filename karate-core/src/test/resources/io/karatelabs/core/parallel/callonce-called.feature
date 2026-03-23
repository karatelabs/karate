Feature: Called feature for callonce

  Scenario:
    * def calledId = java.util.UUID.randomUUID().toString()
    * karate.log('callonce-called executed, calledId:', calledId)
    * def sharedData = { id: calledId, counter: 0 }
