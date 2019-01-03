@ignore
Feature: check that a failure populates scenario info correctly

Background:
* configure afterScenario = function(){ karate.log('*** MESSAGE:', karate.info.errorMessage); karate.call('fail-hook-log.feature') }
* configure afterFeature = function() { karate.log('%%% AFTER FEATURE') }

Scenario:
* def foo = 1
* match foo == 2
