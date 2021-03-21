Feature:

Background:
  # 20sec delay
  * configure responseDelay = 20000

Scenario: pathMatches('/products')
  * def response = [ { foo: bar } ]

Scenario: pathMatches('/products/slow')
  * def response = [ { foo: bar } ]