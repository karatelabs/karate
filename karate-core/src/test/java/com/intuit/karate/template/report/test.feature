Feature: feature name

Background:
# background comment
* print 'in background'

@one
Scenario: first one
* print 'in first one'
* def foo = 'no log'
* call read('called.feature')

@one @two
Scenario: second one
* print 'in second one'
# some comment
* def bar = 'no log'