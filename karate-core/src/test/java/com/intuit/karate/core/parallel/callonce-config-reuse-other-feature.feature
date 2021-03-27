@ignore
Feature:

Scenario:
  # purposely using __arg as it's a special var to pass context data
  * def otherFeature = __arg
  * print otherFeature.apiUrl
  * print otherFeature.fun()
  * print fun()
  * print apiUrl