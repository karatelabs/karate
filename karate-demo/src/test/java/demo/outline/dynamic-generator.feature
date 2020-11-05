@mock-servlet-todo
Feature: scenario outline using a dynamic generator function

Background:
    * def generator = function(i){ if (i == 20) return null; return { name: 'cat' + i, age: i } }

Scenario Outline: cat name: <name>
    Given url demoBaseUrl
    And path 'cats'
    And request { name: '#(name)', age: '#(age)' }
    When method post
    Then status 200
    And match response == { id: '#number', name: '#(name)' }

    Examples:
    | generator |
    