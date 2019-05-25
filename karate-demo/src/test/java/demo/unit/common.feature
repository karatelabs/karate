@ignore
Feature:

  Scenario:
    * def toCat = function(x){ return karate.toBean(x, 'com.intuit.karate.demo.domain.Cat') }
    # second argument (true) is to strip keys with null values
    * def toJson = function(x){ return karate.toJson(x, true) }