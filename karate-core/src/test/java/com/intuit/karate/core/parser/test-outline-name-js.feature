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
  * match title == karate.info.scenarioName

Examples:
| name   | age | title |
| Bob    | 10  | name is Bob and age is 10 |
| Nyan   | 5   | name is Nyan and age is 5 |


Scenario Outline: `name is ${name} and age is ${age}`
  * def name = '<name>'
  * match name == "#? _ == 'Bob' || _ == 'Nyan'"
  * match title == karate.info.scenarioName

Examples:
  | js_data |


Scenario Outline: `name is ${name.first} and age is ${age}`
  * match name.first == "#? _ == 'Bob' || _ == 'Nyan'"
  * match title == karate.info.scenarioName

Examples:
  | nested_js_data |


Scenario Outline: `name is ${name.first} ${name.last} \
                    and age is ${age}`
  * match name.first == "#? _ == 'Bob' || _ == 'Nyan'"
  * match name.last == "#? _ == 'Dylan' || _ == 'Cat'"
  * match title == karate.info.scenarioName

Examples:
  | name!  | age  | title |
  | { "first": "Bob", "last": "Dylan" }  | 10 | name is Bob Dylan and age is 10 |
  | { "first": "Nyan", "last": "Cat" }  | 5 | name is Nyan Cat and age is 5 |


# String interpolation allows you to use operators
Scenario: `one plus one equals ${1 + 1}`
  * match karate.info.scenarioName == "one plus one equals 2"

# String interpolation allows you to use operators
Scenario: if name is not entirely wrapped in backticks... won't be evaluated `one plus one equals ${1 + 1}`
  * match karate.info.scenarioName == "if name is not entirely wrapped in backticks... won't be evaluated `one plus one equals ${1 + 1}`"

# can even access the karate object
Scenario: `scenario execution (env = ${karate.env})`
  # the env is set on the unit test in FeatureParserTest.java
  * match karate.info.scenarioName == "scenario execution (env = unit-test)"

# functions can also be used, including access to the Java Interop API
Scenario: `math scenario: should return ${java.lang.Math.pow(2, 2)}`
  * def powResult = java.lang.Math.pow(2, 2)
  * match karate.info.scenarioName == "math scenario: should return " + powResult
  * match karate.info.scenarioName == "math scenario: should return 4"

# and if you really really have a need... you can wrap your scenario name with backtick to have a multi-line name
# note that by default any content after the first new line that does not include a backslah will be set as the scenario description
Scenario: `math scenario: should return ${java.lang.Math.pow(2, 2)} \
            because 2 is the base and 2 is the exponent \
            and 2^2=${java.lang.Math.pow(2, 2)}`
            and the next new line will be the description of your scenario... \
            which can also be multi-line
    * def powResult = java.lang.Math.pow(2, 2)
    * match karate.info.scenarioName == "math scenario: should return 4 because 2 is the base and 2 is the exponent and 2^2=4"


# if you don't add the backslash at the end of first line, the second line onwards will be the scenario description so in this case
# there's nothing to evaluate as there's no closing backslash
Scenario: `math scenario: should return ${java.lang.Math.pow(2, 2)}
          because 2 is the base and 2 is the exponent
          and 2^2=${java.lang.Math.pow(2, 2)}`
    * def powResult = java.lang.Math.pow(2, 2)
    * match karate.info.scenarioName == "`math scenario: should return ${java.lang.Math.pow(2, 2)}"