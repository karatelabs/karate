@one
Feature:

Background:
* print 'in background'

@two
Scenario: hello world
* print 'before'
* call read('scenario-result-called.feature')
* print 'after'

Scenario Outline: hello <name>
* print 'name:', name

Examples:
| name |
| foo  |
| bar  |
