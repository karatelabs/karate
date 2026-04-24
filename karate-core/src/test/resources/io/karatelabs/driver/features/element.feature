Feature: Element Tests
  Element operations, inputs, clicks, selects, and waits

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/input'

  # ========== Basic Element State ==========

  Scenario: Element exists check
    * match exists('#username') == true
    * match exists('#email') == true
    * match exists('#nonexistent') == false

  Scenario: Text content
    * def text = text('h1')
    * match text == 'Input Test Page'

  Scenario: HTML content
    * def html = html('h1')
    * match html contains 'Input Test Page'
    * match html contains '<h1'

  Scenario: Get attribute
    * def placeholder = attribute('#username', 'placeholder')
    * match placeholder == 'Enter username'

  Scenario: Get property
    * def type = script("document.getElementById('email').type")
    * match type == 'email'

  Scenario: Script with arrow function
    * waitFor('h1')
    * def fn = () => document.title
    * def title = script(fn)
    * match title == 'Input Test'

  Scenario: Script with arrow function returning expression
    * def fn = () => document.querySelectorAll('input').length
    * def count = script(fn)
    * assert count >= 3

  Scenario: Element enabled check
    * match enabled('#username') == true
    * match enabled('#submit-btn') == true

  # ========== Input Operations ==========

  Scenario: Input text
    * input('#username', 'testuser')
    * def value = value('#username')
    * match value == 'testuser'

  Scenario: Clear input
    * input('#username', 'initial')
    * clear('#username')
    * def value = value('#username')
    * match value == ''

  Scenario: Input multiple fields
    * input('#username', 'john_doe')
    * input('#email', 'john@example.com')
    * input('#password', 'secret123')
    * match value('#username') == 'john_doe'
    * match value('#email') == 'john@example.com'
    * match value('#password') == 'secret123'

  Scenario: Input time field
    * input('#meeting-time', '14:30')
    * match value('#meeting-time') == '14:30'

  Scenario: Input date field
    * input('#birthday', '2026-03-05')
    * match value('#birthday') == '2026-03-05'

  Scenario: Input textarea
    * input('#bio', 'This is my biography.')
    * def value = value('#bio')
    * match value contains 'biography'

  Scenario: Input triggers DOM events
    # Clear event log
    * script('window.inputEvents = []')
    # Type into the field
    * input('#username', 'test')
    # Verify value is set
    * match value('#username') == 'test'
    # Verify input events were triggered (React/Vue compatibility)
    * def events = script('window.inputEvents')
    * assert events.length > 0
    # Check that at least one input event was fired for the username field
    * def inputEvents = events.filter(e => e.type == 'input' && e.target == 'username')
    * assert inputEvents.length > 0

  # ========== Click Operations ==========

  Scenario: Click button
    * input('#username', 'clicktest')
    * click('#submit-btn')
    * def output = text('#form-output')
    * match output contains 'clicktest'

  Scenario: Click clear button
    * input('#username', 'tobecleared')
    * click('#clear-btn')
    * def output = text('#form-output')
    * match output contains 'cleared'

  # ========== Select Operations ==========

  Scenario: Select by value
    * select('#country', 'us')
    * def selected = value('#country')
    * match selected == 'us'

  Scenario: Select by exact text
    * select('#country', '{}United Kingdom')
    * def selected = value('#country')
    * match selected == 'uk'

  Scenario: Select by text contains
    * select('#country', '{^}Austr')
    * def selected = value('#country')
    * match selected == 'au'

  Scenario: Select by index
    # Index 0 is "Select a country", index 1 is "United States"
    * select('#country', 1)
    * def selected = value('#country')
    * match selected == 'us'

  Scenario: Select by text fallback (no prefix)
    # When value doesn't match, should fall back to text match
    * select('#country', 'United States')
    * def selected = value('#country')
    * match selected == 'us'

  @cdp
  Scenario: Select triggers change event with bubbles
    # Register a listener to verify bubbling works
    * script("window.selectChanged = false; document.addEventListener('change', function(e) { if(e.target.id === 'country') window.selectChanged = true; })")
    * select('#country', 'uk')
    * def changed = script("window.selectChanged")
    * match changed == true

  # ========== Checkbox Operations ==========

  Scenario: Checkbox click
    # Initially unchecked
    * def checked = script("document.getElementById('agree').checked")
    * match checked == false
    # Click to check
    * click('#agree')
    * def checked = script("document.getElementById('agree').checked")
    * match checked == true
    # Click to uncheck
    * click('#agree')
    * def checked = script("document.getElementById('agree').checked")
    * match checked == false

  # ========== Element Class ==========

  Scenario: Locate element
    * def element = locate('#username')
    * match element.exists() == true
    * match element.getLocator() == '#username'

  Scenario: Locate non-existent element
    * def element = locate('#nonexistent')
    * match element.exists() == false

  Scenario: Element chaining
    * def element = locate('#username').clear().input('chained').focus()
    * match element.value() == 'chained'

  Scenario: Optional element
    * def exists = optional('#username')
    * match exists.isPresent() == true
    * def notExists = optional('#nonexistent')
    * match notExists.isPresent() == false

  # ========== LocateAll ==========

  Scenario: Locate all elements
    * def inputs = locateAll("input[type='text'], input[type='email'], input[type='password']")
    * assert inputs.length >= 3

  Scenario: Locate all options
    * def options = locateAll('#country option')
    * match options.length == 5

  # ========== Element Navigation (closest / matches) ==========

  Scenario: closest finds the nearest matching ancestor
    * def form = locate('#username').closest('form')
    * match form.exists() == true
    * match form.attribute('id') == 'test-form'

  Scenario: closest matches the element itself when it matches
    * def self = locate('#username').closest('input')
    * match self.exists() == true
    * match self.attribute('id') == 'username'

  Scenario: closest returns no match when no ancestor matches
    * def none = locate('#username').closest('.does-not-exist')
    * match none.exists() == false

  Scenario: closest supports attribute selectors
    * def labelled = locate('#username').closest('[id=test-form]')
    * match labelled.exists() == true
    * match labelled.attribute('id') == 'test-form'

  Scenario: closest chains with locateAll for sibling-style walks
    # v2 replacement for the v1 pattern e.parent.children — find row, enumerate cells
    * def inputs = locate('#username').closest('form').locateAll('input')
    * assert inputs.length >= 3

  Scenario: matches returns true when selector matches
    * match locate('#username').matches('input[type=text]') == true
    * match locate('#submit-btn').matches('button[type=submit]') == true

  Scenario: matches returns false when selector does not match
    * match locate('#username').matches('button') == false
    * match locate('#username').matches('.does-not-exist') == false

  Scenario: matches pairs with closest for conditional walks
    * def form = locate('#username').closest('form')
    * match form.matches('#test-form') == true

  # ========== script() and scriptAll() behaviors ==========
  # Regressions for #2803: plain strings passed to script() must reach the browser
  # unchanged — no implicit parenthesization that would break void method calls,
  # statements, or the comma operator.

  Scenario: Script all elements
    * def values = scriptAll('#country option', '_.value')
    * match values contains 'us'
    * match values contains 'uk'
    * match values contains 'ca'
    * match values contains 'au'

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

  Scenario: script() with comma operator in parens returns last expression
    # Must NOT be interpreted as multiple arguments to an outer call
    * def result = script("(1, 2, 3)")
    * match result == 3

  Scenario: script() returns value for value-producing expressions
    * def result = script("document.title")
    * match result == 'Input Test'

  Scenario: script() returns null for void expressions (undefined)
    # v1 behaviour: .click() returns undefined which surfaces as null in Karate
    * def result = script("document.getElementById('submit-btn').click()")
    * match result == null

  # ========== Position ==========

  Scenario: Get element position
    * def pos = position('#username')
    * match pos.x != null
    * match pos.y != null
    * match pos.width != null
    * match pos.height != null
    * assert pos.width > 0
    * assert pos.height > 0

  # ========== Wait Methods ==========

  Scenario: Wait for element
    * def element = waitFor('#username')
    * match element.exists() == true

  Scenario: Wait for text
    * def element = waitForText('h1', 'Input Test')
    * match element.exists() == true

  Scenario: Wait for enabled
    * def element = waitForEnabled('#submit-btn')
    * match element.enabled() == true

  Scenario: Wait until element condition
    * input('#username', 'waited')
    * def element = waitUntil('#username', "_.value === 'waited'")
    * match element.value() == 'waited'

  Scenario: Wait until expression
    * script('window.testFlag = true')
    * def result = waitUntil('window.testFlag === true')
    * match result == true

  Scenario: Wait for result count
    * def elements = waitForResultCount('.form-group', 8)
    * match elements.length == 8

  # ========== Scroll and Highlight ==========

  Scenario: Scroll to element
    * scroll('#bio')

  Scenario: Highlight element
    * highlight('#username')

  # ========== Focus ==========

  Scenario: Focus element
    * focus('#email')
    * def activeId = script('document.activeElement.id')
    * match activeId == 'email'

  # ========== XPath and Wildcard Locators ==========

  Scenario: XPath locator
    * def text = text('//h1')
    * match text == 'Input Test Page'

  Scenario: Wildcard locator exact match
    * def text = text('{h1}Input Test Page')
    * match text == 'Input Test Page'

  Scenario: Wildcard locator contains
    * def element = locate('{^button}Submit')
    * match element.exists() == true

  # ========== Full Form Submission ==========

  Scenario: Complete form submission
    * input('#username', 'johndoe')
    * input('#email', 'john@example.com')
    * input('#password', 'secret123')
    * select('#country', 'us')
    * input('#bio', 'Test biography')
    * click('#agree')
    * click('#submit-btn')
    * def output = text('#form-output')
    * match output contains 'johndoe'
    * match output contains 'john@example.com'
    * match output contains 'secret123'
    * match output contains 'us'
