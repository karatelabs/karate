@lock=* @cdp
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

  # Regression guard for a script() that itself opens a blocking JS dialog
  # A script() that itself opens a blocking JS dialog (confirm/alert/prompt)
  # must not throw — the dialog text must be readable via driver.dialogText
  # and the dialog must be dismissable with dialog(true|false).
  Scenario: Script that opens confirm dialog without a handler
    * script("confirm('Hello World!')")
    * match driver.dialogText == 'Hello World!'
    * dialog(false)
    * def cleared = driver.dialogText
    * match cleared == null

  Scenario: Script that opens alert dialog without a handler
    * script("alert('boom!')")
    * match driver.dialogText == 'boom!'
    * dialog(true)
    * def cleared = driver.dialogText
    * match cleared == null

  # Regression guard: an explicit navigation past a page's beforeunload handler
  # must not hang. With no onDialog handler the driver auto-confirms the
  # "Leave page?" prompt (the caller asked to leave). Chrome only raises
  # beforeunload after a trusted gesture, so input() (CDP keypresses) primes it;
  # the navigation must then land instead of blocking to the CDP timeout.
  Scenario: Navigate away from a page with a beforeunload handler
    * driver 'data:text/html,<body><input id="x"><script>window.onbeforeunload=function(e){e.preventDefault();return "stay"}</script></body>'
    * input('#x', 'hello')
    * driver 'data:text/html,<h1 id="landed">Landed</h1>'
    * waitFor('#landed')
    * match text('#landed') == 'Landed'

  # Regression guard for click() on an element whose onclick opens a blocking dialog
  # click() on an element whose onclick opens a blocking dialog must not throw.
  # Previously the post-action BaseElement.of() re-ran exists(), which hit the
  # "dialog is blocking" fail-fast check in cdpEval.
  Scenario: Click that opens alert dialog without a handler
    * driver 'about:blank'
    * script("document.body.innerHTML = '<button id=\"alertBtn\" onclick=\"alert(\\'Click!\\')\">X</button>'")
    * click('#alertBtn')
    * match driver.dialogText == 'Click!'
    * dialog(true)
    * def cleared = driver.dialogText
    * match cleared == null
