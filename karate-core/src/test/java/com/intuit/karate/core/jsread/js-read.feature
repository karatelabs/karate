Feature:

Background:
    * call read 'js-read-2.json'
    * def varInContext = callonce read('this:js-read-reuse.feature')
    * call read('this:js-read-reuse.feature')
    * call read 'this:js-read-reuse-2.feature'
    * def varInBackgroundContext = callonce read('this:js-read-reuse-only-background.feature')
    * call read('this:js-read-reuse-only-background.feature')

Scenario: reading json 1
    * def fun = function(){ var temp = read('js-read.json'); return temp.error[1].id }
    * def val = call fun
    * match val == 2

Scenario: reading json 2
    * def fun = function(){ var temp = karate.read('js-read.json'); return temp.error[1].id }
    * def val = call fun
    * match val == 2

Scenario: reading json, calling feature and reusing json
    * def fun = function(){ var temp = karate.read('js-read.json'); return temp.error[1].id }
    * def result = call read('js-read-called.feature@name=checkUsageOfFunc')

Scenario: reading json, calling feature and reusing json
    * call read 'js-read.json'
    * def result = call read('js-read-called.feature@name=checkReadingJsonKeys')

Scenario: reading json (with callonce), calling feature and reusing json
    * callonce read 'js-read.json'
    * def result = call read('js-read-called.feature@name=checkReadingJsonKeys')

Scenario: calling feature and reusing json read in background section
    * def result = call read('js-read-called.feature@name=checkReadingSecondJsonKeys')

Scenario: calling feature that will read a json file in its background (no scenario defined), then call a feature that uses data from that json
    * print storedVarBackground
    * match varInBackgroundContext.storedVarBackground.fourtherror[0].id == 1
    * match varInBackgroundContext.fourtherror[0].id == 1
    * def result = call read('js-read-called.feature@name=checkReadingFourthJsonKeys')


Scenario: calling feature that will read a json (with space after the read keyword) file and then call a feature that uses data from that json
# note that when reading the reusable feature like this: * call read 'this:js-read-reuse-2.feature'
# the scope of that feature file is not available in the caller
    * def matchVar2 = karate.get('storedVar2')
    * match matchVar2 == null
    * def matchFifthError = karate.get('fiftherror')
    * match matchFifthError == null


Scenario: calling feature that will read a json file and then call a feature that uses data from that json
    * match varInContext.storedVar.thirderror[0].id == 1
    * match varInContext.thirderror[0].id == 1
    * match storedVar.thirderror[0].id == 1
    * match thirderror[0].id == 1
    * def result = call read('js-read-called.feature@name=checkReadingThirdJsonKeys')

Scenario Outline: using a scenario outline, call feature that will read a json file and then call a feature that uses data from that json
    * match varInContext.storedVar.thirderror[0].id == <value>
    * match varInContext.thirderror[0].id == <value>
    * match storedVar.thirderror[0].id == <value>
    * match thirderror[0].id == 1
    * def result = call read('js-read-called.feature@name=checkReadingThirdJsonKeys')

Examples:
    | value |
    | 1     |
    | 1     |