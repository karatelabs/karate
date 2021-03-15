Feature: continue on step failure keyword

Background:
  * configure continueOnStepFailure = true

Scenario: test
  * def var = 'foo'
  * configure continueOnStepFailure = true
  * match var == 'bar'
  * match var == 'pub'
  * match var == 'crawl'
  * match var == 'foo'
  * configure continueOnStepFailure = false
  * match var == 'foo'
  * match var == 'bar2'

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
