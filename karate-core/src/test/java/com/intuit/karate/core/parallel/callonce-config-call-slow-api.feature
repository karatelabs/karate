@ignore
Feature:

Scenario:
  * url slowServerUrl
  * path 'products/slow'
  * method get
  * status 200
  * match response == '#array'