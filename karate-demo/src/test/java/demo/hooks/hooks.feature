Feature: demo karate's equivalent of before and after hooks
    note that 'afterScenario' / 'afterFeature' if set up using 'configure'
    is not supported within features invoked using the 'call' or 'callonce' keywords 

Background:
# anything here is run before every scenario (and every Example row for Scenario Outline-s)
# use the 'callonce' keyword if you want "once only" control: https://github.com/intuit/karate#callonce
# any variable here is "global" to all scenarios
* def foo = 'hello'

# for custom code to run after every scenario / feature: https://github.com/intuit/karate#configure
# note that these can be complex JS functions that you can read from separate files and re-use in multiple features
# and you can give control to another (re-usable) feature via 'karate.call' if needed

# the JSON returned from 'karate.info' has the following properties:
#   - featureDir
#   - featureFileName
#   - scenarioName
#   - scenarioType (either 'Scenario' or 'Scenario Outline')
#   - scenarioDescription
#   - errorMessage (will be not-null if the Scenario failed)

* configure afterScenario = 
"""
function(){
  var info = karate.info; 
  karate.log('after', info.scenarioType + ':', info.scenarioName);
  karate.call('after-scenario.feature', { caller: info.featureFileName });
}
"""

# for an explanation of 'karate.info' above: https://github.com/intuit/karate#the-karate-object
# note that 'karate.info' will not work within features invoked using the 'call' or 'callonce' keywords
# one limitation of afterScenario and afterFeature is that any feature steps involved will NOT appear
# in the JSON report output and HTML reports

* configure afterFeature = function(){ karate.call('after-feature.feature'); }

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

Scenario: 'after' hooks do not apply to called features
    # 'afterScenario' and 'afterFeature' only work in the "top-level" feature
    #  and are NOT supported in 'called' features
    * def result = call read('called.feature')
