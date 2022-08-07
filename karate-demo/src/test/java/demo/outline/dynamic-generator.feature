@mock-servlet-todo
Feature: scenario outline using a dynamic generator function

@setup
Scenario:
    * def generator = function(i){ if (i == 10) return null; return { name: 'DynaCat' + i, age: i } }

Scenario Outline: cat name: <name>
    Given url demoBaseUrl
    And path 'cats'
    And request { name: '#(name)', age: '#(age)' }
    When method post
    Then status 200
    And match response == { id: '#number', name: '#(name)' }

    Examples:
    | karate.setup().generator |
    