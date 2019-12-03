Feature:

Scenario Outline: name is <name> and age is <age>
# ensure nested function is not lost in dynamic-scenario setup deep-copy
* match myUtils.hello() == 'hello world'
* def name = '<name>'
* match name == "#? _ == 'Bob' || _ == 'Nyan'"
* def title = karate.info.scenarioName

Examples:
| read('cats.json') |
