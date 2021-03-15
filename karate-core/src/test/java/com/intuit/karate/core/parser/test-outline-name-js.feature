Feature:

Background:
  * def sum = function(x,y){ return x + y; }
  * def js_data =
  """
  [
    {
      "name": "Bob",
      "age": 10,
      "title": "name is Bob and age is 10"
    },
    {
      "name": "Nyan",
      "age": 5,
      "title": "name is Nyan and age is 5"
    }
  ]
  """

  * def nested_js_data =
  """
  [
    {
      "name": {
        "first": "Bob",
        "last": "Dylan"
      },
      "age": 10,
      "title": "name is Bob and age is 10"
    },
    {
      "name": {
        "first": "Nyan",
        "last": "Cat"
      },
      "age": 5,
      "title": "name is Nyan and age is 5"
    }
  ]
  """

Scenario Outline: `name is ${name} and age is ${age}`
  * def name = '<name>'
  * match name == "#? _ == 'Bob' || _ == 'Nyan'"
  * match title == karate.scenario.name

Examples:
| name   | age | title                     |
| Bob    | 10  | name is Bob and age is 10 |
| Nyan   | 5   | name is Nyan and age is 5 |


Scenario Outline: `name is ${name} and age is ${age}`
  * def name = '<name>'
  * match name == "#? _ == 'Bob' || _ == 'Nyan'"
  * match title == karate.scenario.name

Examples:
  | js_data |


Scenario Outline: `name is ${name.first} and age is ${age}`
  * match name.first == "#? _ == 'Bob' || _ == 'Nyan'"
  * match title == karate.scenario.name

Examples:
  | nested_js_data |


Scenario Outline: `name is ${name.first} ${name.last} and age is ${age}`
  * match name.first == "#? _ == 'Bob' || _ == 'Nyan'"
  * match name.last == "#? _ == 'Dylan' || _ == 'Cat'"
  * match title == karate.scenario.name

Examples:
  | name!                               | age | title                           |
  | { "first": "Bob", "last": "Dylan" } | 10  | name is Bob Dylan and age is 10 |
  | { "first": "Nyan", "last": "Cat" }  | 5   | name is Nyan Cat and age is 5   |


# String interpolation allows you to use operators
Scenario: one plus one equals ${1 + 1}
  * match karate.scenario.name == "one plus one equals 2"

Scenario: `one plus one equals ${1 + 1}`
  * match karate.scenario.name == "one plus one equals 2"

# can even access the karate object
Scenario: scenario execution (env = ${karate.env})
  # the env is set on the unit test in FeatureParserTest.java
  * match karate.scenario.name == "scenario execution (env = unit-test)"

# functions can also be used, including access to the Java Interop API
Scenario: math scenario: should return ${java.lang.Math.pow(2, 2)}
  * def powResult = java.lang.Math.pow(2, 2)
  * match karate.scenario.name == "math scenario: should return " + powResult
  * match karate.scenario.name == "math scenario: should return 4"