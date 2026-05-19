@ignore
Feature: Provision a per-account session by calling the auth service.

  # Arguments: preset, market (passed by the caller via `call read(...) arg`).
  # Returns: account = { token, accountId, preset, market } -- to be reused as the
  # session for that account across all subsequent API features.

  Scenario:
    * url serverUrl
    * path 'auth', 'provision'
    * request { preset: '#(preset)', market: '#(market)' }
    * method post
    * status 200
    * def account = response
