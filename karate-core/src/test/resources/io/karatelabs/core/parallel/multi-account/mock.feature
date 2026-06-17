Feature: Mock backend for the multi-account demo

  # POST /auth/provision { preset, market } -> { token, accountId, preset, market }
  # token encodes preset+market so the test can verify each scenario uses its own token.
  Scenario: methodIs('post') && pathMatches('/auth/provision')
    * def preset = request.preset
    * def market = request.market
    * def token = preset + '-' + market + '-' + karate.uuid().substring(0, 8)
    * def accountId = market + '-' + karate.uuid().substring(0, 8)
    * def response = { token: '#(token)', accountId: '#(accountId)', preset: '#(preset)', market: '#(market)' }

  # GET /api/users -- echoes the bearer token back so we can prove no token bleed across scenarios.
  # headerValue('Authorization') is case-insensitive and returns the value, no list indexing needed.
  Scenario: methodIs('get') && pathMatches('/api/users')
    * def token = headerValue('Authorization').substring(7)
    * def response = { token: '#(token)', users: [{ id: 1, name: 'user-of-' + token }] }

  Scenario: methodIs('get') && pathMatches('/api/orders')
    * def token = headerValue('Authorization').substring(7)
    * def response = { token: '#(token)', orders: [{ id: 'order-1' }, { id: 'order-2' }] }
