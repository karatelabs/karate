Feature: test definition of env variable via code

@ignore
@demo
Scenario: test demo environment
  # adding @ignore tag for the other unit tests to ignore this one
  * print "env: " + karate.env
  * match karate.env == 'demo'
  * def var = karate.get('testVariable')
  * match var == '#null'

@ignore
@devunit
Scenario: test demo environment
  # adding @ignore tag for the other unit tests to ignore this one
  * print "env: " + karate.env
  * match karate.env == 'devunit'
  # dev-env-property is defined in the karate-config-devunit.js file
  # which should only be loaded with the environment "devunit"
  * match testVariable == 'unit testing dev env'