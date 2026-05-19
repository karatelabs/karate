@ignore
Feature: Exercise the /api/orders endpoint with an account session.

  Scenario:
    * url serverUrl
    * path 'api', 'orders'
    * header Authorization = 'Bearer ' + account.token
    * method get
    * status 200
    * match response.token == account.token
    * match response.orders == '#[2]'
