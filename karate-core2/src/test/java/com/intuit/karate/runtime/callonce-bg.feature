Feature:

Background:
  * callonce read('callonce-bg-called.feature')

Scenario: first
  * print 'first'

Scenario Outline: outline <value>
  * print 'in main outline value:', value
  * call read('callonce-bg-outline.feature')

  Examples:
    | value |
    | 1     |
