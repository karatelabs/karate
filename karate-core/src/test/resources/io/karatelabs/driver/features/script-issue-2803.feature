Feature: script() - void DOM calls and statements (regression for #2803)
  https://github.com/karatelabs/karate/issues/2803
  script() was reported to wrap expressions in (...) which broke void method
  calls, var/let/const statements, and comma-operator expressions. These
  scenarios lock in the v1-compatible behaviour: plain strings passed to
  script() must reach the browser unchanged.

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/input'

  # ========== Void DOM Method Calls ==========

  Scenario: script() with void .click() on an element
    # .click() returns undefined; must not be invoked as a function
    * script("document.getElementById('submit-btn').click()")

  Scenario: script() with void .focus()
    * script("document.getElementById('username').focus()")
    * match script("document.activeElement.id") == 'username'

  Scenario: script() with void .scrollIntoView()
    * script("document.getElementById('bio').scrollIntoView()")

  Scenario: script() with void .dispatchEvent()
    * script("document.getElementById('username').dispatchEvent(new Event('focus'))")

  Scenario: script() with void sessionStorage.setItem()
    * script("sessionStorage.setItem('k2803', 'v2803')")
    * match script("sessionStorage.getItem('k2803')") == 'v2803'

  # ========== Statements ==========

  Scenario: script() with semicolon-separated statements
    * script("window.__t1_2803 = 1; window.__t2_2803 = 2")
    * match script("window.__t1_2803") == 1
    * match script("window.__t2_2803") == 2

  Scenario: script() with var declaration
    * script("var x = 42; window.__tvar_2803 = x")
    * match script("window.__tvar_2803") == 42

  Scenario: script() with let declaration
    * script("let x = 43; window.__tlet_2803 = x")
    * match script("window.__tlet_2803") == 43

  Scenario: script() with const declaration
    * script("const x = 44; window.__tconst_2803 = x")
    * match script("window.__tconst_2803") == 44

  # ========== Comma Operator ==========

  Scenario: script() with comma operator in parens returns last expression
    # Must NOT be interpreted as multiple arguments to an outer call
    * def result = script("(1, 2, 3)")
    * match result == 3

  # ========== Return-value Passthrough ==========

  Scenario: script() still returns value for value-producing expressions
    * def result = script("document.title")
    * match result == 'Input Test'

  Scenario: script() returns null for void expressions (undefined)
    # v1 behaviour: .click() returns undefined which surfaces as null in Karate
    * def result = script("document.getElementById('submit-btn').click()")
    * match result == null
