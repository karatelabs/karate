Feature:

@setup
Scenario:
    * def x = read('js-read-3.json')
    * def data = x.thirderror

Scenario Outline:
    * match id == '#number'

Examples:
    | karate.setup().data |
