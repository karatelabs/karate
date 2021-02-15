@version=2.3
Feature: test tags

Scenario: test feature level tag
    * def tags = karate.tags
    * match tags == ['version=2.3']
    * def vals = karate.tagValues
    * match vals == { version: ['2.3'] }

@foo
Scenario: test feature and scenario tag
    * def tags = karate.tags
    * match tags == ['version=2.3', 'foo']
    * def vals = karate.tagValues
    * match vals == { version: ['2.3'], foo: [] }

@foo=bar
Scenario: test value tag
    * def tags = karate.tags
    * match tags contains ['version=2.3', 'foo=bar']
    * def vals = karate.tagValues
    * match vals == { version: ['2.3'], foo: ['bar'] }

@foo=bar,baz
Scenario: test multi value tag
    * def tags = karate.tags
    * match tags contains ['version=2.3', 'foo=bar,baz']
    * def vals = karate.tagValues
    * match vals == { version: ['2.3'], foo: ['bar', 'baz'] }

@version=5.6
Scenario: test scenario overrides tag
    * def tags = karate.tags
    * match tags contains ['version=2.3', 'version=5.6']
    * def vals = karate.tagValues
    * match vals == { version: ['5.6'] }

@tagdemo
Scenario Outline: examples partitioned by tag
    * def vals = karate.tagValues
    * match vals.region[0] == expected

    @region=US
    Examples:
        | expected |
        | US       |

    @region=GB
    Examples:
        | expected |
        | GB       |