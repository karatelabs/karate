@ignore
Feature:

Scenario:
  # reusing variable from 'callonce-config-api-url.feature'
  * url apiUrl
  * path 'products'
  * method get
  * status 200
  * match response == '#array'