Feature:

Background:
  * callonce read('callonce-bg-called.feature')

Scenario: first
  * print 'in first'

Scenario Outline: outline <value>
  * print 'in outline', value
  * call read('callonce-bg-outline.feature')

  Examples:
    | value |
    | 1     |
