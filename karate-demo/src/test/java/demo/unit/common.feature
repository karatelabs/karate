@ignore
Feature:

  Scenario:
    * def catType = 'com.intuit.karate.demo.domain.Cat'
    * def Cat = Java.type(catType)
    * def toCat = function(x){ return karate.toBean(x, catType) }
    # second argument (true) is to strip keys with null values
    * def toJson = function(x){ return karate.toJson(x, true) }
