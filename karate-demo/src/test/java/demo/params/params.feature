Feature: parameters (also see the search demo)

Scenario Outline: first scenario as an outline 
    (to prevent a particular bug from re-appearing)
    
    Given url demoBaseUrl
    And path 'echo'
    And param p = <param>
    When method get
    Then status 200
    And match response == { p: <value> }

Examples:
    | param      | value      |
    | 'a'        | ['a']      |
    | ['a', 'b'] | ['a', 'b'] |

Scenario: comma delimited param value (normal)
    Given url demoBaseUrl
    And path 'echo'
    And param fieldList = 'name,id,date_created,date_modified,created_id,modified_id'
    When method get
    Then status 200
    And match response.fieldList[0] == 'name,id,date_created,date_modified,created_id,modified_id'

@mock-servlet-todo
Scenario: comma delimited param value (in url)
    Given url demoBaseUrl + '?fieldList=name,id,date_created,date_modified,created_id,modified_id'
    And path 'echo'
    When method get
    Then status 200
    And match response.fieldList[0] == 'name,id,date_created,date_modified,created_id,modified_id'

Scenario: parameter which is an array and dynamic
    * def fun =
    """
    function(){
        var temp = [];
        for(var i = 0; i < 5; i++) {          
            temp.push('r');
        }
        return temp;
    }
    """
    * json array = fun()
    Given url demoBaseUrl
    And path 'echo'
    And params { pl: '#(array)' }
    When method get
    Then status 200
    And match response == { pl: ['r', 'r', 'r', 'r', 'r'] }
    