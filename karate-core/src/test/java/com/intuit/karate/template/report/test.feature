Feature: feature name

Background:
* print 'in background'

@one
Scenario: first one
* print 'in first one'

@one @two
Scenario: second one
* print 'in second one'