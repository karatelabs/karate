@one
Feature: my feature
    my description

Background:
* print 'in background'

@two
Scenario: hello world
# Some comments
# Some more comments
* print 'before'
* call read('feature-result-called.feature')
* print 'after'

Scenario Outline: hello <name>
* print 'name:', name

Examples:
| name |
| foo  |
| bar  |
