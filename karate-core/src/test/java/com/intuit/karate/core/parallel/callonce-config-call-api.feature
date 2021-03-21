@ignore
Feature:

Scenario:
  * url serverUrl
  * path 'products'
  * method get
  * status 200
  * match response == '#array'