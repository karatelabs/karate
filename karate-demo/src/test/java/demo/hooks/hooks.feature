Feature: demo karate's equivalent of before and after hooks

Background:
# anything here is run before every scenario (and every Example row for Scenario Outline-s)
# use the 'callonce' keyword if you want "once only" control: https://github.com/intuit/karate#callonce
# any variable here is "global" to all scenarios
* def foo = 'hello'

# for custom code to run after every scenario / feature: https://github.com/intuit/karate#configure
# note that these can be complex JS functions that you can read from separate files
# IMPORTANT: this will only work with the Karate "runners" so IDE "right-click-and run" will not work
# the JSON returned from 'karate.info' has the following properties:

# - featureDir
# - featureFileName
# - scenarioName
# - scenarioType (either 'Scenario' or 'Scenario Outline')
# - scenarioDescription
# - errorMessage (will be not-null if there is an error)

* configure afterScenario = 
"""
function(){
  var info = karate.info; 
  karate.log('after', info.scenarioType + ':', info.scenarioName);
}
"""
* configure afterFeature = function(){ karate.log('end feature') }

# for an explanation of 'karate.info' above: https://github.com/intuit/karate#the-karate-object

Scenario: first
* print foo

Scenario: second
* print foo

Scenario Outline:
* print <bar>

Examples:
| bar     |
| foo + 1 |
| foo + 2 |
