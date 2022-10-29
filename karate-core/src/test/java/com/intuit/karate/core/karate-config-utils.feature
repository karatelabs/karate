@ignore
Feature:

Scenario:
* def hello = function(){ return { helloVar: 'hello world' } }
* def existsFunction =
  """
    function(element){
        // solely for the purpose of testing the usage of a method
        // from the Driver
        return karate.get('driver').exists(element)
    }
  """
