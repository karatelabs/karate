@ignore
Feature: Exercise the /api/users endpoint with an account session.

  # Argument: account = { token, accountId, preset, market } from provision.feature.

  Scenario:
    * url serverUrl
    * path 'api', 'users'
    * header Authorization = 'Bearer ' + account.token
    * method get
    * status 200
    # The mock echoes the token it saw, so we can prove this scenario's token reached the server.
    * match response.token == account.token
    * match response.users == '#[1]'
