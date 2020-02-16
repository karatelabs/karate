Feature: calling a re-usable feature that inits the driver instance

Scenario:
  * callonce read('test-02-called.feature')
  Given input('#eg01InputId', 'hello world')
  When click('input[name=eg01SubmitName]')
  Then match text('#eg01DivId') == 'hello world'
  And match value('#eg01InputId') == 'hello world'  

    