Feature: Parallel feature 3

  Scenario: feature 3 scenario 1
    * def z = 100
    * match z == 100
    * match sharedFunction('f3s1') == 'hello f3s1'

  Scenario: feature 3 scenario 2
    * def z = 200
    * match z == 200
    * match baseFunction('f3s2') == 'base:f3s2'
