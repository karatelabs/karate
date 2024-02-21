Feature: continue on step failure keyword

Background:
  * configure continueOnStepFailure = true

Scenario: test
  * def tmp = 'foo'
  * configure continueOnStepFailure = true
  * match tmp == 'bar'
  * match tmp == 'pub'
  * match tmp == 'crawl'
  * match tmp == 'foo'
  * configure continueOnStepFailure = false
  * match tmp == 'foo'
  * match tmp == 'bar2'

Scenario Outline: hello <name>
  * print 'name:', name
  * match name == 'foo'
  * match name == 'bar'
  * configure continueOnStepFailure = false
  * match name == '<name>'
  * match name == 'a failure'
  * match name == 'skipped'

Examples:
  | name |
  | foo  |
  | bar  |

Scenario Outline: hello <name>
  * print 'name:', name
  * match name == 'foo'
  * match name == 'bar'
  * configure continueOnStepFailure = { enabled: false, continueAfter: true }
  * match name == '<name>'
  * match name == 'a failure'
  * match name == 'skipped'

  Examples:
    | name |
    | foo  |
    | bar  |
