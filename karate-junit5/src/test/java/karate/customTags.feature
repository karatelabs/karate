Feature: cusotm tags test

@first
@requirement=CALC-2
@test_key=CALC-2
Scenario: xray simple scenario
  * print 'xray simple example'

@second
@requirement=CALC-3
Scenario: xray link to requirement
  * print 'xray simple requirement'


@third
@test=CALC-4
Scenario: xray link to test
  * print 'xray simple test'

@fourth
Scenario: no tags
  * print 'without additional tags'


