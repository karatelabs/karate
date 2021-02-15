@ignore
Feature:

Scenario:
* def doSomething =
  """
    function(data) {
      data.forEach(
        function(elem) {
          elem.newKey = 'newValue'
        }
      )
      return data
    }
  """
