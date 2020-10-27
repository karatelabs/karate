Feature: browser automation 1

  Scenario: lighthouse impl

    Given lightHouseWrapper 'https://github.com/login' accessibility,performance
    * print LHR.categories.accessibility.score
    * print LHR.categories.performance.score
    #* match LHR.categories.accessibility.score == 0.96
    #* match LHR.categories.performance.score == 1
