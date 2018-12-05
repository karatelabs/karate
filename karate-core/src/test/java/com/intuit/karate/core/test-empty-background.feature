Feature:

Background:

@foo
Scenario: should have @foo as tag
  * def temp = karate.tags
  * match temp == ['foo']
