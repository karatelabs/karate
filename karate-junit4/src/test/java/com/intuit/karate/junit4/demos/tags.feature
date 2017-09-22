@ignore
Feature: test tags

Scenario: test feature level tag
* def tags = (karate.tags)
* match tags == ['ignore']

@foo
Scenario: test feature and scenario tag
* def tags = (karate.tags)
* match tags == ['ignore', 'foo']
