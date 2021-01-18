Feature:

@name=checkUsageOfFunc
Scenario: trigger call fun from parent read feature
    * def val = call fun
    * match val == 2

@name=checkReadingJsonKeys
Scenario: check json data from parent read feature
    #* print error
    * match error[0].id == 1

@name=checkReadingSecondJsonKeys
Scenario: check json data from parent read feature
    #* print error
    * match errorvar[0].id == 1

@name=checkReadingThirdJsonKeys
Scenario: check json data from parent read feature
    * match thirderror[0].id == 1

@name=checkReadingFourthJsonKeys
Scenario: check json data from parent read feature
    * match fourtherror[0].id == 1

