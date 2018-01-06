Feature: parameters (also see the search demo)

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
    

