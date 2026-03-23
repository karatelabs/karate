Feature: Scenario Outline driver test (simulating v1 00_outline.feature)
  Tests driver inheritance across call levels with Scenario Outline entry point.
  Driver is initialized in the called orchestration feature using inherited driverConfig.

Scenario Outline: <testConfig>
* call read('outline-orchestration.feature')

Examples:
| testConfig | testLabel!                           |
| default    | { name: 'default', timeout: 5000 }   |
