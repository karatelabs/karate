@ignore
Feature:

Background:
  * callonce read('callonce-bg-called.feature')

Scenario: first
  * print 'in first'

Scenario Outline: outline <value>
  * print 'in main outline value:', value
  * call read('callonce-bg-outline.feature')
  * match karate.info.scenarioName == 'outline ' + value

  Examples:
    | value |
    | 1     |
    | 2     |
