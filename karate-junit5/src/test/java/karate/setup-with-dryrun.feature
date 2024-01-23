Feature: names

@setup
Scenario: first hello world
    * def names = [{"name": "dynamic_1"}, {"name": "dynamic_2"}]
    * print 'setup'

@dynamic
Scenario Outline: Dynamic examples from setup <name>
    * print '<name>'
    Examples:
        | karate.setup().names |

@static
Scenario Outline: Static examples <name>
    * print '<name>'
    Examples:
        | name   |
        | static_1 |
        | static_2 |