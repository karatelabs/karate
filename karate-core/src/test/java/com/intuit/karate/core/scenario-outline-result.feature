Feature: my feature

Scenario: single scenario
    * match karate.scenarioOutline == null

Scenario Outline: outline
    description of outline

    # Confirm example index within table
    * match __num == <num>
    * match karate.scenario.exampleIndex == <num>

    # Confirm scenarioOutline result
    * match karate.scenarioOutline == 
    """
        {
            "sectionIndex": 1,
            "numScenariosToExecute": 4,
            "exampleTableCount": 2,
            "line": 6,
            "name": "outline",
            "numScenariosExecuted": <executedSoFar>,
            "description": "description of outline",
            "exampleTables": [
                {
                    "data": [
                        {num: 0, executedSoFar: 1},
                        {num: 1, executedSoFar: 2},
                    ],
                    "tags": ["@one", "@two"]
                },
                {
                    "data": [
                        {num: 0, executedSoFar: 3},
                        {num: 1, executedSoFar: 4},
                    ],
                    "tags": ["@three", "@four"]
                },
            ],
            "scenarioResults": "#[<executedSoFar>] #object"
        }
    """

    @one @two
    Examples:
    | num! | executedSoFar! |
    | 0    | 1              |
    | 1    | 2              |

    @three @four
    Examples:
    | num! | executedSoFar! |
    | 0    | 3              |
    | 1    | 4              |