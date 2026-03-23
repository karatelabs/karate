Feature: Parallel feature 2

  Scenario: feature 2 scenario 1
    * def y = 10
    * match y == 10
    * match sharedFunction('f2s1') == 'hello f2s1'

  Scenario: feature 2 scenario 2
    * def y = 20
    * match y == 20
    * match baseFunction('f2s2') == 'base:f2s2'
