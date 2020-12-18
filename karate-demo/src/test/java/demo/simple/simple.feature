Feature: simple sanity checks that should never fail

  Scenario: using regular expressions doesn't cause an exception

    * print 'regex ng'.replace(/ng/, 'ok')
