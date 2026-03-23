Feature: Parallel feature 1

  Scenario: feature 1 scenario 1
    * def x = 1
    * match x == 1
    * match sharedFunction('f1s1') == 'hello f1s1'

  Scenario: feature 1 scenario 2
    * def x = 2
    * match x == 2
    * match baseFunction('f1s2') == 'base:f1s2'
