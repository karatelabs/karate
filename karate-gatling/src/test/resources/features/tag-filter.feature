Feature: Tag filter regression

  @perf
  Scenario: tagged scenario
    * def ran = 'perf'

  Scenario: untagged scenario
    * def ran = 'untagged'

  @other
  Scenario: other-tagged scenario
    * def ran = 'other'
