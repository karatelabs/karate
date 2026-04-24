Feature: beforeScenario and afterScenario hooks rendering

  @hook-before-call
  Scenario: before hook calls helper feature
    # beforeScenario is installed from karate-config.js (tag-guarded) and calls
    # hooks-helper.feature, so the synthetic hook step at the top of this scenario
    # should render the nested helper feature result.
    * def x = 1
    * match x == 1

  Scenario: after hook calls helper feature
    * configure afterScenario =
    """
    function() {
      karate.log('afterScenario: tearing down');
      karate.call('classpath:io/karatelabs/report/hooks-helper.feature');
    }
    """
    * def y = 2
    * match y == 2

  @wip
  Scenario: afterScenario fails with match-style error
    * configure afterScenario =
    """
    function() {
      var r = karate.match('en', 'enrt');
      if (!r.pass) karate.fail('E2E validation failed: ' + r.message);
    }
    """
    * def z = 3
    * match z == 3

  @wip
  Scenario: body fails and afterScenario also fails
    * configure afterScenario = function() { karate.fail('teardown boom') }
    * def a = 1
    * match a == 99
