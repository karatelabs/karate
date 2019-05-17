Feature: 

Background:
* def helper = Java.type('com.intuit.karate.junit4.demos.CallonceHelper')

Scenario Outline: generate random string

* def fun = function(){ return Math.random().toString(36).substr(2, 10) }
* def result = callonce fun
* print '<example>: ' + result
* assert helper.isEqualToPrevious(result)

Examples: 
  | example |
  | first   |
  | second  |
