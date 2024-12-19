Feature: my feature

Scenario: single scenario
    * match karate.scenarioOutline == null

Scenario Outline: outline
    description of outline

    # Confirm table index
    * match __tableNum == <table>
    * match karate.scenario.exampleTableIndex == <table>

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
                        {table: 0, num: 0, executedSoFar: 1},
                        {table: 0, num: 1, executedSoFar: 2},
                    ],
                    "tags": ["@one", "@two"]
                },
                {
                    "data": [
                        {table: 1, num: 0, executedSoFar: 3},
                        {table: 1, num: 1, executedSoFar: 4},
                    ],
                    "tags": ["@three", "@four"]
                },
            ],
            "scenarioResults": "#[<executedSoFar>] #object"
        }
    """

    @one @two
    Examples:
    | table! | num! | executedSoFar! |
    | 0      | 0    | 1              |
    | 0      | 1    | 2              |

    @three @four
    Examples:
    | table! | num! | executedSoFar! |
    | 1      | 0    | 3              |
    | 1      | 1    | 4              |