Feature: cusotm tags test

@requirement=CALC-2
@test=CALC-2
Scenario: xray simple scenario
  * print 'xray simple example'

@requirement=CALC-3
Scenario: xray link to requirement
  * print 'xray simple requirement'


@test=CALC-4
Scenario: xray link to test
  * print 'xray simple test'

Scenario: no tags
  * print 'without additional tags'

@test_tag=CALC-5
Scenario: invalid tags
  * print 'with invalid tags'

