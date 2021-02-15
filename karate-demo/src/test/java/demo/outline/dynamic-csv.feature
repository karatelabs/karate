@mock-servlet-todo
Feature: scenario outline using a dynamic table
    from a csv file

Scenario Outline: cat name: <name>
    Given url demoBaseUrl
    And path 'cats'
    And request { name: '#(name)', age: '#(age)' }
    When method post
    Then status 200
    And match response == { id: '#number', name: '#(name)' }

    # the single cell can be any valid karate expression
    # and even reference a variable defined in the Background
    Examples:
    | read('kittens.csv') |
    