Feature: Dynamic outline parallel execution

  @setup
  Scenario:
    * def data = [{ name: 'alpha' }, { name: 'beta' }, { name: 'gamma' }, { name: 'delta' }]

  Scenario Outline: parallel outline - <name>
    # Each outline example runs in parallel
    * def myName = name
    * print 'Processing:', myName

    # Verify config function works
    * match sharedFunction('world') == 'hello world'

    # Verify karate-base.js function works
    * match baseFunction(myName) == 'base:' + myName

    Examples:
      | karate.setup().data |
