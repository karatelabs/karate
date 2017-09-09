Feature: demo reading files and using in a test

Background:
* url demoBaseUrl
# a POST to /search will simply echo the request payload
* path 'search'

Scenario: using json from a file
    * table employees
        | firstName | lastName |
        | 'John'    | 'Smith'  |
        | 'Jane'    | 'Doe'    |
    Given request ({ employees: employees })
    When method post
    Then status 200
    And match $ == read('sample.json')

Scenario: using xml from a file
    * set payload /employees
        | path                  | value    |
        | employee[1]/firstName | 'John'   |
        | employee[1]/lastName  | 'Smith'  |
        | employee[2]/firstName | 'Jane'   |
        | employee[2]/lastName  | 'Doe'    |        

    Given request payload
    When method post
    Then status 200
    And match / == read('sample.xml')
