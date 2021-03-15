@ignore
Feature:

Background:
* table data
    | input |
    | 1     |
    | 2     |
    | 3     |
    | 4     |
    | 5     |

Scenario:
* def result = call read('called.feature') data
