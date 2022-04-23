Feature: demo karate's equivalent of before and after hooks
    note that 'afterScenario' / 'afterFeature' if set up using 'configure'
    is not supported within features invoked using the 'call' or 'callonce' keywords 

Background:
# anything here is run before every scenario (and every Example row for Scenario Outline-s)
# use the 'callonce' keyword if you want "once only" control: https://github.com/karatelabs/karate#callonce
# any variable here is "global" to all scenarios
* def foo = 'hello'

# for custom code to run after every scenario / feature: https://github.com/karatelabs/karate#configure
# note that these can be complex JS functions that you can read from separate files and re-use in multiple features
# and you can give control to another (re-usable) feature via 'karate.call' if needed

# the JSON returned from 'karate.scenario' and 'karate.feature' is explained here: 
# https://github.com/karatelabs/karate/wiki/1.0-upgrade-guide#karateinfo-deprecated

* configure afterScenario = 
"""
function(){
  karate.log('after scenario:', karate.scenario.name);
  karate.call('after-scenario.feature', { caller: karate.feature.fileName });
}
"""

# note that afterFeature will not work with the JUnit runners
# use the Runner API instead: https://github.com/karatelabs/karate#parallel-execution

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
