Feature: custom tags

@requirement=CALC-2
@test=CALC-2
Scenario: custom tags are present in xml
    * print 'xray'

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
