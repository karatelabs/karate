Feature: Run the same API features against many account configurations, in parallel.

  # Pattern: run the same features against N different account configurations,
  # where each account needs to be provisioned via an external auth call first.
  #
  # The shape:
  #   1. @setup loads accounts.json -- one row per account configuration.
  #   2. Each Scenario Outline row runs as its own scenario (parallel-ready).
  #   3. Each scenario calls provision.feature ONCE to mint its own session token
  #      via the (mocked) external auth service.
  #   4. Each scenario then calls the real API features (users.feature, orders.feature)
  #      passing that account / token forward.
  #
  # No JVM-global state, no JUnit @TestFactory gymnastics, single Suite, single report.

  @setup
  Scenario:
    * def data = read('accounts.json')

  Scenario Outline: account <name> (preset=<preset>, market=<market>)
    # Outline columns (name, preset, market) are bound as proper JS variables in scope --
    # use them directly, no <> interpolation needed in the step body.

    # 1. Provision this account via the (mocked) external auth service.
    * def provisioned = call read('provision.feature') { preset: '#(preset)', market: '#(market)' }
    * def account = provisioned.account
    * match account.preset == preset
    * match account.market == market

    # 2. Exercise the API features with this account's token.
    * call read('users.feature') { account: '#(account)' }
    * call read('orders.feature') { account: '#(account)' }

    Examples:
      | karate.setup().data |
