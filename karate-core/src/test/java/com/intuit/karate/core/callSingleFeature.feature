#!.karate
Feature: sample feature used by karate.callSingle()

  Scenario: Set a Variable with param value
    * def receivedParam = (__arg && __arg.data) ? __arg.data : 'Nothing'

