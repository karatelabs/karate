#!.karate
Feature: sample feature used by karate.callSingle()

  @ignore
  @storeValue
  Scenario: Set a Variable with param value
    * def receivedParam = (__arg && __arg.data) ? __arg.data : 'Nothing'


  Scenario: Set a Variable with param value
    * def sValue = "test from scenario"
    * def oTest = call read("@storeValue") { data: "#(sValue)" }
    * match oTest.receivedParam == sValue

