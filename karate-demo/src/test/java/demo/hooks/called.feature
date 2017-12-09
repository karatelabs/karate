@ignore
Feature:

Background:
# 'afterScenario' and 'afterFeature' are NOT supported when a feature is called
# so this will have no effect, UNLESS this feature is run directly
* configure afterScenario = function(){ karate.log('end called scenario') }

Scenario: called
* print 'in called'
