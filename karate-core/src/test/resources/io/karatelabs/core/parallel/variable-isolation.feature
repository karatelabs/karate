Feature: Variable isolation between parallel scenarios

  # Each scenario should have its own variable scope
  # Variables defined in one scenario should not leak to others

  Scenario: isolation test 1
    * def myVar = 'scenario1'
    * def myObject = { name: 'obj1', value: 100 }
    # Wait a bit to allow other scenarios to potentially overwrite
    * def wait = function() { java.lang.Thread.sleep(50) }
    * eval wait()
    # Verify our variables are still intact
    * match myVar == 'scenario1'
    * match myObject.name == 'obj1'
    * match myObject.value == 100

  Scenario: isolation test 2
    * def myVar = 'scenario2'
    * def myObject = { name: 'obj2', value: 200 }
    * def wait = function() { java.lang.Thread.sleep(30) }
    * eval wait()
    * match myVar == 'scenario2'
    * match myObject.name == 'obj2'
    * match myObject.value == 200

  Scenario: isolation test 3
    * def myVar = 'scenario3'
    * def myObject = { name: 'obj3', value: 300 }
    * def wait = function() { java.lang.Thread.sleep(40) }
    * eval wait()
    * match myVar == 'scenario3'
    * match myObject.name == 'obj3'
    * match myObject.value == 300

  Scenario: isolation test 4
    * def myVar = 'scenario4'
    * def myObject = { name: 'obj4', value: 400 }
    * def wait = function() { java.lang.Thread.sleep(20) }
    * eval wait()
    * match myVar == 'scenario4'
    * match myObject.name == 'obj4'
    * match myObject.value == 400
