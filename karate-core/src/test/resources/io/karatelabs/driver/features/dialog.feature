@lock=*
Feature: Dialog Tests
  Dialog handling (alert, confirm, prompt)

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/dialog'

  Scenario: Alert dialog auto-handled
    # Set up a handler that auto-accepts alerts
    * def handler = function(d) { d.accept() }
    * onDialog(handler)
    * click('#alert-btn')
    * waitForText('#result', 'Alert was shown')
    * def resultText = text('#result')
    * match resultText == 'Alert was shown'
    * onDialog(null)

  Scenario: Confirm dialog accept
    * def handler = function(d) { d.accept() }
    * onDialog(handler)
    * click('#confirm-btn')
    * waitForText('#result', 'Confirm result: true')
    * def resultText = text('#result')
    * match resultText == 'Confirm result: true'
    * onDialog(null)

  Scenario: Confirm dialog dismiss
    * def handler = function(d) { d.dismiss() }
    * onDialog(handler)
    * click('#confirm-btn')
    * waitForText('#result', 'Confirm result: false')
    * def resultText = text('#result')
    * match resultText == 'Confirm result: false'
    * onDialog(null)

  Scenario: Prompt dialog accept with text
    * def handler = function(d) { d.accept('John Doe') }
    * onDialog(handler)
    * click('#prompt-btn')
    * waitForText('#result', 'Prompt result: John Doe')
    * def resultText = text('#result')
    * match resultText == 'Prompt result: John Doe'
    * onDialog(null)

  Scenario: Prompt dialog dismiss
    * def handler = function(d) { d.dismiss() }
    * onDialog(handler)
    * click('#prompt-btn')
    * waitForText('#result', 'Prompt result: null')
    * def resultText = text('#result')
    * match resultText == 'Prompt result: null'
    * onDialog(null)
