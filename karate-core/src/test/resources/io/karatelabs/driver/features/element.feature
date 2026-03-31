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

  # ========== ScriptAll ==========

  Scenario: Script all elements
    * def values = scriptAll('#country option', '_.value')
    * match values contains 'us'
    * match values contains 'uk'
    * match values contains 'ca'
    * match values contains 'au'

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
