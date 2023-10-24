Feature:

Scenario:
* def sayHello = function() { return 'hello world!' }
* def temp =
  """
  function() {
    return sayHello();
  }
  """
* def reuseExistingFunction = karate.wrapFunction(temp)
