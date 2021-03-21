@ignore
Feature:

Scenario:
  * url __arg.apiUrl
  * path 'products'
  * method get
  * status 200
  * match response == '#array'