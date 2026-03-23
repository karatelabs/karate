Feature: karate-base.js functions in parallel

  # Tests that functions defined in karate-base.js work correctly
  # across parallel scenarios

  Scenario: base function test 1
    * match baseFunction('alpha') == 'base:alpha'
    * match formatName('John', 'Doe') == 'John Doe'

  Scenario: base function test 2
    * match baseFunction('beta') == 'base:beta'
    * match formatName('Jane', 'Smith') == 'Jane Smith'

  Scenario: base function test 3
    * match baseFunction('gamma') == 'base:gamma'
    * match formatName('Bob', 'Wilson') == 'Bob Wilson'

  Scenario: base function test 4
    * match baseFunction('delta') == 'base:delta'
    * match formatName('Alice', 'Brown') == 'Alice Brown'
