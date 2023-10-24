Feature: Retry with setup

  @setup
  Scenario: Setup
    * def data = [{ value: 'b' }, { value: 'a' }]

  Scenario Outline: Scenario
    * def currentValGetter = function () { return java.lang.System.getProperty('CURRENT_VALUE') }
    * assert currentValGetter() == value

    Examples:
      | karate.setup().data |