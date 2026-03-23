Feature: Called from outline

  Scenario:
    * def calledMessage = 'from called feature'
    * match baseFunction('called') == 'base:called'
