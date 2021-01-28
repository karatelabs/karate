Feature: Test Feature with function from global config

Background:
  * def data = [ { name: 'value' }, { name: 'value2' }, { name: 'value3' }, { name: 'value4' } ]

Scenario Outline:
  * def foo = configUtilsJs.someText
  * def bar = configUtilsJs.someFun()
  * def res = call read('called2.feature')
  * def test = configUtils.hello()
  * match foo == 'hello world'
  * match bar == 'hello world'
  * match res contains { calledBar: 'hello world' }
  * match test == { helloVar: 'hello world' }

Examples:
  | data |