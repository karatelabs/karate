Feature:

Scenario:
* def sayHello = function() { return 'hello world!' }
* def reuseExistingFunction =
  """
  function() {
    return sayHello();
  }
  """
