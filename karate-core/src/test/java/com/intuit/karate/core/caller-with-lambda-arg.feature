@ignore
Feature:

Background:
  * def dataFunc =
  """
    function() {
      var element = {
        "something": "value"
      };

      var intBinaryOperator = Java.type('java.util.function.IntBinaryOperator');
      var plusOperation = Java.extend(intBinaryOperator, {
          applyAsInt: function(left, right) {
              return left + right;
          }
      });

      var featureResultTestClass = Java.type('com.intuit.karate.core.FeatureResultTest');
      featureResultTestClass.addLambdaFunctionToMap(element);
      element.sum = new plusOperation();

      return element;
    }
  """
  * def elem = dataFunc()
  * def data = [ "#(elem)" ]

Scenario:
  # ensuring the called.feature returns success
  # passing data with a functional interface should be correctly printed
  # in Result obj
  * def input = 3
  * def result = call read('called.feature') data
  * match data[0].something == "value"
  * match data[0].javaSum(1,3) == 4
  * match data[0].sum(1,3) == 4

  * def left = 1
  * def right = 2
  * def payload = { "leftSide": #(left), "rightSide": #(right), "sum": '#(data[0].sum(left, right))' }
  * match payload == { "leftSide": 1, "rightSide": 2, "sum": '#? _ == 1+2' }
  * match payload == { "leftSide": 1, "rightSide": 2, "sum": '#? _ == data[0].sum( $.leftSide, $.rightSide)' }

